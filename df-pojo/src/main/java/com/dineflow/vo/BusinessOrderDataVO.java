package com.dineflow.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class BusinessOrderDataVO implements Serializable {

    private BigDecimal turnover;

    private Long totalOrderCount;

    private Long validOrderCount;
}