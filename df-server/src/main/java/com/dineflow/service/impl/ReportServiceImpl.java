package com.dineflow.service.impl;

import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.dineflow.entity.User;
import com.dineflow.mapper.OrdersMapper;
import com.dineflow.mapper.UserMapper;
import com.dineflow.service.ReportService;
import com.dineflow.service.WorkspaceService;
import com.dineflow.vo.*;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang.StringUtils;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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

    private final WorkspaceService workspaceService;

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

    /**
     * 导出 Excel 报表
     */
    @Override
    public void exportBusinessData(HttpServletResponse response) {
        // 最近 30 天：今天往前推 30 天 ~ 昨天
        LocalDate begin = LocalDate.now().minusDays(30);
        LocalDate end = LocalDate.now().minusDays(1);

        // 整个统计区间：[begin 00:00:00, end下一天 00:00:00)
        LocalDateTime beginTime = begin.atStartOfDay();
        LocalDateTime endTime = end.plusDays(1).atStartOfDay();

        try (InputStream inputStream =
                     new ClassPathResource("template/运营数据报表模板.xlsx").getInputStream();
             // 将模版 xlsx 读入内存，并在内存中对 xlsx 进行编辑
             XSSFWorkbook workbook = new XSSFWorkbook(inputStream)
        ) {

            XSSFSheet sheet = workbook.getSheetAt(0);

            // 1. 填写统计日期
            // A2:F2 是合并单元格，所以只填写 A2 即可
            sheet.getRow(1)
                    .getCell(0)
                    .setCellValue(begin + "至" + end);

            // 2. 查询并填写概览数据，复用 workspaceService 中的 getBusinessData 方法
            BusinessDataVO businessData = workspaceService.getBusinessData(beginTime, endTime);

            // B4 营业额
            sheet.getRow(3)
                    .getCell(1)
                    .setCellValue(businessData.getTurnover().doubleValue());

            // D4 订单完成率，例如 0.75，模板会显示为 75.00%
            sheet.getRow(3)
                    .getCell(3)
                    .setCellValue(businessData.getOrderCompletionRate().doubleValue());

            // F4 新增用户数
            sheet.getRow(3)
                    .getCell(5)
                    .setCellValue(businessData.getNewUsers());

            // B5 有效订单数
            sheet.getRow(4)
                    .getCell(1)
                    .setCellValue(businessData.getValidOrderCount());

            // D5 平均客单价
            sheet.getRow(4)
                    .getCell(3)
                    .setCellValue(businessData.getUnitPrice().doubleValue());


            // 3. 填写 30 天明细
            // Excel 第 8 行开始，POI 下标从 0 开始，所以对应 row = 7
            for (int i = 0; i < 30; i++) {

                LocalDate date = begin.plusDays(i);

                LocalDateTime dayBegin = date.atStartOfDay();

                LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();

                BusinessDataVO dayData = workspaceService.getBusinessData(dayBegin, dayEnd);

                XSSFRow row = sheet.getRow(7 + i);

                // A列 日期
                row.getCell(0)
                        .setCellValue(date.toString());

                // B列 营业额
                row.getCell(1)
                        .setCellValue(dayData.getTurnover().doubleValue());

                // C列 有效订单
                row.getCell(2)
                        .setCellValue(dayData.getValidOrderCount());

                // D列 订单完成率
                row.getCell(3)
                        .setCellValue(dayData.getOrderCompletionRate().doubleValue());

                // E列 平均客单价
                row.getCell(4)
                        .setCellValue(dayData.getUnitPrice().doubleValue());

                // F列 新增用户
                row.getCell(5)
                        .setCellValue(dayData.getNewUsers());
            }

            // 4. 设置响应头
            // 告诉浏览器接下来返回的不是 JSON，不是 HTML，而是一个 .xlsx Excel 文件
            // application/vnd.openxmlformats-officedocument.spreadsheetml.sheet 字符串
            // 就是 .xlsx 文件对应的 MIME 类型
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

            // HTTP 响应头对中文文件名处理比较敏感，所以先对文件名进行编码处理
            // URLEncoder 遇到空格时会编码成 +，而 HTTP 文件名场景中通常更规范地使用 %20，所以顺手替换一下
            String fileName = URLEncoder
                    .encode("运营数据报表.xlsx", StandardCharsets.UTF_8)
                    .replace("+", "%20");

            // 这个响应头是在告诉浏览器：这个响应不直接展示，而是让用户把它当作文件下载
            response.setHeader("Content-Disposition",
                    "attachment; filename*=UTF-8''" + fileName);

            // 5. 输出 Excel 到浏览器
            ServletOutputStream outputStream = response.getOutputStream();

            workbook.write(outputStream);

            outputStream.flush();

        } catch (IOException e) {
            throw new RuntimeException("运营数据报表导出失败", e);
        }
    }
}
