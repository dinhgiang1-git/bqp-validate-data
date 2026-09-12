package com.bqpvalidateexcel.controller;

import com.bqpvalidateexcel.model.AppendixAuditResult;
import com.bqpvalidateexcel.model.AppendixType;
import com.bqpvalidateexcel.model.MultiAuditReport;
import com.bqpvalidateexcel.service.AuditService;
import com.bqpvalidateexcel.service.ExcelExportService;
import com.bqpvalidateexcel.service.PdfExportService;
import com.bqpvalidateexcel.service.ZipExportService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Controller
public class AuditWebController {

    private final AuditService auditService;
    private final ExcelExportService excelExportService;
    private final PdfExportService pdfExportService;
    private final ZipExportService zipExportService;

    // Bộ nhớ đệm lưu kết quả báo cáo tạm thời theo reportId
    private final Map<String, MultiAuditReport> reportCache = new ConcurrentHashMap<>();

    public AuditWebController(AuditService auditService,
                              ExcelExportService excelExportService,
                              PdfExportService pdfExportService,
                              ZipExportService zipExportService) {
        this.auditService = auditService;
        this.excelExportService = excelExportService;
        this.pdfExportService = pdfExportService;
        this.zipExportService = zipExportService;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("title", "Hệ Thống Kiểm Toán Tính Chế Độ Chính Sách Quân Đội");
        return "index";
    }

