package com.dineflow.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dineflow.dto.OrdersPageQueryDTO;
import com.dineflow.entity.Orders;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dineflow.vo.HistoryOrdersQueryVO;
import org.apache.ibatis.annotations.Param;

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
}
