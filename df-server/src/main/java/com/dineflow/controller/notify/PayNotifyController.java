package com.dineflow.controller.notify;

import com.dineflow.entity.Orders;
import com.dineflow.service.IOrdersService;
import com.dineflow.utils.WeChatPayUtil;
import com.wechat.pay.java.core.exception.ValidationException;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.refund.model.RefundNotification;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;

/**
 * 微信支付、退款结果通知接口。
 */
@RestController
@Slf4j
@Api(tags = "支付回调相关接口")
@RequiredArgsConstructor
@RequestMapping("/notify/wechat")
@ConditionalOnProperty(
        prefix = "dineflow.wechat.pay",
        name = "enabled",
        havingValue = "true"
)
public class PayNotifyController {

    private final IOrdersService ordersService;

    private final WeChatPayUtil weChatPayUtil;

    /**
     * 微信支付结果通知。
     */
    @PostMapping("/pay")
    public ResponseEntity<Void> payNotify(
            @RequestHeader("Wechatpay-Serial") String serial,
            @RequestHeader("Wechatpay-Nonce") String nonce,
            @RequestHeader("Wechatpay-Signature") String signature,
            @RequestHeader("Wechatpay-Timestamp") String timestamp,
            HttpServletRequest request) {

        try {
            /*
             * 直接读取原始请求体。
             * 保留 JSON 中的空格、换行和字段顺序，
             * 不经过 Jackson 反序列化。
             */
            String body = StreamUtils.copyToString(request.getInputStream(), StandardCharsets.UTF_8);

            // SDK 完成验签、解密和反序列化
            Transaction transaction = weChatPayUtil.parsePayNotify(
                    serial,
                    nonce,
                    signature,
                    timestamp,
                    body
            );

            /*
             * 第一阶段：执行本地事务。
             * 方法通过 Spring 代理返回时，事务已经提交。
             */
            Orders paidOrder = ordersService.paySuccess(transaction);

            /*
             * 第二阶段：事务外处理。
             * 晚到支付继续查询或申请退款。
             * 首次正常支付发送来单提醒，晚到支付不发送来单提醒。
             */
            ordersService.handlePaySuccessAfterCommit(paidOrder);
            return ResponseEntity.ok().build();
        } catch (ValidationException e) {
            log.warn("微信支付回调验签失败，exceptionType={}", e.getClass().getSimpleName());
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .build();
        } catch (Exception e) {
            log.error("微信支付回调处理失败，exceptionType={}", e.getClass().getSimpleName());
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .build();
        }
    }

    /**
     * 微信退款结果通知。
     */
    @PostMapping("/refund")
    public ResponseEntity<Void> refundNotify(
            @RequestHeader("Wechatpay-Serial") String serial,
            @RequestHeader("Wechatpay-Nonce") String nonce,
            @RequestHeader("Wechatpay-Signature") String signature,
            @RequestHeader("Wechatpay-Timestamp") String timestamp,
            HttpServletRequest request) {

        try {
            // 直接读取原始请求体，交给 SDK 验签
            String body = StreamUtils.copyToString(request.getInputStream(), StandardCharsets.UTF_8);

            RefundNotification notification = weChatPayUtil.parseRefundNotify(
                    serial,
                    nonce,
                    signature,
                    timestamp,
                    body
            );
            log.info("收到已验签的微信退款回调");
            ordersService.handleRefundNotify(notification);
            return ResponseEntity.ok().build();
        } catch (ValidationException e) {
            log.warn("微信退款回调验签失败，exceptionType={}", e.getClass().getSimpleName());
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .build();
        } catch (Exception e) {
            log.error("微信退款回调处理失败，exceptionType={}", e.getClass().getSimpleName());
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .build();
        }
    }
}
