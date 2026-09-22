package com.dineflow.service;

import com.dineflow.vo.OrderReportVO;
import com.dineflow.vo.SalesTop10ReportVO;
import com.dineflow.vo.TurnoverReportVO;
import com.dineflow.vo.UserReportVO;

import javax.servlet.http.HttpServletResponse;
import java.time.LocalDate;

public interface ReportService {
    TurnoverReportVO turnoverStatistics(LocalDate begin, LocalDate end);

    UserReportVO userStatistics(LocalDate begin, LocalDate end);

    OrderReportVO ordersStatistics(LocalDate begin, LocalDate end);

    SalesTop10ReportVO salesTop10Report(LocalDate begin, LocalDate end);

    void exportBusinessData(HttpServletResponse response);
}
