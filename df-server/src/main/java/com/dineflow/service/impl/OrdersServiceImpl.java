package com.dineflow.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.dineflow.constant.MessageConstant;
import com.dineflow.constant.RedisKeyConstant;
import com.dineflow.dto.*;
import com.dineflow.entity.*;
import com.dineflow.exception.AddressBookBusinessException;
import com.dineflow.exception.OrderBusinessException;
import com.dineflow.exception.ShoppingCartBusinessException;
import com.dineflow.mapper.OrdersMapper;
import com.dineflow.model.Coordinate;
import com.dineflow.properties.WeChatProperties;
import com.dineflow.result.PageResult;
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
import com.wechat.pay.java.service.refund.model.RefundNotification;
import com.wechat.pay.java.service.refund.model.Status;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
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

    private final IShoppingCartService shoppingCartService;

    public static final double DELIVERY_RANGE_METERS = 5000D;


    /**
     * 结算预览：读取当前商品信息，校验并计算报价。
     * 只读，不创建订单，不删除购物车，也不锁定商品价格。
     */
    @Override
    public OrderPreviewVO previewOrder() {
        Long userId = ThreadLocalUtil.getCurrentId();
        if (userId == null) {
            throw new OrderBusinessException(MessageConstant.USER_NOT_LOGIN);
        }

        // 1. 检查营业状态，沿用现有Redis中的事实来源
        String shopStatus = redisTemplate.opsForValue().get(RedisKeyConstant.SHOP_STATUS);
        if (!"0".equals(shopStatus) && !"1".equals(shopStatus)) {
            throw new OrderBusinessException(MessageConstant.SHOP_STATUS_ABNORMAL);
        }
        if ("0".equals(shopStatus)) {
            throw new OrderBusinessException("店铺暂未营业");
        }

        // 2. 复用已完成的购物车查询：
        // 按本人查询购物车，再批量读取商品当前价格和起售状态
        List<ShoppingCartItemVO> carts = shoppingCartService.showShoppingCart();

        if (carts.isEmpty()) {
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        if (carts.size() > 100) {
            throw new ShoppingCartBusinessException("购物车最多支持100条商品，请移除部分条目后再结算");
        }

        BigDecimal goodsAmount = new BigDecimal("0.00");
        BigDecimal maxAmount = new BigDecimal("99999999.99");
        int totalNumber = 0;

        List<ShoppingCartItemVO> items = new ArrayList<>();

        // 3. 校验每条商品，并按当前价格计算小计
        for (ShoppingCartItemVO cart : carts) {
            if (!Boolean.TRUE.equals(cart.getAvailable())) {
                String reason = cart.getUnavailableReason();
                throw new ShoppingCartBusinessException("购物车中含失效商品：" + (reason == null ? "商品不可售" : reason));
            }

            Integer number = cart.getNumber();

            if (number == null || number < 1 || number > 99) {
                throw new OrderBusinessException("单项商品数量必须为1～99份");
            }

            BigDecimal price = cart.getCurrentPrice();

            if (price == null || price.signum() <= 0 || price.compareTo(maxAmount) > 0) {
                throw new OrderBusinessException("商品价格异常");
            }

            BigDecimal subtotal = price.multiply(BigDecimal.valueOf(number));

            goodsAmount = goodsAmount.add(subtotal);

            // 对齐当前orders.amount的DECIMAL(10,2)范围
            if (goodsAmount.compareTo(maxAmount) > 0) {
                throw new OrderBusinessException("订单金额超出允许范围");
            }

            totalNumber += number;

            // 数据来自后端本次读取的VO，不是客户端请求
            ShoppingCartItemVO item = BeanUtil.copyProperties(cart, ShoppingCartItemVO.class);

            item.setCurrentPrice(price);
            item.setSubtotal(subtotal);

            items.add(item);
        }

        // TODO 4. 当前阶段暂不收打包费、配送费
        BigDecimal packAmount = new BigDecimal("0.00");
        BigDecimal deliveryAmount = new BigDecimal("0.00");

        BigDecimal payableAmount = goodsAmount
                .add(packAmount)
                .add(deliveryAmount);

        // 5. 返回本次报价
        OrderPreviewVO preview = new OrderPreviewVO();

        preview.setItems(items);
        preview.setGoodsAmount(goodsAmount);
        preview.setPackAmount(packAmount);
        preview.setDeliveryAmount(deliveryAmount);
        preview.setPayableAmount(payableAmount);
        preview.setTotalNumber(totalNumber);

        return preview;
    }

    /**
     * C端-用户提交订单
     */
    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public OrderSubmitVO submitOrder(OrdersSubmitDTO dto) {

        Long userId = ThreadLocalUtil.getCurrentId();
        if (userId == null) {
            throw new OrderBusinessException(MessageConstant.USER_NOT_LOGIN);
        }

        if (dto == null || dto.getAddressBookId() == null) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }

        // 1. 查询本人收货地址
        AddressBook address = Db.lambdaQuery(AddressBook.class)
                .eq(AddressBook::getId, dto.getAddressBookId())
                .eq(AddressBook::getUserId, userId)
                .one();

        if (address == null) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }

        // 2. 沿用原有配送范围校验
        String wholeAddress = address.getProvinceName()
                + address.getCityName()
                + address.getDistrictName()
                + address.getDetail();

        Coordinate coordinate = baiduMapClient.getCoordinate(wholeAddress);

        double distance = baiduMapClient.getDrivingDistance(coordinate);

        if (distance > DELIVERY_RANGE_METERS) {
            throw new AddressBookBusinessException(MessageConstant.DISTANCE_MORE_THAN_5KM);
        }

        // 3. 重新读取商品并计算报价
        // 包括：营业状态、购物车非空、商品可售状态、当前价格等校验
        // 不采用 shopping_cart.amount
        OrderPreviewVO latestQuote = previewOrder();

        // 4. 与用户确认的数据比较
        // 失败会直接抛异常，不创建订单
        checkConfirmedQuote(dto, latestQuote);

        // 5. 创建订单
        Orders order = BeanUtil.copyProperties(dto, Orders.class);

        order.setUserId(userId);
        order.setAddressBookId(address.getId());

        order.setNumber(String.valueOf(redisIdWorker.nextId("order")));

        order.setStatus(Orders.PENDING_PAYMENT);
        order.setPayStatus(Orders.UN_PAID);
        order.setOrderTime(LocalDateTime.now());

        order.setPhone(address.getPhone());
        order.setAddress(wholeAddress);
        order.setConsignee(address.getConsignee());

        // 保存本次复核采用的应付金额
        order.setAmount(latestQuote.getPayableAmount());

        order.setPackAmount(latestQuote.getPackAmount());

        if (!save(order)) {
            throw new OrderBusinessException("订单创建失败");
        }

        // 6. 使用本次复核的商品信息构建成交快照（订单细节）
        // 不再直接复制 ShoppingCart 实体
        List<OrderDetail> details = new ArrayList<>();

        for (ShoppingCartItemVO item : latestQuote.getItems()) {
            OrderDetail detail = new OrderDetail();

            detail.setOrderId(order.getId());
            detail.setDishId(item.getDishId());
            detail.setSetmealId(item.getSetmealId());

            detail.setName(item.getName());
            detail.setImage(item.getImage());
            detail.setDishFlavor(item.getDishFlavor());

            detail.setNumber(item.getNumber());

            // 注意：order_detail.amount 存储成交单价，不是小计
            detail.setAmount(item.getCurrentPrice());

            details.add(detail);
        }

        if (!Db.saveBatch(details)) {
            throw new OrderBusinessException("订单明细保存失败");
        }

        // 7. 删除本次成交的购物车条目
        // 同时校验数量，若复核后数量又被修改，则回滚本次下单
        // 避免按 userId 直接清空后来新增的其他条目
        for (ShoppingCartItemVO item : latestQuote.getItems()) {
            boolean removed = Db.lambdaUpdate(ShoppingCart.class)
                    .eq(ShoppingCart::getUserId, userId)
                    .eq(ShoppingCart::getId, item.getCartId())
                    .eq(ShoppingCart::getNumber, item.getNumber())
                    .remove();

            if (!removed) {
                throw new OrderBusinessException("购物车已变化，请重新预览并确认");
            }
        }

        // 8. 返回创建结果
        return OrderSubmitVO.builder()
                .id(order.getId())
                .orderNumber(order.getNumber())
                .orderAmount(order.getAmount())
                .orderTime(order.getOrderTime())
                .build();
    }

    /**
     * C端-订单支付
     */
    @Override
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) {

        // 检验有没有配置商户号等，若配置了则可删除该段判断
        WeChatPayUtil weChatPayUtil = weChatPayUtilProvider.getIfAvailable();
        if (weChatPayUtil == null) {
            throw new OrderBusinessException("当前环境未配置微信支付");
        }

        // 正常支付逻辑如下

        Long userId = ThreadLocalUtil.getCurrentId();

        // 1. 校验支付方式
        if (!Objects.equals(ordersPaymentDTO.getPayMethod(), Orders.wechatPay)) {
            throw new OrderBusinessException("暂不支持该支付方式");
        }

        // 2. 查询当前用户订单
        Orders order = lambdaQuery()
                .eq(Orders::getNumber, ordersPaymentDTO.getOrderNumber())
                .eq(Orders::getUserId, userId)
                .one();

        if (order == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        // 3. 必须是待付款订单
        if (!Objects.equals(order.getStatus(), Orders.PENDING_PAYMENT)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        // 4. 必须是未支付状态
        if (!Objects.equals(order.getPayStatus(), Orders.UN_PAID)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        // 5. 查询当前用户openid
        User user = Db.lambdaQuery(User.class)
                .eq(User::getId, userId)
                .one();

        if (user == null || StrUtil.isBlank(user.getOpenid())) {
            throw new OrderBusinessException(MessageConstant.ACCOUNT_NOT_FOUND);
        }

        // 6. 调用微信支付
        PrepayWithRequestPaymentResponse response = weChatPayUtil.pay(
                order.getNumber(),
                order.getAmount(),
                "DineFlow订单",
                user.getOpenid()
        );

        // 7. 封装小程序支付参数
        return OrderPaymentVO.builder()
                .timeStamp(response.getTimeStamp())
                .nonceStr(response.getNonceStr())
                .packageStr(response.getPackageVal())
                .signType(response.getSignType())
                .paySign(response.getPaySign())
                .build();
    }

    /**
     * C端-支付成功回调
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void paySuccess(Transaction transaction) {

        // 1. 微信支付状态必须为 SUCCESS
        if (transaction.getTradeState() != Transaction.TradeStateEnum.SUCCESS) {
            log.warn("微信支付状态非SUCCESS，订单号：{}，状态：{}", transaction.getOutTradeNo(), transaction.getTradeState());
            return;
        }

        // 2. 根据订单号查询本地订单
        Orders order = lambdaQuery()
                .eq(Orders::getNumber, transaction.getOutTradeNo())
                .one();

        if (order == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        // 3. 幂等判断
        if (Objects.equals(order.getPayStatus(), Orders.PAID)) {
            log.info("订单已完成支付，忽略重复回调，订单号：{}", order.getNumber());
            return;
        }

        // 4. 校验商户号
        if (!Objects.equals(transaction.getMchid(), weChatProperties.getMchid())) {
            throw new OrderBusinessException("微信支付商户号不一致");
        }

        // 5. 校验 AppId
        if (!Objects.equals(transaction.getAppid(), weChatProperties.getAppid())) {
            throw new OrderBusinessException("微信支付 AppId 不一致");
        }

        // 6. 校验订单金额
        int expectedAmount = order.getAmount()
                .movePointRight(2)
                .intValueExact();

        Integer actualAmount = transaction.getAmount().getTotal();

        if (!Objects.equals(expectedAmount, actualAmount)) {
            throw new OrderBusinessException("微信支付金额不一致");
        }

        // 7. 幂等更新订单：官方明确要求商户系统做好重入/幂等设计；
        //                  如果微信没有收到成功响应，还会重新通知。
        boolean success = lambdaUpdate()
                .eq(Orders::getId, order.getId())
                .eq(Orders::getPayStatus, Orders.UN_PAID)
                .set(Orders::getPayStatus, Orders.PAID)
                .set(Orders::getStatus, Orders.TO_BE_CONFIRMED)
                .set(Orders::getCheckoutTime, LocalDateTime.now())
                .set(Orders::getTransactionId, transaction.getTransactionId())
                .update();

        if (!success) {
            /*
             * 可能另外一个重复回调已经先完成更新。
             * 此处根据项目业务决定：
             * 可以重新查询判断，也可以记录日志。
             */
            log.info("订单支付状态未发生变化，可能已经被其他回调处理，订单号：{}", order.getNumber());
        }

        log.info("订单支付成功，订单号：{}，微信支付交易号：{}", order.getNumber(), transaction.getTransactionId());

        // WebSocket 推送来单提醒
        Map<String, Object> message = new HashMap<>();
        message.put("type", 1);
        message.put("orderId", order.getId());
        message.put("content", "订单号：" + order.getNumber());

        webSocketServer.sendToAllClient(JSONUtil.toJsonStr(message));
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
                detailMap.getOrDefault(
                        order.getId(),
                        Collections.emptyList()
                )
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

        return orderDetailVO;
    }

    /**
     * C端-取消订单
     */
    @Override
    public void cancelOrder(Long id) {

        Long userId = ThreadLocalUtil.getCurrentId();

        // 1. 查询当前用户的订单
        Orders order = lambdaQuery()
                .eq(Orders::getUserId, userId)
                .eq(Orders::getId, id)
                .one();

        if (order == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        // 2. 校验订单状态
        if (!Orders.PENDING_PAYMENT.equals(order.getStatus()) && !Orders.TO_BE_CONFIRMED.equals(order.getStatus())) {
            throw new OrderBusinessException("当前订单状态不可取消");
        }

        Integer payStatus = order.getPayStatus();

        // 3. 已支付订单进行退款
        if (Orders.PAID.equals(payStatus)) {
            /*
            Refund refund = weChatPayUtil.refund(
                    order.getNumber(),
                    "RF" + order.getNumber(),
                    order.getAmount(),
                    order.getAmount(),
                    reason
            );

            Integer payStatus;

            if (Status.SUCCESS.equals(refund.getStatus())) {
                payStatus = Orders.REFUND;
            } else if (Status.PROCESSING.equals(refund.getStatus())) {
                payStatus = Orders.REFUNDING;
            } else {
                throw new OrderBusinessException("退款申请失败，退款状态：" + refund.getStatus());
            }
            */

            // 模拟退款成功
            payStatus = Orders.REFUND;
        }

        // 4. 更新订单状态
        lambdaUpdate()
                .eq(Orders::getId, id)
                .eq(Orders::getUserId, userId)
                .set(Orders::getStatus, Orders.CANCELLED)
                .set(Orders::getCancelReason, "用户取消订单")
                .set(Orders::getCancelTime, LocalDateTime.now())
                .set(Orders::getPayStatus, payStatus)
                .update();
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
        if (!Orders.COMPLETED.equals(order.getStatus())
                && !Orders.CANCELLED.equals(order.getStatus())) {
            throw new OrderBusinessException("当前订单状态不支持再来一单");
        }

        // 3. 查询原订单明细
        List<OrderDetail> orderDetailList =
                Db.lambdaQuery(OrderDetail.class)
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

        // 查询当前用户自己的订单
        Orders order = lambdaQuery()
                .eq(Orders::getId, id)
                .eq(Orders::getUserId, userId)
                .one();

        if (order == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        // 只有待接单、已接单、派送中的订单才能催单
        Integer status = order.getStatus();

        if (!Orders.TO_BE_CONFIRMED.equals(status)
                && !Orders.CONFIRMED.equals(status)
                && !Orders.DELIVERY_IN_PROGRESS.equals(status)) {

            throw new OrderBusinessException("当前订单状态不可催单");
        }

        // 构造 WebSocket 催单消息
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
        // 只有派送中的订单才能点击完成订单
        boolean success = lambdaUpdate()
                .eq(Orders::getId, id)
                .eq(Orders::getStatus, Orders.DELIVERY_IN_PROGRESS)
                .set(Orders::getStatus, Orders.COMPLETED)
                .set(Orders::getDeliveryTime, LocalDateTime.now())
                .update();

        if (!success) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
    }

    /**
     * 管理端取消订单
     */
    @Override
    public void adminCancelOrder(OrdersCancelDTO dto) {

        // 1. 查询订单
        Orders order = getById(dto.getId());

        if (order == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        // 2. 已完成、已取消订单不能取消
        if (Orders.COMPLETED.equals(order.getStatus())
                || Orders.CANCELLED.equals(order.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Integer payStatus = order.getPayStatus();

        // 3. 已支付订单先申请退款
        if (Orders.PAID.equals(payStatus)) {
            /*
            // 同一订单整单退款固定使用同一个退款单号
            Refund refund = weChatPayUtil.refund(
                    order.getNumber(),
                    "RF" + order.getNumber(),
                    order.getAmount(),
                    order.getAmount(),
                    reason
            );

            Integer payStatus;

            if (Status.SUCCESS.equals(refund.getStatus())) {
                payStatus = Orders.REFUND;
            } else if (Status.PROCESSING.equals(refund.getStatus())) {
                payStatus = Orders.REFUNDING;
            } else {
                throw new OrderBusinessException("退款申请失败，退款状态：" + refund.getStatus());
            }
            */

            // 模拟退款成功
            payStatus = Orders.REFUND;

        }

        // 4. 更新订单
        boolean success = lambdaUpdate()
                .eq(Orders::getId, dto.getId())
                .eq(Orders::getStatus, order.getStatus())
                .set(Orders::getStatus, Orders.CANCELLED)
                .set(Orders::getPayStatus, payStatus)
                .set(Orders::getCancelReason, dto.getCancelReason())
                .set(Orders::getCancelTime, LocalDateTime.now())
                .update();

        if (!success) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
    }

    /**
     * 拒单
     */
    @Override
    public void rejectionOrder(OrdersRejectionDTO dto) {
        // 1. 查询订单
        Orders order = getById(dto.getId());

        if (order == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        // 2. 只有待接单订单才能拒单
        if (!Orders.TO_BE_CONFIRMED.equals(order.getStatus())) {
            throw new OrderBusinessException("当前订单状态不可拒单");
        }

        Integer payStatus = order.getPayStatus();

        // 3. 已支付订单，需要退款
        if (Orders.PAID.equals(payStatus)) {
            /*
            Refund refund = weChatPayUtil.refund(
                    order.getNumber(),
                    "RF" + order.getNumber(),
                    order.getAmount(),
                    order.getAmount(),
                    reason
            );

            Integer payStatus;

            if (Status.SUCCESS.equals(refund.getStatus())) {
                payStatus = Orders.REFUND;
            } else if (Status.PROCESSING.equals(refund.getStatus())) {
                payStatus = Orders.REFUNDING;
            } else {
                throw new OrderBusinessException("退款申请失败，退款状态：" + refund.getStatus());
            }
            */

            payStatus = Orders.REFUND;

        }

        // 4. 更新订单状态
        boolean success = lambdaUpdate()
                .eq(Orders::getId, dto.getId())
                .eq(Orders::getStatus, Orders.TO_BE_CONFIRMED)
                .set(Orders::getStatus, Orders.CANCELLED)
                .set(Orders::getPayStatus, payStatus)
                .set(Orders::getRejectionReason, dto.getRejectionReason())
                .set(Orders::getCancelTime, LocalDateTime.now())
                .update();

        if (!success) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
    }

    /**
     * 接单
     */
    @Override
    public void confirmOrder(OrdersConfirmDTO ordersConfirmDTO) {

        Long id = ordersConfirmDTO.getId();

        // 只有待接单订单才能接单
        boolean success = lambdaUpdate()
                .eq(Orders::getId, id)
                .eq(Orders::getStatus, Orders.TO_BE_CONFIRMED)
                .set(Orders::getStatus, Orders.CONFIRMED)
                .update();

        if (!success) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
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
        return orderDetailVO;
    }

    /**
     * 派送订单
     */
    @Override
    public void deliveryOrder(Long id) {

        // 只有已接单的订单才能开始派送
        boolean success = lambdaUpdate()
                .eq(Orders::getId, id)
                .eq(Orders::getStatus, Orders.CONFIRMED)
                .set(Orders::getStatus, Orders.DELIVERY_IN_PROGRESS)
                .update();

        if (!success) {
            throw new OrderBusinessException(
                    MessageConstant.ORDER_STATUS_ERROR
            );
        }
    }

    /**
     * 处理微信退款结果回调
     */
    @Override
    public void handleRefundNotify(RefundNotification notification) {

        String orderNumber = notification.getOutTradeNo();

        // 1. 根据商户订单号查询订单
        Orders order = lambdaQuery()
                .eq(Orders::getNumber, orderNumber)
                .one();

        if (order == null) {
            throw new OrderBusinessException("退款订单不存在");
        }

        // 2. 幂等处理
        // 微信可能重复通知，已经退款成功直接返回
        if (Orders.REFUND.equals(order.getPayStatus())) {
            return;
        }

        // 3. 校验退款金额
        if (notification.getAmount() == null || notification.getAmount().getRefund() == null) {
            throw new OrderBusinessException("退款金额异常");
        }

        long refundAmount = notification
                .getAmount()
                .getRefund();

        long orderAmount = order.getAmount()
                .movePointRight(2)
                .longValueExact();

        if (refundAmount != orderAmount) {
            throw new OrderBusinessException("退款金额与订单金额不一致");
        }

        // 4. 根据微信最终退款状态处理
        Status refundStatus = notification.getRefundStatus();

        if (Status.SUCCESS.equals(refundStatus)) {
            boolean success = lambdaUpdate()
                    .eq(Orders::getId, order.getId())
                    .in(
                            Orders::getPayStatus,
                            Orders.PAID,
                            Orders.REFUNDING
                    )
                    .set(
                            Orders::getPayStatus,
                            Orders.REFUND
                    )
                    .update();

            // 如果已经被重复回调处理，不需要认为业务失败
            if (!success) {
                Orders latestOrder = getById(order.getId());
                if (!Orders.REFUND.equals(latestOrder.getPayStatus())) {
                    throw new OrderBusinessException("订单退款状态更新失败");
                }
            }

            log.info(
                    "订单退款成功，orderNumber={}, refundNumber={}",
                    notification.getOutTradeNo(),
                    notification.getOutRefundNo()
            );

            return;
        }

        if (Status.PROCESSING.equals(refundStatus)) {
            // 仍然退款中，无需标记已退款
            lambdaUpdate()
                    .eq(Orders::getId, order.getId())
                    .eq(Orders::getPayStatus, Orders.PAID)
                    .set(
                            Orders::getPayStatus,
                            Orders.REFUNDING
                    )
                    .update();

            return;
        }

        // CLOSED / ABNORMAL
        log.error(
                "订单退款异常，orderNumber={}, refundNumber={}, status={}",
                notification.getOutTradeNo(),
                notification.getOutRefundNo(),
                refundStatus
        );

        throw new OrderBusinessException(
                "微信退款状态异常：" + refundStatus
        );
    }


    /**
     * 比较用户确认的数据与后端本次重新计算的报价。
     * 任意一项不一致，都要求重新预览确认。
     */
    private void checkConfirmedQuote(OrdersSubmitDTO dto, OrderPreviewVO latestQuote) {

        List<OrderSubmitItemDTO> confirmedItems = dto.getItems();

        if (confirmedItems == null || confirmedItems.isEmpty()) {
            throw new OrderBusinessException("缺少商品确认信息，请重新预览");
        }

        if (dto.getAmount() == null || dto.getAmount().signum() <= 0) {
            throw new OrderBusinessException("确认金额不合法，请重新预览");
        }

        // 按购物车条目ID建立索引，不能按菜品ID建立索引：
        // 同一菜品的不同口味可能对应多条购物车记录。
        Map<Long, OrderSubmitItemDTO> confirmedMap = new HashMap<>();

        for (OrderSubmitItemDTO item : confirmedItems) {
            if (item == null
                    || item.getCartId() == null
                    || item.getNumber() == null
                    || item.getNumber() <= 0
                    || item.getCurrentPrice() == null
                    || item.getCurrentPrice().signum() <= 0) {
                throw new OrderBusinessException("商品确认信息不完整，请重新预览");
            }

            // 不允许重复提交同一购物车条目。
            if (confirmedMap.putIfAbsent(item.getCartId(), item) != null) {
                throw new OrderBusinessException("商品确认信息重复");
            }
        }

        List<ShoppingCartItemVO> latestItems = latestQuote.getItems();

        // 增加或删除了购物车条目，需要重新确认。
        if (confirmedMap.size() != latestItems.size()) {
            throw new OrderBusinessException("购物车已变化，请重新预览并确认");
        }

        for (ShoppingCartItemVO latest : latestItems) {
            OrderSubmitItemDTO confirmed = confirmedMap.get(latest.getCartId());

            if (confirmed == null) {
                throw new OrderBusinessException("购物车已变化，请重新预览并确认");
            }

            // 检查商品身份、所选口味和数量是否发生变化。
            if (!Objects.equals(confirmed.getDishId(), latest.getDishId())
                    || !Objects.equals(confirmed.getSetmealId(), latest.getSetmealId())
                    || !Objects.equals(confirmed.getDishFlavor(), latest.getDishFlavor())
                    || !Objects.equals(confirmed.getNumber(), latest.getNumber())) {
                throw new OrderBusinessException("购物车商品或数量已变化，请重新预览并确认");
            }

            // BigDecimal比较数值用compareTo：
            // 18.0和18.00应当视为相同价格。
            if (confirmed.getCurrentPrice().compareTo(latest.getCurrentPrice()) != 0) {
                throw new OrderBusinessException("商品价格已变化，请重新预览并确认");
            }
        }

        // 逐项比较通过后，再比较应付总金额。
        if (dto.getAmount().compareTo(latestQuote.getPayableAmount()) != 0) {
            throw new OrderBusinessException("订单金额已变化，请重新预览并确认");
        }
    }
}
