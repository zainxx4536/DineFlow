package com.dineflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("payment_record")
public class PaymentRecord {
    // 待确认支付结果
    public static final Integer PENDING = 0;
    // 已确认支付成功
    public static final Integer SUCCESS = 1;
    public static final Integer CLOSED = 2;
    public static final Integer MANUAL = 3;
    private Integer queryCount;
    private LocalDateTime nextQueryTime;
    private String lastError;
    private LocalDateTime updateTime;
    @TableId(type = IdType.AUTO)
    private Long id;
    // 订单ID
    private Long orderId;
    // 商户支付号，目前直接使用订单号
    private String payNumber;
    // 本次支付金额，单位元
    private BigDecimal amount;
    // 0待确认，1支付成功，2支付关闭，3待人工核查
    private Integer status;
    // 微信支付交易号
    private String transactionId;
    // 支付记录创建时间
    private LocalDateTime createTime;
    // 微信确认的支付成功时间
    private LocalDateTime successTime;
}
