package com.dineflow.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.dineflow.dto.*;
import com.dineflow.entity.Orders;
import com.dineflow.result.PageResult;
import com.dineflow.vo.*;
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

    PageResult<OrderConditionSearchVO> orderConditionSearch(OrdersPageQueryDTO ordersPageQueryDTO);

    OrderStatisticsVO orderStatistics();

    void completeOrder(Long id);

    void adminCancelOrder(OrdersCancelDTO ordersCancelDTO);

    void rejectionOrder(OrdersRejectionDTO ordersRejectionDTO);

    void confirmOrder(OrdersConfirmDTO ordersConfirmDTO);

    OrderDetailVO adminGetOrderDetail(Long id);

    void deliveryOrder(Long id);
}
