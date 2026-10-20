package com.dineflow.order.service;

import com.dineflow.entity.Orders;
import com.dineflow.entity.PaymentRecord;
import com.dineflow.exception.OrderBusinessException;
import com.dineflow.service.IOrdersService;
import com.dineflow.utils.WeChatPayUtil;
import com.wechat.pay.java.core.exception.ServiceException;
import com.wechat.pay.java.service.payments.model.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 支付结果恢复编排；远程请求不进入数据库事务。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentRecoveryService {

    private final PaymentRecordTxService paymentRecordTxService;

    private final ObjectProvider<WeChatPayUtil> weChatPayUtilProvider;

    private final IOrdersService ordersService;

    @Scheduled(fixedDelay = 60000)
    public void recoverPendingPayments() {
        if (weChatPayUtilProvider.getIfAvailable() == null) {
            return;
        }

        for (PaymentRecord record : paymentRecordTxService.pendingRecords()) {
            process(record.getPayNumber());
        }
    }

    /**
     * 先提交查询计划，再查原支付号，最后委托统一结果事务。
     */
    public void process(String number) {
        try {
            WeChatPayUtil util = weChatPayUtilProvider.getIfAvailable();

            if (util == null) {
                return;
            }

            PaymentRecord record = paymentRecordTxService.claim(number);

            if (record == null) {
                return;
            }

            Transaction result;

            try {
                result = util.queryPayment(record.getPayNumber());

            } catch (ServiceException e) {
                if (e.getHttpStatusCode() == 404 && "ORDER_NOT_EXIST".equals(e.getErrorCode())) {
                    paymentRecordTxService.notFound(number);
                    return;
                }
                throw e;
            }

            if (result == null || !Objects.equals(record.getPayNumber(), result.getOutTradeNo())) {
                throw new OrderBusinessException("支付查询结果关联错误");
            }

            if (result.getTradeState() == Transaction.TradeStateEnum.SUCCESS) {
                Orders paid = ordersService.paySuccess(result);
                ordersService.handlePaySuccessAfterCommit(paid);
            } else {
                paymentRecordTxService.applyNonSuccess(number, result);
            }

        } catch (Exception e) {
            String error = RecoveryPolicy.error(e);

            try {
                paymentRecordTxService.rememberError(number, error);

            } catch (Exception failure) {
                log.error("支付异常记录失败，payNumber={}, exceptionType={}", number, failure.getClass().getSimpleName());
            }

            log.warn("支付结果待确认，payNumber={}, error={}", number, error);
        }
    }

    public void cancelOverdueOrders() {
        for (Orders order : paymentRecordTxService.overdueWithoutPayment()) {
            try {
                paymentRecordTxService.cancelWithoutPayment(order.getNumber());

            } catch (Exception e) {
                log.warn("超时关单待重查，orderId={}, exceptionType={}", order.getId(), e.getClass().getSimpleName());
            }
        }
    }
}
