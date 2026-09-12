package com.bqpvalidateexcel;

import com.bqpvalidateexcel.model.AppendixType;
import com.bqpvalidateexcel.model.CalculatedRecord;
import com.bqpvalidateexcel.model.ParsedRecord;
import com.bqpvalidateexcel.service.CalculationService;
import com.bqpvalidateexcel.util.DateParserUtil;
import com.bqpvalidateexcel.util.NumberParserUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;

public class CalculationServiceTest {

    private final CalculationService calculationService = new CalculationService();

    @Test
    void testRoundMonthsToYears() {
        // 0 tháng -> 0 năm
        Assertions.assertEquals(new BigDecimal("0"), NumberParserUtil.roundMonthsToYears(0));
        // 1..6 tháng -> +0.5 năm
        Assertions.assertEquals(new BigDecimal("0.5"), NumberParserUtil.roundMonthsToYears(1));
        Assertions.assertEquals(new BigDecimal("0.5"), NumberParserUtil.roundMonthsToYears(6));
        // 7..11 tháng -> +1.0 năm
        Assertions.assertEquals(new BigDecimal("1"), NumberParserUtil.roundMonthsToYears(7));
        Assertions.assertEquals(new BigDecimal("1"), NumberParserUtil.roundMonthsToYears(11));
        // 12 tháng -> 1 năm
        Assertions.assertEquals(new BigDecimal("1"), NumberParserUtil.roundMonthsToYears(12));
        // 50 tháng = 4 năm 2 tháng -> 4.5 năm
        Assertions.assertEquals(new BigDecimal("4.5"), NumberParserUtil.roundMonthsToYears(50));
        // 32 tháng = 2 năm 8 tháng -> 3.0 năm
        Assertions.assertEquals(new BigDecimal("3"), NumberParserUtil.roundMonthsToYears(32));
    }

    @Test
    void testPhuLucI1_NguyenKhacDuan() {
        // Hồ sơ mẫu: Đại tá Nguyễn Khắc Duẩn, sinh 07/1971, nhập ngũ 09/1990, sáp nhập 03/2025, nghỉ 01/06/2025, lương 34.229.520
        // Đại tá trần 58t -> sinh 07/1971 + 58 = 07/2029.
        // Nghỉ 06/2025 -> 49 hoặc 50 tháng.
        // BHXH: 09/1990 -> 06/2025 = 34 năm 9 tháng -> 35.0 năm (hoặc 34.9 năm).
        ParsedRecord parsed = ParsedRecord.builder()
                .fullName("Nguyễn Khắc Duẩn")
                .rank("Đại tá")
                .birthDate(YearMonth.of(1971, 7))
                .enlistDate(YearMonth.of(1990, 9))
                .mergerDate(YearMonth.of(2025, 3))
                .retirementDate(YearMonth.of(2025, 6))
                .salary(new BigDecimal("34229520"))
                .monthsEarly(new BigDecimal("50"))
                .yearsEarly(new BigDecimal("4.5"))
                .yearsBhxh(new BigDecimal("34.9"))
                .build();

        CalculatedRecord result = calculationService.calculatePhuLucI1(parsed);

        Assertions.assertEquals(58, result.getRankAgeCeiling());
        Assertions.assertEquals(49, result.getMonthsEarlyCalculated()); // 06/2025 đến 07/2029 = 49 tháng
        Assertions.assertTrue(result.isDT1());
        Assertions.assertTrue(result.isTH1()); // 03/2025 đến 06/2025 = 3 tháng <= 12 tháng
        Assertions.assertTrue(result.isBeforeJuly2025()); // 06/2025 < 07/2025
    }

    @Test
    void testPhuLucI2_NguyenVanPhuong() {
        // Hồ sơ mẫu: Nguyễn Văn Phương, Đại úy (4/), sinh 09/1993, nhập ngũ 08/2011, sáp nhập 05/2025, thôi việc 07/2025
        // Lương: 19.333.080.
        // BHXH: 08/2011 đến 07/2025 = 13 năm 11 tháng = 167 tháng -> Cột 10 = MIN(167, 60) = 60 tháng.
        // Cột 11: 13 năm 11 tháng -> 14 năm.
        // TH1 (05/2025 đến 07/2025 = 2 tháng <= 12 tháng).
        ParsedRecord parsed = ParsedRecord.builder()
                .fullName("Nguyễn Văn Phương")
                .rank("4/")
                .birthDate(YearMonth.of(1993, 9))
                .enlistDate(YearMonth.of(2011, 8))
                .mergerDate(YearMonth.of(2025, 5))
                .retirementDate(YearMonth.of(2025, 7))
                .salary(new BigDecimal("19333080"))
                .build();

        CalculatedRecord result = calculationService.calculatePhuLucI2(parsed);

        Assertions.assertEquals(60, result.getMonthsEarlyCalculated());
        Assertions.assertEquals(new BigDecimal("14"), result.getYearsEarlyCalculated());
        Assertions.assertTrue(result.isTH1());

        // C12 = 60 * 0.8 * 19.333.080 = 927.987.840
        Assertions.assertEquals(new BigDecimal("927987840"), result.getC1Amount());
        // C13 = 14 * 1.5 * 19.333.080 = 405.994.680
        Assertions.assertEquals(new BigDecimal("405994680"), result.getC2Amount());
        // C14 = 3 * 19.333.080 = 57.999.240
        Assertions.assertEquals(new BigDecimal("57999240"), result.getJobCreationAmount());
        // Tổng = 927.987.840 + 405.994.680 + 57.999.240 = 1.391.981.760
        Assertions.assertEquals(new BigDecimal("1391981760"), result.getTotalAmount());
    }

    @Test
    void testPhuLucI3_PhamToanThang() {
        // Hồ sơ mẫu: Đại tá Phạm Toàn Thắng, sinh 07/1968, nhập ngũ 09/1985, sáp nhập 03/2025, thôi việc 01/06/2025
        // Lương: 31.224.960. Số năm nghỉ sớm: 1.5 năm.
        // Số năm BHXH: 09/1985 đến 06/2025 = 39 năm 9 tháng -> 40.0 năm.
        // C12 = 1.5 * 5 * 31.224.960 = 234.187.200
        // C13 = 5 * 31.224.960 = 156.124.800 (trước 01/07/2025)
        // C14 = (40 - 20) * 0.5 * 31.224.960 = 20 * 0.5 * 31.224.960 = 10 * 31.224.960 = 312.249.600
        // Tổng = 234.187.200 + 156.124.800 + 312.249.600 = 702.561.600
        ParsedRecord parsed = ParsedRecord.builder()
                .fullName("Phạm Toàn Thắng")
                .rank("Đại tá")
                .birthDate(YearMonth.of(1968, 7))
                .enlistDate(YearMonth.of(1985, 9))
                .mergerDate(YearMonth.of(2025, 3))
                .retirementDate(YearMonth.of(2025, 6))
                .salary(new BigDecimal("31224960"))
                .monthsEarly(new BigDecimal("5"))
                .yearsEarly(new BigDecimal("1.5"))
                .build();

        CalculatedRecord result = calculationService.calculatePhuLucI3(parsed);

        Assertions.assertEquals(new BigDecimal("234187200"), result.getC1Amount());
        Assertions.assertEquals(new BigDecimal("156124800"), result.getC3BaseAmount());
        Assertions.assertEquals(new BigDecimal("312249600"), result.getC3ExtraAmount());
        Assertions.assertEquals(new BigDecimal("702561600"), result.getTotalAmount());
    }
}
