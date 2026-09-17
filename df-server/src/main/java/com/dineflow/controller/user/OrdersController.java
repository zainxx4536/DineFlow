package com.dineflow.controller.user;


import com.dineflow.dto.OrdersSubmitDTO;
import com.dineflow.result.Result;
import com.dineflow.service.IOrdersService;
import com.dineflow.vo.OrderSubmitVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 订单表 前端控制器
 * </p>
 *
 * @author zainxx
 * @since 2026-09-08
 */
@RestController
@RequestMapping("/user/order")
@Slf4j
@RequiredArgsConstructor
@Api(tags = "订单相关接口")
public class OrdersController {

    private final IOrdersService ordersService;

    @PostMapping("/submit")
    @ApiOperation("用户下单")
    public Result<OrderSubmitVO> submitOrder(@RequestBody OrdersSubmitDTO ordersSubmitDTO) {
        log.info("用户下单：{}", ordersSubmitDTO);
        OrderSubmitVO orderSubmitVO = ordersService.submitOrder(ordersSubmitDTO);
        return Result.success(orderSubmitVO);
    }
}
