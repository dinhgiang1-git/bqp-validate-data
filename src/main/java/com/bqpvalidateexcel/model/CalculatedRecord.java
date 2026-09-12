package com.bqpvalidateexcel.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalculatedRecord {
    private int rankAgeCeiling;            // Trần tuổi theo quân hàm
    private int monthsEarlyCalculated;     // Số tháng nghỉ sớm (hoặc số tháng công tác BHXH)
    private BigDecimal yearsEarlyCalculated; // Số năm nghỉ sớm làm tròn
    private int totalMonthsBhxh;           // Tổng số tháng BHXH (Thời điểm nghỉ - Nhập ngũ)
    private BigDecimal yearsBhxhCalculated;// Số năm BHXH làm tròn
    private boolean isDT1;                 // true: DT1 (<= 60 tháng), false: DT2 (> 60 tháng)
    private boolean isTH1;                 // true: TH1 (<= 12 tháng sáp nhập), false: TH2 (> 12 tháng)
    private boolean isBeforeJuly2025;      // Nghỉ trước 01/07/2025 hay từ 01/07/2025 trở đi

    private BigDecimal c1Amount;           // Chế độ 1: theo tháng nghỉ sớm
    private BigDecimal c2Amount;           // Chế độ 2: theo năm nghỉ sớm
    private BigDecimal c3BaseAmount;       // Chế độ 3: BHXH cơ sở (20 năm hoặc 15 năm đầu)
    private BigDecimal c3ExtraAmount;      // Chế độ 3: BHXH tăng thêm
    private BigDecimal jobCreationAmount;  // Trợ cấp việc làm (Phụ lục I.2)
    private BigDecimal totalAmount;        // Tổng cộng số tiền tính toán đúng
}
