package com.dineflow.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.dineflow.entity.OrderDetail;
import com.dineflow.mapper.OrderDetailMapper;
import com.dineflow.service.IOrderDetailService;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 订单明细表 服务实现类
 * </p>
 *
 * @author zainxx
 * @since 2026-09-08
 */
@Service
public class OrderDetailServiceImpl extends ServiceImpl<OrderDetailMapper, OrderDetail> implements IOrderDetailService {

}
