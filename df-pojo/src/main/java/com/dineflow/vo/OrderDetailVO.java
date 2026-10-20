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

    // 退款记录状态：0待确认，1处理中，2成功，3待人工核查；无记录时为空。
    private Integer refundStatus;
    private Integer paymentStatus;
    private String paymentMessage;

    // 面向用户的公开退款提示，不返回通道原文或内部异常。
    private String refundMessage;
}
