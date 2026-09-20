package com.dineflow.controller.admin;

import com.dineflow.dto.OrdersPageQueryDTO;
import com.dineflow.result.PageResult;
import com.dineflow.result.Result;
import com.dineflow.service.IOrdersService;
import com.dineflow.vo.OrderConditionSearchVO;
import com.dineflow.vo.OrderStatisticsVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/order")
@Slf4j
@RequiredArgsConstructor
@Api(tags = "订单相关接口")
public class OrderController {

    private final IOrdersService ordersService;

    /**
     * 订单搜索
     */
    @GetMapping("/conditionSearch")
    @ApiOperation("订单搜索")
    public Result<PageResult<OrderConditionSearchVO>> orderConditionSearch(OrdersPageQueryDTO ordersPageQueryDTO){
        log.info("订单搜索：{}", ordersPageQueryDTO);
        PageResult<OrderConditionSearchVO> pageResult = ordersService.orderConditionSearch(ordersPageQueryDTO);
        return Result.success(pageResult);
    }

    /**
     * 各个状态的订单数量统计
     */
    @GetMapping("/statistics")
    @ApiOperation("各个状态的订单数量统计")
    public Result<OrderStatisticsVO> orderStatistics() {
        log.info("各个状态的订单数量统计");
        OrderStatisticsVO orderStatistics = ordersService.orderStatistics();
        return Result.success(orderStatistics);
    }
}
