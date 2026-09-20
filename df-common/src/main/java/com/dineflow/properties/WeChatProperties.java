package com.dineflow.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "dineflow.wechat")
public class WeChatProperties {

    /**
     * 小程序 AppId
     */
    private String appid;

    /**
     * 小程序 Secret
     */
    private String secret;

    /**
     * 微信支付商户号
     */
    private String mchid;

    /**
     * 商户 API 证书序列号
     */
    private String mchSerialNo;

    /**
     * 商户 API 私钥路径
     * apiclient_key.pem
     */
    private String privateKeyFilePath;

    /**
     * APIv3 密钥
     */
    private String apiV3Key;

    /**
     * 微信支付公钥路径
     */
    private String publicKeyFilePath;

    /**
     * 微信支付公钥 ID
     */
    private String publicKeyId;

    /**
     * 支付结果通知地址
     */
    private String notifyUrl;

    /**
     * 退款结果通知地址
     */
    private String refundNotifyUrl;
}
