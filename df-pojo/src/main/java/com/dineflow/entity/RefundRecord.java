package com.dineflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("refund_record")
public class RefundRecord {

    // 已保存意图，退款结果待确认
    public static final Integer PENDING = 0;

    // 微信确认退款处理中
    public static final Integer PROCESSING = 1;

    // 已确认退款成功
    public static final Integer SUCCESS = 2;

    // 退款关闭或异常，转人工处理
    public static final Integer MANUAL = 3;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer attemptCount;
    private Integer queryCount;

    private Long orderId;

    private String orderNumber;

    private String refundNumber;

    private BigDecimal amount;

    private Integer status;

    private String refundId;

    private String lastError;

    private LocalDateTime nextRetryTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private LocalDateTime successTime;
}