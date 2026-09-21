package com.dineflow.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.dineflow.constant.MessageConstant;
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
import org.springframework.stereotype.Service;
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

    //private final WeChatPayUtil weChatPayUtil;
    private final ObjectProvider<WeChatPayUtil> weChatPayUtilProvider;

    private final WeChatProperties weChatProperties;

    private final OrdersMapper ordersMapper;

    private final BaiduMapClient baiduMapClient;

    private final WebSocketServer webSocketServer;

    public static final double DELIVERY_RANGE_METERS = 5000D;

    /**
     * C端-用户提交订单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {
        Long userId = ThreadLocalUtil.getCurrentId();
        // 校验订单状态（收货地址为空、购物车为空）
        // 获取地址信息
        AddressBook address = Db.lambdaQuery(AddressBook.class)
                .eq(AddressBook::getId, ordersSubmitDTO.getAddressBookId())
                .eq(AddressBook::getUserId, userId)
                .one();
        if (address == null) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }
        // 判断是否超出配送范围
        // 拼接完整地址
        String wholeAddress = address.getProvinceName()
                + address.getCityName()
                + address.getDistrictName()
                + address.getDetail();
        // 获取收货地址经纬度
        Coordinate coordinate = baiduMapClient.getCoordinate(wholeAddress);
        // 获取收货地址距离店铺的驾车距离（米）
        double distance = baiduMapClient.getDrivingDistance(coordinate);
        if (distance > DELIVERY_RANGE_METERS) {
            throw new AddressBookBusinessException(MessageConstant.DISTANCE_MORE_THAN_5KM);
        }

        // 获取购物车中的商品信息
        List<ShoppingCart> itemsList = Db.lambdaQuery(ShoppingCart.class)
                .eq(ShoppingCart::getUserId, userId)
                .list();
        if (CollUtil.isEmpty(itemsList)) {
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        // 订单金额应以后端购物车数据为准
        BigDecimal amount = itemsList.stream()
                .map(item -> item.getAmount()
                        .multiply(BigDecimal.valueOf(item.getNumber())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 使用全局唯一ID生成器生成订单号
        long orderNumber = redisIdWorker.nextId("order");

        // 插入订单数据
        Orders order = BeanUtil.copyProperties(ordersSubmitDTO, Orders.class);
        order.setAmount(amount);
        order.setPhone(address.getPhone());
        order.setAddress(wholeAddress);
        order.setConsignee(address.getConsignee());
        order.setNumber(String.valueOf(orderNumber));
        order.setUserId(userId);
        order.setStatus(Orders.PENDING_PAYMENT);
        order.setPayStatus(Orders.UN_PAID);
        order.setOrderTime(LocalDateTime.now());
        save(order);

        // 插入订单详情数据
        List<OrderDetail> orderDetailList = new ArrayList<>();
        for (ShoppingCart item : itemsList) {
            OrderDetail orderDetail = BeanUtil.copyProperties(item, OrderDetail.class);
            orderDetail.setOrderId(order.getId());
            orderDetailList.add(orderDetail);
        }
        Db.saveBatch(orderDetailList);

        // 删除购物车数据
        Db.lambdaUpdate(ShoppingCart.class)
                .eq(ShoppingCart::getUserId, userId)
                .remove();

        // 封装返回结果
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
        records.forEach(order -> {
            order.setOrderDetailList(
                    detailMap.getOrDefault(
                            order.getId(),
                            Collections.emptyList()
                    )
            );
        });

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
        List<ShoppingCart> shoppingCartList = orderDetailList.stream()
                .map(orderDetail -> ShoppingCart.builder()
                        .userId(userId)
                        .dishId(orderDetail.getDishId())
                        .setmealId(orderDetail.getSetmealId())
                        .dishFlavor(orderDetail.getDishFlavor())
                        .name(orderDetail.getName())
                        .image(orderDetail.getImage())
                        .amount(orderDetail.getAmount())
                        .number(orderDetail.getNumber())
                        .createTime(LocalDateTime.now())
                        .build())
                .toList();

        Db.saveBatch(shoppingCartList);
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
}
