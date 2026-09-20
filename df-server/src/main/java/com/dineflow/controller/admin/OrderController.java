package com.dineflow.controller.admin;

import com.dineflow.dto.OrdersCancelDTO;
import com.dineflow.dto.OrdersConfirmDTO;
import com.dineflow.dto.OrdersPageQueryDTO;
import com.dineflow.dto.OrdersRejectionDTO;
import com.dineflow.result.PageResult;
import com.dineflow.result.Result;
import com.dineflow.service.IOrdersService;
import com.dineflow.vo.OrderConditionSearchVO;
import com.dineflow.vo.OrderDetailVO;
import com.dineflow.vo.OrderStatisticsVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

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
    public Result<PageResult<OrderConditionSearchVO>> orderConditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
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

    /**
     * 完成订单
     */
    @PutMapping("/complete/{id}")
    @ApiOperation("完成订单")
    public Result<String> completeOrder(@PathVariable Long id) {
        log.info("完成订单：{}", id);
        ordersService.completeOrder(id);
        return Result.success();
    }

    /**
     * 取消订单
     */
    @PutMapping("/cancel")
    @ApiOperation("取消订单")
    public Result<String> adminCancelOrder(@RequestBody OrdersCancelDTO ordersCancelDTO) {
        log.info("取消订单：{}", ordersCancelDTO);
        ordersService.adminCancelOrder(ordersCancelDTO);
        return Result.success();
    }

    /**
     * 拒单
     */
    @PutMapping("/rejection")
    @ApiOperation("拒单")
    public Result<String> rejectionOrder(@RequestBody OrdersRejectionDTO ordersRejectionDTO) {
        log.info("拒单：{}", ordersRejectionDTO);
        ordersService.rejectionOrder(ordersRejectionDTO);
        return Result.success();
    }

    /**
     * 接单
     */
    @PutMapping("/confirm")
    @ApiOperation("接单")
    public Result<String> confirmOrder(@RequestBody OrdersConfirmDTO ordersConfirmDTO) {
        log.info("接单：{}", ordersConfirmDTO);
        ordersService.confirmOrder(ordersConfirmDTO);
        return Result.success();
    }

    /**
     * 查询订单详情
     */
    @GetMapping("/details/{id}")
    @ApiOperation("查询订单详情")
    public Result<OrderDetailVO> getOrderDetails(@PathVariable Long id) {
        log.info("查询订单详情：{}", id);
        OrderDetailVO orderDetail = ordersService.adminGetOrderDetail(id);
        return Result.success(orderDetail);
    }

    /**
     * 派送订单
     */
    @PutMapping("/delivery/{id}")
    @ApiOperation("派送订单")
    public Result<String> deliveryOrder(@PathVariable Long id) {
        log.info("派送订单：{}", id);
        ordersService.deliveryOrder(id);
        return Result.success();
    }
}
