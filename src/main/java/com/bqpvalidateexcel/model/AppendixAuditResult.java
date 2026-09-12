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
public class AppendixAuditResult {
    private AppendixType appendixType;
    private String originalFileName;
    private String inputFormat; // "PDF" hoặc "EXCEL"
    private int totalRecords;
    private int validRecords;
    private int errorRecords;
    private BigDecimal totalDeclaredMoney;
    private BigDecimal totalCalculatedMoney;
    private BigDecimal totalDifferenceMoney;

    @Builder.Default
    private List<AuditRecord> records = new ArrayList<>();

    // Các hàng tiêu đề gốc để tái lập bảng Excel gốc
    @Builder.Default
    private List<List<String>> headerRows = new ArrayList<>();

    public List<AuditRecord> getErrorOnlyRecords() {
        if (records == null) return List.of();
        return records.stream().filter(AuditRecord::isError).toList();
    }
}
