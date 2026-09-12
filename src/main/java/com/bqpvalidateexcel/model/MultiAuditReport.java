package com.bqpvalidateexcel.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MultiAuditReport {
    private String reportId;
    private String createdAt;

    // Danh sách kết quả từng phụ lục đã nộp
    @Builder.Default
    private List<AppendixAuditResult> appendixResults = new ArrayList<>();

    // Thống kê tổng hợp toàn bộ các phụ lục đã nộp
    private int totalFilesSubmitted;
    private int totalAllRecords;
    private int totalAllValid;
    private int totalAllErrors;
    private BigDecimal totalAllDeclaredMoney;
    private BigDecimal totalAllCalculatedMoney;
    private BigDecimal totalAllDifferenceMoney;

    public void recalculateTotals() {
        int totalRec = 0;
        int totalVal = 0;
        int totalErr = 0;
        BigDecimal dec = BigDecimal.ZERO;
        BigDecimal calc = BigDecimal.ZERO;
        BigDecimal diff = BigDecimal.ZERO;

        if (appendixResults != null) {
            for (AppendixAuditResult res : appendixResults) {
                totalRec += res.getTotalRecords();
                totalVal += res.getValidRecords();
                totalErr += res.getErrorRecords();
                if (res.getTotalDeclaredMoney() != null) dec = dec.add(res.getTotalDeclaredMoney());
                if (res.getTotalCalculatedMoney() != null) calc = calc.add(res.getTotalCalculatedMoney());
                if (res.getTotalDifferenceMoney() != null) diff = diff.add(res.getTotalDifferenceMoney());
            }
        }

        this.totalFilesSubmitted = (appendixResults != null) ? appendixResults.size() : 0;
        this.totalAllRecords = totalRec;
        this.totalAllValid = totalVal;
        this.totalAllErrors = totalErr;
        this.totalAllDeclaredMoney = dec;
        this.totalAllCalculatedMoney = calc;
        this.totalAllDifferenceMoney = diff;
    }

    public List<AuditRecord> getAllErrorRecords() {
        List<AuditRecord> allErrors = new ArrayList<>();
        if (appendixResults != null) {
            for (AppendixAuditResult res : appendixResults) {
                allErrors.addAll(res.getErrorOnlyRecords());
            }
        }
        return allErrors;
    }
}
