package com.dineflow.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * mapper 层返回的每日营业额
 */
@Data
public class TurnoverDailyVO {

    /**
     * 日期
     */
    private LocalDate date;

    /**
     * 当日营业额
     */
    private BigDecimal turnover;
}