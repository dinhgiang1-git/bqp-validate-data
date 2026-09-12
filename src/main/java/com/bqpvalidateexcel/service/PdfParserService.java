package com.bqpvalidateexcel.service;

import com.bqpvalidateexcel.model.AppendixType;
import com.bqpvalidateexcel.model.ParsedRecord;
import com.bqpvalidateexcel.util.DateParserUtil;
import com.bqpvalidateexcel.util.NumberParserUtil;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import technology.tabula.*;
import technology.tabula.extractors.SpreadsheetExtractionAlgorithm;

import java.io.File;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@Service
public class PdfParserService implements RecordParserService {

    @Override
    public boolean supports(String fileName) {
        if (fileName == null) return false;
        return fileName.toLowerCase().endsWith(".pdf");
    }

    /**
     * Tự động nhận diện loại Phụ lục từ nội dung PDF
     */
    public AppendixType detectAppendixType(File file) {
        try (PDDocument doc = PDDocument.load(file)) {
            return detectAppendixType(doc);
        } catch (Exception ignored) {
            return AppendixType.PHU_LUC_I1;
        }
    }

    public AppendixType detectAppendixType(PDDocument doc) {
        try {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setEndPage(Math.min(2, doc.getNumberOfPages()));
            String text = stripper.getText(doc).toLowerCase();

            if (text.contains("phụ lục i.3") || text.contains("phu luc i.3") || text.contains("nghị định số 177") || text.contains("177/2024")) {
                return AppendixType.PHU_LUC_I3;
            }
            if (text.contains("phụ lục i.2") || text.contains("phu luc i.2") || text.contains("thôi việc") || text.contains("phục viên")) {
                return AppendixType.PHU_LUC_I2;
            }
            if (text.contains("phụ lục i.1") || text.contains("phu luc i.1") || text.contains("nghỉ hưu trước tuổi") || text.contains("178/2024")) {
                return AppendixType.PHU_LUC_I1;
            }
        } catch (Exception ignored) {
        }
        return AppendixType.PHU_LUC_I1;
    }

    @Override
    public ExtractedData parse(File file, AppendixType forcedType) throws Exception {
        return parsePdf(file, forcedType);
    }

    @Override
    public ExtractedData parse(InputStream inputStream, String fileName, AppendixType forcedType) throws Exception {
        try (PDDocument document = PDDocument.load(inputStream)) {
            return parseDocument(document, forcedType);
        }
    }

    /**
     * Bóc tách bảng dữ liệu từ PDF sử dụng Tabula Spreadsheet Extraction Algorithm
     */
    public ExtractedData parsePdf(File file, AppendixType forcedType) throws Exception {
        try (PDDocument document = PDDocument.load(file)) {
            return parseDocument(document, forcedType);
        }
    }

    private ExtractedData parseDocument(PDDocument document, AppendixType forcedType) throws Exception {
        ExtractedData result = new ExtractedData();
        result.inputFormat = "PDF";
        result.appendixType = (forcedType != null) ? forcedType : detectAppendixType(document);
            ObjectExtractor oe = new ObjectExtractor(document);
            SpreadsheetExtractionAlgorithm sea = new SpreadsheetExtractionAlgorithm();
            PageIterator pages = oe.extract();

            boolean headerCaptured = false;
            int rowIndexCounter = 0;

            while (pages.hasNext()) {
                Page page = pages.next();
                List<Table> tables = sea.extract(page);

                for (Table table : tables) {
                    if (table.getRowCount() == 0) continue;

                    for (int r = 0; r < table.getRowCount(); r++) {
                        List<RectangularTextContainer> row = table.getRows().get(r);
                        List<String> cellTexts = new ArrayList<>();
                        for (RectangularTextContainer cell : row) {
                            String text = cell.getText().replace("\r", " ").replace("\n", " ").trim();
                            cellTexts.add(text);
                        }

                        // Kiểm tra nếu là hàng trống hoàn toàn
                        boolean isEmptyRow = cellTexts.stream().allMatch(String::isEmpty);
                        if (isEmptyRow) continue;

                        result.allRawRows.add(cellTexts);

                        // Kiểm tra hàng tiêu đề
                        String firstCell = cellTexts.size() > 0 ? cellTexts.get(0).toLowerCase() : "";
                        String secondCell = cellTexts.size() > 1 ? cellTexts.get(1).toLowerCase() : "";

                        if (firstCell.contains("số tt") || firstCell.contains("stt") || secondCell.contains("họ và tên") || firstCell.contains("họ và tên")) {
                            if (!headerCaptured) {
                                result.headerRows.add(cellTexts);
                            }
                            continue;
                        }
                        if (firstCell.contains("trợ cấp") || secondCell.contains("nghỉ trong") || secondCell.contains("tuổi đời")
                                || (cellTexts.size() > 2 && cellTexts.get(1).equals("1") && cellTexts.get(2).equals("2"))) {
                            if (!headerCaptured) {
                                result.headerRows.add(cellTexts);
                            }
                            continue;
                        }

                        // Nếu đã gặp dòng dữ liệu đầu tiên, đánh dấu đã bắt xong header
                        headerCaptured = true;

                        // Kiểm tra dòng tổng cộng hoặc tiêu đề đơn vị (vd: "I. Cục...", "* Sĩ quan", "Tổng cộng", "Cộng")
                        if (isSummaryOrSectionRow(cellTexts)) {
                            continue;
                        }

                        // Bóc tách dòng dữ liệu cá nhân
                        ParsedRecord parsed = parseDataRow(cellTexts, result.appendixType, rowIndexCounter++);
                        if (parsed != null && parsed.getFullName() != null && !parsed.getFullName().isEmpty()) {
                            result.records.add(parsed);
                        }
                    }
                }
            }

            return result;
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

        // Nếu cột STT không phải là số nguyên (1, 2, 3...) và cột họ tên không có chữ cái hợp lệ
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

        // Nếu cột retirement date bị overflow '########' hoặc trống, có thể fallback sang mergerDate + thời hạn hoặc parse từ các cột
        YearMonth parsedRetire = DateParserUtil.parseYearMonth(retireStr);
        YearMonth parsedMerger = DateParserUtil.parseYearMonth(mergerStr);
        if (parsedRetire == null && parsedMerger != null) {
            // Nếu cột ngày nghỉ bị lỗi '########' trong PDF thì fallback sang 06/2025 hoặc merger + 3 tháng
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

        // Các cột tiền trợ cấp:
        // C1 (Cột 13..16)
        BigDecimal c1 = getFirstNonZero(cells, 13, 14, 15, 16);
        // C2 (Cột 17, 20)
        BigDecimal c2 = getFirstNonZero(cells, 17, 20);
        // C3 Base (Cột 18, 21)
        BigDecimal c3Base = getFirstNonZero(cells, 18, 21);
        // C3 Extra (Cột 19, 22)
        BigDecimal c3Extra = getFirstNonZero(cells, 19, 22);
        // Tổng tiền (Cột 23)
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

        // Nếu cột 9 bị điền nhầm '60' nhưng cJob có giá trị, lương = cJob / 3
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
}
