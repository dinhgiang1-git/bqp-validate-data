package com.bqpvalidateexcel.excel.parser;

import com.bqpvalidateexcel.excel.model.dto.CellReadResult;
import com.bqpvalidateexcel.excel.model.dto.CellStatus;
import com.bqpvalidateexcel.excel.model.dto.FormulaReadReport;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.math.BigDecimal;

public class ExcelParserUtilsTest {

    @Test
    public void testLiteralAndBlankCells() {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("TestSheet");
            Row row = sheet.createRow(0);

            // Ô A1: Số 0 literal
            Cell cA1 = row.createCell(0);
            cA1.setCellValue(0.0);
            CellReadResult<Object> resA1 = ExcelParserUtils.readCellValue(cA1, null);
            Assertions.assertEquals(CellStatus.VALUE, resA1.getStatus());
            Assertions.assertEquals(0, ExcelParserUtils.getBigDecimal(row, 0, null).compareTo(BigDecimal.ZERO), "Số 0 phải đọc được là 0");

            // Ô B1: Số dương
            Cell cB1 = row.createCell(1);
            cB1.setCellValue(15500000.0);
            CellReadResult<Object> resB1 = ExcelParserUtils.readCellValue(cB1, null);
            Assertions.assertEquals(CellStatus.VALUE, resB1.getStatus());
            Assertions.assertEquals(0, ExcelParserUtils.getBigDecimal(row, 1, null).compareTo(new BigDecimal("15500000")));

            // Ô C1: Chuỗi text
            Cell cC1 = row.createCell(2);
            cC1.setCellValue("Nguyễn Văn A");
            CellReadResult<Object> resC1 = ExcelParserUtils.readCellValue(cC1, null);
            Assertions.assertEquals(CellStatus.VALUE, resC1.getStatus());
            Assertions.assertEquals("Nguyễn Văn A", resC1.getValue());
            Assertions.assertEquals("Nguyễn Văn A", ExcelParserUtils.getString(row, 2, null));

            // Ô D1: Blank
            Cell cD1 = row.createCell(3);
            CellReadResult<Object> resD1 = ExcelParserUtils.readCellValue(cD1, null);
            Assertions.assertEquals(CellStatus.BLANK, resD1.getStatus());
            Assertions.assertNull(ExcelParserUtils.getBigDecimal(row, 3, null));
            Assertions.assertEquals("", ExcelParserUtils.getString(row, 3, null));

            // Ô E1: Null cell
            CellReadResult<Object> resE1 = ExcelParserUtils.readCellValue(row, 4, null);
            Assertions.assertEquals(CellStatus.BLANK, resE1.getStatus());
            Assertions.assertNull(ExcelParserUtils.getBigDecimal(row, 4, null));
        } catch (Exception e) {
            Assertions.fail("Lỗi test: " + e.getMessage());
        }
    }

    @Test
    public void testFormulaEvaluationAndEmpty() {
        try (Workbook wb = new XSSFWorkbook()) {
            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();
            Sheet sheet = wb.createSheet("FormulaSheet");
            Row r0 = sheet.createRow(0);
            r0.createCell(0).setCellValue(10.0);
            r0.createCell(1).setCellValue(20.0);

            // Công thức SUM = 30
            Cell cSum = r0.createCell(2);
            cSum.setCellFormula("SUM(A1:B1)");
            evaluator.evaluateFormulaCell(cSum);

            CellReadResult<Object> resSum = ExcelParserUtils.readCellValue(cSum, evaluator);
            Assertions.assertEquals(CellStatus.FORMULA_CACHED, resSum.getStatus());
            Assertions.assertEquals(0, ExcelParserUtils.getBigDecimal(r0, 2, evaluator).compareTo(new BigDecimal("30")));

            // Công thức IF trả về rỗng ""
            Cell cIfEmpty = r0.createCell(3);
            cIfEmpty.setCellFormula("IF(A1>100, B1, \"\")");
            evaluator.evaluateFormulaCell(cIfEmpty);

            CellReadResult<Object> resIfEmpty = ExcelParserUtils.readCellValue(cIfEmpty, evaluator);
            Assertions.assertEquals(CellStatus.FORMULA_EMPTY, resIfEmpty.getStatus());
            Assertions.assertEquals("", resIfEmpty.getValue());
            Assertions.assertNull(ExcelParserUtils.getBigDecimal(r0, 3, evaluator), "Công thức rỗng không được coi là số 0");
            Assertions.assertEquals("", ExcelParserUtils.getString(r0, 3, evaluator));

            // Báo cáo công thức
            FormulaReadReport report = ExcelParserUtils.generateFormulaReport(wb, evaluator);
            Assertions.assertEquals(2, report.getTotalFormulas());
            Assertions.assertEquals(1, report.getCachedFormulas());
            Assertions.assertEquals(1, report.getEmptyFormulas());
            Assertions.assertEquals(0, report.getErrorFormulas());
            Assertions.assertEquals(0, report.getNoResultFormulas());
        } catch (Exception e) {
            Assertions.fail("Lỗi test: " + e.getMessage());
        }
    }

    @Test
    public void testRealFileQK4FormulasIfAvailable() {
        File f = new File("d:/bqp/input/12. Phu lục QK4.xlsx");
        if (!f.exists()) {
            f = new File("../input/12. Phu lục QK4.xlsx");
        }
        if (f.exists()) {
            try (FileInputStream fis = new FileInputStream(f);
                 Workbook wb = WorkbookFactory.create(fis)) {
                Sheet s1 = wb.getSheet("Phụ lục I.1");
                if (s1 != null) {
                    // Dòng 15 (0-indexed là 14), Cột S (cột 19, 0-indexed là 18)
                    Row r15 = s1.getRow(14);
                    Assertions.assertNotNull(r15, "Dòng 15 phải tồn tại trong sheet Phụ lục I.1");
                    Cell cS15 = r15.getCell(18);
                    Assertions.assertNotNull(cS15, "Ô S15 phải tồn tại");
                    
                    CellReadResult<Object> resS15 = ExcelParserUtils.readCellValue(cS15, null);
                    Assertions.assertEquals(CellStatus.FORMULA_CACHED, resS15.getStatus());
                    Assertions.assertEquals(0, ExcelParserUtils.getBigDecimal(r15, 18, null).compareTo(new BigDecimal("160828200")));

                    // Dòng 13 (0-indexed là 12), Cột O (cột 15, 0-indexed là 14)
                    Row r13 = s1.getRow(12);
                    Assertions.assertNotNull(r13);
                    Cell cO13 = r13.getCell(14);
                    Assertions.assertNotNull(cO13);
                    CellReadResult<Object> resO13 = ExcelParserUtils.readCellValue(cO13, null);
                    Assertions.assertEquals(CellStatus.FORMULA_EMPTY, resO13.getStatus());
                    Assertions.assertNull(ExcelParserUtils.getBigDecimal(r13, 14, null));
                }
            } catch (Exception e) {
                Assertions.fail("Lỗi đọc file QK4: " + e.getMessage());
            }
        }
    }

    @Test
    public void testFallbackEvaluationWhenCacheMissing() throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();
            Sheet sheet = wb.createSheet("FallbackSheet");
            Row row = sheet.createRow(0);
            row.createCell(0).setCellValue(15.0);
            row.createCell(1).setCellValue(25.0);

            // Tạo ô công thức KHÔNG gán giá trị cache trước (mô phỏng file OOXML thiếu hoàn toàn thẻ <v>)
            Cell cFormula = row.createCell(2);
            cFormula.setCellFormula("SUM(A1:B1)");
            if (((org.apache.poi.xssf.usermodel.XSSFCell) cFormula).getCTCell().isSetV()) {
                ((org.apache.poi.xssf.usermodel.XSSFCell) cFormula).getCTCell().unsetV();
            }

            // 1. Khi có evaluator: Fallback thành công
            CellReadResult<Object> resWithEval = ExcelParserUtils.readCellValue(cFormula, evaluator);
            Assertions.assertEquals(CellStatus.FORMULA_EVALUATED, resWithEval.getStatus(), "Phải đánh giá thành công khi có evaluator");
            Assertions.assertEquals("evaluated", resWithEval.getValueSource());
            Assertions.assertEquals(0, ExcelParserUtils.getBigDecimal(row, 2, evaluator).compareTo(new BigDecimal("40")));

            // 2. Khi KHÔNG có evaluator và thiếu cache: Phải trả về FORMULA_NO_RESULT
            CellReadResult<Object> resNoEval = ExcelParserUtils.readCellValue(cFormula, null);
            Assertions.assertEquals(CellStatus.FORMULA_NO_RESULT, resNoEval.getStatus(), "Thiếu cache và không có evaluator phải là FORMULA_NO_RESULT");
            Assertions.assertEquals("none", resNoEval.getValueSource());
            Assertions.assertNull(resNoEval.getValue());
            Assertions.assertNull(ExcelParserUtils.getBigDecimal(row, 2, null), "Tuyệt đối không âm thầm gán 0 cho ô chưa tính");
        }
    }

    @Test
    public void testConsistencyBetweenGetStringAndGetBigDecimal() {
        try (Workbook wb = new XSSFWorkbook()) {
            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();
            Sheet sheet = wb.createSheet("ConsistencySheet");
            Row row = sheet.createRow(0);
            row.createCell(0).setCellValue(50.0);
            row.createCell(1).setCellValue(50.0);

            Cell cSum = row.createCell(2);
            cSum.setCellFormula("SUM(A1:B1)");
            evaluator.evaluateFormulaCell(cSum);

            CellReadResult<Object> res = ExcelParserUtils.readCellValue(cSum, evaluator);
            BigDecimal bd = ExcelParserUtils.getBigDecimal(row, 2, evaluator);
            String str = ExcelParserUtils.getString(row, 2, evaluator);

            Assertions.assertEquals(0, bd.compareTo(new BigDecimal("100")));
            Assertions.assertTrue(str.contains("100"), "getString phải phản ánh đúng 100");
            Assertions.assertEquals(CellStatus.FORMULA_CACHED, res.getStatus());
        } catch (Exception e) {
            Assertions.fail("Lỗi test: " + e.getMessage());
        }
    }
}
