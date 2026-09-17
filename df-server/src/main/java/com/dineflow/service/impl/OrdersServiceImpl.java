package com.dineflow.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.UUID;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.dineflow.constant.MessageConstant;
import com.dineflow.dto.OrdersSubmitDTO;
import com.dineflow.entity.AddressBook;
import com.dineflow.entity.OrderDetail;
import com.dineflow.entity.Orders;
import com.dineflow.entity.ShoppingCart;
import com.dineflow.exception.AddressBookBusinessException;
import com.dineflow.exception.OrderBusinessException;
import com.dineflow.exception.ShoppingCartBusinessException;
import com.dineflow.mapper.OrdersMapper;
import com.dineflow.service.IOrdersService;
import com.dineflow.utils.RedisIdWorker;
import com.dineflow.utils.ThreadLocalUtil;
import com.dineflow.vo.OrderSubmitVO;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 订单表 服务实现类
 * </p>
 *
 * @author zainxx
 * @since 2026-09-08
 */
@Service
@RequiredArgsConstructor
public class OrdersServiceImpl extends ServiceImpl<OrdersMapper, Orders> implements IOrdersService {

    private final RedisIdWorker redisIdWorker;

    /**
     * 用户提交订单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {
        Long userId = ThreadLocalUtil.getCurrentId();
        //校验订单状态（收货地址为空、购物车为空）
        // todo 判断是否超出配送范围
        //获取地址信息
        AddressBook address = Db.lambdaQuery(AddressBook.class)
                .eq(AddressBook::getId, ordersSubmitDTO.getAddressBookId())
                .eq(AddressBook::getUserId, userId)
                .one();
        if (address == null) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }
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
}
