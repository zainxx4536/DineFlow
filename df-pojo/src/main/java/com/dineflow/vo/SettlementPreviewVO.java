package com.dineflow.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 结算预览返回结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementPreviewVO {

    private String settlementToken;

    private java.time.LocalDateTime expiresAt;

    private Long addressBookId;

    private Long addressVersion;

    private List<SettlementItemVO> items;

    private BigDecimal goodsAmount;

    private BigDecimal packAmount;

    private BigDecimal deliveryFee;

    private BigDecimal discountAmount;

    /**
     * 最终应付金额
     */
    private BigDecimal amount;

}