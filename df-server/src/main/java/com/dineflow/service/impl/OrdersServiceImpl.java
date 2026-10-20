package com.dineflow.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.dineflow.constant.MessageConstant;
import com.dineflow.constant.RedisKeyConstant;
import com.dineflow.dto.*;
import com.dineflow.entity.*;
import com.dineflow.exception.AddressBookBusinessException;
import com.dineflow.exception.BaseException;
import com.dineflow.exception.OrderBusinessException;
import com.dineflow.exception.OrderSubmitUnknownException;
import com.dineflow.mapper.OrdersMapper;
import com.dineflow.mapper.PaymentRecordMapper;
import com.dineflow.model.Coordinate;
import com.dineflow.order.service.OrderCreateTxService;
import com.dineflow.order.service.PaymentRecordTxService;
import com.dineflow.order.service.RefundRecordTxService;
import com.dineflow.order.service.RefundRecoveryService;
import com.dineflow.order.support.OrderRequestNormalizer;
import com.dineflow.properties.WeChatProperties;
import com.dineflow.result.PageResult;
import com.dineflow.service.IAddressBookService;
import com.dineflow.service.IOrdersService;
import com.dineflow.service.IShoppingCartService;
import com.dineflow.utils.BaiduMapClient;
import com.dineflow.utils.RedisIdWorker;
import com.dineflow.utils.ThreadLocalUtil;
import com.dineflow.utils.WeChatPayUtil;
import com.dineflow.vo.*;
import com.dineflow.websocket.WebSocketServer;
import com.wechat.pay.java.service.payments.jsapi.model.PrepayWithRequestPaymentResponse;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.refund.model.Refund;
import com.wechat.pay.java.service.refund.model.RefundNotification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * <p>
 * 订单表 服务实现类
 * </p>
 *
 * @author zainxx
 * @since 2026-09-08
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OrdersServiceImpl extends ServiceImpl<OrdersMapper, Orders> implements IOrdersService {

    private final RedisIdWorker redisIdWorker;

    private final StringRedisTemplate redisTemplate;

    //private final WeChatPayUtil weChatPayUtil;
    private final ObjectProvider<WeChatPayUtil> weChatPayUtilProvider;

    private final WeChatProperties weChatProperties;

    private final OrdersMapper ordersMapper;

    private final BaiduMapClient baiduMapClient;

    private final WebSocketServer webSocketServer;

    private final PaymentRecordTxService paymentRecordTxService;

    private final IShoppingCartService shoppingCartService;

    private final IAddressBookService addressBookService;

    public static final double DELIVERY_RANGE_METERS = 5000D;

    private final OrderCreateTxService orderCreateTxService;

    private final OrderRequestNormalizer orderRequestNormalizer;

    private final PaymentRecordMapper paymentRecordMapper;

    private final RefundRecordTxService refundRecordTxService;

    private final RefundRecoveryService refundRecoveryService;


    /**
     * C端-订单结算预览：检查营业状态，返回本次商品报价。
     */
    @Override
    public OrderPreviewVO previewOrder() {

        if (ThreadLocalUtil.getCurrentId() == null) {
            throw new OrderBusinessException(MessageConstant.USER_NOT_LOGIN);
        }

        checkShopOpen();

        return orderCreateTxService.calculateQuote();
    }

    /**
     * 检查营业状态，沿用现有Redis中的状态来源。
     */
    private void checkShopOpen() {

        String status = redisTemplate.opsForValue().get(RedisKeyConstant.SHOP_STATUS);

        if (!"0".equals(status) && !"1".equals(status)) {
            throw new OrderBusinessException(MessageConstant.SHOP_STATUS_ABNORMAL);
        }

        if ("0".equals(status)) {
            throw new OrderBusinessException("店铺暂未营业");
        }
    }

    /**
     * C端-用户提交订单
     */
    @Override
    public OrderSubmitVO submitOrder(OrdersSubmitDTO source) {

        Long userId = ThreadLocalUtil.getCurrentId();

        if (userId == null) {
            throw new OrderBusinessException(MessageConstant.USER_NOT_LOGIN);
        }

        // 1. 静态检查、规范化及摘要计算，不读取当前购物车和地址。
        OrdersSubmitDTO dto = orderRequestNormalizer.normalize(source);

        String requestHash = orderRequestNormalizer.calculateHash(userId, dto);

        // 2. 已创建的原订单优先返回。
        OrderSubmitVO existing = findReplayOrNull(userId, dto.getRequestId(), requestHash);

        if (existing != null) {
            return existing;
        }

        boolean creationAttempted = false;

        try {
            // 3. 首次创建才检查当前业务条件。
            orderCreateTxService.validateSubmitParams(dto);

            AddressBook checkedAddress = addressBookService.getAddressById(dto.getAddressBookId());

            if (checkedAddress == null) {
                throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
            }

            if (!Objects.equals(dto.getAddressVersion(), checkedAddress.getVersion())) {
                throw new AddressBookBusinessException("收货地址已变化，请重新加载并确认");
            }

            String wholeAddress = orderCreateTxService.buildWholeAddress(checkedAddress);

            // 提前拒绝已失效的报价，但事务内仍须再复核。
            orderCreateTxService.checkConfirmedQuote(dto, previewOrder());

            // 4. 地图请求仍在数据库事务外。
            Coordinate coordinate = baiduMapClient.getCoordinate(wholeAddress);

            double distance = baiduMapClient.getDrivingDistance(coordinate);

            if (!Double.isFinite(distance) || distance <= 0) {
                throw new OrderBusinessException("配送距离数据异常");
            }

            if (distance > DELIVERY_RANGE_METERS) {
                throw new AddressBookBusinessException(MessageConstant.DISTANCE_MORE_THAN_5KM);
            }

            checkShopOpen();

            // Redis取号仍在数据库事务外。
            String orderNumber = String.valueOf(redisIdWorker.nextId("order"));

            // 5. 经独立Spring Bean进入创建事务。
            creationAttempted = true;

            return orderCreateTxService.createOrder(userId, dto, checkedAddress, orderNumber, requestHash);

        } catch (RuntimeException failure) {

            // 6. 创建事务代理已退出。
            // 动态校验失败也可能与另一个同号请求成功创建交错。
            OrderSubmitVO committed = findReplayOrNull(userId, dto.getRequestId(), requestHash);

            if (committed != null) {
                return committed;
            }

            // 不记录完整DTO、SQL原文、备注和收件信息。
            log.warn("下单未取得成功结果，userId={}，requestId={}，failureType={}",
                    userId,
                    dto.getRequestId(),
                    failure.getClass().getSimpleName());

            if (isUniqueConstraint(failure, "uk_orders_user_request")) {
                // 请求号冲突但暂时未查到结果，保留原号继续确认。
                throw new OrderSubmitUnknownException(dto.getRequestId());
            }

            if (isUniqueConstraint(failure, "uk_orders_number")) {
                throw new OrderBusinessException("订单号冲突，请使用原请求号稍后重试");
            }

            if (failure instanceof PessimisticLockingFailureException) {
                throw new OrderBusinessException("购物车操作冲突，请使用原请求号重试");
            }

            if (failure instanceof BaseException businessFailure) {
                throw businessFailure;
            }

            if (failure instanceof DataIntegrityViolationException) {
                throw new OrderBusinessException("订单数据保存失败");
            }

            if (creationAttempted && (failure instanceof DataAccessException || failure instanceof TransactionException)) {
                // 包括COMMIT附近的连接异常，不能直接认定没有创建订单。
                throw new OrderSubmitUnknownException(dto.getRequestId());
            }

            throw new OrderBusinessException("订单提交失败，请使用原请求号稍后重试");
        }
    }

    /**
     * 查询本人同请求号的已提交订单。
     * 本方法及外层submitOrder均不开启事务。
     */
    private OrderSubmitVO findReplayOrNull(Long userId, String requestId, String requestHash) {

        Orders order;

        try {
            order = ordersMapper.findByUserAndRequestId(userId, requestId);
        } catch (DataAccessException failure) {
            log.warn("原订单查询暂不可用，userId={}，requestId={}，failureType={}",
                    userId,
                    requestId,
                    failure.getClass().getSimpleName());

            throw new OrderSubmitUnknownException(requestId);
        }

        if (order == null) {
            return null;
        }

        if (order.getRequestId() == null
                || order.getRequestId().startsWith("legacy-")
                || order.getRequestHash() == null
                || !order.getRequestHash().matches("[0-9a-f]{64}")) {
            throw new OrderBusinessException("该订单不能通过此请求重放");
        }

        if (!Objects.equals(requestHash, order.getRequestHash())) {
            throw new OrderBusinessException("请求号已用于其他下单内容，请勿复用");
        }

        return OrderSubmitVO.builder()
                .id(order.getId())
                .orderNumber(order.getNumber())
                .orderAmount(order.getAmount())
                .orderTime(order.getOrderTime())
                .build();
    }

    /**
     * 精确识别MySQL 1062及指定约束。
     * 不把所有DuplicateKeyException都当成请求号冲突。
     */
    private boolean isUniqueConstraint(Throwable failure, String constraintName) {

        Pattern pattern = Pattern.compile(
                "for key ['`](?:[^'`]+\\.)?"
                        + Pattern.quote(constraintName)
                        + "['`]",
                Pattern.CASE_INSENSITIVE
        );

        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {

            if (cause instanceof SQLException sql
                    && sql.getErrorCode() == 1062
                    && sql.getMessage() != null
                    && pattern.matcher(sql.getMessage()).find()) {
                return true;
            }
        }

        return false;
    }

    /**
     * C端-订单支付
     */
    @Override
    public OrderPaymentVO payment(OrdersPaymentDTO dto) {
        Long userId = ThreadLocalUtil.getCurrentId();
        if (userId == null) throw new OrderBusinessException(MessageConstant.USER_NOT_LOGIN);
        if (dto == null || StrUtil.isBlank(dto.getOrderNumber())
                || !Orders.wechatPay.equals(dto.getPayMethod())) {
            throw new OrderBusinessException("支付参数错误");
        }
        WeChatPayUtil util = weChatPayUtilProvider.getIfAvailable();
        if (util == null) throw new OrderBusinessException("当前环境未配置微信支付");
        User user = Db.getById(userId, User.class);
        if (user == null || StrUtil.isBlank(user.getOpenid())) {
            throw new OrderBusinessException(MessageConstant.ACCOUNT_NOT_FOUND);
        }
        PaymentRecord record = paymentRecordTxService.prepare(dto.getOrderNumber(), userId);
        PrepayWithRequestPaymentResponse response = util.pay(record.getPayNumber(), record.getAmount(),
                "DineFlow订单", user.getOpenid());
        return OrderPaymentVO.builder().timeStamp(response.getTimeStamp()).nonceStr(response.getNonceStr())
                .packageStr(response.getPackageVal()).signType(response.getSignType()).paySign(response.getPaySign()).build();
    }


    /**
     * C端-支付成功回调
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Orders paySuccess(Transaction transaction) {

        if (transaction == null) {
            throw new OrderBusinessException("支付通知为空");
        }

        if (transaction.getTradeState() != Transaction.TradeStateEnum.SUCCESS) {
            return null;
        }

        /*
         * 回调已经由现有 Controller 使用微信 SDK 验签、解密。
         * 这里继续校验业务信息。
         */
        if (StrUtil.isBlank(transaction.getMchid())
                || StrUtil.isBlank(transaction.getAppid())
                || !Objects.equals(weChatProperties.getMchid(), transaction.getMchid())
                || !Objects.equals(weChatProperties.getAppid(), transaction.getAppid())
                || StrUtil.isBlank(transaction.getOutTradeNo())
                || StrUtil.isBlank(transaction.getTransactionId())
                || transaction.getTransactionId().length() > 64
                || transaction.getAmount() == null
                || !"CNY".equals(transaction.getAmount().getCurrency())) {

            throw new OrderBusinessException("支付通知信息不一致");
        }

        // 先锁定订单，再检查状态
        Orders order = ordersMapper.selectByNumberForUpdate(
                transaction.getOutTradeNo()
        );

        if (order == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        if (!Orders.FUNDS_WECHAT.equals(order.getFundsSource())
                || !Orders.wechatPay.equals(order.getPayMethod())
                || !Objects.equals(moneyToFen(order.getAmount()), transaction.getAmount().getTotal())) {

            throw new OrderBusinessException("支付方式或金额不一致");
        }

        // 使用微信确认的支付成功时间
        LocalDateTime paidAt;

        try {
            paidAt = OffsetDateTime
                    .parse(transaction.getSuccessTime())
                    .atZoneSameInstant(ZoneId.of("Asia/Shanghai"))
                    .toLocalDateTime()
                    .withNano(0);
        } catch (RuntimeException e) {
            throw new OrderBusinessException("支付成功时间无效");
        }

        PaymentRecord record = paymentRecordTxService.getRecord(order);

        String transactionId = transaction.getTransactionId();

        // 即使是重复通知，也不能跳过交易号检查
        if (PaymentRecord.SUCCESS.equals(record.getStatus())
                && !Objects.equals(record.getTransactionId(), transactionId)) {

            throw new OrderBusinessException("微信支付交易号不一致");
        }

        // 正常支付：待付款、未支付
        boolean normal = Orders.PENDING_PAYMENT.equals(order.getStatus())
                && Orders.UN_PAID.equals(order.getPayStatus());

        // 晚到支付：订单已取消、本地仍未记录支付成功
        boolean late = Orders.CANCELLED.equals(order.getStatus())
                && Orders.UN_PAID.equals(order.getPayStatus());

        /*
         * 已经处理过的同一笔支付：
         * 1. 正常履约中的已支付订单；
         * 2. 已取消、退款中或已退款的订单。
         * 必须存在调用支付前已提交的支付意图，
         * 不为历史订单或缺失意图的结果补造流水。
         */
        boolean knownPayment = Objects.equals(
                order.getTransactionId(), transactionId)
                && ((Orders.PAID.equals(order.getPayStatus())
                && List.of(Orders.TO_BE_CONFIRMED, Orders.CONFIRMED, Orders.DELIVERY_IN_PROGRESS, Orders.COMPLETED).contains(order.getStatus()))
                || (Orders.CANCELLED.equals(order.getStatus())
                && (Orders.REFUNDING.equals(order.getPayStatus())
                || Orders.REFUND.equals(order.getPayStatus())))
        );

        if ((!normal && !late && !knownPayment)
                || ((normal || late)
                && PaymentRecord.SUCCESS.equals(record.getStatus()))) {

            throw new OrderBusinessException("订单与支付记录状态不一致");
        }

        // 可信成功可从待确认、已关闭或待核查收敛，成功事实不回退
        if (!PaymentRecord.SUCCESS.equals(record.getStatus())) {

            int changed = paymentRecordMapper.update(
                    null,
                    Wrappers.<PaymentRecord>lambdaUpdate()
                            .eq(PaymentRecord::getId, record.getId())
                            .in(PaymentRecord::getStatus, PaymentRecord.PENDING, PaymentRecord.CLOSED, PaymentRecord.MANUAL)
                            .set(PaymentRecord::getNextQueryTime, null)
                            .set(PaymentRecord::getLastError, null)
                            .set(PaymentRecord::getUpdateTime, LocalDateTime.now().withNano(0))
                            .set(PaymentRecord::getStatus, PaymentRecord.SUCCESS)
                            .set(PaymentRecord::getTransactionId, transactionId)
                            .set(PaymentRecord::getSuccessTime, paidAt)
            );

            if (changed != 1) {
                throw new OrderBusinessException("支付记录更新失败");
            }
        }

        // 重复结果不能再次改变订单状态
        if (knownPayment) {

            // 仍在退款中，可以继续使用原退款号申请同一笔退款
            return Orders.REFUNDING.equals(order.getPayStatus()) ? order : null;
        }

        int targetStatus = late ? Orders.CANCELLED : Orders.TO_BE_CONFIRMED;

        int targetPayStatus = late ? Orders.REFUNDING : Orders.PAID;

        boolean changed = lambdaUpdate()
                .eq(Orders::getId, order.getId())
                .eq(Orders::getStatus, order.getStatus())
                .eq(Orders::getPayStatus, Orders.UN_PAID)
                .set(Orders::getStatus, targetStatus)
                .set(Orders::getPayStatus, targetPayStatus)
                .set(Orders::getCheckoutTime, paidAt)
                .set(Orders::getTransactionId, transactionId)
                .update();

        if (!changed) {
            throw new OrderBusinessException("订单支付状态更新失败");
        }

        /*
         * 返回本次处理后的快照。
         * 晚到支付只修改支付信息，保留原取消原因和取消时间，
         * 不会重新激活已取消订单。
         */
        order.setStatus(targetStatus);
        order.setPayStatus(targetPayStatus);
        order.setCheckoutTime(paidAt);
        order.setTransactionId(transactionId);

        // 晚到支付：退款意图与本次支付结果在同一事务中保存。
        if (late) {
            refundRecordTxService.prepare(order.getNumber());
        }

        return order;
    }

    /**
     * 由 Controller 在 paySuccess() 返回后调用。
     * 这个方法不加 @Transactional。
     */
    @Override
    public void handlePaySuccessAfterCommit(Orders order) {
        if (order == null) {
            return;
        }

        if (Orders.CANCELLED.equals(order.getStatus())
                && Orders.REFUNDING.equals(order.getPayStatus())) {
            requestRefund(order);
            return;
        }

        // 支付事务已经提交；重复结果返回 null，晚到支付只走退款。
        try {
            Orders latest = getById(order.getId());
            if (latest == null
                    || !Orders.TO_BE_CONFIRMED.equals(latest.getStatus())
                    || !Orders.PAID.equals(latest.getPayStatus())) {
                return;
            }

            Map<String, Object> message = new HashMap<>();
            message.put("type", 1);
            message.put("orderId", latest.getId());
            message.put("content", "订单号：" + latest.getNumber());
            webSocketServer.sendToAllClient(JSONUtil.toJsonStr(message));
        } catch (Exception e) {
            log.warn("来单提醒发送失败，orderId={}, exceptionType={}",
                    order.getId(), e.getClass().getSimpleName());
        }
    }


    /**
     * 金额由元转为分。
     */
    private int moneyToFen(BigDecimal amount) {

        if (amount == null || amount.signum() <= 0) {
            throw new OrderBusinessException("订单金额无效");
        }

        try {
            return amount.movePointRight(2).intValueExact();
        } catch (ArithmeticException e) {
            throw new OrderBusinessException("订单金额精度或范围无效");
        }
    }

    /**
     * C端-历史订单查询
     */
    @Override
    public PageResult<HistoryOrdersQueryVO> historyOrdersQuery(OrdersPageQueryDTO ordersPageQueryDTO) {

        Long userId = ThreadLocalUtil.getCurrentId();

        // 1. 分页查询当前用户的订单
        Page<HistoryOrdersQueryVO> page = Page.of(
                ordersPageQueryDTO.getPage(),
                ordersPageQueryDTO.getPageSize()
        );

        Page<HistoryOrdersQueryVO> resultPage = ordersMapper.historyOrdersQuery(page, userId, ordersPageQueryDTO);

        List<HistoryOrdersQueryVO> records = resultPage.getRecords();

        if (CollUtil.isEmpty(records)) {
            return new PageResult<>(resultPage.getTotal(), records);
        }

        // 2. 获取当前页所有订单 id
        List<Long> orderIds = records.stream()
                .map(HistoryOrdersQueryVO::getId)
                .toList();

        // 3. 一次性查询当前页全部订单明细
        List<OrderDetail> orderDetails = Db.lambdaQuery(OrderDetail.class)
                .in(OrderDetail::getOrderId, orderIds)
                .list();

        // 4. 按 orderId 分组
        Map<Long, List<OrderDetail>> detailMap = orderDetails.stream()
                .collect(Collectors.groupingBy(OrderDetail::getOrderId));

        // 5. 将订单明细装入对应的 VO
        records.forEach(order -> order.setOrderDetailList(
                detailMap.getOrDefault(order.getId(), Collections.emptyList())
        ));

        return new PageResult<>(resultPage.getTotal(), records);
    }

    /**
     * C端-查询订单详情
     */
    @Override
    public OrderDetailVO getOrderDetail(Long id) {

        Long userId = ThreadLocalUtil.getCurrentId();

        // 查询当前用户的订单
        Orders order = lambdaQuery()
                .eq(Orders::getUserId, userId)
                .eq(Orders::getId, id)
                .one();

        if (order == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        // 查询订单明细
        List<OrderDetail> orderDetailList = Db.lambdaQuery(OrderDetail.class)
                .eq(OrderDetail::getOrderId, id)
                .list();

        // 组装返回对象
        OrderDetailVO orderDetailVO = BeanUtil.copyProperties(order, OrderDetailVO.class);

        orderDetailVO.setOrderDetailList(orderDetailList);

        paymentRecordTxService.fillPaymentInfo(orderDetailVO);
        refundRecordTxService.fillRefundInfo(orderDetailVO);

        return orderDetailVO;
    }

    /**
     * C端-取消订单
     */
    @Override
    public void cancelOrder(Long id) {

        Long userId = ThreadLocalUtil.getCurrentId();

        if (userId == null) {
            throw new OrderBusinessException(MessageConstant.USER_NOT_LOGIN);
        }

        if (id == null || id <= 0) {
            throw new OrderBusinessException("订单ID无效");
        }

        Orders order = lambdaQuery()
                .eq(Orders::getId, id)
                .eq(Orders::getUserId, userId)
                .one();

        if (order == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        if (cannotCancel(order, false)) {
            throw new OrderBusinessException("当前订单状态不可取消");
        }

        cancelAndRefund(order, "用户取消订单"
        );
    }

    /**
     * C端-再来一单
     */
    @Override
    public void oneMoreOrder(Long id) {
        // 查询原订单及订单明细，将商品重新加入当前用户购物车
        Long userId = ThreadLocalUtil.getCurrentId();

        // 1. 查询当前用户的原订单
        Orders order = lambdaQuery()
                .eq(Orders::getId, id)
                .eq(Orders::getUserId, userId)
                .one();

        if (order == null) {
            throw new OrderBusinessException("订单不存在");
        }

        // 2. 只有已完成或已取消订单允许再来一单
        if (!Orders.COMPLETED.equals(order.getStatus()) && !Orders.CANCELLED.equals(order.getStatus())) {
            throw new OrderBusinessException("当前订单状态不支持再来一单");
        }

        // 3. 查询原订单明细
        List<OrderDetail> orderDetailList = Db.lambdaQuery(OrderDetail.class)
                .eq(OrderDetail::getOrderId, id)
                .list();

        if (CollUtil.isEmpty(orderDetailList)) {
            throw new OrderBusinessException("订单明细不存在");
        }

        // 4. 将订单明细重新放入当前用户购物车
        for (OrderDetail detail : orderDetailList) {
            ShoppingCartDTO dto = new ShoppingCartDTO();
            dto.setDishId(detail.getDishId());
            dto.setSetmealId(detail.getSetmealId());
            dto.setDishFlavor(detail.getDishFlavor());

            // 原订单有几份，就复用加购方法追加几份。
            for (int i = 0; i < detail.getNumber(); i++) {
                shoppingCartService.addItemsToCart(dto);
            }
        }
    }

    /**
     * C端-用户催单
     */
    @Override
    public void urgeOrder(Long id) {
        Long userId = ThreadLocalUtil.getCurrentId();
        if (userId == null) throw new OrderBusinessException(MessageConstant.USER_NOT_LOGIN);
        Orders order = lambdaQuery().eq(Orders::getId, id).eq(Orders::getUserId, userId).one();
        if (order == null) throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);

        if (!Orders.FUNDS_WECHAT.equals(order.getFundsSource())
                || !Orders.PAID.equals(order.getPayStatus())
                || !List.of(Orders.TO_BE_CONFIRMED, Orders.CONFIRMED,
                Orders.DELIVERY_IN_PROGRESS).contains(order.getStatus())) {
            throw new OrderBusinessException("当前订单状态不可催单");
        }

        Map<String, Object> message = new HashMap<>();
        message.put("type", 2);
        message.put("orderId", order.getId());
        message.put("content", "订单号：" + order.getNumber());
        webSocketServer.sendToAllClient(JSONUtil.toJsonStr(message));
    }

    /**
     * 管理端-订单搜索
     */
    @Override
    public PageResult<OrderConditionSearchVO> orderConditionSearch(OrdersPageQueryDTO dto) {

        // 1. 条件分页查询订单
        Page<OrderConditionSearchVO> page = Page.of(dto.getPage(), dto.getPageSize());

        Page<OrderConditionSearchVO> resultPage = ordersMapper.orderConditionSearch(page, dto);

        List<OrderConditionSearchVO> records = resultPage.getRecords();

        // 当前页无数据
        if (CollUtil.isEmpty(records)) {
            return new PageResult<>(resultPage.getTotal(), records);
        }

        // 2. 获取当前页所有订单 ID
        List<Long> orderIds = records.stream()
                .map(OrderConditionSearchVO::getId)
                .toList();

        // 3. 批量查询当前页所有订单明细
        List<OrderDetail> orderDetailList = Db.lambdaQuery(OrderDetail.class)
                .in(OrderDetail::getOrderId, orderIds)
                .list();

        // 4. 按订单 ID 分组
        Map<Long, List<OrderDetail>> detailMap = orderDetailList.stream()
                .collect(Collectors.groupingBy(OrderDetail::getOrderId));

        // 5. 拼接菜品名称
        records.forEach(record -> {
            String orderDishes = detailMap.getOrDefault(record.getId(), Collections.emptyList())
                    .stream()
                    .map(OrderDetail::getName)
                    .collect(Collectors.joining(","));

            record.setOrderDishes(orderDishes);
        });

        return new PageResult<>(resultPage.getTotal(), records);
    }

    /**
     * 各个状态的订单数量统计
     */
    @Override
    public OrderStatisticsVO orderStatistics() {
        return ordersMapper.orderStatistics();
    }

    /**
     * 完成订单
     */
    @Override
    public void completeOrder(Long id) {

        if (id == null || id <= 0) {
            throw new OrderBusinessException("订单ID无效");
        }

        boolean changed = lambdaUpdate()
                .eq(Orders::getFundsSource, Orders.FUNDS_WECHAT)
                .eq(Orders::getId, id)
                .eq(Orders::getStatus, Orders.DELIVERY_IN_PROGRESS)
                .eq(Orders::getPayStatus, Orders.PAID)
                .set(Orders::getStatus, Orders.COMPLETED)
                .set(Orders::getDeliveryTime, LocalDateTime.now().withNano(0))
                .update();

        if (!changed) {
            throw new OrderBusinessException("订单状态已变化，请刷新后操作");
        }
    }

    /**
     * 管理端取消订单
     */
    @Override
    public void adminCancelOrder(OrdersCancelDTO dto) {

        if (dto == null || dto.getId() == null || dto.getId() <= 0) {
            throw new OrderBusinessException("订单ID无效");
        }

        String reason = requireReason(dto.getCancelReason());

        Orders order = getById(dto.getId());

        if (order == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        if (cannotCancel(order, true)) {
            throw new OrderBusinessException("当前订单状态不可取消");
        }

        cancelAndRefund(order, reason);
    }

    /**
     * 管理端拒单。
     * 取消状态与退款记录一起提交，之后再调用微信。
     */
    @Override
    public void rejectionOrder(OrdersRejectionDTO dto) {

        if (dto == null || dto.getId() == null || dto.getId() <= 0) {
            throw new OrderBusinessException("订单ID无效");
        }

        String reason = requireReason(dto.getRejectionReason());

        Orders order = getById(dto.getId());

        if (order == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        // 事务服务内部仍严格限定：待接单＋已支付。
        refundRecordTxService.cancelAndPrepare(order, reason, true);

        requestRefund(order);
    }

    /**
     * 接单
     */
    @Override
    public void confirmOrder(OrdersConfirmDTO dto) {

        if (dto == null || dto.getId() == null || dto.getId() <= 0) {
            throw new OrderBusinessException("订单ID无效");
        }

        boolean changed = lambdaUpdate()
                .eq(Orders::getFundsSource, Orders.FUNDS_WECHAT)
                .eq(Orders::getId, dto.getId())
                .eq(Orders::getStatus, Orders.TO_BE_CONFIRMED)
                .eq(Orders::getPayStatus, Orders.PAID)
                .set(Orders::getStatus, Orders.CONFIRMED)
                .update();

        if (!changed) {
            throw new OrderBusinessException("订单状态已变化，请刷新后操作");
        }
    }

    /**
     * 管理端-查询订单详情
     */
    @Override
    public OrderDetailVO adminGetOrderDetail(Long id) {
        Orders order = getById(id);

        if (order == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        // 查询订单详情
        List<OrderDetail> orderDetailList = Db.lambdaQuery(OrderDetail.class)
                .eq(OrderDetail::getOrderId, id)
                .list();

        OrderDetailVO orderDetailVO = BeanUtil.copyProperties(order, OrderDetailVO.class);

        orderDetailVO.setOrderDetailList(orderDetailList);
        paymentRecordTxService.fillPaymentInfo(orderDetailVO);
        refundRecordTxService.fillRefundInfo(orderDetailVO);
        return orderDetailVO;
    }

    /**
     * 派送订单
     */
    @Override
    public void deliveryOrder(Long id) {

        if (id == null || id <= 0) {
            throw new OrderBusinessException("订单ID无效");
        }

        boolean changed = lambdaUpdate()
                .eq(Orders::getFundsSource, Orders.FUNDS_WECHAT)
                .eq(Orders::getId, id)
                .eq(Orders::getStatus, Orders.CONFIRMED)
                .eq(Orders::getPayStatus, Orders.PAID)
                .set(Orders::getStatus, Orders.DELIVERY_IN_PROGRESS)
                .update();

        if (!changed) {
            throw new OrderBusinessException("订单状态已变化，请刷新后操作");
        }
    }

    /**
     * 处理微信退款结果回调
     */
    @Override
    public void handleRefundNotify(RefundNotification notification) {

        if (notification == null) {
            throw new OrderBusinessException("退款通知为空");
        }

        // Controller已经完成验签、解密。
        // 统一转换后，与主动查询共用同一套结果处理。
        Refund result = new Refund();

        result.setOutTradeNo(notification.getOutTradeNo());
        result.setOutRefundNo(notification.getOutRefundNo());
        result.setTransactionId(notification.getTransactionId());
        result.setRefundId(notification.getRefundId());
        result.setAmount(notification.getAmount());
        result.setStatus(notification.getRefundStatus());
        result.setSuccessTime(notification.getSuccessTime());

        refundRecordTxService.applyResult(result);
    }

    /**
     * 用户允许取消：
     * 1. 待付款、未支付；
     * 2. 待接单、已支付。
     * 管理端额外允许取消：
     * 3. 已接单、已支付。
     * 派送中和已完成均不能取消。
     */
    private boolean cannotCancel(Orders order, boolean admin) {

        return !Orders.FUNDS_WECHAT.equals(order.getFundsSource())
                || (!Orders.PENDING_PAYMENT.equals(order.getStatus())
                || !Orders.UN_PAID.equals(order.getPayStatus()))
                && (!Orders.TO_BE_CONFIRMED.equals(order.getStatus()) || !Orders.PAID.equals(order.getPayStatus()))
                && (!admin || !Orders.CONFIRMED.equals(order.getStatus())
                || !Orders.PAID.equals(order.getPayStatus()));
    }


    /**
     * 规范化并检查取消、拒单原因。
     */
    private String requireReason(String reason) {

        String value = StrUtil.trim(reason);

        if (StrUtil.isBlank(value) || value.length() > 255) {
            throw new OrderBusinessException("取消或拒单原因不能为空且不能超过255个字符");
        }

        return value;
    }


    /**
     * 通过独立事务服务一起保存取消结果与退款意图，
     * 提交后再进入事务外退款编排。
     * 这个外层方法不加@Transactional。
     */
    private void cancelAndRefund(Orders order, String reason) {

        refundRecordTxService.cancelAndPrepare(order, reason, false);

        if (Orders.PAID.equals(order.getPayStatus())) {
            requestRefund(order);
        }
    }


    /**
     * 事务外查询或申请退款。
     * 退款记录与订单结果由独立短事务统一更新。
     */
    private void requestRefund(Orders order) {

        refundRecoveryService.process(order.getNumber());
    }
}
