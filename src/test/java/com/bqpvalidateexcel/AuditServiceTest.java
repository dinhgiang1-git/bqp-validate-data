package com.bqpvalidateexcel;

import com.bqpvalidateexcel.model.AppendixAuditResult;
import com.bqpvalidateexcel.model.AppendixType;
import com.bqpvalidateexcel.model.MultiAuditReport;
import com.bqpvalidateexcel.service.AuditService;
import com.bqpvalidateexcel.service.CalculationService;
import com.bqpvalidateexcel.service.ExcelParserService;
import com.bqpvalidateexcel.service.PdfParserService;
import com.bqpvalidateexcel.util.NumberParserUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

public class AuditServiceTest {

    private final PdfParserService pdfParserService = new PdfParserService();
    private final ExcelParserService excelParserService = new ExcelParserService();
    private final CalculationService calculationService = new CalculationService();
    private final AuditService auditService = new AuditService(pdfParserService, excelParserService, calculationService);

    @Test
    void testAuditSamplePdfI1() throws Exception {
        File file = new File("src/main/resources/dulieu/3. PHU_LUC I.1.pdf");
        AppendixAuditResult result = auditService.auditSinglePdf(file, AppendixType.PHU_LUC_I1);

        System.out.println("=== AUDIT RESULT PHU LUC I.1 ===");
        System.out.println("Total records: " + result.getTotalRecords());
        System.out.println("Valid records: " + result.getValidRecords());
        System.out.println("Error records: " + result.getErrorRecords());
        System.out.println("Total declared money: " + NumberParserUtil.formatMoney(result.getTotalDeclaredMoney()));
        System.out.println("Total calc money:     " + NumberParserUtil.formatMoney(result.getTotalCalculatedMoney()));
        System.out.println("Total diff money:     " + NumberParserUtil.formatMoney(result.getTotalDifferenceMoney()));

        Assertions.assertTrue(result.getTotalRecords() > 0, "Phải đọc được ít nhất 1 hồ sơ");
    }

    @Test
    void testAuditSamplePdfI2() throws Exception {
        File file = new File("src/main/resources/dulieu/3. PHU_LUC_I.2.pdf");
        AppendixAuditResult result = auditService.auditSinglePdf(file, AppendixType.PHU_LUC_I2);

        System.out.println("=== AUDIT RESULT PHU LUC I.2 ===");
        System.out.println("Total records: " + result.getTotalRecords());
        System.out.println("Valid records: " + result.getValidRecords());
        System.out.println("Error records: " + result.getErrorRecords());
        System.out.println("Total declared money: " + NumberParserUtil.formatMoney(result.getTotalDeclaredMoney()));
        System.out.println("Total calc money:     " + NumberParserUtil.formatMoney(result.getTotalCalculatedMoney()));
        System.out.println("Total diff money:     " + NumberParserUtil.formatMoney(result.getTotalDifferenceMoney()));

        Assertions.assertTrue(result.getTotalRecords() > 0, "Phải đọc được ít nhất 1 hồ sơ");
    }

    @Test
    void testAuditSamplePdfI3() throws Exception {
        File file = new File("src/main/resources/dulieu/3. PHU_LUC_I.3.pdf");
        AppendixAuditResult result = auditService.auditSinglePdf(file, AppendixType.PHU_LUC_I3);

        System.out.println("=== AUDIT RESULT PHU LUC I.3 ===");
        System.out.println("Total records: " + result.getTotalRecords());
        System.out.println("Valid records: " + result.getValidRecords());
        System.out.println("Error records: " + result.getErrorRecords());
        System.out.println("Total declared money: " + NumberParserUtil.formatMoney(result.getTotalDeclaredMoney()));
        System.out.println("Total calc money:     " + NumberParserUtil.formatMoney(result.getTotalCalculatedMoney()));
        System.out.println("Total diff money:     " + NumberParserUtil.formatMoney(result.getTotalDifferenceMoney()));

        Assertions.assertTrue(result.getTotalRecords() > 0, "Phải đọc được ít nhất 1 hồ sơ");
    }

    @Test
    void testMultiAudit() throws Exception {
        List<File> files = List.of(
                new File("src/main/resources/dulieu/3. PHU_LUC I.1.pdf"),
                new File("src/main/resources/dulieu/3. PHU_LUC_I.2.pdf"),
                new File("src/main/resources/dulieu/3. PHU_LUC_I.3.pdf")
        );
        List<AppendixType> types = List.of(AppendixType.PHU_LUC_I1, AppendixType.PHU_LUC_I2, AppendixType.PHU_LUC_I3);

        MultiAuditReport report = auditService.auditMultiple(files, types);
        System.out.println("=== MULTI AUDIT TOTALS ===");
        System.out.println("Total files: " + report.getTotalFilesSubmitted());
        System.out.println("Total records across all: " + report.getTotalAllRecords());
        System.out.println("Total errors across all:  " + report.getTotalAllErrors());
        System.out.println("Total error records size: " + report.getAllErrorRecords().size());

        Assertions.assertEquals(3, report.getTotalFilesSubmitted());
        Assertions.assertTrue(report.getTotalAllRecords() > 0);
    }
}
