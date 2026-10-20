package com.dineflow.order.service;

import com.dineflow.entity.RefundRecord;
import com.dineflow.utils.WeChatPayUtil;
import com.wechat.pay.java.core.exception.ServiceException;
import com.wechat.pay.java.service.refund.model.Refund;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class RefundRecoveryService {

    private final RefundRecordTxService refundRecordTxService;
    private final ObjectProvider<WeChatPayUtil> weChatPayUtilProvider;

    /**
     * 非事务编排。
     * 调用方也应在数据库事务提交后进入。
     */
    public void process(String orderNumber) {

        try {
            WeChatPayUtil payUtil = weChatPayUtilProvider.getIfAvailable();

            if (payUtil == null) {
                return;
            }

            // 该方法通过另一个Bean完成短事务，返回后事务已经结束。
            RefundRecord record = refundRecordTxService.prepare(orderNumber);

            if (record == null
                    || RefundRecord.SUCCESS.equals(record.getStatus())
                    || RefundRecord.MANUAL.equals(record.getStatus())) {

                return;
            }

            Refund result;

            try {
                result = payUtil.queryRefund(record.getRefundNumber());

            } catch (ServiceException e) {

                // 必须同时满足HTTP状态和业务错误码。
                if (e.getHttpStatusCode() != 404
                        || !"RESOURCE_NOT_EXISTS".equals(e.getErrorCode())) {

                    throw e;
                }

                // 只有明确不存在才申请，始终复用原号和原金额。
                result = payUtil.refund(
                        record.getOrderNumber(),
                        record.getRefundNumber(),
                        record.getAmount(),
                        record.getAmount(),
                        "订单取消退款"
                );
            }

            // SDK返回结果仍需通过本地业务校验。
            refundRecordTxService.applyResult(result);

        } catch (Exception e) {

            // 只保存错误类型和受限的通道错误码，不保存原始响应。
            String error = e.getClass().getSimpleName();

            if (e instanceof ServiceException service
                    && service.getErrorCode() != null
                    && service.getErrorCode().matches("[A-Z0-9_]{1,64}")) {

                error += ":" + service.getErrorCode();
            }

            try {
                refundRecordTxService.rememberError(orderNumber, error);

            } catch (Exception saveFailure) {

                log.error(
                        "退款异常记录保存失败，orderNumber={}, exceptionType={}",
                        orderNumber,
                        saveFailure.getClass().getSimpleName()
                );
            }

            log.warn(
                    "退款结果待确认，orderNumber={}, error={}",
                    orderNumber,
                    error
            );
        }
    }

    /**
     * 每轮结束后间隔一分钟继续处理。
     * 任务从数据库读取，因此应用重启后仍能继续。
     */
    @Scheduled(fixedDelay = 60000)
    public void recoverPendingRefunds() {

        if (weChatPayUtilProvider.getIfAvailable() == null) {
            return;
        }

        for (RefundRecord record : refundRecordTxService.pendingRecords()) {
            process(record.getOrderNumber());
        }
    }
}
