package com.dineflow.controller.admin;

import com.dineflow.result.Result;
import com.dineflow.service.WorkspaceService;
import com.dineflow.vo.BusinessDataVO;
import com.dineflow.vo.DishOverViewVO;
import com.dineflow.vo.OrderOverViewVO;
import com.dineflow.vo.SetmealOverViewVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/admin/workspace")
@Slf4j
@Api(tags = "工作台相关接口")
@RequiredArgsConstructor
public class WorkSpaceController {

    private final WorkspaceService workspaceService;

    /**
     * 工作台今日数据查询
     */
    @GetMapping("/businessData")
    @ApiOperation("工作台今日数据查询")
    public Result<BusinessDataVO> businessData() {
        log.info("工作台今日数据查询");
        LocalDate today = LocalDate.now();
        LocalDateTime beginTime = today.atStartOfDay();
        LocalDateTime endTime = today.plusDays(1).atStartOfDay();
        BusinessDataVO businessData = workspaceService.getBusinessData(beginTime, endTime);
        return Result.success(businessData);
    }

    /**
     * 查询订单管理数据
     */
    @GetMapping("/overviewOrders")
    @ApiOperation("查询订单管理数据")
    public Result<OrderOverViewVO> orderOverView() {
        log.info("查询订单管理数据");
        return Result.success(workspaceService.getOrderOverView());
    }

    /**
     * 查询菜品总览
     */
    @GetMapping("/overviewDishes")
    @ApiOperation("查询菜品总览")
    public Result<DishOverViewVO> dishOverView() {
        log.info("查询菜品总览");
        return Result.success(workspaceService.getDishOverView());
    }

    /**
     * 查询套餐总览
     */
    @GetMapping("/overviewSetmeals")
    @ApiOperation("查询套餐总览")
    public Result<SetmealOverViewVO> setmealOverView() {
        log.info("查询套餐总览");
        return Result.success(workspaceService.getSetmealOverView());
    }
}
