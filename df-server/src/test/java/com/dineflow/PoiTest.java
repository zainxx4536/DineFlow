package com.dineflow;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.io.FileOutputStream;

public class PoiTest {

    /**
     * 基于 POI 向 Excel 文件写入数据
     */
    public static void write() throws Exception {
        // 在内存中创建一个 Excel 文件
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            // 创建 Sheet 页
            XSSFSheet sheet = workbook.createSheet("DineFlow");

            // 创建第 1 行
            XSSFRow titleRow = sheet.createRow(0);
            // 创建单元格
            // 单元格下标从 0 开始
            titleRow.createCell(0).setCellValue("姓名");
            titleRow.createCell(1).setCellValue("城市");
            titleRow.createCell(2).setCellValue("年龄");

            // 创建第 2 行
            XSSFRow row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("张三");
            row1.createCell(1).setCellValue("北京");
            row1.createCell(2).setCellValue(22);

            // 创建第 3 行
            XSSFRow row2 = sheet.createRow(2);
            row2.createCell(0).setCellValue("李四");
            row2.createCell(1).setCellValue("上海");
            row2.createCell(2).setCellValue(25);

            // 将内存中的 Excel 写入磁盘
            try (FileOutputStream out = new FileOutputStream("E:\\iDocuments\\Java_Learn\\DineFlow\\DineFlow\\poiTest.xlsx")) {
                workbook.write(out);
            }
        }

        System.out.println("写入完成");
    }

    /**
     * 基于 POI 读取 Excel 文件
     */
    public static void read() throws Exception {

        try (FileInputStream in = new FileInputStream("E:\\iDocuments\\Java_Learn\\DineFlow\\DineFlow\\poiTest.xlsx");

             XSSFWorkbook workbook = new XSSFWorkbook(in)) {

            // 获取第 1 个 Sheet
            XSSFSheet sheet = workbook.getSheetAt(0);

            // DataFormatter 可以比较方便地读取不同类型的单元格
            DataFormatter formatter = new DataFormatter();

            // 获取最后一行的下标
            int lastRowNum = sheet.getLastRowNum();

            for (int i = 0; i <= lastRowNum; i++) {
                // 获取当前行
                XSSFRow row = sheet.getRow(i);

                if (row == null) {
                    continue;
                }
                // 获取第 1 个单元格
                String name = formatter.formatCellValue(row.getCell(0));
                // 获取第 2 个单元格
                String city = formatter.formatCellValue(row.getCell(1));
                // 获取第 3 个单元格
                String age = formatter.formatCellValue(row.getCell(2));

                System.out.println(name + " " + city + " " + age);
            }
        }
    }

    public static void main(String[] args) throws Exception {

        // 先生成 Excel
        write();

        System.out.println("------读取 Excel------");

        // 再读取刚刚生成的 Excel
        read();
    }

}
