package com.dineflow.controller.user;


import com.dineflow.dto.OrdersPageQueryDTO;
import com.dineflow.dto.OrdersPaymentDTO;
import com.dineflow.dto.OrdersSubmitDTO;
import com.dineflow.result.PageResult;
import com.dineflow.result.Result;
import com.dineflow.service.IOrdersService;
import com.dineflow.vo.HistoryOrdersQueryVO;
import com.dineflow.vo.OrderDetailVO;
import com.dineflow.vo.OrderPaymentVO;
import com.dineflow.vo.OrderSubmitVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 订单表 前端控制器
 * </p>
 *
 * @author zainxx
 * @since 2026-09-08
 */
@RestController("userOrdersController")
@RequestMapping("/user/order")
@Slf4j
@RequiredArgsConstructor
@Api(tags = "订单相关接口")
public class OrdersController {

    private final IOrdersService ordersService;

    /**
     * 用户下单接口
     */
    @PostMapping("/submit")
    @ApiOperation("用户下单")
    public Result<OrderSubmitVO> submitOrder(@RequestBody OrdersSubmitDTO ordersSubmitDTO) {
        log.info("用户下单：{}", ordersSubmitDTO);
        OrderSubmitVO orderSubmitVO = ordersService.submitOrder(ordersSubmitDTO);
        return Result.success(orderSubmitVO);
    }

    /**
     * 订单支付
     */
    @PutMapping("/payment")
    @ApiOperation("订单支付")
    public Result<OrderPaymentVO> payment(@RequestBody OrdersPaymentDTO ordersPaymentDTO) {
        log.info("订单支付");
        OrderPaymentVO orderPaymentVO = ordersService.payment(ordersPaymentDTO);
        return Result.success(orderPaymentVO);
    }

    /**
     * 历史订单查询（分页查询）
     */
    @GetMapping("/historyOrders")
    @ApiOperation("历史订单查询")
    public Result<PageResult<HistoryOrdersQueryVO>> historyOrdersQuery(OrdersPageQueryDTO ordersPageQueryDTO) {
        log.info("历史订单分页查询：{}", ordersPageQueryDTO);
        PageResult<HistoryOrdersQueryVO> pageResult = ordersService.historyOrdersQuery(ordersPageQueryDTO);
        return Result.success(pageResult);
    }

    /**
     * 查询订单详情
     */
    @GetMapping("/orderDetail/{id}")
    @ApiOperation("查询订单详情")
    public Result<OrderDetailVO> getOrderDetail(@PathVariable Long id) {
        log.info("查询订单详情：{}", id);
        OrderDetailVO orderDetail = ordersService.getOrderDetail(id);
        return Result.success(orderDetail);
    }

    /**
     * 取消订单
     */
    @PutMapping("/cancel/{id}")
    @ApiOperation("取消订单")
    public Result<String> cancelOrder(@PathVariable Long id) {
        log.info("取消订单：{}", id);
        ordersService.cancelOrder(id);
        return Result.success();
    }

    /**
     * 再来一单
     */
    @PostMapping("/repetition/{id}")
    @ApiOperation("再来一单")
    public Result<String> oneMoreOrder(@PathVariable Long id){
        log.info("再来一单：{}", id);
        ordersService.oneMoreOrder(id);
        return Result.success();
    }
}
