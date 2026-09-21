package com.dineflow.task;

import com.dineflow.constant.MessageConstant;
import com.dineflow.entity.Orders;
import com.dineflow.service.IOrdersService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 订单处理定时任务
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OrderProcessingTask {

    private final IOrdersService ordersService;

    /**
     * 定时任务：每 3 分钟检查一次支付超时订单
     * 下单超过 15 分钟仍未支付，则自动取消
     */
    @Scheduled(cron = "0 */3 * * * *")
    public void cancelOverdueOrders() {

        log.info("开始处理支付超时订单...");

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime timeout = now.minusMinutes(15);

        // 一条更新语句就可以完成超时订单查询和状态更新
        boolean updated = ordersService.lambdaUpdate()
                // 仍然处于待付款状态
                .eq(Orders::getStatus, Orders.PENDING_PAYMENT)
                // 再加一道支付状态保护
                .eq(Orders::getPayStatus, Orders.UN_PAID)
                // 下单时间超过 15 分钟
                .lt(Orders::getOrderTime, timeout)
                // 更新订单状态
                .set(Orders::getStatus, Orders.CANCELLED)
                .set(Orders::getCancelReason, MessageConstant.CANCEL_OVERDUE_ORDER)
                .set(Orders::getCancelTime, now)
                .update();

        if (updated) {
            log.info("支付超时订单处理完成");
        } else {
            log.info("暂无支付超时订单");
        }
    }

    /**
     * 定时任务：
     * 每天凌晨 3 点检查超过预计送达时间 1 小时、
     * 但仍处于“派送中”的订单，并自动完成
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void completeDeliveryInProgressOrders() {

        log.info("开始处理长时间处于派送中的订单...");

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime timeout = now.minusHours(1);

        boolean updated = ordersService.lambdaUpdate()
                .eq(Orders::getStatus, Orders.DELIVERY_IN_PROGRESS)
                .lt(Orders::getEstimatedDeliveryTime, timeout)
                .set(Orders::getStatus, Orders.COMPLETED)
                .set(Orders::getDeliveryTime, now)
                .update();

        if (updated) {
            log.info("长时间派送中的订单处理完成");
        } else {
            log.info("暂无需要自动完成的订单");
        }
    }
}
