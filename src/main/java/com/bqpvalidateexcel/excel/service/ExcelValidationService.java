package com.bqpvalidateexcel.excel.service;

import com.bqpvalidateexcel.excel.model.dto.*;
import com.bqpvalidateexcel.excel.model.expected.*;
import com.bqpvalidateexcel.excel.parser.*;
import com.bqpvalidateexcel.excel.service.rules.*;
import com.bqpvalidateexcel.excel.service.*;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Date;
import java.text.SimpleDateFormat;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelValidationService {

    private final ExcelRowParsePLI1 parsePLI1;
    private final ExcelRowParsePLI2 parsePLI2;
    private final ExcelRowParsePLI3 parsePLI3;

    public byte[] validateAndGenerateErrorReport(InputStream inputStream) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

            // Tạo font chữ màu đỏ
            Font redFont = workbook.createFont();
            redFont.setColor(IndexedColors.RED.getIndex());
            
            // Font đỏ in đậm cho Header Note
            Font boldRedFont = workbook.createFont();
            boldRedFont.setColor(IndexedColors.RED.getIndex());
            boldRedFont.setBold(true);
            
            // Map để cache các CellStyle đã được clone để đổi font đỏ (tránh lỗi vượt quá 4000 styles của Excel)
            java.util.Map<Short, CellStyle> redStyleCache = new java.util.HashMap<>();

            Sheet sheetPLI1 = workbook.getSheet("Phụ lục I.1");
            if (sheetPLI1 != null) {
                validateSheetPLI1(sheetPLI1, evaluator, redFont, boldRedFont, redStyleCache);
            }

            // Tạm thời bỏ qua các sheet khác theo yêu cầu của user
            /*
            Sheet sheetPLI2 = workbook.getSheet("Phụ lục I.2");
            if (sheetPLI2 != null) {
                validateSheetPLI2(sheetPLI2, evaluator, redFont, boldRedFont, redStyleCache);
            }

            Sheet sheetPLI3 = workbook.getSheet("Phụ lục I.3");
            if (sheetPLI3 != null) {
                validateSheetPLI3(sheetPLI3, evaluator, redFont, boldRedFont, redStyleCache);
            }
            */

            // Xuất file
            try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                workbook.write(bos);
                return bos.toByteArray();
            }
        }
    }

    private String fmt(Object val) {
        if (val == null) return "null";
        if (val instanceof BigDecimal) {
            BigDecimal bd = (BigDecimal) val;
            bd = bd.stripTrailingZeros();
            if (bd.scale() <= 0) {
                return new java.text.DecimalFormat("#,###").format(bd);
            } else {
                return new java.text.DecimalFormat("#,###.##").format(bd);
            }
        }
        if (val instanceof Integer || val instanceof Long) {
            return new java.text.DecimalFormat("#,###").format(val);
        }
        if (val instanceof Double || val instanceof Float) {
            return new java.text.DecimalFormat("#,###.##").format(val);
        }
        return val.toString();
    }

    private void addError(Row row, int colIndex, String note, List<String> errorDetails, Font redFont, java.util.Map<Short, CellStyle> redStyleCache) {
        Cell cell = row.getCell(colIndex);
        if (cell == null) {
            cell = row.createCell(colIndex);
        }
        
        CellStyle oldStyle = cell.getCellStyle();
        short oldStyleIdx = oldStyle != null ? oldStyle.getIndex() : -1;
        
        CellStyle newStyle = redStyleCache.get(oldStyleIdx);
        if (newStyle == null) {
            newStyle = row.getSheet().getWorkbook().createCellStyle();
            if (oldStyle != null) {
                newStyle.cloneStyleFrom(oldStyle);
            }
            newStyle.setFont(redFont);
            redStyleCache.put(oldStyleIdx, newStyle);
        }
        
        cell.setCellStyle(newStyle);
        errorDetails.add(note);
    }

    
    private void checkCol(Row row, int colIndex, BigDecimal actual, BigDecimal expected, String formula, List<String> errorDetails, Font redFont, java.util.Map<Short, CellStyle> redStyleCache) {
        boolean expectedHasValue = expected != null && expected.compareTo(BigDecimal.ZERO) > 0;
        boolean actualHasValue = actual != null && actual.compareTo(BigDecimal.ZERO) > 0;

        if (expectedHasValue) {
            if (!actualHasValue || !isEqual(expected, actual)) {
                addError(row, colIndex, "Cột " + colIndex + ". Kết quả đúng: " + fmt(expected) + ". Công thức: " + formula, errorDetails, redFont, redStyleCache);
            }
        } else {
            if (actualHasValue) {
                addError(row, colIndex, "Cột " + colIndex + ". Kết quả đúng: 0 (Không đủ điều kiện).", errorDetails, redFont, redStyleCache);
            }
        }
    }

    private int calcThang(Date d1, Date d2) {
        if (d1 == null || d2 == null) return 0;
        java.util.Calendar c1 = java.util.Calendar.getInstance(); c1.setTime(d1);
        java.util.Calendar c2 = java.util.Calendar.getInstance(); c2.setTime(d2);
        int m1 = c1.get(java.util.Calendar.YEAR) * 12 + c1.get(java.util.Calendar.MONTH);
        int m2 = c2.get(java.util.Calendar.YEAR) * 12 + c2.get(java.util.Calendar.MONTH);
        return m1 - m2;
    }

    private BigDecimal calcNamLamTron(int thang) {
        if (thang <= 0) return BigDecimal.ZERO;
        int years = thang / 12;
        int rem = thang % 12;
        if (rem == 0) return BigDecimal.valueOf(years);
        if (rem <= 6) return BigDecimal.valueOf(years).add(BigDecimal.valueOf(0.5));
        return BigDecimal.valueOf(years).add(BigDecimal.ONE);
    }
    
    private int getTran(String capBac) {
        if (capBac == null) return 0;
        String cb = capBac.toLowerCase();
        if (cb.contains("đại tá")) return 58;
        if (cb.contains("thượng tá")) return 56;
        if (cb.contains("trung tá")) return 54;
        if (cb.contains("thiếu tá")) return 52;
        if (cb.contains("uý") || cb.contains("úy")) return 50;
        return 0;
    }

    private void writeNotesToRow(Row row, List<String> errorDetails, int noteColumnIndex, Font redFont) {
        if (!errorDetails.isEmpty()) {
            Cell noteCell = row.getCell(noteColumnIndex);
            if (noteCell == null) {
                noteCell = row.createCell(noteColumnIndex);
            }
            noteCell.setCellValue(String.join("\n", errorDetails));
            
            // Đổi màu chữ Note thành đỏ
            CellStyle style = row.getSheet().getWorkbook().createCellStyle();
            style.setWrapText(true);
            style.setFont(redFont);
            noteCell.setCellStyle(style);
        }
    }
    
    private void createNoteHeader(Sheet sheet, int noteColumnIndex, Font boldRedFont) {
        // Header in Excel is usually on row 11 (index 10) for PLI.1, PLI.2, PLI.3
        Row headerRow = sheet.getRow(10);
        if (headerRow == null) headerRow = sheet.createRow(10);
        Cell headerCell = headerRow.getCell(noteColumnIndex);
        if (headerCell == null) headerCell = headerRow.createCell(noteColumnIndex);
        headerCell.setCellValue("Note");
        CellStyle headerStyle = sheet.getWorkbook().createCellStyle();
        headerStyle.setFont(boldRedFont);
        headerCell.setCellStyle(headerStyle);
        
        // Auto width is sometimes not enough, so force a reasonable width
        sheet.setColumnWidth(noteColumnIndex, 256 * 60); // 60 chars wide
    }

    private void validateSheetPLI1(Sheet sheet, FormulaEvaluator evaluator, Font redFont, Font boldRedFont, java.util.Map<Short, CellStyle> redStyleCache) {
        int startRow = 12;
        int rowNum = startRow;
        createNoteHeader(sheet, 24, boldRedFont);
        
        while (true) {
            Row row = sheet.getRow(rowNum);
            if (row == null) break;

            Optional<PhuLucI1> optData = parsePLI1.parse(row, rowNum, evaluator);
            if (optData.isEmpty()) {
                Cell cellB = row.getCell(1);
                if (cellB != null && cellB.getCellType() == CellType.STRING && cellB.getStringCellValue().contains("Cộng")) {
                    break;
                }
                rowNum++;
                continue;
            }

            PhuLucI1 data = optData.get();
            List<String> errorDetails = new ArrayList<>();
            
            // USE THE CALCULATOR!
            PLI1ExpectedResult exp = PLI1Calculator.calculateExpected(data);

            String luongFmt = fmt(data.getLuongThangHienThuongTheoThongTu());
            int c10 = exp.getCot10();
            int capC10 = Math.min(c10, 60);
            String c11Fmt = fmt(exp.getCot11());
            String c12Fmt = fmt(exp.getCot12());
            boolean nghiTruoc172025 = true;
            try {
                if (data.getThoiDiemNghiHuuHuongTroCap() != null && data.getThoiDiemNghiHuuHuongTroCap().after(new java.text.SimpleDateFormat("dd/MM/yyyy").parse("01/07/2025"))) {
                    nghiTruoc172025 = false;
                }
            } catch (Exception ignored) {}
            String hs18 = nghiTruoc172025 ? "5" : "4";
            String moc19 = nghiTruoc172025 ? "20" : "15";

            String bdStr = "";
            if (data.getNgaySinh() != null) bdStr = new java.text.SimpleDateFormat("MM/yyyy").format(data.getNgaySinh());
            String retStr = "";
            if (data.getThoiDiemNghiHuuHuongTroCap() != null) retStr = new java.text.SimpleDateFormat("MM/yyyy").format(data.getThoiDiemNghiHuuHuongTroCap());
            
            if (data.getSoThangNghiHuuTruocTuoiTheoThongTu() != null && data.getSoThangNghiHuuTruocTuoiTheoThongTu() != exp.getCot10()) {
                addError(row, 10, "Cột 10. Kết quả đúng: " + exp.getCot10() + ". Công thức: (Tháng sinh + Trần) - Cột 8 (VD: Khoảng cách từ " + retStr + " đến Mốc hưu chuẩn của " + bdStr + ")", errorDetails, redFont, redStyleCache);
            }
            if (data.getSoNamNghiHuuTruocTuoiTheoThongTu() != null && data.getSoNamNghiHuuTruocTuoiTheoThongTu() != exp.getCot11().intValue()) {
                addError(row, 11, "Cột 11. Kết quả đúng: " + c11Fmt + ". Công thức: Cột 10 / 12 (VD: " + c10 + " / 12)", errorDetails, redFont, redStyleCache);
            }
            if (data.getSoNamCongTacDongBHXHTheoThongTu() != null && !isEqual(exp.getCot12(), data.getSoNamCongTacDongBHXHTheoThongTu())) {
                addError(row, 12, "Cột 12. Kết quả đúng: " + c12Fmt + ". Công thức: Cột 8 - Cột 5", errorDetails, redFont, redStyleCache);
            }

            checkCol(row, 13, data.getTuoiDoiCon5NamTroXuong1(), exp.getCot13(), 
                "Cột 10 (<= 60) * 1 tháng * Cột 9 (VD: " + capC10 + " * 1 * " + luongFmt + ")", errorDetails, redFont, redStyleCache);
            checkCol(row, 14, data.getTuoiDoiConTren5NamDenDuoi10Nam1(), exp.getCot14(), 
                "Cột 10 (> 60) * 0.9 tháng * Cột 9 (VD: " + capC10 + " * 0.9 * " + luongFmt + ")", errorDetails, redFont, redStyleCache);
            checkCol(row, 15, data.getTuoiDoiCon5NamTroXuong2(), exp.getCot15(), 
                "Cột 10 (<= 60) * 0.5 tháng * Cột 9 (VD: " + capC10 + " * 0.5 * " + luongFmt + ")", errorDetails, redFont, redStyleCache);
            checkCol(row, 16, data.getTuoiDoiConTren5NamDenDuoi10Nam2(), exp.getCot16(), 
                "Cột 10 (> 60) * 0.45 tháng * Cột 9 (VD: " + capC10 + " * 0.45 * " + luongFmt + ")", errorDetails, redFont, redStyleCache);

            checkCol(row, 17, data.getG5ThangTienLuongHienHuongCho1NamNghiSom(), exp.getCot17(), 
                "Cột 11 * 5 tháng * Cột 9 (VD: " + c11Fmt + " * 5 * " + luongFmt + ")", errorDetails, redFont, redStyleCache);
            checkCol(row, 18, data.getG5ThangTienLuongHienHuongCho20NamDauCongTac1(), exp.getCot18(), 
                "Cột 9 * " + hs18 + " tháng (VD: " + luongFmt + " * " + hs18 + ")", errorDetails, redFont, redStyleCache);
            checkCol(row, 19, data.getTuNamThu21TroDiCuMoiNamCongTacHuong12ThangLuongHienHuong1(), exp.getCot19(), 
                "Cột 9 * 0.5 * (Cột 12 - " + moc19 + ") (VD: " + luongFmt + " * 0.5 * (" + c12Fmt + " - " + moc19 + "))", errorDetails, redFont, redStyleCache);

            checkCol(row, 20, data.getG4ThangTienLuongHienHuongCho1NamNghi(), exp.getCot20(), 
                "Cột 11 * 4 tháng * Cột 9 (VD: " + c11Fmt + " * 4 * " + luongFmt + ")", errorDetails, redFont, redStyleCache);
            checkCol(row, 21, data.getG5ThangTienLuongHienHuongCho20NamDauCongTac2(), exp.getCot21(), 
                "Cột 9 * " + hs18 + " tháng (VD: " + luongFmt + " * " + hs18 + ")", errorDetails, redFont, redStyleCache);
            checkCol(row, 22, data.getTuNamThu21TroDiCuMoiNamCongTacHuong12ThangLuongHienHuong2(), exp.getCot22(), 
                "Cột 9 * 0.5 * (Cột 12 - " + moc19 + ") (VD: " + luongFmt + " * 0.5 * (" + c12Fmt + " - " + moc19 + "))", errorDetails, redFont, redStyleCache);

            BigDecimal expectedTotal = BigDecimal.ZERO;
            expectedTotal = expectedTotal.add(exp.getCot13() != null ? exp.getCot13() : BigDecimal.ZERO);
            expectedTotal = expectedTotal.add(exp.getCot14() != null ? exp.getCot14() : BigDecimal.ZERO);
            expectedTotal = expectedTotal.add(exp.getCot15() != null ? exp.getCot15() : BigDecimal.ZERO);
            expectedTotal = expectedTotal.add(exp.getCot16() != null ? exp.getCot16() : BigDecimal.ZERO);
            expectedTotal = expectedTotal.add(exp.getCot17() != null ? exp.getCot17() : BigDecimal.ZERO);
            expectedTotal = expectedTotal.add(exp.getCot18() != null ? exp.getCot18() : BigDecimal.ZERO);
            expectedTotal = expectedTotal.add(exp.getCot19() != null ? exp.getCot19() : BigDecimal.ZERO);
            expectedTotal = expectedTotal.add(exp.getCot20() != null ? exp.getCot20() : BigDecimal.ZERO);
            expectedTotal = expectedTotal.add(exp.getCot21() != null ? exp.getCot21() : BigDecimal.ZERO);
            expectedTotal = expectedTotal.add(exp.getCot22() != null ? exp.getCot22() : BigDecimal.ZERO);

            BigDecimal actualTotal = data.getTongCongSoTienTheoNghiDinhSo178() != null ? data.getTongCongSoTienTheoNghiDinhSo178() : BigDecimal.ZERO;
            if (!isEqual(expectedTotal, actualTotal)) {
                addError(row, 23, "Cột 23. Kết quả đúng: " + fmt(expectedTotal) + ". Công thức: SUM(13..22)", errorDetails, redFont, redStyleCache);
            }

            writeNotesToRow(row, errorDetails, 24, redFont);

            rowNum++;
        }
    }



    private void validateSheetPLI2(Sheet sheet, FormulaEvaluator evaluator, Font redFont, Font boldRedFont, java.util.Map<Short, CellStyle> redStyleCache) {
        int startRow = 12;
        int rowNum = startRow;
        createNoteHeader(sheet, 24, boldRedFont);
        
        while (true) {
            Row row = sheet.getRow(rowNum);
            if (row == null) break;

            Optional<PhuLucI2> optData = parsePLI2.parse(row, rowNum, evaluator);
            if (optData.isEmpty()) {
                Cell cellB = row.getCell(1);
                if (cellB != null && cellB.getCellType() == CellType.STRING && cellB.getStringCellValue().contains("Cộng")) {
                    break;
                }
                rowNum++;
                continue;
            }

            PhuLucI2 data = optData.get();
            List<String> errorDetails = new ArrayList<>();
            BigDecimal expectedTotal = BigDecimal.ZERO;

            // Cột 10 = Cột 8 - Cột 5 (tháng)
            int cot10 = calcThang(data.getThoiDiemThoiViecHuongTroCap(), data.getNhapNgu());
            if (cot10 < 0) cot10 = 0;
            Integer actualCot10 = data.getSoThangThoiViecTheoThongTu();
            if (actualCot10 != null && actualCot10 != cot10) {
                addError(row, 10, "Cột 10. Kết quả đúng: " + cot10 + ". Công thức: Cột 8 - Cột 5", errorDetails, redFont, redStyleCache);
            }

            // Cột 11 = Cột 10 / 12
            BigDecimal exp11 = calcNamLamTron(cot10);
                        Integer actualCot11 = data.getSoNamHuongTroCapTheoThongTu();
            if (actualCot11 != null && actualCot11 != exp11.intValue()) {
                addError(row, 11, "Cột 11. Kết quả đúng: " + fmt(exp11) + ". Công thức: Cột 10 / 12", errorDetails, redFont, redStyleCache);
            }

            BigDecimal luong = data.getLuongThangHienThuongTheoThongTu() != null ? data.getLuongThangHienThuongTheoThongTu() : BigDecimal.ZERO;
            
            // Tính khoảng cách Cột 8 - Cột 7
            int distance8_7 = calcThang(data.getThoiDiemThoiViecHuongTroCap(), data.getThoiGianDonViSapNhapGiaiThe());
            boolean isLess12 = distance8_7 < 12;

            BigDecimal expectedCol12 = BigDecimal.ZERO;
            BigDecimal expectedCol13 = BigDecimal.ZERO;
            BigDecimal expectedCol14 = BigDecimal.ZERO;
            BigDecimal expectedCol15 = BigDecimal.ZERO;
            BigDecimal expectedCol16 = BigDecimal.ZERO;
            BigDecimal expectedCol17 = BigDecimal.ZERO;

            if (isLess12) {
                expectedCol12 = luong.multiply(BigDecimal.valueOf(0.8)).multiply(BigDecimal.valueOf(cot10));
                expectedCol13 = luong.multiply(BigDecimal.valueOf(1.5)).multiply(exp11);
                expectedCol14 = luong.multiply(BigDecimal.valueOf(3));
            } else {
                expectedCol15 = luong.multiply(BigDecimal.valueOf(0.4)).multiply(BigDecimal.valueOf(cot10));
                expectedCol16 = luong.multiply(BigDecimal.valueOf(1.5)).multiply(exp11);
                expectedCol17 = luong.multiply(BigDecimal.valueOf(3));
            }

            if (!isEqual(expectedCol12, data.getTroCap1LanChoSoThangCongTacCoDongBHXH1())) {
                addError(row, 12, "Cột 12. Kết quả đúng: " + fmt(expectedCol12) + ". Công thức: " + (isLess12 ? "Cột 9 * 0.8 * Cột 10" : "0 (Cột 8 - Cột 7 >= 12)"), errorDetails, redFont, redStyleCache);
            }
            expectedTotal = expectedTotal.add(data.getTroCap1LanChoSoThangCongTacCoDongBHXH1() != null ? data.getTroCap1LanChoSoThangCongTacCoDongBHXH1() : BigDecimal.ZERO);

            if (!isEqual(expectedCol13, data.getTroCap1LanChoSoNamCongTacDongBHXH1())) {
                addError(row, 13, "Cột 13. Kết quả đúng: " + fmt(expectedCol13) + ". Công thức: " + (isLess12 ? "Cột 9 * 1.5 * Cột 11" : "0 (Cột 8 - Cột 7 >= 12)"), errorDetails, redFont, redStyleCache);
            }
            expectedTotal = expectedTotal.add(data.getTroCap1LanChoSoNamCongTacDongBHXH1() != null ? data.getTroCap1LanChoSoNamCongTacDongBHXH1() : BigDecimal.ZERO);

            if (!isEqual(expectedCol14, data.getTroCapTaoViecLam1())) {
                addError(row, 14, "Cột 14. Kết quả đúng: " + fmt(expectedCol14) + ". Công thức: " + (isLess12 ? "Cột 9 * 3" : "0 (Cột 8 - Cột 7 >= 12)"), errorDetails, redFont, redStyleCache);
            }
            expectedTotal = expectedTotal.add(data.getTroCapTaoViecLam1() != null ? data.getTroCapTaoViecLam1() : BigDecimal.ZERO);

            if (!isEqual(expectedCol15, data.getTroCap1LanChoSoThangCongTacCoDongBHXH2())) {
                addError(row, 15, "Cột 15. Kết quả đúng: " + fmt(expectedCol15) + ". Công thức: " + (!isLess12 ? "Cột 9 * 0.4 * Cột 10" : "0 (Cột 8 - Cột 7 < 12)"), errorDetails, redFont, redStyleCache);
            }
            expectedTotal = expectedTotal.add(data.getTroCap1LanChoSoThangCongTacCoDongBHXH2() != null ? data.getTroCap1LanChoSoThangCongTacCoDongBHXH2() : BigDecimal.ZERO);

            if (!isEqual(expectedCol16, data.getTroCap1LanChoSoNamCongTacDongBHXH2())) {
                addError(row, 16, "Cột 16. Kết quả đúng: " + fmt(expectedCol16) + ". Công thức: " + (!isLess12 ? "Cột 9 * 1.5 * Cột 11" : "0 (Cột 8 - Cột 7 < 12)"), errorDetails, redFont, redStyleCache);
            }
            expectedTotal = expectedTotal.add(data.getTroCap1LanChoSoNamCongTacDongBHXH2() != null ? data.getTroCap1LanChoSoNamCongTacDongBHXH2() : BigDecimal.ZERO);

            if (!isEqual(expectedCol17, data.getTroCapTaoViecLam2())) {
                addError(row, 17, "Cột 17. Kết quả đúng: " + fmt(expectedCol17) + ". Công thức: " + (!isLess12 ? "Cột 9 * 3" : "0 (Cột 8 - Cột 7 < 12)"), errorDetails, redFont, redStyleCache);
            }
            expectedTotal = expectedTotal.add(data.getTroCapTaoViecLam2() != null ? data.getTroCapTaoViecLam2() : BigDecimal.ZERO);

            BigDecimal actualTotal = data.getTongCongSoTienNghiThoiViecTheoNghiDinhSo178() != null ? data.getTongCongSoTienNghiThoiViecTheoNghiDinhSo178() : BigDecimal.ZERO;
            if (!isEqual(expectedTotal, actualTotal)) {
                addError(row, 18, "Cột 18. Kết quả đúng: " + fmt(expectedTotal) + ". Công thức: SUM(12..17)", errorDetails, redFont, redStyleCache);
            }

            writeNotesToRow(row, errorDetails, 24, redFont);

            rowNum++;
        }
    }


    private void validateSheetPLI3(Sheet sheet, FormulaEvaluator evaluator, Font redFont, Font boldRedFont, java.util.Map<Short, CellStyle> redStyleCache) {
        int startRow = 12;
        int rowNum = startRow;
        createNoteHeader(sheet, 24, boldRedFont);
        
        while (true) {
            Row row = sheet.getRow(rowNum);
            if (row == null) break;

            Optional<PhuLucI3> optData = parsePLI3.parse(row, rowNum, evaluator);
            if (optData.isEmpty()) {
                Cell cellB = row.getCell(1);
                if (cellB != null && cellB.getCellType() == CellType.STRING && cellB.getStringCellValue().contains("Cộng")) {
                    break;
                }
                rowNum++;
                continue;
            }

            PhuLucI3 data = optData.get();
            List<String> errorDetails = new ArrayList<>();
            BigDecimal expectedTotal = BigDecimal.ZERO;

            // Cột 10 = (Cột 2 + trần) - Cột 8
            int tran = getTran(data.getCapBac());
            int cot10 = 0;
            if (tran > 0 && data.getNgaySinh() != null && data.getThoiDiemNghiHuuHuongTroCap() != null) {
                java.util.Calendar calDob = java.util.Calendar.getInstance();
                calDob.setTime(data.getNgaySinh());
                calDob.add(java.util.Calendar.YEAR, tran);
                cot10 = calcThang(calDob.getTime(), data.getThoiDiemNghiHuuHuongTroCap());
            }
            if (cot10 < 0) cot10 = 0;

            Integer actualCot10 = data.getSoThangThoiViecTheoHuongDan();
            if (actualCot10 != null && actualCot10 != cot10) {
                addError(row, 10, "Cột 10. Kết quả đúng: " + cot10 + ". Công thức: (Cột 2 + trần) - Cột 8", errorDetails, redFont, redStyleCache);
            }

            // Cột 11 = Cột 10 / 12
            BigDecimal exp11 = calcNamLamTron(cot10);
                        Integer actualCot11 = data.getSoNamHuongTroCapTheoHuongDan();
            if (actualCot11 != null && actualCot11 != exp11.intValue()) {
                addError(row, 11, "Cột 11. Kết quả đúng: " + fmt(exp11) + ". Công thức: Cột 10 / 12", errorDetails, redFont, redStyleCache);
            }

            BigDecimal luong = data.getLuongThangHienThuongTheoHuongDan() != null ? data.getLuongThangHienThuongTheoHuongDan() : BigDecimal.ZERO;

            // Cột 12: Cột 11 * 5 (tháng) * cột 9
            BigDecimal expectedCol12 = luong.multiply(BigDecimal.valueOf(5)).multiply(exp11);
            if (!isEqual(expectedCol12, data.getG5ThangTienLuongHienHuongCho1NamNghiSom())) {
                addError(row, 12, "Cột 12. Kết quả đúng: " + fmt(expectedCol12) + ". Công thức: Cột 9 * 5 * Cột 11", errorDetails, redFont, redStyleCache);
            }
            expectedTotal = expectedTotal.add(data.getG5ThangTienLuongHienHuongCho1NamNghiSom() != null ? data.getG5ThangTienLuongHienHuongCho1NamNghiSom() : BigDecimal.ZERO);

            // Cột 13: Cột 9 * 5 (tháng)
            BigDecimal expectedCol13 = luong.multiply(BigDecimal.valueOf(5));
            if (!isEqual(expectedCol13, data.getG5ThangTienLuongHienHuongCho20NamDauCongTac())) {
                addError(row, 13, "Cột 13. Kết quả đúng: " + fmt(expectedCol13) + ". Công thức: Cột 9 * 5", errorDetails, redFont, redStyleCache);
            }
            expectedTotal = expectedTotal.add(data.getG5ThangTienLuongHienHuongCho20NamDauCongTac() != null ? data.getG5ThangTienLuongHienHuongCho20NamDauCongTac() : BigDecimal.ZERO);

            // Cột 14: Cột 9 * 0.5 (tháng) * (Cột 11 - 20 năm)
            BigDecimal expectedCol14 = BigDecimal.ZERO;
            if (exp11.compareTo(BigDecimal.valueOf(20)) > 0) {
                expectedCol14 = luong.multiply(BigDecimal.valueOf(0.5)).multiply(exp11.subtract(BigDecimal.valueOf(20)));
            }
            if (!isEqual(expectedCol14, data.getTuNamThu21TroDiCuMoiNamCongTacHuong12ThangLuongHienHuong())) {
                addError(row, 14, "Cột 14. Kết quả đúng: " + fmt(expectedCol14) + ". Công thức: Cột 9 * 0.5 * (Cột 11 - 20)", errorDetails, redFont, redStyleCache);
            }
            expectedTotal = expectedTotal.add(data.getTuNamThu21TroDiCuMoiNamCongTacHuong12ThangLuongHienHuong() != null ? data.getTuNamThu21TroDiCuMoiNamCongTacHuong12ThangLuongHienHuong() : BigDecimal.ZERO);

            BigDecimal actualTotal = data.getTongCongSoTienNghiThoiViecTheoNghiDinhSo177() != null ? data.getTongCongSoTienNghiThoiViecTheoNghiDinhSo177() : BigDecimal.ZERO;
            if (!isEqual(expectedTotal, actualTotal)) {
                addError(row, 15, "Cột 15. Kết quả đúng: " + fmt(expectedTotal) + ". Công thức: SUM(12..14)", errorDetails, redFont, redStyleCache);
            }

            writeNotesToRow(row, errorDetails, 24, redFont);

            rowNum++;
        }
    }


    private boolean isEqual(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) return true;
        if (a == null) a = BigDecimal.ZERO;
        if (b == null) b = BigDecimal.ZERO;
        return a.setScale(0, RoundingMode.HALF_UP).compareTo(b.setScale(0, RoundingMode.HALF_UP)) == 0;
    }
}
