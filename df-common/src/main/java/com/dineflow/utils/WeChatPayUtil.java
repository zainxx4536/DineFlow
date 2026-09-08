package com.dineflow.utils;

import com.dineflow.properties.WeChatProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wechat.pay.contrib.apache.httpclient.WechatPayHttpClientBuilder;
import com.wechat.pay.contrib.apache.httpclient.util.PemUtil;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

/**
 * 微信支付工具类
 */
@Component
public class WeChatPayUtil {

    /**
     * JSAPI 下单接口地址
     */
    public static final String JSAPI =
            "https://api.mch.weixin.qq.com/v3/pay/transactions/jsapi";

    /**
     * 申请退款接口地址
     */
    public static final String REFUNDS =
            "https://api.mch.weixin.qq.com/v3/refund/domestic/refunds";

    /**
     * Jackson 对象映射器
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private WeChatProperties weChatProperties;

    /**
     * 获取调用微信支付接口的 HttpClient
     * <p>
     * 通过 WechatPayHttpClientBuilder 构造的 HttpClient
     * 会自动处理请求签名和微信响应验签。
     *
     * @return CloseableHttpClient
     */
    private CloseableHttpClient getClient() throws Exception {

        // 加载商户 API 私钥
        PrivateKey merchantPrivateKey = loadMerchantPrivateKey();

        // 加载微信支付平台证书
        X509Certificate weChatPayCertificate;

        try (FileInputStream inputStream =
                     new FileInputStream(
                             weChatProperties.getWeChatPayCertFilePath()
                     )) {

            weChatPayCertificate =
                    PemUtil.loadCertificate(inputStream);
        }

        List<X509Certificate> weChatPayCertificates =
                Collections.singletonList(weChatPayCertificate);

        WechatPayHttpClientBuilder builder =
                WechatPayHttpClientBuilder.create()
                        .withMerchant(
                                weChatProperties.getMchid(),
                                weChatProperties.getMchSerialNo(),
                                merchantPrivateKey
                        )
                        .withWechatPay(weChatPayCertificates);

        return builder.build();
    }

    /**
     * 加载商户 API 私钥
     *
     * @return 商户私钥
     */
    private PrivateKey loadMerchantPrivateKey() throws Exception {

        try (FileInputStream inputStream =
                     new FileInputStream(
                             weChatProperties.getPrivateKeyFilePath()
                     )) {

            return PemUtil.loadPrivateKey(inputStream);
        }
    }

    /**
     * 发送 POST 请求
     *
     * @param url  请求地址
     * @param body JSON 请求体
     * @return 微信接口响应数据
     */
    private String post(String url, String body) throws Exception {

        try (CloseableHttpClient httpClient = getClient()) {

            HttpPost httpPost = new HttpPost(url);

            httpPost.addHeader(
                    HttpHeaders.ACCEPT,
                    ContentType.APPLICATION_JSON.toString()
            );

            httpPost.addHeader(
                    HttpHeaders.CONTENT_TYPE,
                    ContentType.APPLICATION_JSON.toString()
            );

            httpPost.addHeader(
                    "Wechatpay-Serial",
                    weChatProperties.getMchSerialNo()
            );

            httpPost.setEntity(
                    new StringEntity(
                            body,
                            ContentType.APPLICATION_JSON
                                    .withCharset(StandardCharsets.UTF_8)
                    )
            );

            try (CloseableHttpResponse response =
                         httpClient.execute(httpPost)) {

                return EntityUtils.toString(
                        response.getEntity(),
                        StandardCharsets.UTF_8
                );
            }
        }
    }

    /**
     * 发送 GET 请求
     *
     * @param url 请求地址
     * @return 微信接口响应数据
     */
    private String get(String url) throws Exception {

        try (CloseableHttpClient httpClient = getClient()) {

            HttpGet httpGet = new HttpGet(url);

            httpGet.addHeader(
                    HttpHeaders.ACCEPT,
                    ContentType.APPLICATION_JSON.toString()
            );

            httpGet.addHeader(
                    HttpHeaders.CONTENT_TYPE,
                    ContentType.APPLICATION_JSON.toString()
            );

            httpGet.addHeader(
                    "Wechatpay-Serial",
                    weChatProperties.getMchSerialNo()
            );

            try (CloseableHttpResponse response =
                         httpClient.execute(httpGet)) {

                return EntityUtils.toString(
                        response.getEntity(),
                        StandardCharsets.UTF_8
                );
            }
        }
    }

