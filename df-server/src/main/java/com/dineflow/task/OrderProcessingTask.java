package com.dineflow.task;

import com.dineflow.order.service.PaymentRecoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderProcessingTask {
    private final PaymentRecoveryService paymentRecoveryService;

    @Scheduled(cron = "0 */3 * * * *")
    public void cancelOverdueOrders() {
        paymentRecoveryService.cancelOverdueOrders();
    }
}
