package com.dineflow.controller.admin;

import com.dineflow.result.Result;
import com.dineflow.service.ReportService;
import com.dineflow.vo.OrderReportVO;
import com.dineflow.vo.SalesTop10ReportVO;
import com.dineflow.vo.TurnoverReportVO;
import com.dineflow.vo.UserReportVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/admin/report")
@Slf4j
@RequiredArgsConstructor
@Api(tags = "数据统计相关接口")
public class ReportController {

    private final ReportService reportService;

    /**
     * 每日营业额统计
     */
    @GetMapping("/turnoverStatistics")
    @ApiOperation("营业额统计")
    public Result<TurnoverReportVO> turnoverStatistics(
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate begin,
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end
    ) {

        log.info("营业额统计：{}, {}", begin, end);
        TurnoverReportVO turnoverReportVO = reportService.turnoverStatistics(begin, end);
        return Result.success(turnoverReportVO);
    }

    /**
     * 用户统计
     */
    @GetMapping("/userStatistics")
    @ApiOperation("用户统计")
    public Result<UserReportVO> userStatistics(
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate begin,
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end
    ) {

        log.info("用户统计：{}, {}", begin, end);
        UserReportVO userStatistics = reportService.userStatistics(begin, end);
        return Result.success(userStatistics);
    }

    /**
     * 订单统计
     */
    @GetMapping("/ordersStatistics")
    @ApiOperation("订单统计")
    public Result<OrderReportVO> ordersStatistics(
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate begin,
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end
    ) {

        log.info("订单统计");
        OrderReportVO ordersStatistics = reportService.ordersStatistics(begin, end);
        return Result.success(ordersStatistics);
    }

    /**
     * 查询销量排名 TOP10
     */
    @GetMapping("/top10")
    @ApiOperation("查询销量排名 TOP10")
    public Result<SalesTop10ReportVO> salesTop10Report(
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate begin,
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end
    ) {

        log.info("查询销量排名 TOP10");
        SalesTop10ReportVO salesTop10 = reportService.salesTop10Report(begin, end);
        return Result.success(salesTop10);
    }
}
