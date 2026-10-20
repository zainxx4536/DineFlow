package com.dineflow.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dineflow.dto.OrdersPageQueryDTO;
import com.dineflow.entity.Orders;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dineflow.vo.*;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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

    Page<OrderConditionSearchVO> orderConditionSearch(@Param("page") Page<OrderConditionSearchVO> page, @Param("dto") OrdersPageQueryDTO dto);

    OrderStatisticsVO orderStatistics();

    List<TurnoverDailyVO> turnoverStatistics(LocalDateTime beginTime, LocalDateTime endTime);

    List<OrderDailyVO> dailyOrderStatistics(LocalDateTime beginTime, LocalDateTime endTime);

    List<GoodsSalesTop10VO> getSalesTop10(LocalDateTime beginTime, LocalDateTime endTime);

    BusinessOrderDataVO getBusinessOrderData(LocalDateTime beginTime, LocalDateTime endTime);

    OrderOverViewVO getOrderOverView(LocalDateTime beginTime, LocalDateTime endTime);

    Orders findByUserAndRequestId(@Param("userId") Long userId, @Param("requestId") String requestId);

    /**
     * 在支付结果处理事务中锁定并查询订单。
     * 先锁定，再判断当前订单状态，
     * 避免重复回调和取消操作之间出现检查、更新不一致。
     */
    @Select("SELECT * FROM orders WHERE number = #{number} FOR UPDATE")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    Orders selectByNumberForUpdate(@Param("number") String number);
}
