package com.dineflow.utils;

import com.dineflow.properties.WeChatProperties;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.jsapi.JsapiServiceExtension;
import com.wechat.pay.java.service.payments.jsapi.model.Amount;
import com.wechat.pay.java.service.payments.jsapi.model.Payer;
import com.wechat.pay.java.service.payments.jsapi.model.PrepayRequest;
import com.wechat.pay.java.service.payments.jsapi.model.PrepayWithRequestPaymentResponse;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.refund.RefundService;
import com.wechat.pay.java.service.refund.model.AmountReq;
import com.wechat.pay.java.service.refund.model.CreateRequest;
import com.wechat.pay.java.service.refund.model.Refund;
import com.wechat.pay.java.service.refund.model.RefundNotification;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 微信支付相关功能工具类
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "dineflow.wechat.pay",
        name = "enabled",
        havingValue = "true"
)
public class WeChatPayUtil {

    private final JsapiServiceExtension jsapiService;

    private final RefundService refundService;

    private final NotificationParser notificationParser;

    private final WeChatProperties weChatProperties;

    /**
     * 小程序支付
     *
     * @param orderNumber 商户订单号
     * @param total       支付金额，单位：元
     * @param description 商品描述
     * @param openid      用户openid
     */
    public PrepayWithRequestPaymentResponse pay(
            String orderNumber,
            BigDecimal total,
            String description,
            String openid) {

        PrepayRequest request = new PrepayRequest();

        request.setAppid(weChatProperties.getAppid());

        request.setMchid(weChatProperties.getMchid());

        request.setOutTradeNo(orderNumber);

        request.setDescription(description);

        request.setNotifyUrl(weChatProperties.getNotifyUrl());

        // 金额
        Amount amount = new Amount();
        amount.setTotal(yuanToFen(total));
        amount.setCurrency("CNY");

        request.setAmount(amount);

        // 支付者
        Payer payer = new Payer();
        payer.setOpenid(openid);

        request.setPayer(payer);

        /*
         * SDK完成：
         * 1. 调用JSAPI预下单
         * 2. 获取prepay_id
         * 3. 生成timeStamp
         * 4. 生成nonceStr
         * 5. 二次签名
         * 6. 生成paySign
         */
        return jsapiService.prepayWithRequestPayment(request);
    }

    /**
     * 申请退款
     *
     * @param orderNumber  原商户订单号
     * @param refundNumber 商户退款单号
     * @param refundAmount 退款金额，单位：元
     * @param totalAmount  原订单金额，单位：元
     * @param reason       退款原因
     */
    public Refund refund(
            String orderNumber,
            String refundNumber,
            BigDecimal refundAmount,
            BigDecimal totalAmount,
            String reason) {

        CreateRequest request = new CreateRequest();

        request.setOutTradeNo(orderNumber);

        request.setOutRefundNo(refundNumber);

        request.setReason(reason);

        request.setNotifyUrl(weChatProperties.getRefundNotifyUrl());

        AmountReq amount = new AmountReq();

        amount.setRefund((long) yuanToFen(refundAmount));

        amount.setTotal((long) yuanToFen(totalAmount));

        amount.setCurrency("CNY");

        request.setAmount(amount);

        return refundService.create(request);
    }

    /**
     * 解析支付结果回调
     */
    public Transaction parsePayNotify(
            String serial,
            String nonce,
            String signature,
            String timestamp,
            String body) {

        RequestParam requestParam =
                buildRequestParam(
                        serial,
                        nonce,
                        signature,
                        timestamp,
                        body
                );

        return notificationParser.parse(
                requestParam,
                Transaction.class
        );
    }

    /**
     * 解析退款结果回调
     */
    public RefundNotification parseRefundNotify(
            String serial,
            String nonce,
            String signature,
            String timestamp,
            String body) {

        RequestParam requestParam =
                buildRequestParam(
                        serial,
                        nonce,
                        signature,
                        timestamp,
                        body
                );

        return notificationParser.parse(
                requestParam,
                RefundNotification.class
        );
    }

    /**
     * 构建微信支付通知参数
     */
    private RequestParam buildRequestParam(
            String serial,
            String nonce,
            String signature,
            String timestamp,
            String body) {

        return new RequestParam.Builder()
                .serialNumber(serial)
                .nonce(nonce)
                .signature(signature)
                .timestamp(timestamp)
                .body(body)
                .build();
    }

    /**
     * 元 -> 分
     */
    private int yuanToFen(BigDecimal amount) {

        return amount
                .movePointRight(2)
                .setScale(0, RoundingMode.UNNECESSARY)
                .intValueExact();
    }
}