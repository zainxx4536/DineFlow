package com.dineflow.vo;

import com.dineflow.entity.Orders;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

@EqualsAndHashCode(callSuper = true)
@Data
public class OrderConditionSearchVO extends Orders implements Serializable {

    //订单包含的菜品，以字符串形式展示
    private String orderDishes;
}
