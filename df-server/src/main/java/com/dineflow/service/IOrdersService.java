package com.dineflow.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.dineflow.dto.OrdersSubmitDTO;
import com.dineflow.entity.Orders;
import com.dineflow.vo.OrderSubmitVO;

/**
 * <p>
 * 订单表 服务类
 * </p>
 *
 * @author zainxx
 * @since 2026-09-08
 */
public interface IOrdersService extends IService<Orders> {

    OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO);
}
