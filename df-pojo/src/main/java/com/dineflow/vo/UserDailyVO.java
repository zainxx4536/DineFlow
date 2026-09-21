package com.dineflow.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * mapper 层返回的每日新用户数
 */
@Data
public class UserDailyVO {

    /**
     * 日期
     */
    private LocalDate date;

    /**
     * 当日新用户数
     */
    private Long newUserCount;
}