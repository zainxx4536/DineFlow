package com.dineflow.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 数据概览
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessDataVO implements Serializable {

    private BigDecimal turnover;//营业额

    private Long validOrderCount;//有效订单数

    private BigDecimal orderCompletionRate;//订单完成率

    private BigDecimal unitPrice;//平均客单价

    private Long newUsers;//新增用户数

}
