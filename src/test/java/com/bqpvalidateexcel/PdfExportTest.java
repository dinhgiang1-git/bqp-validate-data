package com.bqpvalidateexcel;

import com.bqpvalidateexcel.model.AppendixAuditResult;
import com.bqpvalidateexcel.model.AppendixType;
import com.bqpvalidateexcel.model.MultiAuditReport;
import com.bqpvalidateexcel.service.AuditService;
import com.bqpvalidateexcel.service.CalculationService;
import com.bqpvalidateexcel.service.ExcelExportService;
import com.bqpvalidateexcel.service.PdfExportService;
import com.bqpvalidateexcel.service.PdfParserService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

public class PdfExportTest {

    private final PdfParserService pdfParserService = new PdfParserService();
    private final CalculationService calculationService = new CalculationService();
    private final AuditService auditService = new AuditService(pdfParserService, calculationService);
    private final ExcelExportService excelExportService = new ExcelExportService();
    private final PdfExportService pdfExportService = new PdfExportService(excelExportService);

    @Test
    void testExportAllPdfs() throws Exception {
        File outDir = new File("target/test-output");
        outDir.mkdirs();

        List<File> files = List.of(
                new File("src/main/resources/dulieu/3. PHU_LUC I.1.pdf"),
                new File("src/main/resources/dulieu/3. PHU_LUC_I.2.pdf"),
                new File("src/main/resources/dulieu/3. PHU_LUC_I.3.pdf")
        );
        List<AppendixType> types = List.of(AppendixType.PHU_LUC_I1, AppendixType.PHU_LUC_I2, AppendixType.PHU_LUC_I3);

        MultiAuditReport report = auditService.auditMultiple(files, types);

        for (AppendixAuditResult res : report.getAppendixResults()) {
            byte[] pdfBytes = pdfExportService.generateAppendixPdf(res);
            Assertions.assertNotNull(pdfBytes);
            Assertions.assertTrue(pdfBytes.length > 5000, "PDF size must be > 5KB");

            File outFile = new File(outDir, "KetQua_" + res.getAppendixType().name() + ".pdf");
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                fos.write(pdfBytes);
            }
            System.out.println("Exported PDF: " + outFile.getAbsolutePath() + " (" + pdfBytes.length + " bytes)");
        }

        byte[] phuLucIIBytes = pdfExportService.generatePhuLucIIPdf(report);
        Assertions.assertNotNull(phuLucIIBytes);
        Assertions.assertTrue(phuLucIIBytes.length > 5000, "Phu Luc II PDF size must be > 5KB");

        File pl2File = new File(outDir, "KetQua_PhuLucII.pdf");
        try (FileOutputStream fos = new FileOutputStream(pl2File)) {
            fos.write(phuLucIIBytes);
        }
        System.out.println("Exported Phu Luc II PDF: " + pl2File.getAbsolutePath() + " (" + phuLucIIBytes.length + " bytes)");
    }
}
