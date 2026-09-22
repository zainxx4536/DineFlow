package com.dineflow.service.impl;

import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.dineflow.entity.User;
import com.dineflow.mapper.DishMapper;
import com.dineflow.mapper.OrdersMapper;
import com.dineflow.mapper.SetmealMapper;
import com.dineflow.service.WorkspaceService;
import com.dineflow.vo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class WorkspaceServiceImpl implements WorkspaceService {

    private final OrdersMapper ordersMapper;

    private final DishMapper dishMapper;

    private final SetmealMapper setmealMapper;

    /**
     * 统计今日营业数据：
     * 营业额：当日已完成订单的总金额
     * 有效订单：当日已完成订单的数量
     * 订单完成率：有效订单数 / 总订单数
     * 平均客单价：营业额 / 有效订单数
     * 新增用户数：当日新增用户的数量
     */
    public BusinessDataVO getBusinessData(LocalDateTime beginTime, LocalDateTime endTime) {

        // 新增用户数
        long newUsers = Db.lambdaQuery(User.class)
                .ge(User::getCreateTime, beginTime)
                .lt(User::getCreateTime, endTime)
                .count();

        // 订单统计
        BusinessOrderDataVO orderData = ordersMapper.getBusinessOrderData(beginTime, endTime);

        long totalOrderCount = orderData.getTotalOrderCount();

        long validOrderCount = orderData.getValidOrderCount();

        BigDecimal turnover = orderData.getTurnover();

        // 订单完成率
        BigDecimal orderCompletionRate = totalOrderCount == 0 ? BigDecimal.ZERO :
                BigDecimal.valueOf(validOrderCount).divide(BigDecimal.valueOf(totalOrderCount), 4, RoundingMode.HALF_UP);

        // 平均客单价
        BigDecimal unitPrice = validOrderCount == 0 ? BigDecimal.ZERO :
                turnover.divide(BigDecimal.valueOf(validOrderCount), 2, RoundingMode.HALF_UP);

        return BusinessDataVO.builder()
                .turnover(turnover)
                .validOrderCount(validOrderCount)
                .orderCompletionRate(orderCompletionRate)
                .unitPrice(unitPrice)
                .newUsers(newUsers)
                .build();
    }


    /**
     * 查询订单管理数据：
     * 全部订单数
     * 已取消订单数量
     * 已完成订单数量
     * 待派送订单数量
     * 待接单订单数量
     */
    public OrderOverViewVO getOrderOverView() {
        LocalDate today = LocalDate.now();

        LocalDateTime beginTime = today.atStartOfDay();
        LocalDateTime endTime = today.plusDays(1).atStartOfDay();

        return ordersMapper.getOrderOverView(beginTime, endTime);
    }

    /**
     * 查询菜品总览
     */
    public DishOverViewVO getDishOverView() {
        return dishMapper.getDishOverView();
    }

    /**
     * 查询套餐总览
     */
    public SetmealOverViewVO getSetmealOverView() {
        return setmealMapper.getSetmealOverView();
    }
}
