package com.bqpvalidateexcel.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedRecord {
    private int rowIndex;
    private String stt;
    private String fullName;
    private String birthDateStr;
    private YearMonth birthDate;
    private String rank;
    private String position;
    private String enlistDateStr;
    private YearMonth enlistDate;
    private String evaluation;
    private String mergerDateStr;
    private YearMonth mergerDate;
    private String retirementDateStr;
    private YearMonth retirementDate;

    private BigDecimal salary;             // Lương tháng hiện hưởng (Cột 9)
    private BigDecimal monthsEarly;        // Số tháng nghỉ sớm / thôi việc (Cột 10)
    private BigDecimal yearsEarly;         // Số năm nghỉ sớm / thôi việc (Cột 11)
    private BigDecimal yearsBhxh;          // Số năm BHXH (Cột 12)

    // Các cột số tiền khai báo trên PDF
    private BigDecimal declaredC1;         // Trợ cấp theo tháng nghỉ sớm (Cột 13/14/15/16)
    private BigDecimal declaredC2;         // Trợ cấp theo năm nghỉ sớm (Cột 17/20)
    private BigDecimal declaredC3Base;     // Trợ cấp BHXH 20/15 năm đầu (Cột 18/21)
    private BigDecimal declaredC3Extra;    // Trợ cấp BHXH năm thứ 21/16 trở đi (Cột 19/22)
    private BigDecimal declaredJobCreation;// Trợ cấp tạo việc làm (Phụ lục I.2 - Cột 14/17)
    private BigDecimal declaredTotal;      // Tổng cộng số tiền khai báo trên PDF

    // Dữ liệu dòng gốc từ PDF để xuất lại Excel nguyên bản
    private List<String> rawRowCells;
}
