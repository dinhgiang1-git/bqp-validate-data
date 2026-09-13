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
    private org.apache.poi.ss.usermodel.Sheet findSheet(org.apache.poi.ss.usermodel.Workbook workbook, String keyword) {
        String cleanKeyword = cleanSheetName(keyword);
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.getSheetAt(i);
            if (sheet.getSheetName() != null && cleanSheetName(sheet.getSheetName()).contains(cleanKeyword)) {
                return sheet;
            }
        }
        return null;
    }

    private String cleanSheetName(String name) {
        if (name == null) return "";
        String s = name.replace('\u00A0', ' ')
                   .replaceAll("\\s+", "")
                   .toLowerCase();
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replace("đ", "d");
    }


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

            List<ErrorRecordDto> errorRecords = new ArrayList<>();

            Sheet sheetPLI1 = findSheet(workbook, "I.1");
            if (sheetPLI1 != null) {
                validateSheetPLI1(sheetPLI1, evaluator, redFont, boldRedFont, redStyleCache, errorRecords);
            }

            Sheet sheetPLI2 = findSheet(workbook, "I.2");
            if (sheetPLI2 != null) {
                validateSheetPLI2(sheetPLI2, evaluator, redFont, boldRedFont, redStyleCache, errorRecords);
            }

            Sheet sheetPLI3 = findSheet(workbook, "I.3");
            if (sheetPLI3 != null) {
                validateSheetPLI3(sheetPLI3, evaluator, redFont, boldRedFont, redStyleCache, errorRecords);
            }

            // Ghi các bản ghi sai sang sheet Phụ lục II
            if (!errorRecords.isEmpty()) {
                writeErrorsToPLII(workbook, errorRecords, evaluator);
            }


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
        checkColWithLabel(row, colIndex, "Cột " + colIndex, actual, expected, formula, errorDetails, redFont, redStyleCache);
    }

    private void checkColWithLabel(Row row, int colIndex, String colLabel, BigDecimal actual, BigDecimal expected, String formula, List<String> errorDetails, Font redFont, java.util.Map<Short, CellStyle> redStyleCache) {
        boolean expectedHasValue = expected != null && expected.compareTo(BigDecimal.ZERO) > 0;
        boolean actualHasValue = actual != null && actual.compareTo(BigDecimal.ZERO) > 0;

        if (expectedHasValue) {
            if (!actualHasValue || !isEqual(expected, actual)) {
                addError(row, colIndex, colLabel + ". Kết quả đúng: " + fmt(expected) + ". Công thức: " + formula, errorDetails, redFont, redStyleCache);
            }
        } else {
            if (actualHasValue) {
                String reason = (formula != null && formula.contains("không cần tính")) ? formula : "Không đủ điều kiện";
                addError(row, colIndex, colLabel + ". Kết quả đúng: 0 (" + reason + ").", errorDetails, redFont, redStyleCache);
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
        String cb = capBac.replace('\u00A0', ' ').trim().toLowerCase().replaceAll("\\s+", " ");
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
    
    private void createNoteHeader(Sheet sheet, int headerRowIndex, int noteColumnIndex, Font boldRedFont) {
        // Header in Excel is usually on row 11 (index 10) for PLI.1, PLI.2, PLI.3
        Row headerRow = sheet.getRow(headerRowIndex);
        if (headerRow == null) headerRow = sheet.createRow(headerRowIndex);
        Cell headerCell = headerRow.getCell(noteColumnIndex);
        if (headerCell == null) headerCell = headerRow.createCell(noteColumnIndex);
        headerCell.setCellValue("Ghi chú");
        CellStyle headerStyle = sheet.getWorkbook().createCellStyle();
        headerStyle.setFont(boldRedFont);
        headerCell.setCellStyle(headerStyle);
        
        // Auto width is sometimes not enough, so force a reasonable width
        sheet.setColumnWidth(noteColumnIndex, 256 * 60); // 60 chars wide
    }

    private void validateSheetPLI1(Sheet sheet, FormulaEvaluator evaluator, Font redFont, Font boldRedFont, java.util.Map<Short, CellStyle> redStyleCache, List<ErrorRecordDto> errorRecords) {
        int startRow = 12;
        int rowNum = startRow;
        createNoteHeader(sheet, 10, 24, boldRedFont);
        String currentUnit = "";
        
        while (true) {
            Row row = sheet.getRow(rowNum);
            if (row == null) break;

            currentUnit = checkUnitHeader(row, evaluator, currentUnit);

            Optional<PhuLucI1> optData = parsePLI1.parse(row, rowNum, evaluator);
            if (optData.isEmpty()) {
                String valB = ExcelParserUtils.getString(row, 1, evaluator).toLowerCase().replaceAll("\\s+", "");
                if (valB.contains("cộng")) {
                    break;
                }
                rowNum++;
                continue;
            }

            PhuLucI1 data = optData.get();
            if (data.getCapBac() == null || data.getCapBac().trim().isEmpty() || data.getNgaySinh() == null) {
                rowNum++;
                continue;
            }
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
                Date threshold = new java.text.SimpleDateFormat("dd/MM/yyyy").parse("01/07/2025");
                if (data.getThoiDiemNghiHuuHuongTroCap() != null && !data.getThoiDiemNghiHuuHuongTroCap().before(threshold)) {
                    nghiTruoc172025 = false;
                }
            } catch (Exception ignored) {}
            String hs18 = nghiTruoc172025 ? "5" : "4";
            String moc19 = nghiTruoc172025 ? "20" : "15";
            String hs19 = "0.5";

            String bdStr = "";
            if (data.getNgaySinh() != null) bdStr = new java.text.SimpleDateFormat("MM/yyyy").format(data.getNgaySinh());
            String nnStr = "";
            if (data.getNhapNgu() != null) nnStr = new java.text.SimpleDateFormat("MM/yyyy").format(data.getNhapNgu());
            String retStr = "";
            if (data.getThoiDiemNghiHuuHuongTroCap() != null) retStr = new java.text.SimpleDateFormat("MM/yyyy").format(data.getThoiDiemNghiHuuHuongTroCap());
            
            if (data.getSoThangNghiHuuTruocTuoiTheoThongTu() != null && data.getSoThangNghiHuuTruocTuoiTheoThongTu() != exp.getCot10()) {
                addError(row, 10, "Cột 10. Kết quả đúng: " + exp.getCot10() + ". Công thức: (Tháng sinh + Trần) - Cột 8 (VD: Khoảng cách từ " + retStr + " đến Mốc hưu chuẩn của " + bdStr + ")", errorDetails, redFont, redStyleCache);
            }
            if (data.getSoNamNghiHuuTruocTuoiTheoThongTu() != null && !isEqual(exp.getCot11(), data.getSoNamNghiHuuTruocTuoiTheoThongTu())) {
                addError(row, 11, "Cột 11. Kết quả đúng: " + c11Fmt + ". Công thức: Cột 10 / 12 (làm tròn: <= 0,5 = 0,5; > 0,5 = 1) (VD: " + c10 + " / 12 = " + c11Fmt + " năm)", errorDetails, redFont, redStyleCache);
            }
            if (data.getSoNamCongTacDongBHXHTheoThongTu() != null && !isEqual(exp.getCot12(), data.getSoNamCongTacDongBHXHTheoThongTu())) {
                addError(row, 12, "Cột 12. Kết quả đúng: " + c12Fmt + ". Công thức: Cột 8 - Cột 5 (làm tròn: <= 6 tháng + 0,5; > 6 tháng + 1) (VD: Khoảng cách từ " + nnStr + " đến " + retStr + " = " + c12Fmt + " năm)", errorDetails, redFont, redStyleCache);
            }

            checkCol(row, 13, data.getTuoiDoiCon5NamTroXuong1(), exp.getCot13(), 
                "Cột 10 (<= 60) * 1 tháng * Cột 9 (VD: " + capC10 + " * 1 * " + luongFmt + ")", errorDetails, redFont, redStyleCache);
            checkCol(row, 14, data.getTuoiDoiConTren5NamDenDuoi10Nam1(), exp.getCot14(), 
                "Cột 10 (> 60) * 0.9 tháng * Cột 9 (VD: " + capC10 + " * 0.9 * " + luongFmt + ")", errorDetails, redFont, redStyleCache);
            checkCol(row, 15, data.getTuoiDoiCon5NamTroXuong2(), exp.getCot15(), 
                "Cột 10 (<= 60) * 0.5 tháng * Cột 9 (VD: " + capC10 + " * 0.5 * " + luongFmt + ")", errorDetails, redFont, redStyleCache);
            checkCol(row, 16, data.getTuoiDoiConTren5NamDenDuoi10Nam2(), exp.getCot16(), 
                "Cột 10 (> 60) * 0.45 tháng * Cột 9 (VD: " + capC10 + " * 0.45 * " + luongFmt + ")", errorDetails, redFont, redStyleCache);

            if (exp.getCot11().compareTo(BigDecimal.valueOf(2)) < 0) {
                checkColWithLabel(row, 17, "Cột 17", data.getG5ThangTienLuongHienHuongCho1NamNghiSom(), BigDecimal.ZERO, "Cột 11 < 2 năm không cần tính", errorDetails, redFont, redStyleCache);
                checkColWithLabel(row, 18, "Cột 18", data.getG5ThangTienLuongHienHuongCho20NamDauCongTac1(), BigDecimal.ZERO, "Cột 11 < 2 năm không cần tính", errorDetails, redFont, redStyleCache);
                checkColWithLabel(row, 19, "Cột 19", data.getTuNamThu21TroDiCuMoiNamCongTacHuong12ThangLuongHienHuong1(), BigDecimal.ZERO, "Cột 11 < 2 năm không cần tính", errorDetails, redFont, redStyleCache);
                checkColWithLabel(row, 20, "Cột 20", data.getG4ThangTienLuongHienHuongCho1NamNghi(), BigDecimal.ZERO, "Cột 11 < 2 năm không cần tính", errorDetails, redFont, redStyleCache);
                checkColWithLabel(row, 21, "Cột 21", data.getG5ThangTienLuongHienHuongCho20NamDauCongTac2(), BigDecimal.ZERO, "Cột 11 < 2 năm không cần tính", errorDetails, redFont, redStyleCache);
                checkColWithLabel(row, 22, "Cột 22", data.getTuNamThu21TroDiCuMoiNamCongTacHuong12ThangLuongHienHuong2(), BigDecimal.ZERO, "Cột 11 < 2 năm không cần tính", errorDetails, redFont, redStyleCache);
            } else {
                checkCol(row, 17, data.getG5ThangTienLuongHienHuongCho1NamNghiSom(), exp.getCot17(), 
                    "Cột 11 * 5 tháng * Cột 9 (VD: " + c11Fmt + " * 5 * " + luongFmt + ")", errorDetails, redFont, redStyleCache);
                checkCol(row, 18, data.getG5ThangTienLuongHienHuongCho20NamDauCongTac1(), exp.getCot18(), 
                    "Cột 9 * " + hs18 + " tháng (VD: " + luongFmt + " * " + hs18 + ")", errorDetails, redFont, redStyleCache);
                checkCol(row, 19, data.getTuNamThu21TroDiCuMoiNamCongTacHuong12ThangLuongHienHuong1(), exp.getCot19(), 
                    "Cột 9 * " + hs19 + " * (Cột 12 - " + moc19 + ") (VD: " + luongFmt + " * " + hs19 + " * (" + c12Fmt + " - " + moc19 + "))", errorDetails, redFont, redStyleCache);

                checkCol(row, 20, data.getG4ThangTienLuongHienHuongCho1NamNghi(), exp.getCot20(), 
                    "Cột 11 * 4 tháng * Cột 9 (VD: " + c11Fmt + " * 4 * " + luongFmt + ")", errorDetails, redFont, redStyleCache);
                checkCol(row, 21, data.getG5ThangTienLuongHienHuongCho20NamDauCongTac2(), exp.getCot21(), 
                    "Cột 9 * " + hs18 + " tháng (VD: " + luongFmt + " * " + hs18 + ")", errorDetails, redFont, redStyleCache);
                checkCol(row, 22, data.getTuNamThu21TroDiCuMoiNamCongTacHuong12ThangLuongHienHuong2(), exp.getCot22(), 
                    "Cột 9 * " + hs19 + " * (Cột 12 - " + moc19 + ") (VD: " + luongFmt + " * " + hs19 + " * (" + c12Fmt + " - " + moc19 + "))", errorDetails, redFont, redStyleCache);
            }

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

            if (!errorDetails.isEmpty()) {
                boolean c10Err = (data.getSoThangNghiHuuTruocTuoiTheoThongTu() != null && data.getSoThangNghiHuuTruocTuoiTheoThongTu() != exp.getCot10());
                boolean c11Err = (data.getSoNamNghiHuuTruocTuoiTheoThongTu() != null && !isEqual(exp.getCot11(), data.getSoNamNghiHuuTruocTuoiTheoThongTu()));
                boolean c12Err = (data.getSoNamCongTacDongBHXHTheoThongTu() != null && !isEqual(exp.getCot12(), data.getSoNamCongTacDongBHXHTheoThongTu()));
                boolean saiThoiGian = c10Err || c11Err || c12Err;

                boolean diff24C10 = (data.getSoThangNghiHuuTruocTuoiTheoThongTu() != null 
                        && Math.abs(data.getSoThangNghiHuuTruocTuoiTheoThongTu() - exp.getCot10()) == 24);
                boolean diff24C11 = (data.getSoNamNghiHuuTruocTuoiTheoThongTu() != null 
                        && Math.abs(data.getSoNamNghiHuuTruocTuoiTheoThongTu().doubleValue() - exp.getCot11().doubleValue()) == 2.0);
                boolean diff24C12 = (data.getSoNamCongTacDongBHXHTheoThongTu() != null 
                        && Math.abs(data.getSoNamCongTacDongBHXHTheoThongTu().doubleValue() - exp.getCot12().doubleValue()) == 2.0);
                boolean hasDiff24Months = diff24C10 || diff24C11 || diff24C12;

                boolean saiTongTien = !isEqual(expectedTotal, actualTotal);
                String soTienSaiText = null;
                BigDecimal thucTe = null;
                BigDecimal tinhLai = null;
                BigDecimal chenhLech = null;
                if (saiTongTien) {
                    thucTe = actualTotal;
                    tinhLai = expectedTotal;
                    chenhLech = actualTotal.subtract(expectedTotal);
                    soTienSaiText = "Thực tế: " + fmt(actualTotal) + ", Tính lại: " + fmt(expectedTotal);
                }

                String unit = (currentUnit != null && !currentUnit.isEmpty()) ? currentUnit : "BQP";

                if (!hasDiff24Months) {
                    errorRecords.add(ErrorRecordDto.builder()
                            .sheetSource("I.1")
                            .donVi(unit)
                            .hoTen(data.getHoTen())
                            .ngaySinh(data.getNgaySinh())
                            .capBac(data.getCapBac())
                            .chucVu(data.getChucVu())
                            .nhapNgu(data.getNhapNgu())
                            .thoiGianDonViSapNhapGiaiThe(data.getThoiGianDonViSapNhapGiaiThe())
                            .nghiHuuND178(true)
                            .nghiThoiViec(false)
                            .nghiHuuND177(false)
                            .saiThoiGianDuocHuong(saiThoiGian)
                            .saiTongSoTien(saiTongTien)
                            .tongTienThucTe(thucTe)
                            .tongTienTinhLai(tinhLai)
                            .tongTienChenhLech(chenhLech)
                            .noiDungSoTienSai(soTienSaiText)
                            .errorDetails(new ArrayList<>(errorDetails))
                            .build());
                }
            }

            rowNum++;
        }
    }



    private void validateSheetPLI2(Sheet sheet, FormulaEvaluator evaluator, Font redFont, Font boldRedFont, java.util.Map<Short, CellStyle> redStyleCache, List<ErrorRecordDto> errorRecords) {
        if (sheet == null) return;

        // Tìm cột cuối cùng của bảng dữ liệu để đặt cột Ghi chú ngay sau đó (sau Cột 20 - Tổng số tiền)
        int lastDataCol = 18;
        Row headerRow6 = sheet.getRow(5);
        Row headerRow9 = sheet.getRow(8);
        for (int c = 18; c <= 30; c++) {
            String text6 = headerRow6 != null ? ExcelParserUtils.getString(headerRow6, c, evaluator) : "";
            String text9 = headerRow9 != null ? ExcelParserUtils.getString(headerRow9, c, evaluator) : "";
            if ((!text6.isEmpty() || !text9.isEmpty()) && !text6.equalsIgnoreCase("Ghi chú") && !text9.equalsIgnoreCase("Ghi chú")) {
                lastDataCol = c;
            }
        }
        int noteColumnIndex = lastDataCol + 1;
        createNoteHeader(sheet, 5, noteColumnIndex, boldRedFont);
        createNoteHeader(sheet, 8, noteColumnIndex, boldRedFont);
        List<String> errorDetails = new ArrayList<>();
        String currentUnit = "";
        
        for (int r = 11; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            currentUnit = checkUnitHeader(row, evaluator, currentUnit);

            Optional<PhuLucI2> optData = parsePLI2.parse(row, r, evaluator);
            if (!optData.isPresent()) continue;

            PhuLucI2 data = optData.get();
            if (data.getHoTen() == null || data.getHoTen().trim().isEmpty()) continue;
            if (data.getHoTen().toLowerCase().replaceAll("\\s+", "").contains("cộng")) break;

            // Bỏ qua các dòng tiêu đề đơn vị cấp dưới, phân loại (không phải cá nhân)
            if (data.getCapBac() == null || data.getCapBac().trim().isEmpty() || data.getNgaySinh() == null) {
                continue;
            }

            errorDetails.clear();

            com.bqpvalidateexcel.excel.model.expected.PLI2ExpectedResult exp = 
                com.bqpvalidateexcel.excel.service.rules.PLI2Calculator.calculateExpected(data);

            String luongFmt = fmt(data.getLuongThangHienThuongTheoThongTu());
            int c10 = exp.getCot10();
            String c11Fmt = fmt(exp.getCot11());

            String c5Str = (data.getNhapNgu() != null) ? new java.text.SimpleDateFormat("MM/yyyy").format(data.getNhapNgu()) : "";
            String c8Str = (data.getThoiDiemThoiViecHuongTroCap() != null) ? new java.text.SimpleDateFormat("MM/yyyy").format(data.getThoiDiemThoiViecHuongTroCap()) : "";

            int diffMonths = 0;
            if (data.getThoiDiemThoiViecHuongTroCap() != null && data.getNhapNgu() != null) {
                diffMonths = calcThang(data.getThoiDiemThoiViecHuongTroCap(), data.getNhapNgu());
            }
            int diffY = diffMonths / 12;
            int diffM = diffMonths % 12;

            // Cột 10 = Số tháng thôi việc (khống chế tối đa 60 tháng)
            int cot10Actual = data.getSoThangThoiViecTheoThongTu() != null ? data.getSoThangThoiViecTheoThongTu() : 0;
            checkCol(row, 10, BigDecimal.valueOf(cot10Actual), BigDecimal.valueOf(exp.getCot10()), 
                "Số tháng thôi việc khống chế tối đa 60 tháng (VD: " + c10 + ")", errorDetails, redFont, redStyleCache);

            // Cột 11 = Cột 8 - Cột 5 = ...(năm) (làm tròn: <=0.5 -> +0.5, >0.5 -> +1)
            checkCol(row, 11, data.getSoNamHuongTroCapTheoThongTu(), exp.getCot11(), 
                "Cột 8 - Cột 5 (VD: " + c8Str + " - " + c5Str + " = " + diffY + " năm " + diffM + " tháng -> " + c11Fmt + " năm)", errorDetails, redFont, redStyleCache);

            // Cột 12
            checkCol(row, 12, data.getTroCap1LanChoSoThangCongTacCoDongBHXH1(), exp.getCot12(), 
                "Cột 10 * 0.8 tháng * Cột 9 (nếu > 60 tháng thì lấy 60 tháng) (VD: " + c10 + " * 0.8 * " + luongFmt + ")", errorDetails, redFont, redStyleCache);

            // Cột 13
            checkCol(row, 13, data.getTroCap1LanChoSoNamCongTacDongBHXH1(), exp.getCot13(), 
                "Cột 11 * 1.5 tháng * Cột 9 (VD: " + c11Fmt + " * 1.5 * " + luongFmt + ")", errorDetails, redFont, redStyleCache);

            // Cột 14
            checkCol(row, 14, data.getTroCapTaoViecLam1(), exp.getCot14(), 
                "3 tháng * Cột 9 (VD: 3 * " + luongFmt + ")", errorDetails, redFont, redStyleCache);

            // Cột 15
            checkCol(row, 15, data.getTroCap1LanChoSoThangCongTacCoDongBHXH2(), exp.getCot15(), 
                "Cột 10 * 0.4 tháng * Cột 9 (nếu > 60 tháng thì lấy 60 tháng) (VD: " + c10 + " * 0.4 * " + luongFmt + ")", errorDetails, redFont, redStyleCache);

            // Cột 16
            checkCol(row, 16, data.getTroCap1LanChoSoNamCongTacDongBHXH2(), exp.getCot16(), 
                "Cột 11 * 1.5 tháng * Cột 9 (VD: " + c11Fmt + " * 1.5 * " + luongFmt + ")", errorDetails, redFont, redStyleCache);

            // Cột 17
            checkCol(row, 17, data.getTroCapTaoViecLam2(), exp.getCot17(), 
                "3 tháng * Cột 9 (VD: 3 * " + luongFmt + ")", errorDetails, redFont, redStyleCache);

            // Cột 18 = SUM(12..17)
            String sumFormula = exp.isWithin12Months()
                ? "Cột 12 + Cột 13 + Cột 14 (VD: " + fmt(exp.getCot12()) + " + " + fmt(exp.getCot13()) + " + " + fmt(exp.getCot14()) + ")"
                : "Cột 15 + Cột 16 + Cột 17 (VD: " + fmt(exp.getCot15()) + " + " + fmt(exp.getCot16()) + " + " + fmt(exp.getCot17()) + ")";
            checkCol(row, 18, data.getTongCongSoTienNghiThoiViecTheoNghiDinhSo178(), exp.getCot18(), 
                sumFormula, errorDetails, redFont, redStyleCache);

            // Cột 20 = Cột 18 + Cột 19
            BigDecimal c19Val = ExcelParserUtils.getBigDecimal(row, 19, evaluator);
            BigDecimal c20Val = ExcelParserUtils.getBigDecimal(row, 20, evaluator);
            if (c20Val != null) {
                BigDecimal exp20 = exp.getCot18().add(c19Val != null ? c19Val : BigDecimal.ZERO);
                checkCol(row, 20, c20Val, exp20, 
                    "Cột 18 + Cột 19 (VD: " + fmt(exp.getCot18()) + " + " + fmt(c19Val) + ")", errorDetails, redFont, redStyleCache);
            }

            writeNotesToRow(row, errorDetails, noteColumnIndex, redFont);

            if (!errorDetails.isEmpty()) {
                boolean c10Err = (cot10Actual != exp.getCot10());
                boolean c11Err = !isEqual(exp.getCot11(), data.getSoNamHuongTroCapTheoThongTu());
                boolean saiThoiGian = c10Err || c11Err;

                boolean diff24C10 = Math.abs(cot10Actual - exp.getCot10()) == 24;
                boolean diff24C11 = (data.getSoNamHuongTroCapTheoThongTu() != null 
                        && Math.abs(data.getSoNamHuongTroCapTheoThongTu().doubleValue() - exp.getCot11().doubleValue()) == 2.0);
                boolean hasDiff24Months = diff24C10 || diff24C11;

                BigDecimal actualTotal = data.getTongCongSoTienNghiThoiViecTheoNghiDinhSo178() != null ? data.getTongCongSoTienNghiThoiViecTheoNghiDinhSo178() : BigDecimal.ZERO;
                BigDecimal expTotal = exp.getCot18();
                BigDecimal actTotal = actualTotal;
                boolean saiTongTien = !isEqual(expTotal, actTotal);
                if (c20Val != null) {
                    BigDecimal exp20 = exp.getCot18().add(c19Val != null ? c19Val : BigDecimal.ZERO);
                    if (!isEqual(exp20, c20Val)) {
                        saiTongTien = true;
                        actTotal = c20Val;
                        expTotal = exp20;
                    }
                }
                String soTienSaiText = null;
                BigDecimal thucTe = null;
                BigDecimal tinhLai = null;
                BigDecimal chenhLech = null;
                if (saiTongTien) {
                    thucTe = actTotal;
                    tinhLai = expTotal;
                    chenhLech = actTotal.subtract(expTotal);
                    soTienSaiText = "Thực tế: " + fmt(actTotal) + ", Tính lại: " + fmt(expTotal);
                }

                String unit = (currentUnit != null && !currentUnit.isEmpty()) ? currentUnit : "BQP";

                if (!hasDiff24Months) {
                    errorRecords.add(ErrorRecordDto.builder()
                            .sheetSource("I.2")
                            .donVi(unit)
                            .hoTen(data.getHoTen())
                            .ngaySinh(data.getNgaySinh())
                            .capBac(data.getCapBac())
                            .chucVu(data.getChucVu())
                            .nhapNgu(data.getNhapNgu())
                            .thoiGianDonViSapNhapGiaiThe(data.getThoiGianDonViSapNhapGiaiThe())
                            .nghiHuuND178(false)
                            .nghiThoiViec(true)
                            .nghiHuuND177(false)
                            .saiThoiGianDuocHuong(saiThoiGian)
                            .saiTongSoTien(saiTongTien)
                            .tongTienThucTe(thucTe)
                            .tongTienTinhLai(tinhLai)
                            .tongTienChenhLech(chenhLech)
                            .noiDungSoTienSai(soTienSaiText)
                            .errorDetails(new ArrayList<>(errorDetails))
                            .build());
                }
            }
        }
        sheet.autoSizeColumn(noteColumnIndex);
    }


    private void validateSheetPLI3(Sheet sheet, FormulaEvaluator evaluator, Font redFont, Font boldRedFont, java.util.Map<Short, CellStyle> redStyleCache, List<ErrorRecordDto> errorRecords) {
        if (sheet == null) return;

        // Kiểm tra xem sheet có cột 'Đơn vị' ở cột index 5 hay không
        boolean hasDonVi = false;
        Row headerRow6 = sheet.getRow(5);
        if (headerRow6 != null) {
            String h5 = ExcelParserUtils.getString(headerRow6, 5, evaluator).toLowerCase();
            if (h5.contains("đơn vị") || h5.contains("don vi")) {
                hasDonVi = true;
            }
        }
        int offset = hasDonVi ? 1 : 0;

        // Tìm cột cuối cùng của bảng dữ liệu để đặt cột Ghi chú ngay sau đó (sau Cột 15 - Tổng số tiền)
        int lastDataCol = 15 + offset;
        Row headerRow8 = sheet.getRow(7);
        for (int c = 14 + offset; c <= 25; c++) {
            String text6 = headerRow6 != null ? ExcelParserUtils.getString(headerRow6, c, evaluator) : "";
            String text8 = headerRow8 != null ? ExcelParserUtils.getString(headerRow8, c, evaluator) : "";
            if ((!text6.isEmpty() || !text8.isEmpty()) && !text6.equalsIgnoreCase("Ghi chú") && !text8.equalsIgnoreCase("Ghi chú")) {
                lastDataCol = Math.max(lastDataCol, c);
            }
        }
        int noteColumnIndex = lastDataCol + 1;
        createNoteHeader(sheet, 5, noteColumnIndex, boldRedFont);
        createNoteHeader(sheet, 7, noteColumnIndex, boldRedFont);

        List<String> errorDetails = new ArrayList<>();
        String currentUnit = "";
        
        for (int r = 10; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            currentUnit = checkUnitHeader(row, evaluator, currentUnit);
            
            Optional<PhuLucI3> optData = parsePLI3.parse(row, r, evaluator);
            if (!optData.isPresent()) continue;
            
            PhuLucI3 data = optData.get();
            if (data.getHoTen() == null || data.getHoTen().trim().isEmpty()) continue;
            if (data.getHoTen().toLowerCase().replaceAll("\\s+", "").contains("cộng")) break;
            
            // Bỏ qua các dòng tiêu đề đơn vị cấp dưới, phân loại (không phải cá nhân)
            if (data.getCapBac() == null || data.getCapBac().trim().isEmpty() || data.getNgaySinh() == null) {
                continue;
            }
            
            errorDetails.clear();
            
            com.bqpvalidateexcel.excel.model.expected.PLI3ExpectedResult exp = 
                com.bqpvalidateexcel.excel.service.rules.PLI3Calculator.calculateExpected(data);
                
            String luongFmt = fmt(data.getLuongThangHienThuongTheoHuongDan());
            String c10Fmt = fmt(exp.getCot10());
            String c11Fmt = fmt(exp.getCot11());

            boolean nghiTruoc172025 = true;
            if (data.getThoiDiemNghiHuuHuongTroCap() != null) {
                try {
                    java.util.Date threshold = new java.text.SimpleDateFormat("dd/MM/yyyy").parse("01/07/2025");
                    if (!data.getThoiDiemNghiHuuHuongTroCap().before(threshold)) {
                        nghiTruoc172025 = false;
                    }
                } catch (Exception ignored) {}
            }
            String moc14Str = nghiTruoc172025 ? "20" : "15";

            String nnStr = (data.getNhapNgu() != null) ? new java.text.SimpleDateFormat("MM/yyyy").format(data.getNhapNgu()) : "";
            String retStr = (data.getThoiDiemNghiHuuHuongTroCap() != null) ? new java.text.SimpleDateFormat("MM/yyyy").format(data.getThoiDiemNghiHuuHuongTroCap()) : "";
            String vdC10 = (!nnStr.isEmpty() && !retStr.isEmpty()) ? " (VD: Khoảng cách từ " + nnStr + " đến " + retStr + " = " + c10Fmt + " năm)" : "";

            // Cột 10 = cột 8 - cột 5, với điều kiện làm tròn < 6 tháng + 0,5, > 6 tháng + 1 (tính năm)
            checkColWithLabel(row, 10 + offset, "Cột 10", data.getSoThangThoiViecTheoHuongDan(), exp.getCot10(), 
                "Cột 8 - Cột 5 (Nghỉ hưu - Nhập ngũ)" + vdC10, errorDetails, redFont, redStyleCache);
                
            // Cột 11 = (cột 2 + trần quân hàm) - cột 8 , với điều kiện làm tròn < 6 tháng + 0,5, > 6 tháng + 1 (tính năm)
            if (data.getThoiGianDonViSapNhapGiaiThe() == null) {
                checkColWithLabel(row, 11 + offset, "Cột 11", data.getSoNamHuongTroCapTheoHuongDan(), exp.getCot11(), 
                    "(Cột 2 + Trần) - Cột 8", errorDetails, redFont, redStyleCache);
            }

            // Cột 12: 5 tháng tiền lương hiện hưởng cho 1 năm nghỉ sớm
            checkColWithLabel(row, 12 + offset, "Cột 12", data.getG5ThangTienLuongHienHuongCho1NamNghiSom(), exp.getCot12(), 
                "Cột 11 * 5 tháng * Cột 9 (VD: " + c11Fmt + " * 5 * " + luongFmt + ")", errorDetails, redFont, redStyleCache);
                
            // Cột 13: 5 tháng tiền lương hiện hưởng cho 15/20 năm đầu công tác
            checkColWithLabel(row, 13 + offset, "Cột 13", data.getG5ThangTienLuongHienHuongCho20NamDauCongTac(), exp.getCot13(), 
                "Cột 9 * 5 tháng (VD: " + luongFmt + " * 5)", errorDetails, redFont, redStyleCache);
                
            // Cột 14: Từ năm thứ 16/21 trở đi, cứ mỗi năm công tác hưởng 1/2 tháng lương hiện hưởng
            checkColWithLabel(row, 14 + offset, "Cột 14", data.getTuNamThu21TroDiCuMoiNamCongTacHuong12ThangLuongHienHuong(), exp.getCot14(), 
                "Cột 9 * 0,5 * (Cột 10 - " + moc14Str + " năm) (VD: " + luongFmt + " * 0.5 * (" + c10Fmt + " - " + moc14Str + "))", errorDetails, redFont, redStyleCache);
                
            // Cột 15: Tổng cộng số tiền
            checkColWithLabel(row, 15 + offset, "Cột 15", data.getTongCongSoTienNghiThoiViecTheoNghiDinhSo177(), exp.getCot15(), 
                "Cột 12 + Cột 13 + Cột 14 (VD: " + fmt(exp.getCot12()) + " + " + fmt(exp.getCot13()) + " + " + fmt(exp.getCot14()) + ")", errorDetails, redFont, redStyleCache);
                
            writeNotesToRow(row, errorDetails, noteColumnIndex, redFont);

            if (!errorDetails.isEmpty()) {
                boolean c10Err = (data.getSoThangThoiViecTheoHuongDan() == null || !isEqual(exp.getCot10(), data.getSoThangThoiViecTheoHuongDan()));
                boolean c11Err = (data.getThoiGianDonViSapNhapGiaiThe() == null && (data.getSoNamHuongTroCapTheoHuongDan() == null || !isEqual(exp.getCot11(), data.getSoNamHuongTroCapTheoHuongDan())));
                boolean saiThoiGian = c10Err || c11Err;

                boolean diff24C10 = (data.getSoThangThoiViecTheoHuongDan() != null 
                        && (Math.abs(data.getSoThangThoiViecTheoHuongDan().doubleValue() - exp.getCot10().doubleValue()) == 2.0 
                            || Math.abs(data.getSoThangThoiViecTheoHuongDan().doubleValue() - exp.getCot10().doubleValue()) == 24.0));
                boolean diff24C11 = (data.getSoNamHuongTroCapTheoHuongDan() != null 
                        && (Math.abs(data.getSoNamHuongTroCapTheoHuongDan().doubleValue() - exp.getCot11().doubleValue()) == 2.0
                            || Math.abs(data.getSoNamHuongTroCapTheoHuongDan().doubleValue() - exp.getCot11().doubleValue()) == 24.0));
                boolean hasDiff24Months = diff24C10 || diff24C11;

                BigDecimal actualTotal = data.getTongCongSoTienNghiThoiViecTheoNghiDinhSo177() != null ? data.getTongCongSoTienNghiThoiViecTheoNghiDinhSo177() : BigDecimal.ZERO;
                boolean saiTongTien = !isEqual(exp.getCot15(), actualTotal);
                String soTienSaiText = null;
                BigDecimal thucTe = null;
                BigDecimal tinhLai = null;
                BigDecimal chenhLech = null;
                if (saiTongTien) {
                    thucTe = actualTotal;
                    tinhLai = exp.getCot15();
                    chenhLech = actualTotal.subtract(exp.getCot15());
                    soTienSaiText = "Thực tế: " + fmt(actualTotal) + ", Tính lại: " + fmt(exp.getCot15());
                }

                String unit = currentUnit;
                if (hasDonVi) {
                    String unitColVal = ExcelParserUtils.getString(row, 5, evaluator);
                    if (unitColVal != null && !unitColVal.trim().isEmpty()) {
                        unit = unitColVal.trim();
                    }
                }
                if (unit == null || unit.isEmpty()) {
                    unit = "BQP";
                }

                if (!hasDiff24Months) {
                    errorRecords.add(ErrorRecordDto.builder()
                            .sheetSource("I.3")
                            .donVi(unit)
                            .hoTen(data.getHoTen())
                            .ngaySinh(data.getNgaySinh())
                            .capBac(data.getCapBac())
                            .chucVu(data.getChucVu())
                            .nhapNgu(data.getNhapNgu())
                            .thoiGianDonViSapNhapGiaiThe(data.getThoiGianDonViSapNhapGiaiThe())
                            .nghiHuuND178(false)
                            .nghiThoiViec(false)
                            .nghiHuuND177(true)
                            .saiThoiGianDuocHuong(saiThoiGian)
                            .saiTongSoTien(saiTongTien)
                            .tongTienThucTe(thucTe)
                            .tongTienTinhLai(tinhLai)
                            .tongTienChenhLech(chenhLech)
                            .noiDungSoTienSai(soTienSaiText)
                            .errorDetails(new ArrayList<>(errorDetails))
                            .build());
                }
            }
        }
        sheet.autoSizeColumn(noteColumnIndex);
    }


    private boolean isEqual(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) return true;
        if (a == null) a = BigDecimal.ZERO;
        if (b == null) b = BigDecimal.ZERO;
        return a.setScale(0, RoundingMode.HALF_UP).compareTo(b.setScale(0, RoundingMode.HALF_UP)) == 0;
    }

    private String checkUnitHeader(Row row, FormulaEvaluator evaluator, String currentUnit) {
        if (row == null) return currentUnit;
        String val0 = ExcelParserUtils.getString(row, 0, evaluator).trim();
        String val1 = ExcelParserUtils.getString(row, 1, evaluator).trim();

        if (val0.matches("^(?i)[IVXLCDM]+\\.?$") && !val1.isEmpty()
                && !val1.equalsIgnoreCase("SĨ QUAN") && !val1.equalsIgnoreCase("QUÂN NHÂN CHUYÊN NGHIỆP")
                && !val1.equalsIgnoreCase("SI QUAN") && !val1.equalsIgnoreCase("QUAN NHAN CHUYEN NGHIEP")
                && !val1.equalsIgnoreCase("VCQP")) {
            return val0.endsWith(".") ? (val0 + " " + val1) : (val0 + ". " + val1);
        }

        String val1Upper = val1.toUpperCase();
        if (val1Upper.startsWith("BỘ CHQS") || val1Upper.startsWith("LỮ ĐOÀN") || val1Upper.startsWith("SƯ ĐOÀN")
                || val1Upper.startsWith("BỘ THAM MƯU") || val1Upper.startsWith("CỤC ") || val1Upper.startsWith("TRƯỜNG ")
                || val1Upper.startsWith("PHÒNG ") || val1Upper.startsWith("BAN CHQS")) {
            return !val0.isEmpty() ? (val0 + ". " + val1) : val1;
        }
        return currentUnit;
    }

    private String toRoman(int num) {
        String[] romans = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X",
                "XI", "XII", "XIII", "XIV", "XV", "XVI", "XVII", "XVIII", "XIX", "XX",
                "XXI", "XXII", "XXIII", "XXIV", "XXV", "XXVI", "XXVII", "XXVIII", "XXIX", "XXX"};
        if (num >= 1 && num <= romans.length) {
            return romans[num - 1];
        }
        return String.valueOf(num);
    }

    private void createDefaultPLIIHeader(Sheet sheet, Workbook workbook) {
        Font bold14 = workbook.createFont();
        bold14.setBold(true);
        bold14.setFontHeightInPoints((short) 14);

        Font bold12 = workbook.createFont();
        bold12.setBold(true);
        bold12.setFontHeightInPoints((short) 12);

        Font boldNormal = workbook.createFont();
        boldNormal.setBold(true);

        CellStyle titleStyle = workbook.createCellStyle();
        titleStyle.setFont(bold14);
        titleStyle.setAlignment(HorizontalAlignment.CENTER);

        CellStyle subTitleStyle = workbook.createCellStyle();
        subTitleStyle.setFont(bold12);
        subTitleStyle.setAlignment(HorizontalAlignment.CENTER);

        CellStyle headerStyle = workbook.createCellStyle();
        headerStyle.setFont(boldNormal);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);
        headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        headerStyle.setBorderTop(BorderStyle.THIN);
        headerStyle.setBorderBottom(BorderStyle.THIN);
        headerStyle.setBorderLeft(BorderStyle.THIN);
        headerStyle.setBorderRight(BorderStyle.THIN);
        headerStyle.setWrapText(true);

        Row r0 = sheet.createRow(0);
        Cell c0 = r0.createCell(0);
        c0.setCellValue("Phụ lục II");
        c0.setCellStyle(titleStyle);

        Row r1 = sheet.createRow(1);
        Cell c1 = r1.createCell(0);
        c1.setCellValue("TỔNG HỢP TIÊU CHÍ XÉT ĐỐI TƯỢNG NGHỈ HƯỞNG CHẾ ĐỘ DO SẮP XẾP TỔ CHỨC BỘ MÁY TRONG BỘ QUỐC PHÒNG VÀ KẾT QUẢ RÀ SOÁT");
        c1.setCellStyle(subTitleStyle);

        Row r5 = sheet.createRow(5);
        String[] headers = {
            "TT", "Họ và tên", "Tháng, năm sinh", "Cấp bậc", "Chức vụ", "Nhập ngũ",
            "Thời gian đơn vị sáp nhập, giải thể", "Nghỉ hưu theo NĐ177", "Nghỉ hưu theo NĐ 178, NĐ 67", "Nghỉ thôi việc",
            "Thuộc đơn vị tác động trực tiếp", "Thuộc đơn vị tác động gián tiếp", "Sai thời gian được hưởng",
            "Thực tế", "Tính lại", "Chênh lệch", "Giải thích"
        };

        for (int i = 0; i < headers.length; i++) {
            Cell cell = r5.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        Row r6 = sheet.createRow(6);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = r6.createCell(i);
            cell.setCellValue(i == 0 ? "a" : String.valueOf(i));
            cell.setCellStyle(headerStyle);
        }

        sheet.setColumnWidth(0, 256 * 6);
        sheet.setColumnWidth(1, 256 * 22);
        sheet.setColumnWidth(2, 256 * 14);
        sheet.setColumnWidth(3, 256 * 18);
        sheet.setColumnWidth(4, 256 * 25);
        sheet.setColumnWidth(5, 256 * 12);
        sheet.setColumnWidth(6, 256 * 16);
        sheet.setColumnWidth(7, 256 * 12);
        sheet.setColumnWidth(8, 256 * 14);
        sheet.setColumnWidth(9, 256 * 12);
        sheet.setColumnWidth(10, 256 * 14);
        sheet.setColumnWidth(11, 256 * 14);
        sheet.setColumnWidth(12, 256 * 14);
        sheet.setColumnWidth(13, 256 * 18);
        sheet.setColumnWidth(14, 256 * 18);
        sheet.setColumnWidth(15, 256 * 18);
        sheet.setColumnWidth(16, 256 * 35);
    }

    private void writeErrorsToPLII(Workbook workbook, List<ErrorRecordDto> errorRecords, FormulaEvaluator evaluator) {
        if (errorRecords == null || errorRecords.isEmpty()) return;

        // 1. Tìm hoặc tạo sheet Phụ lục II
        Sheet sheetPLII = null;
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            String sName = workbook.getSheetName(i);
            String clean = cleanSheetName(sName);
            if (clean.equals("phulucii") || clean.equals("plii")) {
                sheetPLII = workbook.getSheetAt(i);
                break;
            }
        }
        if (sheetPLII == null) {
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                String sName = workbook.getSheetName(i);
                String clean = cleanSheetName(sName);
                if ((clean.contains("phulucii") || clean.contains("plii") || clean.endsWith("ii")) && !clean.contains("(2)")) {
                    sheetPLII = workbook.getSheetAt(i);
                    break;
                }
            }
        }
        if (sheetPLII == null) {
            sheetPLII = workbook.getSheet("Phụ lục II");
        }
        if (sheetPLII == null) {
            try {
                sheetPLII = workbook.createSheet("Phụ lục II");
                createDefaultPLIIHeader(sheetPLII, workbook);
            } catch (IllegalArgumentException e) {
                sheetPLII = workbook.getSheetAt(0); // Fallback if name conflict
            }
        }

        // 2. Định vị chỉ số các cột trong sheet Phụ lục II
        int colSTT = 0;
        int colHoTen = 1;
        int colNgaySinh = 2;
        int colCapBac = 3;
        int colChucVu = 4;
        int colNhapNgu = 5;
        int colSapNhap = 6;
        int colND177 = 7;
        int colND178 = 8;
        int colThoiViec = 9;
        int colSaiThoiGian = 12;
        int colThucTe = -1;
        int colTinhLai = -1;
        int colChenhLech = -1;
        int colGiaiThich = -1;

        int maxColFound = 12;
        int headerRowNum = 6;

        for (int r = 0; r <= Math.min(10, sheetPLII.getLastRowNum()); r++) {
            Row row = sheetPLII.getRow(r);
            if (row == null) continue;
            for (int c = 0; c < Math.max(30, (int) row.getLastCellNum()); c++) {
                String val = ExcelParserUtils.getString(row, c, evaluator);
                if (val == null || val.trim().isEmpty()) continue;
                maxColFound = Math.max(maxColFound, c);

                String lower = val.toLowerCase().replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
                if (lower.equals("tt") || lower.equals("stt") || lower.equals("số tt") || lower.equals("so tt")) {
                    colSTT = c;
                    headerRowNum = r;
                } else if (lower.contains("họ và tên") || lower.contains("họ tên") || lower.contains("ho va ten")) {
                    colHoTen = c;
                    headerRowNum = r;
                } else if (lower.contains("năm sinh") || lower.contains("ngày sinh") || lower.contains("tháng, năm sinh")) {
                    colNgaySinh = c;
                } else if (lower.contains("cấp bậc") || lower.contains("cap bac")) {
                    colCapBac = c;
                } else if (lower.contains("chức vụ") || lower.contains("chuc vu")) {
                    colChucVu = c;
                } else if (lower.contains("nhập ngũ") || lower.contains("nhap ngu")) {
                    colNhapNgu = c;
                } else if (lower.contains("sáp nhập") || lower.contains("giải thể") || lower.contains("sap nhap")) {
                    colSapNhap = c;
                } else if (lower.contains("177")) {
                    colND177 = c;
                } else if (lower.contains("178")) {
                    colND178 = c;
                } else if (lower.contains("thôi việc") || lower.contains("thoi viec")) {
                    colThoiViec = c;
                } else if (lower.contains("sai thời gian") || lower.contains("sai thoi gian")) {
                    colSaiThoiGian = c;
                } else if (lower.contains("thực tế") || lower.contains("thuc te")) {
                    colThucTe = c;
                } else if (lower.contains("tính lại") || lower.contains("tinh lai")) {
                    colTinhLai = c;
                } else if (lower.contains("chênh lệch") || lower.contains("chenh lech")) {
                    colChenhLech = c;
                } else if (lower.contains("giải thích") || lower.contains("giai thich")) {
                    colGiaiThich = c;
                }
            }
        }

        // Tạo font và style cho header nếu cần thêm cột mới
        Font boldHeaderFont = workbook.createFont();
        boldHeaderFont.setBold(true);
        CellStyle headerStyle = workbook.createCellStyle();
        headerStyle.setFont(boldHeaderFont);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);
        headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        headerStyle.setBorderTop(BorderStyle.THIN);
        headerStyle.setBorderBottom(BorderStyle.THIN);
        headerStyle.setBorderLeft(BorderStyle.THIN);
        headerStyle.setBorderRight(BorderStyle.THIN);
        headerStyle.setWrapText(true);

        // Xác định hàng cuối của khối tiêu đề (nếu header gộp nhiều hàng)
        int headerEndRow = headerRowNum;
        for (int i = 0; i < sheetPLII.getNumMergedRegions(); i++) {
            org.apache.poi.ss.util.CellRangeAddress range = sheetPLII.getMergedRegion(i);
            if (range.getFirstRow() == headerRowNum && range.getFirstColumn() <= colSTT + 5) {
                headerEndRow = Math.max(headerEndRow, range.getLastRow());
            }
        }

        // Tự chèn thêm cột Thực tế nếu thiếu
        if (colThucTe == -1) {
            colThucTe = maxColFound + 1;
            maxColFound = colThucTe;
            Row hRow = sheetPLII.getRow(headerRowNum);
            if (hRow == null) hRow = sheetPLII.createRow(headerRowNum);
            Cell c = hRow.createCell(colThucTe);
            c.setCellValue("Thực tế");
            c.setCellStyle(headerStyle);
            sheetPLII.setColumnWidth(colThucTe, 256 * 18);

            if (headerEndRow > headerRowNum) {
                for (int r = headerRowNum + 1; r <= headerEndRow; r++) {
                    Row rNext = sheetPLII.getRow(r);
                    if (rNext == null) rNext = sheetPLII.createRow(r);
                    Cell emptyCell = rNext.createCell(colThucTe);
                    emptyCell.setCellStyle(headerStyle);
                }
                sheetPLII.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(headerRowNum, headerEndRow, colThucTe, colThucTe));
            }
            if (headerEndRow + 1 <= sheetPLII.getLastRowNum()) {
                Row numRow = sheetPLII.getRow(headerEndRow + 1);
                if (numRow != null && "a".equalsIgnoreCase(ExcelParserUtils.getString(numRow, colSTT, evaluator))) {
                    Cell numCell = numRow.createCell(colThucTe);
                    numCell.setCellValue(colThucTe + 1);
                    numCell.setCellStyle(headerStyle);
                }
            }
        }

        // Tự chèn thêm cột Tính lại nếu thiếu
        if (colTinhLai == -1) {
            colTinhLai = maxColFound + 1;
            maxColFound = colTinhLai;
            Row hRow = sheetPLII.getRow(headerRowNum);
            if (hRow == null) hRow = sheetPLII.createRow(headerRowNum);
            Cell c = hRow.createCell(colTinhLai);
            c.setCellValue("Tính lại");
            c.setCellStyle(headerStyle);
            sheetPLII.setColumnWidth(colTinhLai, 256 * 18);

            if (headerEndRow > headerRowNum) {
                for (int r = headerRowNum + 1; r <= headerEndRow; r++) {
                    Row rNext = sheetPLII.getRow(r);
                    if (rNext == null) rNext = sheetPLII.createRow(r);
                    Cell emptyCell = rNext.createCell(colTinhLai);
                    emptyCell.setCellStyle(headerStyle);
                }
                sheetPLII.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(headerRowNum, headerEndRow, colTinhLai, colTinhLai));
            }
            if (headerEndRow + 1 <= sheetPLII.getLastRowNum()) {
                Row numRow = sheetPLII.getRow(headerEndRow + 1);
                if (numRow != null && "a".equalsIgnoreCase(ExcelParserUtils.getString(numRow, colSTT, evaluator))) {
                    Cell numCell = numRow.createCell(colTinhLai);
                    numCell.setCellValue(colTinhLai + 1);
                    numCell.setCellStyle(headerStyle);
                }
            }
        }

        // Tự chèn thêm cột Chênh lệch nếu thiếu:
        // "Thêm một cột 'Chênh lệch' vào bên cạnh cột 'Tính lại' là sự chênh lệch của cột 'Thực tế' và 'Tính lại'."
        if (colChenhLech == -1) {
            if (colTinhLai != -1 && colGiaiThich == colTinhLai + 1) {
                // Di chuyển cột Giải thích sang phải 1 cột để nhường chỗ cho Chênh lệch ngay sau Tính lại
                int oldGiaiThich = colGiaiThich;
                colChenhLech = oldGiaiThich;
                colGiaiThich = oldGiaiThich + 1;
                maxColFound = Math.max(maxColFound, colGiaiThich);

                sheetPLII.setColumnWidth(colGiaiThich, sheetPLII.getColumnWidth(oldGiaiThich));
                for (int r = 0; r <= sheetPLII.getLastRowNum(); r++) {
                    Row rObj = sheetPLII.getRow(r);
                    if (rObj != null) {
                        Cell oldC = rObj.getCell(oldGiaiThich);
                        if (oldC != null) {
                            Cell newC = rObj.createCell(colGiaiThich);
                            copyCellValueAndStyle(oldC, newC);
                            rObj.removeCell(oldC);
                        }
                    }
                }
                for (int i = sheetPLII.getNumMergedRegions() - 1; i >= 0; i--) {
                    org.apache.poi.ss.util.CellRangeAddress range = sheetPLII.getMergedRegion(i);
                    if (range.getFirstColumn() == oldGiaiThich && range.getLastColumn() == oldGiaiThich) {
                        sheetPLII.removeMergedRegion(i);
                        sheetPLII.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(
                            range.getFirstRow(), range.getLastRow(), colGiaiThich, colGiaiThich));
                    }
                }
            } else {
                colChenhLech = maxColFound + 1;
                maxColFound = colChenhLech;
            }

            Row hRow = sheetPLII.getRow(headerRowNum);
            if (hRow == null) hRow = sheetPLII.createRow(headerRowNum);
            Cell c = hRow.createCell(colChenhLech);
            c.setCellValue("Chênh lệch");
            c.setCellStyle(headerStyle);
            sheetPLII.setColumnWidth(colChenhLech, 256 * 18);

            if (headerEndRow > headerRowNum) {
                for (int r = headerRowNum + 1; r <= headerEndRow; r++) {
                    Row rNext = sheetPLII.getRow(r);
                    if (rNext == null) rNext = sheetPLII.createRow(r);
                    Cell emptyCell = rNext.createCell(colChenhLech);
                    emptyCell.setCellStyle(headerStyle);
                }
                sheetPLII.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(headerRowNum, headerEndRow, colChenhLech, colChenhLech));
            }
            if (headerEndRow + 1 <= sheetPLII.getLastRowNum()) {
                Row numRow = sheetPLII.getRow(headerEndRow + 1);
                if (numRow != null && "a".equalsIgnoreCase(ExcelParserUtils.getString(numRow, colSTT, evaluator))) {
                    Cell numCell = numRow.createCell(colChenhLech);
                    numCell.setCellValue(colChenhLech + 1);
                    numCell.setCellStyle(headerStyle);
                }
            }
        }

        // Nếu chưa có cột Giải thích -> tự tạo thêm
        if (colGiaiThich == -1) {
            colGiaiThich = maxColFound + 1;
            maxColFound = colGiaiThich;
            Row hRow = sheetPLII.getRow(headerRowNum);
            if (hRow == null) hRow = sheetPLII.createRow(headerRowNum);
            Cell c = hRow.createCell(colGiaiThich);
            c.setCellValue("Giải thích");
            c.setCellStyle(headerStyle);
            sheetPLII.setColumnWidth(colGiaiThich, 256 * 35);

            if (headerEndRow > headerRowNum) {
                for (int r = headerRowNum + 1; r <= headerEndRow; r++) {
                    Row rNext = sheetPLII.getRow(r);
                    if (rNext == null) rNext = sheetPLII.createRow(r);
                    Cell emptyCell = rNext.createCell(colGiaiThich);
                    emptyCell.setCellStyle(headerStyle);
                }
                sheetPLII.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(headerRowNum, headerEndRow, colGiaiThich, colGiaiThich));
            }
            if (headerEndRow + 1 <= sheetPLII.getLastRowNum()) {
                Row numRow = sheetPLII.getRow(headerEndRow + 1);
                if (numRow != null && "a".equalsIgnoreCase(ExcelParserUtils.getString(numRow, colSTT, evaluator))) {
                    Cell numCell = numRow.createCell(colGiaiThich);
                    numCell.setCellValue(colGiaiThich + 1);
                    numCell.setCellStyle(headerStyle);
                }
            }
        }

        // 3. Gom nhóm bản ghi lỗi theo đơn vị
        java.util.Map<String, List<ErrorRecordDto>> groupedByUnit = new java.util.LinkedHashMap<>();
        for (ErrorRecordDto err : errorRecords) {
            String unit = err.getDonVi();
            if (unit == null || unit.trim().isEmpty()) {
                unit = "Cơ quan, đơn vị khác";
            }
            groupedByUnit.computeIfAbsent(unit, k -> new ArrayList<>()).add(err);
        }

        // 4. Xác định vị trí chèn: sau bản ghi cuối cùng của sheet
        int footerStartRow = -1;
        for (int r = 0; r <= sheetPLII.getLastRowNum(); r++) {
            Row row = sheetPLII.getRow(r);
            if (row != null) {
                String c0 = ExcelParserUtils.getString(row, 0, evaluator).toLowerCase().trim();
                String c1 = ExcelParserUtils.getString(row, 1, evaluator).toLowerCase().trim();
                if (c0.contains("cộng") || c1.contains("cộng") || c1.contains("ghi chú") || c0.contains("ghi chú")
                        || c0.contains("cong") || c1.contains("cong") || c1.contains("ghi chu") || c0.contains("ghi chu")) {
                    footerStartRow = r;
                    break;
                }
            }
        }

        int totalRowsToInsert = groupedByUnit.size() + errorRecords.size();
        int insertRowIdx;
        if (footerStartRow != -1) {
            sheetPLII.shiftRows(footerStartRow, sheetPLII.getLastRowNum(), totalRowsToInsert, true, false);
            insertRowIdx = footerStartRow;
        } else {
            insertRowIdx = Math.max(sheetPLII.getLastRowNum() + 1, headerRowNum + 2);
        }

        // 5. Tạo các kiểu dáng ô (styles)
        CellStyle textStyle = workbook.createCellStyle();
        textStyle.setBorderTop(BorderStyle.THIN);
        textStyle.setBorderBottom(BorderStyle.THIN);
        textStyle.setBorderLeft(BorderStyle.THIN);
        textStyle.setBorderRight(BorderStyle.THIN);
        textStyle.setVerticalAlignment(VerticalAlignment.CENTER);

        CellStyle centerStyle = workbook.createCellStyle();
        centerStyle.cloneStyleFrom(textStyle);
        centerStyle.setAlignment(HorizontalAlignment.CENTER);

        CellStyle moneyStyle = workbook.createCellStyle();
        moneyStyle.cloneStyleFrom(textStyle);
        moneyStyle.setAlignment(HorizontalAlignment.RIGHT);
        DataFormat dataFormat = workbook.createDataFormat();
        moneyStyle.setDataFormat(dataFormat.getFormat("#,##0"));

        Font boldFont = workbook.createFont();
        boldFont.setBold(true);

        CellStyle unitStyle = workbook.createCellStyle();
        unitStyle.cloneStyleFrom(textStyle);
        unitStyle.setFont(boldFont);

        CellStyle unitCenterStyle = workbook.createCellStyle();
        unitCenterStyle.cloneStyleFrom(centerStyle);
        unitCenterStyle.setFont(boldFont);

        // 6. Ghi từng đơn vị và bản ghi lỗi
        int unitCounter = 1;
        for (java.util.Map.Entry<String, List<ErrorRecordDto>> entry : groupedByUnit.entrySet()) {
            String unitTitle = entry.getKey();
            String roman = "";
            String unitName = unitTitle;

            if (unitTitle.matches("^(?i)[IVXLCDM]+\\..*")) {
                int dotIdx = unitTitle.indexOf('.');
                roman = unitTitle.substring(0, dotIdx).trim();
                unitName = unitTitle.substring(dotIdx + 1).trim();
            } else {
                roman = toRoman(unitCounter++);
            }

            // Tạo dòng tiêu đề đơn vị
            Row unitRow = sheetPLII.createRow(insertRowIdx++);
            Cell uCell0 = unitRow.createCell(colSTT);
            uCell0.setCellValue(roman);
            uCell0.setCellStyle(unitCenterStyle);

            Cell uCell1 = unitRow.createCell(colHoTen);
            uCell1.setCellValue(unitName);
            uCell1.setCellStyle(unitStyle);

            for (int c = 2; c <= maxColFound; c++) {
                Cell blankCell = unitRow.createCell(c);
                blankCell.setCellStyle(unitStyle);
            }

            // Ghi danh sách bản ghi lỗi của đơn vị
            int sttInUnit = 1;
            for (ErrorRecordDto err : entry.getValue()) {
                Row row = sheetPLII.createRow(insertRowIdx++);

                // Khởi tạo tất cả các ô trong hàng với centerStyle và border
                for (int c = 0; c <= maxColFound; c++) {
                    Cell blankCell = row.createCell(c);
                    blankCell.setCellStyle(centerStyle);
                }

                // TT
                Cell cSTT = row.getCell(colSTT);
                cSTT.setCellValue(sttInUnit++);
                cSTT.setCellStyle(centerStyle);

                // Họ và tên
                Cell cHoTen = row.getCell(colHoTen);
                cHoTen.setCellValue(err.getHoTen() != null ? err.getHoTen() : "");
                cHoTen.setCellStyle(textStyle);

                // Tháng, năm sinh
                Cell cNgaySinh = row.getCell(colNgaySinh);
                if (err.getNgaySinh() != null) {
                    cNgaySinh.setCellValue(new SimpleDateFormat("MM/yyyy").format(err.getNgaySinh()));
                }
                cNgaySinh.setCellStyle(centerStyle);

                // Cấp bậc
                Cell cCapBac = row.getCell(colCapBac);
                cCapBac.setCellValue(err.getCapBac() != null ? err.getCapBac() : "");
                cCapBac.setCellStyle(textStyle);

                // Chức vụ
                Cell cChucVu = row.getCell(colChucVu);
                cChucVu.setCellValue(err.getChucVu() != null ? err.getChucVu() : "");
                cChucVu.setCellStyle(textStyle);

                // Nhập ngũ
                Cell cNhapNgu = row.getCell(colNhapNgu);
                if (err.getNhapNgu() != null) {
                    cNhapNgu.setCellValue(new SimpleDateFormat("MM/yyyy").format(err.getNhapNgu()));
                }
                cNhapNgu.setCellStyle(centerStyle);

                // Thời gian đơn vị sáp nhập, giải thể
                Cell cSapNhap = row.getCell(colSapNhap);
                if (err.getThoiGianDonViSapNhapGiaiThe() != null) {
                    cSapNhap.setCellValue(new SimpleDateFormat("MM/yyyy").format(err.getThoiGianDonViSapNhapGiaiThe()));
                }
                cSapNhap.setCellStyle(centerStyle);

                // Nghỉ hưu theo NĐ177
                if (colND177 >= 0) {
                    Cell cND177 = row.getCell(colND177);
                    if (err.isNghiHuuND177()) cND177.setCellValue("x");
                    cND177.setCellStyle(centerStyle);
                }

                // Nghỉ hưu theo NĐ 178
                if (colND178 >= 0) {
                    Cell cND178 = row.getCell(colND178);
                    if (err.isNghiHuuND178()) cND178.setCellValue("x");
                    cND178.setCellStyle(centerStyle);
                }

                // Nghỉ thôi việc
                if (colThoiViec >= 0) {
                    Cell cThoiViec = row.getCell(colThoiViec);
                    if (err.isNghiThoiViec()) cThoiViec.setCellValue("x");
                    cThoiViec.setCellStyle(centerStyle);
                }

                // Sai thời gian được hưởng
                if (colSaiThoiGian >= 0) {
                    Cell cSaiThoiGian = row.getCell(colSaiThoiGian);
                    if (err.isSaiThoiGianDuocHuong()) cSaiThoiGian.setCellValue("x");
                    cSaiThoiGian.setCellStyle(centerStyle);
                }

                // Cột Thực tế
                if (colThucTe >= 0) {
                    Cell cThucTe = row.getCell(colThucTe);
                    if (err.isSaiTongSoTien() && err.getTongTienThucTe() != null) {
                        cThucTe.setCellValue(err.getTongTienThucTe().doubleValue());
                    }
                    cThucTe.setCellStyle(moneyStyle);
                }

                // Cột Tính lại
                if (colTinhLai >= 0) {
                    Cell cTinhLai = row.getCell(colTinhLai);
                    if (cTinhLai == null) cTinhLai = row.createCell(colTinhLai);
                    if (err.isSaiTongSoTien() && err.getTongTienTinhLai() != null) {
                        cTinhLai.setCellValue(err.getTongTienTinhLai().doubleValue());
                    }
                    cTinhLai.setCellStyle(moneyStyle);
                }

                // Cột Chênh lệch: "là sự chênh lệch của cột 'Thực tế' và 'Tính lại'"
                if (colChenhLech >= 0) {
                    Cell cChenhLech = row.getCell(colChenhLech);
                    if (cChenhLech == null) cChenhLech = row.createCell(colChenhLech);
                    if (err.isSaiTongSoTien() && err.getChenhLech() != null) {
                        cChenhLech.setCellValue(err.getChenhLech().doubleValue());
                    }
                    cChenhLech.setCellStyle(moneyStyle);
                }

                // Cột Giải thích (không chèn dữ liệu vào cột giải thích theo yêu cầu của người dùng)
                if (colGiaiThich >= 0) {
                    Cell cGiaiThich = row.getCell(colGiaiThich);
                    if (cGiaiThich == null) cGiaiThich = row.createCell(colGiaiThich);
                    cGiaiThich.setCellStyle(textStyle);
                }
            }
        }
    }

    private void copyCellValueAndStyle(Cell src, Cell dest) {
        dest.setCellStyle(src.getCellStyle());
        switch (src.getCellType()) {
            case STRING:
                dest.setCellValue(src.getStringCellValue());
                break;
            case NUMERIC:
                dest.setCellValue(src.getNumericCellValue());
                break;
            case BOOLEAN:
                dest.setCellValue(src.getBooleanCellValue());
                break;
            case FORMULA:
                dest.setCellFormula(src.getCellFormula());
                break;
            default:
                break;
        }
    }

}
