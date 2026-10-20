package com.dineflow.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.dineflow.dto.*;
import com.dineflow.entity.Orders;
import com.dineflow.result.PageResult;
import com.dineflow.vo.*;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.refund.model.RefundNotification;

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

    /**
     * 在本地事务中记录支付成功并更新订单。
     * 返回需要继续处理的订单快照；
     * 不需要后续处理时返回 null。
     */
    Orders paySuccess(Transaction transaction);

    /**
     * 支付事务提交后发送首次有效来单提醒，或继续处理晚到支付退款。
     */
    void handlePaySuccessAfterCommit(Orders order);

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

    void handleRefundNotify(RefundNotification notification);

    void urgeOrder(Long id);

    OrderPreviewVO previewOrder();
}
