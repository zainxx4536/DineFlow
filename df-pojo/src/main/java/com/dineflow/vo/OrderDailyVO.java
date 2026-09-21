package com.dineflow.vo;

import lombok.Data;

import java.time.LocalDate;

/**
 * mapper 层返回的每日订单数、有效订单数
 */
@Data
public class OrderDailyVO {

    private LocalDate date;

    /**
     * 当日订单总数
     */
    private Long orderCount;

    /**
     * 当日有效订单数
     */
    private Long validOrderCount;
}