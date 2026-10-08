package com.dineflow.model;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

/** 签名载荷只在服务端解读，不是客户端可编辑的请求实体。 */
@Data
public class SettlementTokenPayload {
    private int schemaVersion;
    private Long userId;
    private Long addressBookId;
    private Long addressVersion;
    private List<CartItem> items;
    private String priceDigest;
    private String contentDigest;
    private BigDecimal amount;
    private Long issuedAt;
    private Long expiresAt;
    @Data
    public static class CartItem {
        private Long cartItemId;
        private Long cartVersion;
        private Integer number;
    }
}
