package com.dineflow.controller.notify;

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
import org.springframework.web.bind.annotation.*;

/**
 * 支付回调相关接口，给微信平台调用的接口
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
     * 微信支付结果回调
     */
    @PostMapping("/pay")
    public ResponseEntity<Void> payNotify(
            @RequestHeader("Wechatpay-Serial") String serial,
            @RequestHeader("Wechatpay-Nonce") String nonce,
            @RequestHeader("Wechatpay-Signature") String signature,
            @RequestHeader("Wechatpay-Timestamp") String timestamp,
            @RequestBody String body) {
        try {
            // 1. SDK完成验签、解密、反序列化
            Transaction transaction = weChatPayUtil.parsePayNotify(
                    serial,
                    nonce,
                    signature,
                    timestamp,
                    body
            );
            log.info(
                    "收到微信支付回调，订单号：{}，微信支付交易号：{}，支付状态：{}",
                    transaction.getOutTradeNo(),
                    transaction.getTransactionId(),
                    transaction.getTradeState()
            );
            // 2. 处理支付成功业务
            ordersService.paySuccess(transaction);
            // 3. 告诉微信：处理成功
            return ResponseEntity.ok().build();
        } catch (ValidationException e) {
            // 回调验签失败
            log.warn("微信支付回调验签失败", e);
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .build();
        } catch (Exception e) {
            // 数据库等业务处理失败
            log.error("微信支付回调处理失败", e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .build();
        }
    }


    /**
     * 微信退款结果回调
     */
    @PostMapping("/refund")
    public ResponseEntity<Void> refundNotify(
            @RequestHeader("Wechatpay-Serial") String serial,
            @RequestHeader("Wechatpay-Nonce") String nonce,
            @RequestHeader("Wechatpay-Signature") String signature,
            @RequestHeader("Wechatpay-Timestamp") String timestamp,
            @RequestBody String body) {

        try {
            // 验签 + 解密 + 反序列化
            RefundNotification notification = weChatPayUtil.parseRefundNotify(
                    serial,
                    nonce,
                    signature,
                    timestamp,
                    body
            );

            log.info("收到微信退款回调：{}", notification);

            // 处理退款结果
            ordersService.handleRefundNotify(notification);

            return ResponseEntity.ok().build();

        } catch (ValidationException e) {
            log.error("微信退款回调验签失败", e);
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .build();
        } catch (Exception e) {
            log.error("微信退款回调处理失败", e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .build();
        }
    }
}
