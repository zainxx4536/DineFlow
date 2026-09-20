package com.dineflow.vo;

import com.dineflow.entity.OrderDetail;
import com.dineflow.entity.Orders;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class OrderDetailVO extends Orders implements Serializable {

    private List<OrderDetail> orderDetailList;
}