    /**
     * API tải dữ liệu mẫu tích hợp sẵn để kiểm tra tức thì
     */
    @GetMapping("/api/load-sample")
    @ResponseBody
    public ResponseEntity<?> loadSampleData() {
        try {
            List<File> files = new ArrayList<>();
            List<AppendixType> types = new ArrayList<>();

            File f1 = new File("src/main/resources/dulieu/3. PHU_LUC I.1.pdf");
            File f2 = new File("src/main/resources/dulieu/3. PHU_LUC_I.2.pdf");
            File f3 = new File("src/main/resources/dulieu/3. PHU_LUC_I.3.pdf");

            if (f1.exists()) { files.add(f1); types.add(AppendixType.PHU_LUC_I1); }
            if (f2.exists()) { files.add(f2); types.add(AppendixType.PHU_LUC_I2); }
            if (f3.exists()) { files.add(f3); types.add(AppendixType.PHU_LUC_I3); }

            if (files.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Không tìm thấy file mẫu trong thư mục src/main/resources/dulieu/"));
            }

            MultiAuditReport report = auditService.auditMultiple(files, types);
            reportCache.put(report.getReportId(), report);

            return ResponseEntity.ok(report);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * API thực hiện rà soát từ 1, 2 hoặc 3 file PDF/Excel người dùng tải lên
     */
    @PostMapping("/api/audit")
    @ResponseBody
    public ResponseEntity<?> auditFiles(
            @RequestParam(value = "fileI1", required = false) MultipartFile fileI1,
            @RequestParam(value = "fileI2", required = false) MultipartFile fileI2,
            @RequestParam(value = "fileI3", required = false) MultipartFile fileI3) {

        List<File> tempFiles = new ArrayList<>();
        List<AppendixType> types = new ArrayList<>();

        try {
            if (fileI1 != null && !fileI1.isEmpty()) {
                String suffix = getFileSuffix(fileI1);
                Path p1 = Files.createTempFile("upload_i1_", suffix);
                Files.copy(fileI1.getInputStream(), p1, StandardCopyOption.REPLACE_EXISTING);
                tempFiles.add(p1.toFile());
                types.add(AppendixType.PHU_LUC_I1);
            }

            if (fileI2 != null && !fileI2.isEmpty()) {
                String suffix = getFileSuffix(fileI2);
                Path p2 = Files.createTempFile("upload_i2_", suffix);
                Files.copy(fileI2.getInputStream(), p2, StandardCopyOption.REPLACE_EXISTING);
                tempFiles.add(p2.toFile());
                types.add(AppendixType.PHU_LUC_I2);
            }

            if (fileI3 != null && !fileI3.isEmpty()) {
                String suffix = getFileSuffix(fileI3);
                Path p3 = Files.createTempFile("upload_i3_", suffix);
                Files.copy(fileI3.getInputStream(), p3, StandardCopyOption.REPLACE_EXISTING);
                tempFiles.add(p3.toFile());
                types.add(AppendixType.PHU_LUC_I3);
            }

            if (tempFiles.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Vui lòng chọn ít nhất 1 file PDF hoặc Excel (Phụ lục I.1, I.2 hoặc I.3) để rà soát!"));
            }

            MultiAuditReport report = auditService.auditMultiple(tempFiles, types);
            reportCache.put(report.getReportId(), report);

            return ResponseEntity.ok(report);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Lỗi khi xử lý rà soát: " + e.getMessage()));
        } finally {
            for (File tf : tempFiles) {
                try { tf.delete(); } catch (Exception ignored) {}
            }
        }
    }

    private String getFileSuffix(MultipartFile file) {
        String orig = file.getOriginalFilename();
        if (orig != null) {
            String lower = orig.toLowerCase();
            if (lower.endsWith(".xlsx")) return ".xlsx";
            if (lower.endsWith(".xls")) return ".xls";
        }
        return ".pdf";
    }

    /**
     * Tải PDF kết quả rà soát cho Phụ lục I.1
     */
    @GetMapping("/download/pdf/i1")
    public ResponseEntity<Resource> downloadPdfI1(@RequestParam("reportId") String reportId) {
        return downloadAppendixPdf(reportId, AppendixType.PHU_LUC_I1, "1_PhuLucI1_RaSoat.pdf");
    }

    /**
     * Tải PDF kết quả rà soát cho Phụ lục I.2
     */
    @GetMapping("/download/pdf/i2")
    public ResponseEntity<Resource> downloadPdfI2(@RequestParam("reportId") String reportId) {
        return downloadAppendixPdf(reportId, AppendixType.PHU_LUC_I2, "1_PhuLucI2_RaSoat.pdf");
    }

    /**
     * Tải PDF kết quả rà soát cho Phụ lục I.3
     */
    @GetMapping("/download/pdf/i3")
    public ResponseEntity<Resource> downloadPdfI3(@RequestParam("reportId") String reportId) {
        return downloadAppendixPdf(reportId, AppendixType.PHU_LUC_I3, "1_PhuLucI3_RaSoat.pdf");
    }

    /**
     * Tải Excel kết quả rà soát cho Phụ lục I.1
     */
    @GetMapping("/download/excel/i1")
    public ResponseEntity<Resource> downloadExcelI1(@RequestParam("reportId") String reportId) {
        return downloadAppendixExcel(reportId, AppendixType.PHU_LUC_I1, "1_PhuLucI1_RaSoat.xlsx");
    }

    /**
     * Tải Excel kết quả rà soát cho Phụ lục I.2
     */
    @GetMapping("/download/excel/i2")
    public ResponseEntity<Resource> downloadExcelI2(@RequestParam("reportId") String reportId) {
        return downloadAppendixExcel(reportId, AppendixType.PHU_LUC_I2, "1_PhuLucI2_RaSoat.xlsx");
    }

    /**
     * Tải Excel kết quả rà soát cho Phụ lục I.3
     */
    @GetMapping("/download/excel/i3")
    public ResponseEntity<Resource> downloadExcelI3(@RequestParam("reportId") String reportId) {
        return downloadAppendixExcel(reportId, AppendixType.PHU_LUC_I3, "1_PhuLucI3_RaSoat.xlsx");
    }

    private ResponseEntity<Resource> downloadAppendixExcel(String reportId, AppendixType type, String filename) {
        MultiAuditReport report = reportCache.get(reportId);
        if (report == null) return ResponseEntity.notFound().build();

        AppendixAuditResult target = report.getAppendixResults().stream()
                .filter(r -> r.getAppendixType() == type)
                .findFirst()
                .orElse(null);

        if (target == null) return ResponseEntity.notFound().build();

        try {
            byte[] bytes = excelExportService.injectAppendixData(target);
            ByteArrayResource resource = new ByteArrayResource(bytes);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(bytes.length)
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private ResponseEntity<Resource> downloadAppendixPdf(String reportId, AppendixType type, String filename) {
        MultiAuditReport report = reportCache.get(reportId);
        if (report == null) return ResponseEntity.notFound().build();

        AppendixAuditResult target = report.getAppendixResults().stream()
                .filter(r -> r.getAppendixType() == type)
                .findFirst()
                .orElse(null);

        if (target == null) return ResponseEntity.notFound().build();

        try {
            byte[] bytes = pdfExportService.generateAppendixPdf(target);
            ByteArrayResource resource = new ByteArrayResource(bytes);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .contentLength(bytes.length)
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Tải PDF Phụ lục II tổng hợp lỗi
     */
    @GetMapping("/download/pdf/phu-luc-ii")
    public ResponseEntity<Resource> downloadPdfPhuLucII(@RequestParam("reportId") String reportId) {
        MultiAuditReport report = reportCache.get(reportId);
        if (report == null) return ResponseEntity.notFound().build();

        try {
            byte[] bytes = pdfExportService.generatePhuLucIIPdf(report);
            ByteArrayResource resource = new ByteArrayResource(bytes);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"2_PhuLucII_TongHopLoi.pdf\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .contentLength(bytes.length)
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Tải Excel Phụ lục II tổng hợp lỗi
     */
    @GetMapping("/download/excel/phu-luc-ii")
    public ResponseEntity<Resource> downloadExcelPhuLucII(@RequestParam("reportId") String reportId) {
        MultiAuditReport report = reportCache.get(reportId);
        if (report == null) return ResponseEntity.notFound().build();

        try {
            byte[] bytes = excelExportService.injectPhuLucIIData(report);
            ByteArrayResource resource = new ByteArrayResource(bytes);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"2_PhuLucII_TongHopLoi.xlsx\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(bytes.length)
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Tải toàn bộ trọn gói file kết quả trong 1 file ZIP
     */
    @GetMapping("/download/all-zip")
    public ResponseEntity<Resource> downloadAllZip(@RequestParam("reportId") String reportId) {
        MultiAuditReport report = reportCache.get(reportId);
        if (report == null) return ResponseEntity.notFound().build();

        try {
            byte[] bytes = zipExportService.generateAllInZip(report);
            ByteArrayResource resource = new ByteArrayResource(bytes);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Bao_Cao_Kiem_Toan_Che_Do.zip\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(bytes.length)
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
