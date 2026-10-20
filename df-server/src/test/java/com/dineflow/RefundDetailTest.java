package com.dineflow;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.dineflow.entity.RefundRecord;
import com.dineflow.mapper.OrdersMapper;
import com.dineflow.mapper.RefundRecordMapper;
import com.dineflow.order.service.RefundRecordTxService;
import com.dineflow.vo.OrderDetailVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RefundDetailTest {

    private RefundRecordMapper mapper;
    private RefundRecordTxService service;
    private OrderDetailVO order;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(
                new MybatisConfiguration(), "refund-detail-test"), RefundRecord.class);
        mapper = mock(RefundRecordMapper.class);
        service = new RefundRecordTxService(mock(OrdersMapper.class), mapper);
        order = new OrderDetailVO();
        order.setId(1L);
        order.setNumber("1001");
        order.setAmount(new BigDecimal("12.34"));
        order.setStatus(6);
        order.setPayStatus(3);
    }

    private JsonNode detail() {
        // 检查实际对外 JSON，避免把内部错误原文暴露给客户端。
        service.fillRefundInfo(order);
        return new ObjectMapper().findAndRegisterModules().valueToTree(order);
    }

    @Test
    void manualRefundIsVisibleWithoutLeakingRawError() {
        RefundRecord record = record(RefundRecord.MANUAL);
        record.setLastError("WECHAT_ABNORMAL secret=do-not-expose");
        when(mapper.selectOne(any())).thenReturn(record);
        JsonNode json = detail();
        assertEquals(3, json.path("refundStatus").asInt(-1));
        assertTrue(json.path("refundMessage").asText().contains("人工核查"));
        assertFalse(json.toString().contains("do-not-expose"));
        assertEquals(3, json.path("payStatus").asInt());
    }

    @Test
    void completedAndProcessingRefundsRemainDistinct() {
        when(mapper.selectOne(any())).thenReturn(record(RefundRecord.PROCESSING));
        assertTrue(detail().path("refundMessage").asText().contains("处理中"));
        when(mapper.selectOne(any())).thenReturn(record(RefundRecord.SUCCESS));
        assertEquals(2, detail().path("refundStatus").asInt(-1));
        assertEquals("已退款", detail().path("refundMessage").asText());
    }

    @Test
    void missingRefundDoesNotInventRecordOrSuccess() {
        when(mapper.selectOne(any())).thenReturn(null);
        JsonNode json = detail();
        assertTrue(json.path("refundStatus").isNull());
        assertTrue(json.path("refundMessage").isNull());
        verify(mapper, never()).insert(any(RefundRecord.class));
    }

    private RefundRecord record(Integer status) {
        RefundRecord record = new RefundRecord();
        record.setOrderId(1L);
        record.setOrderNumber("1001");
        record.setRefundNumber("RF1001");
        record.setAmount(new BigDecimal("12.34"));
        record.setStatus(status);
        return record;
    }
}
