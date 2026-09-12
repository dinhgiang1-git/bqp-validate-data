package com.bqpvalidateexcel.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditDifference {
    private boolean hasError;

    // 6 tiêu chí lỗi tương ứng Cột 11 -> 16 của template PhuLucII.xlsx / Sua.xlsx
    private boolean errCriteria;         // Cột 11: Sai đối tượng / tiêu chí
    private boolean errEarlyTime;        // Cột 12: Sai thời gian nghỉ sớm (tháng/năm)
    private boolean errBhxhSeniority;    // Cột 13: Sai thâm niên BHXH
    private boolean errSalary;           // Cột 14: Sai mức lương bình quân
    private boolean errRegimeFormula;    // Cột 15: Sai công thức tính từng chế độ
    private boolean errTotalAmount;      // Cột 16: Sai tổng số tiền

    // Danh sách các cột bị tính sai (0-indexed) để chỉ bôi đỏ riêng từng ô trong Excel
    @Builder.Default
    private Set<Integer> wrongColumnIndices = new HashSet<>();

    // Số tiền chênh lệch (Tính toán - Khai báo)
    private BigDecimal diffAmount;

    // Danh sách các mục sai lệch chi tiết
    @Builder.Default
    private List<String> errorDetails = new ArrayList<>();

    // Giải thích chi tiết cho Cột 18 trong PhuLucII
    private String detailedExplanation;

    // Ghi chú rà soát cho cột ngoài cùng file Excel gốc
    private String reviewNote;
}
