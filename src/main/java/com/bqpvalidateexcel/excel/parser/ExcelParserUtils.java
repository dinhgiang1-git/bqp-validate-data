package com.bqpvalidateexcel.excel.parser;

import com.bqpvalidateexcel.excel.model.dto.*;
import com.bqpvalidateexcel.excel.model.expected.*;
import com.bqpvalidateexcel.excel.parser.*;
import com.bqpvalidateexcel.excel.service.rules.*;
import com.bqpvalidateexcel.excel.service.*;


import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.DataFormatter;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ExcelParserUtils {
    private static final DataFormatter dataFormatter = new DataFormatter();

    public static String cleanString(String val) {
        if (val == null) return "";
        // Thay thế các loại khoảng trắng đặc biệt (Non-breaking space \u00A0, \u2007, \u202F, zero-width space \u200B, \uFEFF)
        String cleaned = val.replace('\u00A0', ' ')
                            .replace('\u2007', ' ')
                            .replace('\u202F', ' ')
                            .replace('\u200B', ' ')
                            .replace('\uFEFF', ' ');
        // Xóa khoảng trắng thừa ở đầu/cuối và gộp các khoảng trắng liên tiếp bên trong thành 1 khoảng trắng đơn
        return cleaned.trim().replaceAll("\\s+", " ");
    }

    public static String getString(Row row, int col, FormulaEvaluator formulaEvaluator) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return "";
        try {
            return cleanString(dataFormatter.formatCellValue(cell, formulaEvaluator));
        } catch (Exception e) {
            if (cell.getCellType() == CellType.FORMULA) {
                try {
                    CellType cachedType = cell.getCachedFormulaResultType();
                    if (cachedType == CellType.STRING) {
                        return cleanString(cell.getStringCellValue());
                    } else if (cachedType == CellType.NUMERIC) {
                        return cleanString(dataFormatter.formatCellValue(cell));
                    } else if (cachedType == CellType.BOOLEAN) {
                        return String.valueOf(cell.getBooleanCellValue());
                    }
                } catch (Exception ex) {
                    // ignore
                }
            }
            return "";
        }
    }

    public static BigDecimal getBigDecimal(Row row, int col, FormulaEvaluator formulaEvaluator) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return null;

        if (cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue());
        }
        
        if (cell.getCellType() == CellType.FORMULA) {
            try {
                if (cell.getCachedFormulaResultType() == CellType.NUMERIC) {
                    return BigDecimal.valueOf(cell.getNumericCellValue());
                }
            } catch (Exception e) {}
        }

        String val = getString(row, col, formulaEvaluator);
        if (val == null || val.trim().isEmpty() || val.trim().equals("-")) return null;
        
        val = val.trim().replaceAll("\\s+", "");

        // 1. Kiểm tra định dạng YY-MM (ví dụ "34-06", "05-05", "02-04", "30-09", "03-11")
        java.util.regex.Matcher ym = java.util.regex.Pattern.compile("^(\\d+)[-–](\\d+)$").matcher(val);
        if (ym.find()) {
            try {
                int yy = Integer.parseInt(ym.group(1));
                int mm = Integer.parseInt(ym.group(2));
                if (mm == 0) return BigDecimal.valueOf(yy);
                if (mm <= 6) return BigDecimal.valueOf(yy).add(new BigDecimal("0.5"));
                return BigDecimal.valueOf(yy).add(BigDecimal.ONE);
            } catch (Exception ignored) {}
        }

        // 2. Kiểm tra định dạng có chữ "năm", "tháng" (ví dụ "2 năm 2 tháng", "26 tháng", "36 năm")
        String valLower = val.toLowerCase();
        if (valLower.contains("năm") || valLower.contains("tháng") || valLower.contains("nam") || valLower.contains("thang")) {
            java.util.regex.Matcher nm = java.util.regex.Pattern.compile("(?:(\\d+)(?:năm|nam))?(?:(\\d+)(?:tháng|thang))?").matcher(valLower);
            if (nm.find() && (nm.group(1) != null || nm.group(2) != null)) {
                try {
                    int yy = nm.group(1) != null ? Integer.parseInt(nm.group(1)) : 0;
                    int mm = nm.group(2) != null ? Integer.parseInt(nm.group(2)) : 0;
                    if (yy > 0 && mm > 0) {
                        if (mm <= 6) return BigDecimal.valueOf(yy).add(new BigDecimal("0.5"));
                        return BigDecimal.valueOf(yy).add(BigDecimal.ONE);
                    } else if (yy > 0) {
                        return BigDecimal.valueOf(yy);
                    } else if (mm > 0) {
                        return BigDecimal.valueOf(mm);
                    }
                } catch (Exception ignored) {}
            }
        }
        
        int lastComma = val.lastIndexOf(',');
        int lastDot = val.lastIndexOf('.');
        int lastPunctuation = Math.max(lastComma, lastDot);
        
        if (lastPunctuation != -1) {
            String after = val.substring(lastPunctuation + 1);
            if (after.length() != 3) {
                val = val.replaceAll("[,\\.]", "");
                val = val.substring(0, val.length() - after.length()) + "." + after;
            } else {
                val = val.replaceAll("[,\\.]", "");
            }
        }
        
        try {
            return new BigDecimal(val);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Integer getInteger(Row row, int col, FormulaEvaluator formulaEvaluator) {
        BigDecimal bd = getBigDecimal(row, col, formulaEvaluator);
        if (bd == null) return null;
        return bd.intValue();
    }

    public static Date getDate(Row row, int col, FormulaEvaluator formulaEvaluator) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return null;

        Date resultDate = null;

        if (cell.getCellType() == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(cell)) {
                resultDate = cell.getDateCellValue();
            }
        }

        if (resultDate == null) {
            String val = getString(row, col, formulaEvaluator);
            if (val == null || val.trim().isEmpty()) return null;
            val = val.trim();

            // Nếu có nhiều mốc ngày phân tách bằng dấu chấm phẩy (ví dụ: "12/88; 7/96") -> lấy mốc đầu tiên
            if (val.contains(";")) {
                val = val.split(";")[0].trim();
            }

            // Xử lý các chuỗi có chữ "Thg" ví dụ "Thg7-25" -> "7-25"
            if (val.toLowerCase().startsWith("thg")) {
                val = val.replaceAll("(?i)thg\\s*", "");
            }

            // Xử lý gõ nhầm chữ O/o thay vì số 0 (ví dụ "O2/1974" -> "02/1974")
            val = val.replaceAll("^[oO](\\d)", "0$1").replaceAll("[/-][oO](\\d)", "/0$1");

            // Xóa khoảng trắng thừa quanh dấu gạch chéo hoặc gạch ngang
            val = val.replaceAll("\\s*/\\s*", "/").replaceAll("\\s*-\\s*", "-").replaceAll("\\s+", "");

            // Chuẩn hóa năm 2 chữ số dạng dd/MM/yy hoặc MM/yy (ví dụ: "02/69" -> "02/1969", "3/25" -> "3/2025", "8/86" -> "8/1986")
            java.util.regex.Matcher m3 = java.util.regex.Pattern.compile("^(\\d{1,2})([/-])(\\d{1,2})[/-](\\d{2})$").matcher(val);
            if (m3.find()) {
                int yy = Integer.parseInt(m3.group(4));
                int fullY = (yy <= 45) ? (2000 + yy) : (1900 + yy);
                val = m3.group(1) + m3.group(2) + m3.group(3) + m3.group(2) + fullY;
            } else {
                java.util.regex.Matcher m2 = java.util.regex.Pattern.compile("^(\\d{1,2})([/-])(\\d{2})$").matcher(val);
                if (m2.find()) {
                    int yy = Integer.parseInt(m2.group(3));
                    int fullY = (yy <= 45) ? (2000 + yy) : (1900 + yy);
                    val = m2.group(1) + m2.group(2) + fullY;
                }
            }

            String[] formats = {
                    "dd/MM/yyyy", "d/M/yyyy", "dd/M/yyyy", "d/MM/yyyy",
                    "MM/yyyy", "M/yyyy",
                    "yyyy",
                    "dd-MM-yyyy", "d-M-yyyy", "dd-M-yyyy", "d-MM-yyyy",
                    "MM-yyyy", "M-yyyy"
            };

            for (String format : formats) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat(format);
                    sdf.setLenient(false);
                    resultDate = sdf.parse(val);
                    break;
                } catch (Exception e) {
                    // ignore and try next
                }
            }
        }

        if (resultDate != null) {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTime(resultDate);
            int y = cal.get(java.util.Calendar.YEAR);
            if (y < 100) {
                // Xử lý trường hợp lọt năm 2 chữ số (ví dụ 0025 AD, 0069 AD)
                cal.set(java.util.Calendar.YEAR, (y <= 45) ? (2000 + y) : (1900 + y));
                resultDate = cal.getTime();
            } else if (y > 2045) {
                // Xử lý lỗi Excel tự động chuyển năm sinh 69 thành 2069
                cal.set(java.util.Calendar.YEAR, y - 100);
                resultDate = cal.getTime();
            }
        }

        return resultDate;
    }

    public static java.util.Map<Integer, Integer> findColMap(org.apache.poi.ss.usermodel.Sheet sheet, FormulaEvaluator evaluator) {
        java.util.Map<Integer, Integer> colMap = new java.util.HashMap<>();
        if (sheet == null) return colMap;
        for (int r = 0; r <= Math.min(15, sheet.getLastRowNum()); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            java.util.Map<Integer, Integer> rowMap = new java.util.HashMap<>();
            for (int c = 0; c < row.getLastCellNum(); c++) {
                String val = getString(row, c, evaluator);
                if (val != null) {
                    val = val.trim();
                    if (val.contains("/") || val.matches("^\\d+[-–]\\d+$")) {
                        continue;
                    }
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("^[\\[\\(]?(\\d{1,2})").matcher(val);
                    if (m.find()) {
                        try {
                            int num = Integer.parseInt(m.group(1));
                            if (num >= 1 && num <= 35 && !rowMap.containsKey(num)) {
                                rowMap.put(num, c);
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
            if (rowMap.size() >= 7 && rowMap.containsKey(1) && rowMap.containsKey(2) && rowMap.containsKey(3)
                    && rowMap.containsKey(4) && rowMap.containsKey(5)
                    && rowMap.get(1) < rowMap.get(2) && rowMap.get(2) < rowMap.get(3)
                    && rowMap.get(3) < rowMap.get(4) && rowMap.get(4) < rowMap.get(5)) {
                // Nhận diện xem cột Lương (Cột 9 chuẩn) nằm ở số nào trên dòng đánh số (9 hay 10)
                int colLuongNum = -1;
                for (java.util.Map.Entry<Integer, Integer> entry : rowMap.entrySet()) {
                    int num = entry.getKey();
                    int cIdx = entry.getValue();
                    for (int pr = Math.max(0, r - 5); pr < r; pr++) {
                        Row prevRow = sheet.getRow(pr);
                        if (prevRow != null) {
                            String hText = getString(prevRow, cIdx, evaluator).toLowerCase().replaceAll("\\s+", " ").trim();
                            if (hText.contains("lương tháng") || hText.contains("luong thang") || hText.contains("tiền lương")) {
                                colLuongNum = num;
                                break;
                            }
                        }
                    }
                    if (colLuongNum != -1) break;
                }

                // Nhận diện cột Họ và tên nằm ở số nào (1 hay 2)
                int colHoTenNum = -1;
                for (java.util.Map.Entry<Integer, Integer> entry : rowMap.entrySet()) {
                    int num = entry.getKey();
                    int cIdx = entry.getValue();
                    for (int pr = Math.max(0, r - 5); pr < r; pr++) {
                        Row prevRow = sheet.getRow(pr);
                        if (prevRow != null) {
                            String hText = getString(prevRow, cIdx, evaluator).toLowerCase().replaceAll("\\s+", " ").trim();
                            if (hText.contains("họ và tên") || hText.contains("ho va ten") || hText.contains("họ tên")) {
                                colHoTenNum = num;
                                break;
                            }
                        }
                    }
                    if (colHoTenNum != -1) break;
                }

                if (colLuongNum == 10 && colHoTenNum == 2) {
                    // Mẫu Binh chủng (Số TT là 1, Họ tên là 2, Lương là 10, các cột tính toán dịch +1)
                    colMap.put(0, rowMap.get(1)); // STT
                    for (int k = 1; k <= 35; k++) {
                        if (rowMap.containsKey(k + 1)) {
                            colMap.put(k, rowMap.get(k + 1));
                        }
                    }
                } else if (colHoTenNum == 2 && colLuongNum == 9) {
                    // Mẫu gộp Cấp bậc + Chức vụ (như Result_14 Phụ lục I.2): Họ tên là 2, Lương vẫn là 9
                    colMap.put(0, rowMap.get(1)); // STT
                    colMap.put(1, rowMap.get(2)); // Họ tên
                    colMap.put(2, rowMap.get(3)); // Ngày sinh
                    colMap.put(3, rowMap.get(4)); // Cấp bậc
                    colMap.put(4, rowMap.get(4)); // Chức vụ (chung cột với cấp bậc)
                    for (int k = 5; k <= 35; k++) {
                        if (rowMap.containsKey(k)) {
                            colMap.put(k, rowMap.get(k));
                        }
                    }
                } else {
                    // Mẫu chuẩn (Họ tên là 1, Lương là 9, các cột tính toán khớp đúng số)
                    colMap = rowMap;
                }
                break;
            }
        }

        // Fallback: nếu không có dòng đánh số 1, 2, 3... (như sheet I.1 của BC Tăng thiết giáp)
        if (colMap.isEmpty()) {
            for (int r = 0; r <= Math.min(15, sheet.getLastRowNum()); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                int colHoTen = -1;
                for (int c = 0; c < row.getLastCellNum(); c++) {
                    String val = getString(row, c, evaluator).toLowerCase();
                    if (val.contains("họ và tên") || val.contains("ho va ten") || val.contains("họ tên")) {
                        colHoTen = c;
                        break;
                    }
                }
                if (colHoTen != -1) {
                    if (colHoTen > 0) {
                        colMap.put(0, colHoTen - 1);
                    }
                    colMap.put(1, colHoTen);
                    for (int k = 2; k <= 35; k++) {
                        int colIdx = colHoTen + (k - 1);
                        colMap.put(k, colIdx);
                    }
                    break;
                }
            }
        }

        return colMap;
    }

    public static int getHeaderRowNum(org.apache.poi.ss.usermodel.Sheet sheet, FormulaEvaluator evaluator) {
        if (sheet == null) return -1;
        // 1. Tìm dòng đánh số cột có ít nhất 7 số
        for (int r = 0; r <= Math.min(15, sheet.getLastRowNum()); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            java.util.Map<Integer, Integer> rowMap = new java.util.HashMap<>();
            for (int c = 0; c < row.getLastCellNum(); c++) {
                String val = getString(row, c, evaluator);
                if (val != null) {
                    val = val.trim();
                    if (val.contains("/") || val.matches("^\\d+[-–]\\d+$")) {
                        continue;
                    }
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("^[\\[\\(]?(\\d{1,2})").matcher(val);
                    if (m.find()) {
                        try {
                            int num = Integer.parseInt(m.group(1));
                            if (num >= 1 && num <= 35 && !rowMap.containsKey(num)) {
                                rowMap.put(num, c);
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
            if (rowMap.size() >= 7 && rowMap.containsKey(1) && rowMap.containsKey(2) && rowMap.containsKey(3)
                    && rowMap.containsKey(4) && rowMap.containsKey(5)
                    && rowMap.get(1) < rowMap.get(2) && rowMap.get(2) < rowMap.get(3)
                    && rowMap.get(3) < rowMap.get(4) && rowMap.get(4) < rowMap.get(5)) {
                return r;
            }
        }

        // 2. Fallback: tìm dòng có chữ 'Họ và tên'
        for (int r = 0; r <= Math.min(15, sheet.getLastRowNum()); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            for (int c = 0; c < row.getLastCellNum(); c++) {
                String val = getString(row, c, evaluator).toLowerCase();
                if (val.contains("họ và tên") || val.contains("ho va ten") || val.contains("họ tên")) {
                    int lastHeaderRow = r;
                    for (int checkR = r + 1; checkR <= Math.min(r + 5, sheet.getLastRowNum()); checkR++) {
                        Row nextRow = sheet.getRow(checkR);
                        if (nextRow == null) continue;
                        String cVal = getString(nextRow, c, evaluator).toLowerCase().trim();
                        if (cVal.contains("năm 2025") || cVal.contains("sĩ quan") || cVal.contains("quân nhân") || cVal.isEmpty()) {
                            lastHeaderRow = checkR;
                        } else {
                            break;
                        }
                    }
                    return lastHeaderRow;
                }
            }
        }

        return -1;
    }
}
