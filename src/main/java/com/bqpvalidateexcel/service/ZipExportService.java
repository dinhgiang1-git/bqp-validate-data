package com.bqpvalidateexcel.service;

import com.bqpvalidateexcel.model.AppendixAuditResult;
import com.bqpvalidateexcel.model.MultiAuditReport;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class ZipExportService {

    private final ExcelExportService excelExportService;
    private final PdfExportService pdfExportService;

    public ZipExportService(ExcelExportService excelExportService, PdfExportService pdfExportService) {
        this.excelExportService = excelExportService;
        this.pdfExportService = pdfExportService;
    }

    /**
     * Đóng gói toàn bộ các file PDF và Excel kết quả vào 1 file ZIP
     */
    public byte[] generateAllInZip(MultiAuditReport report) throws Exception {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {

            // 1. Xuất file PDF rà soát cho từng phụ lục đã nộp
            for (AppendixAuditResult appResult : report.getAppendixResults()) {
                String pdfName = switch (appResult.getAppendixType()) {
                    case PHU_LUC_I1 -> "1_PhuLucI1_RaSoat.pdf";
                    case PHU_LUC_I2 -> "1_PhuLucI2_RaSoat.pdf";
                    case PHU_LUC_I3 -> "1_PhuLucI3_RaSoat.pdf";
                };
                byte[] pdfBytes = pdfExportService.generateAppendixPdf(appResult);
                ZipEntry entry = new ZipEntry(pdfName);
                zos.putNextEntry(entry);
                zos.write(pdfBytes);
                zos.closeEntry();

                // Kèm file Excel rà soát
                String xlsxName = switch (appResult.getAppendixType()) {
                    case PHU_LUC_I1 -> "1_PhuLucI1_RaSoat.xlsx";
                    case PHU_LUC_I2 -> "1_PhuLucI2_RaSoat.xlsx";
                    case PHU_LUC_I3 -> "1_PhuLucI3_RaSoat.xlsx";
                };
                byte[] xlsxBytes = excelExportService.injectAppendixData(appResult);
                ZipEntry entryXlsx = new ZipEntry("excel/" + xlsxName);
                zos.putNextEntry(entryXlsx);
                zos.write(xlsxBytes);
                zos.closeEntry();
            }

            // 2. File PDF Phụ lục II tổng hợp lỗi
            byte[] pl2Pdf = pdfExportService.generatePhuLucIIPdf(report);
            ZipEntry entry2 = new ZipEntry("2_PhuLucII_TongHopLoi.pdf");
            zos.putNextEntry(entry2);
            zos.write(pl2Pdf);
            zos.closeEntry();

            // 3. File Excel Phụ lục II tổng hợp lỗi
            byte[] pl2Xlsx = excelExportService.injectPhuLucIIData(report);
            ZipEntry entry3 = new ZipEntry("excel/2_PhuLucII_TongHopLoi.xlsx");
            zos.putNextEntry(entry3);
            zos.write(pl2Xlsx);
            zos.closeEntry();

            zos.finish();
            return baos.toByteArray();
        }
    }
}
