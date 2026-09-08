package com.dineflow.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.dineflow.entity.Orders;
import com.dineflow.mapper.OrdersMapper;
import com.dineflow.service.IOrdersService;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 订单表 服务实现类
 * </p>
 *
 * @author zainxx
 * @since 2026-09-08
 */
@Service
public class OrdersServiceImpl extends ServiceImpl<OrdersMapper, Orders> implements IOrdersService {

}
