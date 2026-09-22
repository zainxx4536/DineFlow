package com.dineflow.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dineflow.dto.OrdersPageQueryDTO;
import com.dineflow.entity.Orders;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dineflow.vo.*;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 订单表 Mapper 接口
 * </p>
 *
 * @author zainxx
 * @since 2026-09-08
 */
public interface OrdersMapper extends BaseMapper<Orders> {

    Page<HistoryOrdersQueryVO> historyOrdersQuery(
            Page<HistoryOrdersQueryVO> page,
            @Param("userId") Long userId,
            @Param("dto") OrdersPageQueryDTO ordersPageQueryDTO
    );

    Page<OrderConditionSearchVO> orderConditionSearch(Page<OrderConditionSearchVO> page, OrdersPageQueryDTO dto);

    OrderStatisticsVO orderStatistics();

    List<TurnoverDailyVO> turnoverStatistics(LocalDateTime beginTime, LocalDateTime endTime);

    List<OrderDailyVO> dailyOrderStatistics(LocalDateTime beginTime, LocalDateTime endTime);

    List<GoodsSalesTop10VO> getSalesTop10(LocalDateTime beginTime, LocalDateTime endTime);

    BusinessOrderDataVO getBusinessOrderData(LocalDateTime beginTime, LocalDateTime endTime);

    OrderOverViewVO getOrderOverView(LocalDateTime beginTime, LocalDateTime endTime);
}
