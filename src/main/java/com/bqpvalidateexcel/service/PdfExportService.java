package com.bqpvalidateexcel.service;

import com.bqpvalidateexcel.model.AppendixAuditResult;
import com.bqpvalidateexcel.model.MultiAuditReport;
import com.spire.xls.FileFormat;
import com.spire.xls.PageOrientationType;
import com.spire.xls.PaperSizeType;
import com.spire.xls.Workbook;
import com.spire.xls.Worksheet;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

@Service
public class PdfExportService {

    private final ExcelExportService excelExportService;

    public PdfExportService(ExcelExportService excelExportService) {
        this.excelExportService = excelExportService;
    }

    /**
     * Chuyển đổi file Excel trong RAM sang file PDF bằng Spire.XLS (giữ nguyên viền ô, màu sắc và merge cell)
     */
    public byte[] convertExcelToPdf(byte[] excelBytes, boolean isA3) throws Exception {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(excelBytes);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            Workbook spireWb = new Workbook();

            // 1. Cấu hình đọc font hệ thống Windows tránh thế font lỗi
            java.io.File winFonts = new java.io.File("C:\\Windows\\Fonts");
            if (winFonts.exists() && winFonts.isDirectory()) {
                spireWb.setCustomFontFileDirectory(new String[]{ "C:\\Windows\\Fonts" });
            }

            spireWb.loadFromStream(bais);

            for (int i = 0; i < spireWb.getWorksheets().getCount(); i++) {
                Worksheet ws = spireWb.getWorksheets().get(i);

                int lastCol = ws.getLastColumn();
                int lastRow = ws.getLastRow();

                // 2. Tự động căn chỉnh độ rộng cột 1 đến lastCol - 1 (vừa vặn số tiền và tên)
                for (int col = 1; col < lastCol; col++) {
                    ws.autoFitColumn(col);
                    double currentWidth = ws.getColumnWidth(col);
                    if (currentWidth < 9) {
                        ws.setColumnWidth(col, 9);
                    } else {
                        ws.setColumnWidth(col, currentWidth + 2.0);
                    }
                }

                // 3. Cột Ghi chú cuối cùng: Chiều rộng cố định 38 đơn vị và bật WrapText
                if (lastCol > 0) {
                    ws.setColumnWidth(lastCol, 38);
                    if (lastRow >= 1) {
                        ws.getCellRange(1, lastCol, Math.max(lastRow, 10), lastCol).getStyle().setWrapText(true);
                    }
                }

                // 4. Tự động căn chỉnh chiều cao các dòng theo nội dung đã wrap
                if (ws.getAllocatedRange() != null) {
                    ws.getAllocatedRange().autoFitRows();
                }

                // 5. Cấu hình in PDF A3 Landscape Fit ngang
                ws.getPageSetup().setOrientation(PageOrientationType.Landscape);
                ws.getPageSetup().setPaperSize(isA3 ? PaperSizeType.PaperA3 : PaperSizeType.PaperA4);
                ws.getPageSetup().isFitToPage(true);
                ws.getPageSetup().setFitToPagesWide(1);
                ws.getPageSetup().setFitToPagesTall(0);
            }

            spireWb.saveToStream(baos, FileFormat.PDF);
            return baos.toByteArray();
        }
    }

    /**
     * Xuất PDF kết quả rà soát chi tiết cho 1 phụ lục cụ thể (Phụ lục I.1, I.2 hoặc I.3)
     */
    public byte[] generateAppendixPdf(AppendixAuditResult result) throws Exception {
        byte[] excelBytes = excelExportService.injectAppendixData(result);
        return convertExcelToPdf(excelBytes, true);
    }

    /**
     * Xuất PDF Phụ lục II tổng hợp lỗi từ toàn bộ các phụ lục đã nộp
     */
    public byte[] generatePhuLucIIPdf(MultiAuditReport report) throws Exception {
        byte[] excelBytes = excelExportService.injectPhuLucIIData(report);
        return convertExcelToPdf(excelBytes, true);
    }
}
