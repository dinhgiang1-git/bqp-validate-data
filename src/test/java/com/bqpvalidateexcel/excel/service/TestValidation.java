package com.bqpvalidateexcel.excel.service;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.FileInputStream;

public class TestValidation {
    public static void main(String[] args) {
        try {
            FileInputStream fis = new FileInputStream("d:\\WorkSpace\\spring-master\\spring-microservice\\bqp-validate-excel\\Result_4. Phụ lục TCCT - Copy (4).xlsx");
            Workbook workbook = new XSSFWorkbook(fis);
            System.out.println("Workbook loaded. Sheets:");
            for (int i=0; i<workbook.getNumberOfSheets(); i++) {
                System.out.println(workbook.getSheetName(i));
            }
            
            ExcelValidationService service = new ExcelValidationService(
                new com.bqpvalidateexcel.excel.parser.ExcelRowParsePLI1(),
                new com.bqpvalidateexcel.excel.parser.ExcelRowParsePLI2(),
                new com.bqpvalidateexcel.excel.parser.ExcelRowParsePLI3()
            );
            
            byte[] result = service.validateAndGenerateErrorReport(new FileInputStream("d:\\WorkSpace\\spring-master\\spring-microservice\\bqp-validate-excel\\Result_4. Phụ lục TCCT - Copy (4).xlsx"));
            System.out.println("Validation completed. Result size: " + result.length);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
