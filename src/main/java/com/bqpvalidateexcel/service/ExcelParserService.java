package com.bqpvalidateexcel.service;

import com.bqpvalidateexcel.model.AppendixType;
import com.bqpvalidateexcel.model.ParsedRecord;
import com.bqpvalidateexcel.util.DateParserUtil;
import com.bqpvalidateexcel.util.NumberParserUtil;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class ExcelParserService implements RecordParserService {

    private final DataFormatter dataFormatter = new DataFormatter();

    @Override
    public boolean supports(String fileName) {
        if (fileName == null) return false;
        String lower = fileName.toLowerCase();
        return lower.endsWith(".xlsx") || lower.endsWith(".xls");
    }

    @Override
    public ExtractedData parse(File file, AppendixType forcedType) throws Exception {
        try (InputStream is = new FileInputStream(file)) {
            return parse(is, file.getName(), forcedType);
        }
    }

    @Override
    public ExtractedData parse(InputStream inputStream, String fileName, AppendixType forcedType) throws Exception {
        ExtractedData result = new ExtractedData();
        result.inputFormat = "EXCEL";

        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            if (workbook.getNumberOfSheets() == 0) {
                result.appendixType = (forcedType != null) ? forcedType : AppendixType.PHU_LUC_I1;
                return result;
            }

            Sheet sheet = workbook.getSheetAt(0);
            result.appendixType = (forcedType != null) ? forcedType : detectAppendixType(sheet, fileName);

            boolean headerCaptured = false;
            int rowIndexCounter = 0;

            for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                List<String> cellTexts = new ArrayList<>();
                int maxCol = Math.max(row.getLastCellNum(), 25);
                boolean hasContent = false;

                for (int c = 0; c < maxCol; c++) {
                    Cell cell = row.getCell(c);
                    String val = getCellStringValue(cell);
                    cellTexts.add(val);
                    if (!val.isEmpty()) {
                        hasContent = true;
                    }
                }

                if (!hasContent) continue;

                // Cắt đuôi các ô rỗng thừa bên phải
                while (cellTexts.size() > 10 && cellTexts.get(cellTexts.size() - 1).isEmpty()) {
                    cellTexts.remove(cellTexts.size() - 1);
                }

                result.allRawRows.add(cellTexts);

                // Nhận dạng hàng tiêu đề bảng
                String c0 = cellTexts.size() > 0 ? cellTexts.get(0).toLowerCase() : "";
                String c1 = cellTexts.size() > 1 ? cellTexts.get(1).toLowerCase() : "";

                if (c0.contains("số tt") || c0.contains("stt") || c1.contains("họ và tên") || c0.contains("họ và tên")) {
                    if (!headerCaptured) {
                        result.headerRows.add(cellTexts);
                    }
                    continue;
                }
                if (c0.contains("trợ cấp") || c1.contains("nghỉ trong") || c1.contains("tuổi đời")
                        || (cellTexts.size() > 2 && cellTexts.get(1).equals("1") && cellTexts.get(2).equals("2"))) {
                    if (!headerCaptured) {
                        result.headerRows.add(cellTexts);
                    }
                    continue;
                }

                headerCaptured = true;

                // Lọc bỏ dòng tổng cộng hoặc tiêu đề đơn vị
                if (isSummaryOrSectionRow(cellTexts)) {
                    continue;
                }

                ParsedRecord parsed = parseDataRow(cellTexts, result.appendixType, rowIndexCounter++);
                if (parsed != null && parsed.getFullName() != null && !parsed.getFullName().isEmpty()) {
                    result.records.add(parsed);
                }
            }
        }

        return result;
    }

    private AppendixType detectAppendixType(Sheet sheet, String fileName) {
        String name = (fileName != null ? fileName : "") + " " + sheet.getSheetName();
        String lower = name.toLowerCase();

        if (lower.contains("phu_luc_i.3") || lower.contains("phụ lục i.3") || lower.contains("phuluc i3") || lower.contains("i.3") || lower.contains("177")) {
            return AppendixType.PHU_LUC_I3;
        }
        if (lower.contains("phu_luc_i.2") || lower.contains("phụ lục i.2") || lower.contains("phuluc i2") || lower.contains("i.2") || lower.contains("thôi việc") || lower.contains("phục viên")) {
            return AppendixType.PHU_LUC_I2;
        }
        if (lower.contains("phu_luc_i.1") || lower.contains("phụ lục i.1") || lower.contains("phuluc i1") || lower.contains("i.1") || lower.contains("178")) {
            return AppendixType.PHU_LUC_I1;
        }

        // Kiểm tra nội dung text trong các dòng đầu của sheet
        for (int r = 0; r < Math.min(10, sheet.getLastRowNum() + 1); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            for (Cell cell : row) {
                String val = getCellStringValue(cell).toLowerCase();
                if (val.contains("nghị định số 177") || val.contains("phụ lục i.3")) {
                    return AppendixType.PHU_LUC_I3;
                }
                if (val.contains("thôi việc") || val.contains("phục viên") || val.contains("phụ lục i.2")) {
                    return AppendixType.PHU_LUC_I2;
                }
                if (val.contains("nghỉ hưu trước tuổi") || val.contains("phụ lục i.1")) {
                    return AppendixType.PHU_LUC_I1;
                }
            }
        }

        return AppendixType.PHU_LUC_I1;
    }

    private boolean isSummaryOrSectionRow(List<String> cells) {
        if (cells.isEmpty()) return true;
        String c0 = cells.get(0).trim();
        String c1 = cells.size() > 1 ? cells.get(1).trim() : "";

        if (c1.equalsIgnoreCase("tổng cộng") || c1.equalsIgnoreCase("cộng") || c0.equalsIgnoreCase("tổng cộng") || c0.equalsIgnoreCase("cộng")) {
            return true;
        }
        if (c0.matches("^[IVXLCDM]+$") || c1.startsWith("Cục") || c1.startsWith("Viện") || c1.startsWith("Văn phòng") || c1.startsWith("Bộ") || c1.startsWith("Ban")) {
            return true;
        }
        if (c0.equals("*") || c1.equalsIgnoreCase("sĩ quan") || c1.equalsIgnoreCase("quân nhân chuyên nghiệp") || c1.contains("nghiệp")) {
            return true;
        }
        if (c1.startsWith("Năm 202") || c0.startsWith("Năm 202")) {
            return true;
        }

        // Nếu cột STT không phải là số nguyên và cột Họ tên không có ký tự chữ cái
        if (!c0.matches("^\\d+$") && (c1.isEmpty() || !c1.matches(".*[a-zA-ZÀ-ỹ].*"))) {
            return true;
        }

        return false;
    }

    private ParsedRecord parseDataRow(List<String> cells, AppendixType type, int rowIndex) {
        if (cells.size() < 10) return null;

        String stt = cells.get(0).trim();
        String fullName = cells.get(1).trim();
        String birthStr = (cells.size() > 2) ? cells.get(2).trim() : "";
        String rank = (cells.size() > 3) ? cells.get(3).trim() : "";
        String position = (cells.size() > 4) ? cells.get(4).trim() : "";
        String enlistStr = (cells.size() > 5) ? cells.get(5).trim() : "";
        String eval = (cells.size() > 6) ? cells.get(6).trim() : "";
        String mergerStr = (cells.size() > 7) ? cells.get(7).trim() : "";
        String retireStr = (cells.size() > 8) ? cells.get(8).trim() : "";

        YearMonth parsedRetire = DateParserUtil.parseYearMonth(retireStr);
        YearMonth parsedMerger = DateParserUtil.parseYearMonth(mergerStr);
        if (parsedRetire == null && parsedMerger != null) {
            parsedRetire = YearMonth.of(2025, 6);
        }

        ParsedRecord.ParsedRecordBuilder builder = ParsedRecord.builder()
                .rowIndex(rowIndex)
                .stt(stt)
                .fullName(fullName)
                .birthDateStr(birthStr)
                .birthDate(DateParserUtil.parseYearMonth(birthStr))
                .rank(rank)
                .position(position)
                .enlistDateStr(enlistStr)
                .enlistDate(DateParserUtil.parseYearMonth(enlistStr))
                .evaluation(eval)
                .mergerDateStr(mergerStr)
                .mergerDate(parsedMerger)
                .retirementDateStr(retireStr)
                .retirementDate(parsedRetire)
                .rawRowCells(new ArrayList<>(cells));

        if (type == AppendixType.PHU_LUC_I1) {
            parsePhuLucI1Amounts(cells, builder);
        } else if (type == AppendixType.PHU_LUC_I2) {
            parsePhuLucI2Amounts(cells, builder);
        } else if (type == AppendixType.PHU_LUC_I3) {
            parsePhuLucI3Amounts(cells, builder);
        }

        return builder.build();
    }

    private void parsePhuLucI1Amounts(List<String> cells, ParsedRecord.ParsedRecordBuilder builder) {
        BigDecimal salary = (cells.size() > 9) ? NumberParserUtil.parseMoney(cells.get(9)) : BigDecimal.ZERO;
        BigDecimal monthsEarly = getCellDecimal(cells, 10);
        BigDecimal yearsEarly = getCellDecimal(cells, 11);
        BigDecimal yearsBhxh = getCellDecimal(cells, 12);

        BigDecimal c1 = getFirstNonZero(cells, 13, 14, 15, 16);
        BigDecimal c2 = getFirstNonZero(cells, 17, 20);
        BigDecimal c3Base = getFirstNonZero(cells, 18, 21);
        BigDecimal c3Extra = getFirstNonZero(cells, 19, 22);
        BigDecimal total = (cells.size() > 23) ? NumberParserUtil.parseMoney(cells.get(23)) : BigDecimal.ZERO;

        builder.salary(salary)
                .monthsEarly(monthsEarly)
                .yearsEarly(yearsEarly)
                .yearsBhxh(yearsBhxh)
                .declaredC1(c1)
                .declaredC2(c2)
                .declaredC3Base(c3Base)
                .declaredC3Extra(c3Extra)
                .declaredJobCreation(BigDecimal.ZERO)
                .declaredTotal(total);
    }

    private void parsePhuLucI2Amounts(List<String> cells, ParsedRecord.ParsedRecordBuilder builder) {
        BigDecimal col9 = (cells.size() > 9) ? NumberParserUtil.parseMoney(cells.get(9)) : BigDecimal.ZERO;
        BigDecimal col10 = getCellDecimal(cells, 10);
        BigDecimal col11 = getCellDecimal(cells, 11);

        BigDecimal c12 = (cells.size() > 12) ? NumberParserUtil.parseMoney(cells.get(12)) : BigDecimal.ZERO;
        BigDecimal c13 = (cells.size() > 13) ? NumberParserUtil.parseMoney(cells.get(13)) : BigDecimal.ZERO;
        BigDecimal c14 = (cells.size() > 14) ? NumberParserUtil.parseMoney(cells.get(14)) : BigDecimal.ZERO;
        BigDecimal c15 = (cells.size() > 15) ? NumberParserUtil.parseMoney(cells.get(15)) : BigDecimal.ZERO;
        BigDecimal c16 = (cells.size() > 16) ? NumberParserUtil.parseMoney(cells.get(16)) : BigDecimal.ZERO;
        BigDecimal c17 = (cells.size() > 17) ? NumberParserUtil.parseMoney(cells.get(17)) : BigDecimal.ZERO;
        BigDecimal total = (cells.size() > 18) ? NumberParserUtil.parseMoney(cells.get(18)) : BigDecimal.ZERO;

        BigDecimal cMonth = (c12.compareTo(BigDecimal.ZERO) > 0) ? c12 : c15;
        BigDecimal cYear = (c13.compareTo(BigDecimal.ZERO) > 0) ? c13 : c16;
        BigDecimal cJob = (c14.compareTo(BigDecimal.ZERO) > 0) ? c14 : c17;

        BigDecimal salary = col9;
        if (salary.compareTo(BigDecimal.valueOf(1000)) < 0 && cJob.compareTo(BigDecimal.ZERO) > 0) {
            salary = cJob.divide(BigDecimal.valueOf(3), 0, java.math.RoundingMode.HALF_UP);
        }

        builder.salary(salary)
                .monthsEarly(col10)
                .yearsEarly(col11)
                .yearsBhxh(col11)
                .declaredC1(cMonth)
                .declaredC2(cYear)
                .declaredJobCreation(cJob)
                .declaredTotal(total);
    }

    private void parsePhuLucI3Amounts(List<String> cells, ParsedRecord.ParsedRecordBuilder builder) {
        BigDecimal salary = (cells.size() > 9) ? NumberParserUtil.parseMoney(cells.get(9)) : BigDecimal.ZERO;
        BigDecimal monthsEarly = getCellDecimal(cells, 10);
        BigDecimal yearsEarly = getCellDecimal(cells, 11);

        BigDecimal c12 = (cells.size() > 12) ? NumberParserUtil.parseMoney(cells.get(12)) : BigDecimal.ZERO;
        BigDecimal c13 = (cells.size() > 13) ? NumberParserUtil.parseMoney(cells.get(13)) : BigDecimal.ZERO;
        BigDecimal c14 = (cells.size() > 14) ? NumberParserUtil.parseMoney(cells.get(14)) : BigDecimal.ZERO;
        BigDecimal total = (cells.size() > 15) ? NumberParserUtil.parseMoney(cells.get(15)) : BigDecimal.ZERO;

        builder.salary(salary)
                .monthsEarly(monthsEarly)
                .yearsEarly(yearsEarly)
                .declaredC1(c12)
                .declaredC3Base(c13)
                .declaredC3Extra(c14)
                .declaredJobCreation(BigDecimal.ZERO)
                .declaredTotal(total);
    }

    private BigDecimal getCellDecimal(List<String> cells, int index) {
        if (index < cells.size()) {
            return NumberParserUtil.parseDecimal(cells.get(index));
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal getFirstNonZero(List<String> cells, int... indices) {
        for (int idx : indices) {
            if (idx < cells.size()) {
                BigDecimal val = NumberParserUtil.parseMoney(cells.get(idx));
                if (val.compareTo(BigDecimal.ZERO) > 0) {
                    return val;
                }
            }
        }
        return BigDecimal.ZERO;
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) return "";
        try {
            switch (cell.getCellType()) {
                case STRING:
                    return cell.getStringCellValue().trim();
                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(cell)) {
                        Date d = cell.getDateCellValue();
                        if (d != null) {
                            YearMonth ym = YearMonth.from(d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
                            return String.format("%02d/%d", ym.getMonthValue(), ym.getYear());
                        }
                    }
                    double num = cell.getNumericCellValue();
                    if (num == (long) num) {
                        return String.valueOf((long) num);
                    }
                    return String.valueOf(num);
                case BOOLEAN:
                    return String.valueOf(cell.getBooleanCellValue());
                case FORMULA:
                    try {
                        return dataFormatter.formatCellValue(cell).trim();
                    } catch (Exception e) {
                        return "";
                    }
                case BLANK:
                default:
                    return "";
            }
        } catch (Exception e) {
            return "";
        }
    }
}
