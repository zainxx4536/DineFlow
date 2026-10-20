package com.dineflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dineflow.entity.PaymentRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PaymentRecordMapper extends BaseMapper<PaymentRecord> {

    /**
     * 首次请求创建支付记录。
     * 如果对应记录已经存在，则保留原来的记录，
     * 不覆盖支付金额、支付状态和微信交易号。
     */
    @Insert("""
            INSERT INTO payment_record
                (order_id, pay_number, amount, status, create_time)
            VALUES
                (#{orderId}, #{payNumber}, #{amount}, 0, #{createTime})
            ON DUPLICATE KEY UPDATE id = id
            """)
    void insertIfAbsent(PaymentRecord record);
}