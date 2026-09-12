package com.bqpvalidateexcel.service;

import com.bqpvalidateexcel.model.*;
import com.bqpvalidateexcel.util.NumberParserUtil;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@Service
public class ExcelExportService {

    private static final String TEMPLATE_I1 = "excel_templates/PhuLucI1.xlsx";
    private static final String TEMPLATE_I2 = "excel_templates/PhuLucI2.xlsx";
    private static final String TEMPLATE_I3 = "excel_templates/PhuLucI3.xlsx";
    private static final String TEMPLATE_II = "excel_templates/PhuLucII.xlsx";

    /**
     * Nạp template và đổ dữ liệu cho từng Phụ lục cụ thể (Phụ lục I.1, I.2 hoặc I.3)
     * CHỈ BÔI ĐỎ RIÊNG Ô BỊ TÍNH SAI
     */
    public byte[] injectAppendixData(AppendixAuditResult result) throws Exception {
        String templatePath = switch (result.getAppendixType()) {
            case PHU_LUC_I1 -> TEMPLATE_I1;
            case PHU_LUC_I2 -> TEMPLATE_I2;
            case PHU_LUC_I3 -> TEMPLATE_I3;
        };

        try (InputStream is = new ClassPathResource(templatePath).getInputStream();
             XSSFWorkbook workbook = new XSSFWorkbook(is);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            XSSFSheet sheet = workbook.getSheetAt(0);

            // Tạo các styles chuyên dụng
            XSSFCellStyle normalStyle = createNormalStyle(workbook);
            XSSFCellStyle centerStyle = createCenterStyle(workbook);
            XSSFCellStyle wrapStyle = createWrapTextStyle(workbook, false);
            XSSFCellStyle moneyStyle = createMoneyStyle(workbook, false);
            XSSFCellStyle decimalStyle = createDecimalStyle(workbook, false);

            XSSFCellStyle errorStyle = createErrorStyle(workbook);
            XSSFCellStyle errorCenterStyle = createErrorCenterStyle(workbook);
            XSSFCellStyle errorWrapStyle = createWrapTextStyle(workbook, true);
            XSSFCellStyle errorMoneyStyle = createMoneyStyle(workbook, true);
            XSSFCellStyle errorDecimalStyle = createDecimalStyle(workbook, true);

            // Bắt đầu ghi từ dòng thứ 10 (0-indexed: 10)
            int startRow = 10;
            int rIdx = startRow;

            for (AuditRecord rec : result.getRecords()) {
                Row row = sheet.createRow(rIdx++);
                ParsedRecord p = rec.getParsed();
                CalculatedRecord c = rec.getCalculated();
                AuditDifference d = rec.getDifference();
                Set<Integer> wrongCols = d.getWrongColumnIndices();
                List<String> rawCells = p.getRawRowCells();

                if (result.getAppendixType() == AppendixType.PHU_LUC_I1) {
                    injectPhuLucI1Row(row, p, c, d, wrongCols, rawCells, normalStyle, centerStyle, wrapStyle, moneyStyle, decimalStyle, errorStyle, errorCenterStyle, errorWrapStyle, errorMoneyStyle, errorDecimalStyle);
                } else if (result.getAppendixType() == AppendixType.PHU_LUC_I2) {
                    injectPhuLucI2Row(row, p, c, d, wrongCols, rawCells, normalStyle, centerStyle, wrapStyle, moneyStyle, decimalStyle, errorStyle, errorCenterStyle, errorWrapStyle, errorMoneyStyle, errorDecimalStyle);
                } else if (result.getAppendixType() == AppendixType.PHU_LUC_I3) {
                    injectPhuLucI3Row(row, p, c, d, wrongCols, rawCells, normalStyle, centerStyle, wrapStyle, moneyStyle, decimalStyle, errorStyle, errorCenterStyle, errorWrapStyle, errorMoneyStyle, errorDecimalStyle);
                }
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }

    private void injectPhuLucI1Row(Row row, ParsedRecord p, CalculatedRecord c, AuditDifference d, Set<Integer> wrongCols, List<String> rawCells,
                                  XSSFCellStyle normal, XSSFCellStyle center, XSSFCellStyle wrap, XSSFCellStyle money, XSSFCellStyle decimal,
                                  XSSFCellStyle errNormal, XSSFCellStyle errCenter, XSSFCellStyle errWrap, XSSFCellStyle errMoney, XSSFCellStyle errDecimal) {
        // Cột 0..8: Personal info
        setCellValue(row, 0, p.getStt(), wrongCols.contains(0) ? errCenter : center);
        setCellValue(row, 1, p.getFullName(), wrongCols.contains(1) ? errWrap : wrap);
        setCellValue(row, 2, p.getBirthDateStr(), wrongCols.contains(2) ? errCenter : center);
        setCellValue(row, 3, p.getRank(), wrongCols.contains(3) ? errCenter : center);
        setCellValue(row, 4, p.getPosition(), wrongCols.contains(4) ? errNormal : normal);
        setCellValue(row, 5, p.getEnlistDateStr(), wrongCols.contains(5) ? errCenter : center);
        setCellValue(row, 6, p.getEvaluation(), wrongCols.contains(6) ? errCenter : center);
        setCellValue(row, 7, p.getMergerDateStr(), wrongCols.contains(7) ? errCenter : center);
        setCellValue(row, 8, p.getRetirementDateStr(), wrongCols.contains(8) ? errCenter : center);

        // Cột 9: Lương
        setCellMoney(row, 9, p.getSalary(), wrongCols.contains(9) ? errMoney : money);
        // Cột 10: Số tháng nghỉ sớm
        setCellDecimal(row, 10, p.getMonthsEarly(), wrongCols.contains(10) ? errDecimal : decimal);
        // Cột 11: Số năm nghỉ sớm
        setCellDecimal(row, 11, p.getYearsEarly(), wrongCols.contains(11) ? errDecimal : decimal);
        // Cột 12: Số năm BHXH
        setCellDecimal(row, 12, p.getYearsBhxh(), wrongCols.contains(12) ? errDecimal : decimal);

        // Cột 13..22: Chế độ thành phần
        for (int col = 13; col <= 22; col++) {
            BigDecimal val = (rawCells != null && col < rawCells.size()) ? NumberParserUtil.parseMoney(rawCells.get(col)) : BigDecimal.ZERO;
            setCellMoney(row, col, val, wrongCols.contains(col) ? errMoney : money);
        }

        // Cột 23: Tổng tiền
        setCellMoney(row, 23, p.getDeclaredTotal(), wrongCols.contains(23) ? errMoney : money);

        // Cột 24: Ghi chú rà soát
        Cell noteCell = row.createCell(24);
        noteCell.setCellValue(d.getReviewNote());
        noteCell.setCellStyle(d.isHasError() ? errWrap : wrap);
    }

    private void injectPhuLucI2Row(Row row, ParsedRecord p, CalculatedRecord c, AuditDifference d, Set<Integer> wrongCols, List<String> rawCells,
                                  XSSFCellStyle normal, XSSFCellStyle center, XSSFCellStyle wrap, XSSFCellStyle money, XSSFCellStyle decimal,
                                  XSSFCellStyle errNormal, XSSFCellStyle errCenter, XSSFCellStyle errWrap, XSSFCellStyle errMoney, XSSFCellStyle errDecimal) {
        setCellValue(row, 0, p.getStt(), wrongCols.contains(0) ? errCenter : center);
        setCellValue(row, 1, p.getFullName(), wrongCols.contains(1) ? errWrap : wrap);
        setCellValue(row, 2, p.getBirthDateStr(), wrongCols.contains(2) ? errCenter : center);
        setCellValue(row, 3, p.getRank(), wrongCols.contains(3) ? errCenter : center);
        setCellValue(row, 4, p.getPosition(), wrongCols.contains(4) ? errNormal : normal);
        setCellValue(row, 5, p.getEnlistDateStr(), wrongCols.contains(5) ? errCenter : center);
        setCellValue(row, 6, p.getEvaluation(), wrongCols.contains(6) ? errCenter : center);
        setCellValue(row, 7, p.getMergerDateStr(), wrongCols.contains(7) ? errCenter : center);
        setCellValue(row, 8, p.getRetirementDateStr(), wrongCols.contains(8) ? errCenter : center);

        setCellMoney(row, 9, p.getSalary(), wrongCols.contains(9) ? errMoney : money);
        setCellDecimal(row, 10, p.getMonthsEarly(), wrongCols.contains(10) ? errDecimal : decimal);
        setCellDecimal(row, 11, p.getYearsEarly(), wrongCols.contains(11) ? errDecimal : decimal);

        for (int col = 12; col <= 17; col++) {
            BigDecimal val = (rawCells != null && col < rawCells.size()) ? NumberParserUtil.parseMoney(rawCells.get(col)) : BigDecimal.ZERO;
            setCellMoney(row, col, val, wrongCols.contains(col) ? errMoney : money);
        }

        setCellMoney(row, 18, p.getDeclaredTotal(), wrongCols.contains(18) ? errMoney : money);

        Cell noteCell = row.createCell(19);
        noteCell.setCellValue(d.getReviewNote());
        noteCell.setCellStyle(d.isHasError() ? errWrap : wrap);
    }

    private void injectPhuLucI3Row(Row row, ParsedRecord p, CalculatedRecord c, AuditDifference d, Set<Integer> wrongCols, List<String> rawCells,
                                  XSSFCellStyle normal, XSSFCellStyle center, XSSFCellStyle wrap, XSSFCellStyle money, XSSFCellStyle decimal,
                                  XSSFCellStyle errNormal, XSSFCellStyle errCenter, XSSFCellStyle errWrap, XSSFCellStyle errMoney, XSSFCellStyle errDecimal) {
        setCellValue(row, 0, p.getStt(), wrongCols.contains(0) ? errCenter : center);
        setCellValue(row, 1, p.getFullName(), wrongCols.contains(1) ? errWrap : wrap);
        setCellValue(row, 2, p.getBirthDateStr(), wrongCols.contains(2) ? errCenter : center);
        setCellValue(row, 3, p.getRank(), wrongCols.contains(3) ? errCenter : center);
        setCellValue(row, 4, p.getPosition(), wrongCols.contains(4) ? errNormal : normal);
        setCellValue(row, 5, p.getEnlistDateStr(), wrongCols.contains(5) ? errCenter : center);
        setCellValue(row, 6, p.getEvaluation(), wrongCols.contains(6) ? errCenter : center);
        setCellValue(row, 7, p.getMergerDateStr(), wrongCols.contains(7) ? errCenter : center);
        setCellValue(row, 8, p.getRetirementDateStr(), wrongCols.contains(8) ? errCenter : center);

        setCellMoney(row, 9, p.getSalary(), wrongCols.contains(9) ? errMoney : money);
        setCellDecimal(row, 10, p.getMonthsEarly(), wrongCols.contains(10) ? errDecimal : decimal);
        setCellDecimal(row, 11, p.getYearsEarly(), wrongCols.contains(11) ? errDecimal : decimal);

        for (int col = 12; col <= 14; col++) {
            BigDecimal val = (rawCells != null && col < rawCells.size()) ? NumberParserUtil.parseMoney(rawCells.get(col)) : BigDecimal.ZERO;
            setCellMoney(row, col, val, wrongCols.contains(col) ? errMoney : money);
        }

        setCellMoney(row, 15, p.getDeclaredTotal(), wrongCols.contains(15) ? errMoney : money);

        Cell noteCell = row.createCell(16);
        noteCell.setCellValue(d.getReviewNote());
        noteCell.setCellStyle(d.isHasError() ? errWrap : wrap);
    }

    /**
     * Nạp template PhuLucII.xlsx và đổ toàn bộ các hồ sơ có ít nhất 1 ô sai
     */
    public byte[] injectPhuLucIIData(MultiAuditReport report) throws Exception {
        try (InputStream is = new ClassPathResource(TEMPLATE_II).getInputStream();
             XSSFWorkbook workbook = new XSSFWorkbook(is);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            XSSFSheet sheet = workbook.getSheetAt(0);

            XSSFCellStyle normalStyle = createNormalStyle(workbook);
            XSSFCellStyle wrapStyle = createWrapTextStyle(workbook, false);
            XSSFCellStyle centerStyle = createCenterStyle(workbook);
            XSSFCellStyle moneyDiffStyle = createMoneyStyle(workbook, true);

            int startRow = 10;
            int rIdx = startRow;
            int sttCounter = 1;

            List<AuditRecord> errorRecords = report.getAllErrorRecords();
            for (AuditRecord rec : errorRecords) {
                Row row = sheet.createRow(rIdx++);
                ParsedRecord p = rec.getParsed();
                AuditDifference d = rec.getDifference();
                AppendixType type = rec.getAppendixType();

                // Cột 0..6: Thông tin nhân thân
                setCellValue(row, 0, String.valueOf(sttCounter++), centerStyle);
                setCellValue(row, 1, p.getFullName(), wrapStyle);
                setCellValue(row, 2, p.getBirthDateStr(), centerStyle);
                setCellValue(row, 3, p.getRank(), centerStyle);
                setCellValue(row, 4, p.getPosition(), normalStyle);
                setCellValue(row, 5, p.getEnlistDateStr(), centerStyle);
                setCellValue(row, 6, p.getMergerDateStr(), centerStyle);

                // Cột 7..9: Trường hợp nghỉ
                // Cột 7: NĐ 177 (nếu từ I.3)
                setCellValue(row, 7, type == AppendixType.PHU_LUC_I3 ? "X" : "", centerStyle);
                // Cột 8: NĐ 178 (nếu từ I.1)
                setCellValue(row, 8, type == AppendixType.PHU_LUC_I1 ? "X" : "", centerStyle);
                // Cột 9: Nghỉ thôi việc (nếu từ I.2)
                setCellValue(row, 9, type == AppendixType.PHU_LUC_I2 ? "X" : "", centerStyle);

                // Cột 10..15: 6 nguyên nhân sai
                setCellValue(row, 10, d.isErrCriteria() ? "X" : "", centerStyle);
                setCellValue(row, 11, d.isErrEarlyTime() ? "X" : "", centerStyle);
                setCellValue(row, 12, d.isErrBhxhSeniority() ? "X" : "", centerStyle);
                setCellValue(row, 13, d.isErrSalary() ? "X" : "", centerStyle);
                setCellValue(row, 14, d.isErrRegimeFormula() ? "X" : "", centerStyle);
                setCellValue(row, 15, d.isErrTotalAmount() ? "X" : "", centerStyle);

                // Cột 16: Số tiền chênh lệch
                BigDecimal diffVal = (d.getDiffAmount() != null) ? d.getDiffAmount() : BigDecimal.ZERO;
                setCellMoney(row, 16, diffVal, moneyDiffStyle);

                // Cột 17: Diễn giải chi tiết
                setCellValue(row, 17, d.getDetailedExplanation(), wrapStyle);
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }

    private void setCellValue(Row row, int col, String val, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(val != null ? val : "");
        cell.setCellStyle(style);
    }

    private void setCellMoney(Row row, int col, BigDecimal val, CellStyle style) {
        Cell cell = row.createCell(col);
        if (val != null && val.compareTo(BigDecimal.ZERO) != 0) {
            cell.setCellValue(val.doubleValue());
        } else {
            cell.setCellValue("");
        }
        cell.setCellStyle(style);
    }

    private void setCellDecimal(Row row, int col, BigDecimal val, CellStyle style) {
        Cell cell = row.createCell(col);
        if (val != null && val.compareTo(BigDecimal.ZERO) != 0) {
            cell.setCellValue(val.doubleValue());
        } else {
            cell.setCellValue("");
        }
        cell.setCellStyle(style);
    }

    private XSSFCellStyle createNormalStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setFontName("Times New Roman");
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorders(style);
        return style;
    }

    private XSSFCellStyle createCenterStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = createNormalStyle(wb);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private XSSFCellStyle createWrapTextStyle(XSSFWorkbook wb, boolean isError) {
        XSSFCellStyle style = isError ? createErrorStyle(wb) : createNormalStyle(wb);
        style.setWrapText(true);
        return style;
    }

    private XSSFCellStyle createErrorStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setFontName("Times New Roman");
        font.setBold(true);
        font.setColor(IndexedColors.DARK_RED.getIndex());
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.ROSE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorders(style);
        return style;
    }

    private XSSFCellStyle createErrorCenterStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = createErrorStyle(wb);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private XSSFCellStyle createMoneyStyle(XSSFWorkbook wb, boolean isError) {
        XSSFCellStyle style = isError ? createErrorStyle(wb) : createNormalStyle(wb);
        style.setAlignment(HorizontalAlignment.RIGHT);
        DataFormat format = wb.createDataFormat();
        style.setDataFormat(format.getFormat("#,##0"));
        return style;
    }

    private XSSFCellStyle createDecimalStyle(XSSFWorkbook wb, boolean isError) {
        XSSFCellStyle style = isError ? createErrorStyle(wb) : createNormalStyle(wb);
        style.setAlignment(HorizontalAlignment.CENTER);
        DataFormat format = wb.createDataFormat();
        style.setDataFormat(format.getFormat("#,##0.##"));
        return style;
    }

    private void setBorders(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }
}
