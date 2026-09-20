package com.dineflow.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.dineflow.constant.MessageConstant;
import com.dineflow.dto.OrdersPaymentDTO;
import com.dineflow.dto.OrdersSubmitDTO;
import com.dineflow.entity.*;
import com.dineflow.exception.AddressBookBusinessException;
import com.dineflow.exception.OrderBusinessException;
import com.dineflow.exception.ShoppingCartBusinessException;
import com.dineflow.mapper.OrdersMapper;
import com.dineflow.properties.WeChatProperties;
import com.dineflow.service.IOrdersService;
import com.dineflow.utils.RedisIdWorker;
import com.dineflow.utils.ThreadLocalUtil;
import com.dineflow.utils.WeChatPayUtil;
import com.dineflow.vo.OrderPaymentVO;
import com.dineflow.vo.OrderSubmitVO;
import com.wechat.pay.java.service.payments.jsapi.model.PrepayWithRequestPaymentResponse;
import com.wechat.pay.java.service.payments.model.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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

    /**
     * 用户提交订单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {
        Long userId = ThreadLocalUtil.getCurrentId();
        //校验订单状态（收货地址为空、购物车为空）
        //获取地址信息
        AddressBook address = Db.lambdaQuery(AddressBook.class)
                .eq(AddressBook::getId, ordersSubmitDTO.getAddressBookId())
                .eq(AddressBook::getUserId, userId)
                .one();
        if (address == null) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }
        // todo 判断是否超出配送范围
        //获取购物车中的商品信息
        List<ShoppingCart> itemsList = Db.lambdaQuery(ShoppingCart.class)
                .eq(ShoppingCart::getUserId, userId)
                .list();
        if (CollUtil.isEmpty(itemsList)) {
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        //订单金额应以后端购物车数据为准
        BigDecimal amount = itemsList.stream()
                .map(item -> item.getAmount()
                        .multiply(BigDecimal.valueOf(item.getNumber())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        //使用全局唯一ID生成器生成订单号
        long orderNumber = redisIdWorker.nextId("order");

        //插入订单数据
        Orders order = BeanUtil.copyProperties(ordersSubmitDTO, Orders.class);
        order.setAmount(amount);
        order.setPhone(address.getPhone());
        order.setAddress(address.getDetail());
        order.setConsignee(address.getConsignee());
        order.setNumber(String.valueOf(orderNumber));
        order.setUserId(userId);
        order.setStatus(Orders.PENDING_PAYMENT);
        order.setPayStatus(Orders.UN_PAID);
        order.setOrderTime(LocalDateTime.now());
        save(order);

        //插入订单详情数据
        List<OrderDetail> orderDetailList = new ArrayList<>();
        for (ShoppingCart item : itemsList) {
            OrderDetail orderDetail = BeanUtil.copyProperties(item, OrderDetail.class);
            orderDetail.setOrderId(order.getId());
            orderDetailList.add(orderDetail);
        }
        Db.saveBatch(orderDetailList);

        //删除购物车数据
        Db.lambdaUpdate(ShoppingCart.class)
                .eq(ShoppingCart::getUserId, userId)
                .remove();

        //封装返回结果
        return OrderSubmitVO.builder()
                .id(order.getId())
                .orderNumber(order.getNumber())
                .orderAmount(order.getAmount())
                .orderTime(order.getOrderTime())
                .build();
    }

    /**
     * 订单支付
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
     * 支付成功回调
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void paySuccess(Transaction transaction) {

        // 1. 微信支付状态必须为 SUCCESS
        if (transaction.getTradeState() != Transaction.TradeStateEnum.SUCCESS) {
            log.warn(
                    "微信支付状态非SUCCESS，订单号：{}，状态：{}",
                    transaction.getOutTradeNo(),
                    transaction.getTradeState()
            );
            return;
        }

        // 2. 根据订单号查询本地订单
        Orders order = lambdaQuery()
                .eq(Orders::getNumber, transaction.getOutTradeNo())
                .one();

        if (order == null) {
            throw new OrderBusinessException("订单不存在");
        }

        // 3. 幂等判断
        if (Objects.equals(order.getPayStatus(), Orders.PAID)) {
            log.info(
                    "订单已完成支付，忽略重复回调，订单号：{}",
                    order.getNumber()
            );
            return;
        }

        // 4. 校验商户号
        if (!Objects.equals(transaction.getMchid(), weChatProperties.getMchid())) {
            throw new OrderBusinessException(
                    "微信支付商户号不一致"
            );
        }

        // 5. 校验 AppId
        if (!Objects.equals(transaction.getAppid(), weChatProperties.getAppid())) {
            throw new OrderBusinessException(
                    "微信支付 AppId 不一致"
            );
        }

        // 6. 校验订单金额
        int expectedAmount = order.getAmount()
                .movePointRight(2)
                .intValueExact();

        Integer actualAmount = transaction.getAmount().getTotal();

        if (!Objects.equals(expectedAmount, actualAmount)) {
            throw new OrderBusinessException(
                    "微信支付金额不一致"
            );
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
            log.info(
                    "订单支付状态未发生变化，可能已经被其他回调处理，订单号：{}",
                    order.getNumber()
            );
        }

        log.info(
                "订单支付成功，订单号：{}，微信支付交易号：{}",
                order.getNumber(),
                transaction.getTransactionId()
        );

        // 后续：
        // 来单提醒
        // WebSocket 推送
        // RabbitMQ 消息
    }
}
