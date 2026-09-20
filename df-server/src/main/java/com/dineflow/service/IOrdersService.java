package com.dineflow.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.dineflow.dto.OrdersPageQueryDTO;
import com.dineflow.dto.OrdersPaymentDTO;
import com.dineflow.dto.OrdersSubmitDTO;
import com.dineflow.entity.Orders;
import com.dineflow.result.PageResult;
import com.dineflow.vo.HistoryOrdersQueryVO;
import com.dineflow.vo.OrderDetailVO;
import com.dineflow.vo.OrderPaymentVO;
import com.dineflow.vo.OrderSubmitVO;
import com.wechat.pay.java.service.payments.model.Transaction;

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

    OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO);

    void paySuccess(Transaction transaction);

    PageResult<HistoryOrdersQueryVO> historyOrdersQuery(OrdersPageQueryDTO ordersPageQueryDTO);

    OrderDetailVO getOrderDetail(Long id);

    void cancelOrder(Long id);

    void oneMoreOrder(Long id);
}
