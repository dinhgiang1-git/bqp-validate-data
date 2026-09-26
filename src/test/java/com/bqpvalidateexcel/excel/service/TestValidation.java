package com.bqpvalidateexcel.excel.service;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.FileInputStream;

public class TestValidation {
    public static void main(String[] args) {
        try {
            String filePath = "d:\\bqp\\26.9.PHU_LUC_SUA.xlsx";
            FileInputStream fis = new FileInputStream(filePath);
            Workbook workbook = new XSSFWorkbook(fis);
            System.out.println("Workbook loaded. Sheets:");
            for (int i=0; i<workbook.getNumberOfSheets(); i++) {
                System.out.println(" - " + workbook.getSheetName(i));
            }
            
            ExcelValidationService service = new ExcelValidationService(
                new com.bqpvalidateexcel.excel.parser.ExcelRowParsePLI1(),
                new com.bqpvalidateexcel.excel.parser.ExcelRowParsePLI2(),
                new com.bqpvalidateexcel.excel.parser.ExcelRowParsePLI3(),
                new com.bqpvalidateexcel.excel.parser.ExcelRowParsePLI5()
            );
            
            byte[] result = service.validateAndGenerateErrorReport(new FileInputStream(filePath));
            System.out.println("Validation completed successfully! Result size: " + result.length + " bytes");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
