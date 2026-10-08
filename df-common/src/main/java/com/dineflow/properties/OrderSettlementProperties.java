package com.dineflow.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.Duration;

/** 首版按单收取固定费用，配置不接受客户端覆盖。 */
@Data
@Component
@ConfigurationProperties(prefix = "dineflow.order.settlement")
public class OrderSettlementProperties {
    private BigDecimal packFee = BigDecimal.ZERO;
    private BigDecimal deliveryFee = BigDecimal.ZERO;
    private BigDecimal discountAmount = BigDecimal.ZERO;
    private Duration ttl = Duration.ofMinutes(10);
    // 外部注入 Base64 密钥，解码后至少 32 字节；不在仓库保存真实密钥。
    private String secret;
}