    /**
     * JSAPI 下单
     *
     * @param orderNum    商户订单号
     * @param total       总金额，单位：元
     * @param description 商品描述
     * @param openid      微信用户 openid
     * @return 微信接口响应数据
     */
    private String jsapi(String orderNum,
                         BigDecimal total,
                         String description,
                         String openid) throws Exception {

        // 构造请求 JSON
        ObjectNode jsonObject =
                OBJECT_MAPPER.createObjectNode();

        jsonObject.put(
                "appid",
                weChatProperties.getAppid()
        );

        jsonObject.put(
                "mchid",
                weChatProperties.getMchid()
        );

        jsonObject.put(
                "description",
                description
        );

        jsonObject.put(
                "out_trade_no",
                orderNum
        );

        jsonObject.put(
                "notify_url",
                weChatProperties.getNotifyUrl()
        );

        // 金额信息
        ObjectNode amount =
                OBJECT_MAPPER.createObjectNode();

        amount.put(
                "total",
                yuanToFen(total)
        );

        amount.put(
                "currency",
                "CNY"
        );

        jsonObject.set(
                "amount",
                amount
        );

        // 支付者信息
        ObjectNode payer =
                OBJECT_MAPPER.createObjectNode();

        payer.put(
                "openid",
                openid
        );

        jsonObject.set(
                "payer",
                payer
        );

        // Java对象 -> JSON字符串
        String body =
                OBJECT_MAPPER.writeValueAsString(jsonObject);

        return post(JSAPI, body);
    }

    /**
     * 小程序支付
     *
     * @param orderNum    商户订单号
     * @param total       金额，单位：元
     * @param description 商品描述
     * @param openid      微信用户 openid
     * @return 支付参数或微信错误响应
     */
    public JsonNode pay(String orderNum,
                        BigDecimal total,
                        String description,
                        String openid) throws Exception {

        // 调用 JSAPI 下单接口，获取预支付交易单
        String bodyAsString =
                jsapi(
                        orderNum,
                        total,
                        description,
                        openid
                );

        // JSON字符串 -> JsonNode
        JsonNode jsonNode =
                OBJECT_MAPPER.readTree(bodyAsString);

        JsonNode prepayIdNode =
                jsonNode.get("prepay_id");

        // 下单成功
        if (prepayIdNode != null
                && !prepayIdNode.isNull()) {

            String prepayId =
                    prepayIdNode.asText();

            // 时间戳，单位：秒
            String timeStamp =
                    String.valueOf(
                            System.currentTimeMillis() / 1000
                    );

            // 随机字符串
            String nonceStr =
                    RandomStringUtils.randomNumeric(32);

            /*
             * 小程序调起微信支付时需要进行二次签名。
             *
             * 签名串格式：
             *
             * appId\n
             * timeStamp\n
             * nonceStr\n
             * package\n
             */
            String signMessage =
                    weChatProperties.getAppid()
                            + "\n"
                            + timeStamp
                            + "\n"
                            + nonceStr
                            + "\n"
                            + "prepay_id="
                            + prepayId
                            + "\n";

            byte[] message =
                    signMessage.getBytes(
                            StandardCharsets.UTF_8
                    );

            // SHA256withRSA 签名
            Signature signature =
                    Signature.getInstance(
                            "SHA256withRSA"
                    );

            signature.initSign(
                    loadMerchantPrivateKey()
            );

            signature.update(message);

            String packageSign =
                    Base64.getEncoder()
                            .encodeToString(
                                    signature.sign()
                            );

            /*
             * 构造返回给微信小程序的数据，
             * 小程序使用这些参数调起微信支付。
             */
            ObjectNode result =
                    OBJECT_MAPPER.createObjectNode();

            result.put(
                    "timeStamp",
                    timeStamp
            );

            result.put(
                    "nonceStr",
                    nonceStr
            );

            result.put(
                    "package",
                    "prepay_id=" + prepayId
            );

            result.put(
                    "signType",
                    "RSA"
            );

            result.put(
                    "paySign",
                    packageSign
            );

            return result;
        }

        // 微信下单失败时，直接返回微信返回的错误 JSON
        return jsonNode;
    }

    /**
     * 申请退款
     *
     * @param outTradeNo  商户订单号
     * @param outRefundNo 商户退款单号
     * @param refund      退款金额，单位：元
     * @param total       原订单金额，单位：元
     * @return 微信退款接口响应
     */
    public String refund(String outTradeNo,
                         String outRefundNo,
                         BigDecimal refund,
                         BigDecimal total) throws Exception {

        ObjectNode jsonObject =
                OBJECT_MAPPER.createObjectNode();

        jsonObject.put(
                "out_trade_no",
                outTradeNo
        );

        jsonObject.put(
                "out_refund_no",
                outRefundNo
        );

        // 金额信息
        ObjectNode amount =
                OBJECT_MAPPER.createObjectNode();

        amount.put(
                "refund",
                yuanToFen(refund)
        );

        amount.put(
                "total",
                yuanToFen(total)
        );

        amount.put(
                "currency",
                "CNY"
        );

        jsonObject.set(
                "amount",
                amount
        );

        jsonObject.put(
                "notify_url",
                weChatProperties.getRefundNotifyUrl()
        );

        String body =
                OBJECT_MAPPER.writeValueAsString(jsonObject);

        // 调用微信退款接口
        return post(REFUNDS, body);
    }

    /**
     * 将金额从“元”转换为“分”
     * <p>
     * 例如：
     * 12.34 元 -> 1234 分
     *
     * @param amount 金额，单位：元
     * @return 金额，单位：分
     */
    private int yuanToFen(BigDecimal amount) {

        return amount
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
    }
}