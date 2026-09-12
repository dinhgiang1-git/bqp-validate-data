package com.bqpvalidateexcel;

import com.bqpvalidateexcel.model.AppendixAuditResult;
import com.bqpvalidateexcel.model.AppendixType;
import com.bqpvalidateexcel.model.MultiAuditReport;
import com.bqpvalidateexcel.service.*;
import com.bqpvalidateexcel.util.NumberParserUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

public class ExcelParserTest {

    private final PdfParserService pdfParserService = new PdfParserService();
    private final ExcelParserService excelParserService = new ExcelParserService();
    private final CalculationService calculationService = new CalculationService();
    private final AuditService auditService = new AuditService(pdfParserService, excelParserService, calculationService);
    private final ExcelExportService excelExportService = new ExcelExportService();

    private File tempI1Excel;
    private File tempI2Excel;
    private File tempI3Excel;

    @BeforeEach
    void setUp() throws Exception {
        File outDir = new File("target/test-output");
        if (!outDir.exists()) {
            outDir.mkdirs();
        }

        // Tạo sẵn các file Excel từ kết quả audit PDF để làm nguồn test input Excel
        AppendixAuditResult res1 = auditService.auditSingleFile(new File("src/main/resources/dulieu/3. PHU_LUC I.1.pdf"), AppendixType.PHU_LUC_I1);
        byte[] excel1Bytes = excelExportService.injectAppendixData(res1);
        tempI1Excel = new File("target/test-output/test_input_i1.xlsx");
        try (FileOutputStream fos = new FileOutputStream(tempI1Excel)) {
            fos.write(excel1Bytes);
        }

        AppendixAuditResult res2 = auditService.auditSingleFile(new File("src/main/resources/dulieu/3. PHU_LUC_I.2.pdf"), AppendixType.PHU_LUC_I2);
        byte[] excel2Bytes = excelExportService.injectAppendixData(res2);
        tempI2Excel = new File("target/test-output/test_input_i2.xlsx");
        try (FileOutputStream fos = new FileOutputStream(tempI2Excel)) {
            fos.write(excel2Bytes);
        }

        AppendixAuditResult res3 = auditService.auditSingleFile(new File("src/main/resources/dulieu/3. PHU_LUC_I.3.pdf"), AppendixType.PHU_LUC_I3);
        byte[] excel3Bytes = excelExportService.injectAppendixData(res3);
        tempI3Excel = new File("target/test-output/test_input_i3.xlsx");
        try (FileOutputStream fos = new FileOutputStream(tempI3Excel)) {
            fos.write(excel3Bytes);
        }
    }

    @Test
    void testParseAndAuditExcelI1() throws Exception {
        Assertions.assertTrue(excelParserService.supports(tempI1Excel.getName()));

        RecordParserService.ExtractedData data = excelParserService.parse(tempI1Excel, null);
        Assertions.assertEquals(AppendixType.PHU_LUC_I1, data.appendixType);
        Assertions.assertFalse(data.records.isEmpty(), "Excel I.1 records must not be empty");

        AppendixAuditResult result = auditService.auditSingleFile(tempI1Excel, null);
        System.out.println("=== EXCEL AUDIT RESULT PHU LUC I.1 ===");
        System.out.println("Format: " + result.getInputFormat());
        System.out.println("Total records: " + result.getTotalRecords());
        System.out.println("Valid records: " + result.getValidRecords());
        System.out.println("Error records: " + result.getErrorRecords());
        System.out.println("Total money:   " + NumberParserUtil.formatMoney(result.getTotalDeclaredMoney()));

        Assertions.assertEquals("EXCEL", result.getInputFormat());
        Assertions.assertTrue(result.getTotalRecords() > 0);
    }

    @Test
    void testParseAndAuditExcelI2() throws Exception {
        Assertions.assertTrue(excelParserService.supports(tempI2Excel.getName()));

        RecordParserService.ExtractedData data = excelParserService.parse(tempI2Excel, null);
        Assertions.assertEquals(AppendixType.PHU_LUC_I2, data.appendixType);
        Assertions.assertFalse(data.records.isEmpty(), "Excel I.2 records must not be empty");

        AppendixAuditResult result = auditService.auditSingleFile(tempI2Excel, null);
        System.out.println("=== EXCEL AUDIT RESULT PHU LUC I.2 ===");
        System.out.println("Format: " + result.getInputFormat());
        System.out.println("Total records: " + result.getTotalRecords());

        Assertions.assertEquals("EXCEL", result.getInputFormat());
        Assertions.assertTrue(result.getTotalRecords() > 0);
    }

    @Test
    void testParseAndAuditExcelI3() throws Exception {
        Assertions.assertTrue(excelParserService.supports(tempI3Excel.getName()));

        RecordParserService.ExtractedData data = excelParserService.parse(tempI3Excel, null);
        Assertions.assertEquals(AppendixType.PHU_LUC_I3, data.appendixType);
        Assertions.assertFalse(data.records.isEmpty(), "Excel I.3 records must not be empty");

        AppendixAuditResult result = auditService.auditSingleFile(tempI3Excel, null);
        System.out.println("=== EXCEL AUDIT RESULT PHU LUC I.3 ===");
        System.out.println("Format: " + result.getInputFormat());
        System.out.println("Total records: " + result.getTotalRecords());

        Assertions.assertEquals("EXCEL", result.getInputFormat());
        Assertions.assertTrue(result.getTotalRecords() > 0);
    }

    @Test
    void testMultiAuditMixedFormats() throws Exception {
        // Trộn cả file PDF và file Excel
        List<File> files = List.of(
                tempI1Excel,
                new File("src/main/resources/dulieu/3. PHU_LUC_I.2.pdf"),
                tempI3Excel
        );
        List<AppendixType> types = List.of(AppendixType.PHU_LUC_I1, AppendixType.PHU_LUC_I2, AppendixType.PHU_LUC_I3);

        MultiAuditReport report = auditService.auditMultiple(files, types);
        System.out.println("=== MIXED PDF & EXCEL MULTI AUDIT ===");
        System.out.println("Total files: " + report.getTotalFilesSubmitted());
        System.out.println("Total records: " + report.getTotalAllRecords());

        Assertions.assertEquals(3, report.getTotalFilesSubmitted());
        Assertions.assertTrue(report.getTotalAllRecords() > 0);
    }
}
