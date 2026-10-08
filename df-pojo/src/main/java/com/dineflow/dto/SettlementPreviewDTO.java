package com.dineflow.dto;

import lombok.Data;

import java.util.List;

@Data
public class SettlementPreviewDTO {

    /**
     * 收货地址id
     */
    private Long addressBookId;

    /**
     * 本次选中的购物车条目id
     */
    private List<Long> cartItemId;
}