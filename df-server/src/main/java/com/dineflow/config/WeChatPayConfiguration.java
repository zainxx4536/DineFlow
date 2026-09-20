package com.dineflow.config;

import com.dineflow.properties.WeChatProperties;
import com.wechat.pay.java.core.RSAPublicKeyConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.service.refund.RefundService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.wechat.pay.java.service.payments.jsapi.JsapiServiceExtension;

/**
 * 微信支付配置类
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "dineflow.wechat.pay",
        name = "enabled",
        havingValue = "true"
)
public class WeChatPayConfiguration {

    private final WeChatProperties weChatProperties;

    /**
     * 微信支付核心配置
     */
    @Bean
    public RSAPublicKeyConfig weChatPayConfig() {

        return new RSAPublicKeyConfig.Builder()
                .merchantId(weChatProperties.getMchid())
                .privateKeyFromPath(weChatProperties.getPrivateKeyFilePath())
                .merchantSerialNumber(weChatProperties.getMchSerialNo())
                .publicKeyFromPath(weChatProperties.getPublicKeyFilePath())
                .publicKeyId(weChatProperties.getPublicKeyId())
                .apiV3Key(weChatProperties.getApiV3Key())
                .build();
    }

    /**
     * JSAPI / 小程序支付服务
     */
    @Bean
    public JsapiServiceExtension jsapiService(RSAPublicKeyConfig config) {

        return new JsapiServiceExtension.Builder()
                .config(config)
                .build();
    }

    /**
     * 微信退款服务
     */
    @Bean
    public RefundService refundService(RSAPublicKeyConfig config) {

        return new RefundService.Builder()
                .config(config)
                .build();
    }

    /**
     * 微信支付回调解析器
     * <p>
     * 负责：
     * 1. 验签
     * 2. 解密
     * 3. JSON -> Java对象
     */
    @Bean
    public NotificationParser notificationParser(RSAPublicKeyConfig config) {

        return new NotificationParser(config);
    }
}
