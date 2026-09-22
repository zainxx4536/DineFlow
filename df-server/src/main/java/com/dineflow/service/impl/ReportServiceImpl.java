package com.dineflow.service.impl;

import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.dineflow.entity.User;
import com.dineflow.mapper.OrdersMapper;
import com.dineflow.mapper.UserMapper;
import com.dineflow.service.ReportService;
import com.dineflow.vo.*;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final OrdersMapper ordersMapper;

    private final UserMapper userMapper;

    /**
     * 营业额统计
     */
    @Override
    public TurnoverReportVO turnoverStatistics(LocalDate begin, LocalDate end) {
        // 查询时间范围：[begin 00:00:00, end下一天 00:00:00)
        LocalDateTime beginTime = begin.atStartOfDay();
        LocalDateTime endTime = end.plusDays(1).atStartOfDay();

        // 1. 一次查询得到有营业额的日期
        List<TurnoverDailyVO> turnoverDailyList = ordersMapper.turnoverStatistics(beginTime, endTime);

        // 2. 转成 日期 -> 营业额
        Map<LocalDate, BigDecimal> turnoverMap = turnoverDailyList.stream()
                .collect(Collectors.toMap(TurnoverDailyVO::getDate, TurnoverDailyVO::getTurnover));

        // 3. 生成 begin ~ end 的完整日期列表
        // datesUntil 左闭右开
        List<LocalDate> dateList = begin.datesUntil(end.plusDays(1)).toList();

        // 4. 生成营业额列表
        // 某一天没有已完成订单，则营业额补 0
        List<BigDecimal> turnoverList = dateList.stream()
                .map(date -> turnoverMap.getOrDefault(date, BigDecimal.ZERO))
                .toList();

        // 5. 按接口要求转换成逗号分隔字符串
        String dateListStr = StringUtils.join(dateList, ",");

        String turnoverListStr = StringUtils.join(turnoverList, ",");

        return TurnoverReportVO.builder()
                .dateList(dateListStr)
                .turnoverList(turnoverListStr)
                .build();
    }

    /**
     * 用户统计
     */
    @Override
    public UserReportVO userStatistics(LocalDate begin, LocalDate end) {
        // 查询时间范围：[begin 00:00:00, end下一天 00:00:00)
        LocalDateTime beginTime = begin.atStartOfDay();
        LocalDateTime endTime = end.plusDays(1).atStartOfDay();
        // 生成 begin ~ end 的完整日期列表
        List<LocalDate> dateList = begin.datesUntil(end.plusDays(1)).toList();
        // begin 之前的总用户数
        Long usersBeforeBegin = Db.lambdaQuery(User.class)
                .lt(User::getCreateTime, beginTime)
                .count();

        // 查询 begin ~ end 时间每天的新用户数，并累加在 usersBeforeBegin 得到每天的总用户数
        List<UserDailyVO> dailyNewUserCount = userMapper.newUserStatistics(beginTime, endTime);
        Map<LocalDate, Long> newUserCountMap = dailyNewUserCount.stream()
                .collect(Collectors.toMap(UserDailyVO::getDate, UserDailyVO::getNewUserCount));

        List<Long> newUserList = new ArrayList<>();
        List<Long> totalUserList = new ArrayList<>();
        Long total = usersBeforeBegin;
        for (LocalDate date : dateList) {
            // 今日新增
            Long newUserCount = newUserCountMap.getOrDefault(date, 0L);
            // 今日总数
            total += newUserCount;

            newUserList.add(newUserCount);
            totalUserList.add(total);
        }

        return UserReportVO.builder()
                .dateList(StringUtils.join(dateList, ","))
                .newUserList(StringUtils.join(newUserList, ","))
                .totalUserList(StringUtils.join(totalUserList, ","))
                .build();
    }

    /**
     * 订单统计
     */
    @Override
    public OrderReportVO ordersStatistics(LocalDate begin, LocalDate end) {

        // 查询范围：[begin 00:00:00, end下一天 00:00:00)
        LocalDateTime beginTime = begin.atStartOfDay();
        LocalDateTime endTime = end.plusDays(1).atStartOfDay();

        // 完整日期列表
        List<LocalDate> dateList = begin.datesUntil(end.plusDays(1)).toList();

        // 一次查询每天的总订单数和有效订单数
        List<OrderDailyVO> dailyOrderList = ordersMapper.dailyOrderStatistics(beginTime, endTime);

        Map<LocalDate, OrderDailyVO> dailyOrderMap = dailyOrderList.stream()
                .collect(Collectors.toMap(OrderDailyVO::getDate, item -> item));

        List<Long> orderCountList = new ArrayList<>();
        List<Long> validOrderCountList = new ArrayList<>();

        long totalOrderCount = 0L;
        long validOrderCount = 0L;

        for (LocalDate date : dateList) {

            OrderDailyVO daily = dailyOrderMap.get(date);

            long dailyTotal = daily == null ? 0L : daily.getOrderCount();

            long dailyValid = daily == null ? 0L : daily.getValidOrderCount();

            orderCountList.add(dailyTotal);
            validOrderCountList.add(dailyValid);

            totalOrderCount += dailyTotal;
            validOrderCount += dailyValid;
        }

        // 订单完成率
        BigDecimal orderCompletionRate = totalOrderCount == 0 ? BigDecimal.ZERO :
                BigDecimal.valueOf(validOrderCount).divide(BigDecimal.valueOf(totalOrderCount), 4, RoundingMode.HALF_UP);

        return OrderReportVO.builder()
                .dateList(StringUtils.join(dateList, ","))
                .orderCompletionRate(orderCompletionRate)
                .orderCountList(StringUtils.join(orderCountList, ","))
                .totalOrderCount(totalOrderCount)
                .validOrderCount(validOrderCount)
                .validOrderCountList(StringUtils.join(validOrderCountList, ","))
                .build();
    }

    /**
     * 查询销量排名 TOP10
     */
    @Override
    public SalesTop10ReportVO salesTop10Report(LocalDate begin, LocalDate end) {
        // 查询范围：[begin 00:00:00, end下一天 00:00:00)
        LocalDateTime beginTime = begin.atStartOfDay();
        LocalDateTime endTime = end.plusDays(1).atStartOfDay();

        List<GoodsSalesTop10VO> salesTop10 = ordersMapper.getSalesTop10(beginTime, endTime);

        List<String> nameList = salesTop10.stream().map(GoodsSalesTop10VO::getName).toList();
        List<Integer> numberList = salesTop10.stream().map(GoodsSalesTop10VO::getNumber).toList();

        return SalesTop10ReportVO.builder()
                .nameList(StringUtils.join(nameList, ","))
                .numberList(StringUtils.join(numberList, ","))
                .build();
    }
}
