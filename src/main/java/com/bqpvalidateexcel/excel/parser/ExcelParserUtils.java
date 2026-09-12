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

    public static String getString(Row row, int col, FormulaEvaluator formulaEvaluator) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return "";
        try {
            return dataFormatter.formatCellValue(cell, formulaEvaluator).trim();
        } catch (Exception e) {
            if (cell.getCellType() == CellType.FORMULA) {
                try {
                    CellType cachedType = cell.getCachedFormulaResultType();
                    if (cachedType == CellType.STRING) {
                        return cell.getStringCellValue().trim();
                    } else if (cachedType == CellType.NUMERIC) {
                        return dataFormatter.formatCellValue(cell).trim();
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

        if (cell.getCellType() == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(cell)) {
                return cell.getDateCellValue();
            }
        }

        String val = getString(row, col, formulaEvaluator);
        if (val == null || val.trim().isEmpty()) return null;
        val = val.trim();

        // Xử lý các chuỗi có chữ "Thg" ví dụ "Thg7-25" -> "7-25"
        if (val.toLowerCase().startsWith("thg")) {
            val = val.replaceAll("(?i)thg\\s*", "");
        }

        String[] formats = {
                "dd/MM/yyyy", "d/M/yyyy", "dd/M/yyyy", "d/MM/yyyy",
                "MM/yyyy", "M/yyyy",
                "yyyy",
                "dd-MM-yyyy", "d-M-yyyy", "dd-M-yyyy", "d-MM-yyyy",
                "MM-yyyy", "M-yyyy",
                "MM-yy", "M-yy", "MM/yy", "M/yy"
        };

        // Try standard formats
        for (String format : formats) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(format);
                sdf.setLenient(false);
                return sdf.parse(val);
            } catch (Exception e) {
                // ignore and try next
            }
        }
        return null;
    }
}
