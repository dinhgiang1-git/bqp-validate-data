package com.bqpvalidateexcel.service;

import com.bqpvalidateexcel.model.AppendixType;
import com.bqpvalidateexcel.model.CalculatedRecord;
import com.bqpvalidateexcel.model.ParsedRecord;
import com.bqpvalidateexcel.util.DateParserUtil;
import com.bqpvalidateexcel.util.NumberParserUtil;
import com.bqpvalidateexcel.util.RankUtil;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;

@Service
public class CalculationService {

    private static final YearMonth JULY_2025 = YearMonth.of(2025, 7);

    /**
     * Tính toán độc lập chế độ chính sách cho 1 cá nhân dựa theo loại phụ lục
     */
    public CalculatedRecord calculate(ParsedRecord parsed, AppendixType type) {
        if (type == null) type = AppendixType.PHU_LUC_I1;

        switch (type) {
            case PHU_LUC_I1:
                return calculatePhuLucI1(parsed);
            case PHU_LUC_I2:
                return calculatePhuLucI2(parsed);
            case PHU_LUC_I3:
                return calculatePhuLucI3(parsed);
            default:
                return calculatePhuLucI1(parsed);
        }
    }

    /**
     * Tính toán Phụ lục I.1 (Nghỉ hưu trước tuổi - NĐ 178/2024)
     */
    public CalculatedRecord calculatePhuLucI1(ParsedRecord parsed) {
        int rankAgeCeiling = RankUtil.getRankAgeCeiling(parsed.getRank());
        YearMonth birth = parsed.getBirthDate();
        YearMonth retire = parsed.getRetirementDate();
        YearMonth enlist = parsed.getEnlistDate();
        YearMonth merger = parsed.getMergerDate();
        BigDecimal salary = (parsed.getSalary() != null) ? parsed.getSalary() : BigDecimal.ZERO;

        // 1. Tính số tháng nghỉ sớm (Cột 10) = (Cột 2 + tranQuanHam) - Cột 8
        int monthsEarly = 0;
        if (birth != null && retire != null) {
            YearMonth retireAgeLimit = birth.plusYears(rankAgeCeiling);
            monthsEarly = Math.max(0, DateParserUtil.monthsBetween(retire, retireAgeLimit));
        } else if (parsed.getMonthsEarly() != null) {
            monthsEarly = parsed.getMonthsEarly().intValue();
        }

        // 2. Số năm nghỉ sớm (Cột 11) = Cột 10 / 12 quy đổi làm tròn tháng lẻ (<= 0.5 -> 0.5, > 0.5 -> 1.0)
        BigDecimal yearsEarly = NumberParserUtil.roundMonthsToYears(monthsEarly);

        // 3. Số năm đóng BHXH (Cột 12) = Cột 8 - Cột 5 quy đổi làm tròn tháng lẻ
        int totalMonthsBhxh = 0;
        if (enlist != null && retire != null) {
            totalMonthsBhxh = Math.max(0, DateParserUtil.monthsBetween(enlist, retire));
        }
        BigDecimal yearsBhxh = (totalMonthsBhxh > 0)
                ? NumberParserUtil.roundMonthsToYears(totalMonthsBhxh)
                : (parsed.getYearsBhxh() != null ? parsed.getYearsBhxh() : BigDecimal.ZERO);

        // 4. Phân loại Nhóm A (Cột 10 <= 60 tháng) / Nhóm B (Cột 10 > 60 tháng)
        boolean isDT1 = (monthsEarly <= 60);

        // 5. Phân loại TH1 (<= 12 tháng sáp nhập) / TH2 (> 12 tháng)
        int diffMerger = (merger != null && retire != null) ? DateParserUtil.monthsBetween(merger, retire) : 0;
        boolean isTH1 = (diffMerger <= 12);

        // 6. Nhóm 1: Trợ cấp 1 lần cho thời gian nghỉ sớm (Cột 13, 14, 15, 16)
        BigDecimal c1 = BigDecimal.ZERO;
        if (isTH1) {
            if (isDT1) {
                // Cột 13: Cột 10 * 1.0 * Lương
                c1 = BigDecimal.valueOf(monthsEarly).multiply(BigDecimal.ONE).multiply(salary);
            } else {
                // Cột 14: Cột 10 * 0.9 * Lương
                c1 = BigDecimal.valueOf(monthsEarly).multiply(new BigDecimal("0.9")).multiply(salary);
            }
        } else {
            if (isDT1) {
                // Cột 15: Cột 10 * 0.5 * Lương
                c1 = BigDecimal.valueOf(monthsEarly).multiply(new BigDecimal("0.5")).multiply(salary);
            } else {
                // Cột 16: Cột 10 * 0.45 * Lương
                c1 = BigDecimal.valueOf(monthsEarly).multiply(new BigDecimal("0.45")).multiply(salary);
            }
        }
        c1 = c1.setScale(0, RoundingMode.HALF_UP);

        // 7. Nhóm 2: Phân nhánh theo thời gian nghỉ sớm (Cột 17..19 vs Cột 20..22)
        boolean isBeforeJuly2025 = (retire == null || retire.isBefore(JULY_2025));
        BigDecimal c2 = BigDecimal.ZERO;
        BigDecimal c3Base = BigDecimal.ZERO;
        BigDecimal c3Extra = BigDecimal.ZERO;

        if (isDT1) {
            // Nhánh A (Cột 10 <= 60):
            // Cột 17 = Cột 11 * 5 * Cột 9
            c2 = yearsEarly.multiply(BigDecimal.valueOf(5)).multiply(salary).setScale(0, RoundingMode.HALF_UP);

            // Cột 18: Nghỉ trước 01/07/2025: 5 * Lương; Từ 01/07/2025 trở đi: 4 * Lương
            c3Base = isBeforeJuly2025 ? salary.multiply(BigDecimal.valueOf(5)) : salary.multiply(BigDecimal.valueOf(4));
            c3Base = c3Base.setScale(0, RoundingMode.HALF_UP);

            // Cột 19:
            // Nghỉ trước 01/07/2025: Cột 9 * 0.5 * (Cột 12 - 20) (nếu Cột 12 > 20)
            // Nghỉ từ 01/07/2025:    Cột 9 * 0.4 * (Cột 12 - 15) (nếu Cột 12 > 15)
            if (isBeforeJuly2025) {
                if (yearsBhxh.compareTo(BigDecimal.valueOf(20)) > 0) {
                    BigDecimal extraYears = yearsBhxh.subtract(BigDecimal.valueOf(20));
                    c3Extra = salary.multiply(new BigDecimal("0.5")).multiply(extraYears);
                }
            } else {
                if (yearsBhxh.compareTo(BigDecimal.valueOf(15)) > 0) {
                    BigDecimal extraYears = yearsBhxh.subtract(BigDecimal.valueOf(15));
                    c3Extra = salary.multiply(new BigDecimal("0.4")).multiply(extraYears);
                }
            }
            c3Extra = c3Extra.setScale(0, RoundingMode.HALF_UP);

        } else {
            // Nhánh B (Cột 10 > 60):
            // Cột 20 = Cột 11 * 4 * Cột 9
            c2 = yearsEarly.multiply(BigDecimal.valueOf(4)).multiply(salary).setScale(0, RoundingMode.HALF_UP);

            // Cột 21: Nghỉ trước 01/07/2025: 5 * Lương; Từ 01/07/2025 trở đi: 4 * Lương
            c3Base = isBeforeJuly2025 ? salary.multiply(BigDecimal.valueOf(5)) : salary.multiply(BigDecimal.valueOf(4));
            c3Base = c3Base.setScale(0, RoundingMode.HALF_UP);

            // Cột 22:
            // Nghỉ trước 01/07/2025: Cột 9 * 0.5 * (Cột 12 - 20) (nếu Cột 12 > 20)
            // Nghỉ từ 01/07/2025:    Cột 9 * 0.4 * (Cột 12 - 15) (nếu Cột 12 > 15)
            if (isBeforeJuly2025) {
                if (yearsBhxh.compareTo(BigDecimal.valueOf(20)) > 0) {
                    BigDecimal extraYears = yearsBhxh.subtract(BigDecimal.valueOf(20));
                    c3Extra = salary.multiply(new BigDecimal("0.5")).multiply(extraYears);
                }
            } else {
                if (yearsBhxh.compareTo(BigDecimal.valueOf(15)) > 0) {
                    BigDecimal extraYears = yearsBhxh.subtract(BigDecimal.valueOf(15));
                    c3Extra = salary.multiply(new BigDecimal("0.4")).multiply(extraYears);
                }
            }
            c3Extra = c3Extra.setScale(0, RoundingMode.HALF_UP);
        }

        // Cột 23 (Tổng cộng)
        BigDecimal total = c1.add(c2).add(c3Base).add(c3Extra);

        return CalculatedRecord.builder()
                .rankAgeCeiling(rankAgeCeiling)
                .monthsEarlyCalculated(monthsEarly)
                .yearsEarlyCalculated(yearsEarly)
                .totalMonthsBhxh(totalMonthsBhxh)
                .yearsBhxhCalculated(yearsBhxh)
                .isDT1(isDT1)
                .isTH1(isTH1)
                .isBeforeJuly2025(isBeforeJuly2025)
                .c1Amount(c1)
                .c2Amount(c2)
                .c3BaseAmount(c3Base)
                .c3ExtraAmount(c3Extra)
                .jobCreationAmount(BigDecimal.ZERO)
                .totalAmount(total)
                .build();
    }

    /**
     * Tính toán Phụ lục I.2 (Thôi việc / Phục viên - NĐ 178/2024)
     */
    public CalculatedRecord calculatePhuLucI2(ParsedRecord parsed) {
        int rankAgeCeiling = RankUtil.getRankAgeCeiling(parsed.getRank());
        YearMonth birth = parsed.getBirthDate();
        YearMonth retire = parsed.getRetirementDate();
        YearMonth enlist = parsed.getEnlistDate();
        YearMonth merger = parsed.getMergerDate();
        BigDecimal salary = (parsed.getSalary() != null) ? parsed.getSalary() : BigDecimal.ZERO;

        if (salary.compareTo(BigDecimal.valueOf(1000)) < 0 && parsed.getDeclaredJobCreation() != null && parsed.getDeclaredJobCreation().compareTo(BigDecimal.ZERO) > 0) {
            salary = parsed.getDeclaredJobCreation().divide(BigDecimal.valueOf(3), 0, RoundingMode.HALF_UP);
        }

        // 1. Cột 10 = Cột 8 - Cột 5 (tính bằng số tháng công tác đóng BHXH)
        int totalMonthsBhxh = 0;
        if (enlist != null && retire != null) {
            totalMonthsBhxh = Math.max(0, DateParserUtil.monthsBetween(enlist, retire));
        } else if (parsed.getMonthsEarly() != null) {
            totalMonthsBhxh = parsed.getMonthsEarly().intValue();
        }
        int monthsCongTac = Math.min(totalMonthsBhxh, 60);

        // 2. Cột 11 = Cột 10 / 12 (quy đổi ra năm, phần lẻ <= 0.5 tính 0.5, > 0.5 tính 1.0)
        BigDecimal yearsCongTac = (totalMonthsBhxh > 0)
                ? NumberParserUtil.roundMonthsToYears(totalMonthsBhxh)
                : (parsed.getYearsEarly() != null ? parsed.getYearsEarly() : BigDecimal.ZERO);

        // 3. Điều kiện chung: Tuổi đời còn lại ((Cột 2 + tranQuanHam) - Cột 8) > 2 năm (> 24 tháng)
        int remainingMonths = 999;
        if (birth != null && retire != null) {
            YearMonth ceilingDate = birth.plusYears(rankAgeCeiling);
            remainingMonths = DateParserUtil.monthsBetween(retire, ceilingDate);
        }
        boolean isEligible = (remainingMonths > 24);

        // 4. Phân loại TH1 (<= 12 tháng sáp nhập) / TH2 (> 12 tháng)
        int diffMerger = (merger != null && retire != null) ? DateParserUtil.monthsBetween(merger, retire) : 0;
        boolean isTH1 = (diffMerger <= 12);

        BigDecimal cMonth = BigDecimal.ZERO; // Cột 12 hoặc 15
        BigDecimal cYear = BigDecimal.ZERO;  // Cột 13 hoặc 16
        BigDecimal cJob = BigDecimal.ZERO;   // Cột 14 hoặc 17

        if (isEligible) {
            // TH1: (Cột 8 - Cột 7) <= 12 tháng
            // Cột 12 = Cột 10 * 0.8 * Cột 9
            // Cột 13 = Cột 11 * 1.5 * Cột 9
            // Cột 14 = 3 * Cột 9
            // TH2: (Cột 8 - Cột 7) > 12 tháng
            // Cột 15 = Cột 10 * 0.4 * Cột 9
            // Cột 16 = Cột 11 * 1.5 * Cột 9
            // Cột 17 = 3 * Cột 9
            BigDecimal factorMonth = isTH1 ? new BigDecimal("0.8") : new BigDecimal("0.4");
            cMonth = BigDecimal.valueOf(monthsCongTac).multiply(factorMonth).multiply(salary).setScale(0, RoundingMode.HALF_UP);
            cYear = yearsCongTac.multiply(new BigDecimal("1.5")).multiply(salary).setScale(0, RoundingMode.HALF_UP);
            cJob = salary.multiply(BigDecimal.valueOf(3)).setScale(0, RoundingMode.HALF_UP);
        }

        // Cột 18 (Tổng cộng) = SUM(Cột 12 -> Cột 17)
        BigDecimal total = cMonth.add(cYear).add(cJob);

        return CalculatedRecord.builder()
                .rankAgeCeiling(rankAgeCeiling)
                .monthsEarlyCalculated(monthsCongTac)
                .yearsEarlyCalculated(yearsCongTac)
                .totalMonthsBhxh(totalMonthsBhxh)
                .yearsBhxhCalculated(yearsCongTac)
                .isDT1(true)
                .isTH1(isTH1)
                .isBeforeJuly2025(retire == null || retire.isBefore(JULY_2025))
                .c1Amount(cMonth)
                .c2Amount(cYear)
                .c3BaseAmount(BigDecimal.ZERO)
                .c3ExtraAmount(BigDecimal.ZERO)
                .jobCreationAmount(cJob)
                .totalAmount(total)
                .build();
    }

    /**
     * Tính toán Phụ lục I.3 (Không tái cử / Tái bổ nhiệm - NĐ 177/2024)
     */
    public CalculatedRecord calculatePhuLucI3(ParsedRecord parsed) {
        int rankAgeCeiling = RankUtil.getRankAgeCeiling(parsed.getRank());
        YearMonth birth = parsed.getBirthDate();
        YearMonth retire = parsed.getRetirementDate();
        YearMonth enlist = parsed.getEnlistDate();
        BigDecimal salary = (parsed.getSalary() != null) ? parsed.getSalary() : BigDecimal.ZERO;

        // 1. Cột 10 = (Cột 2 + tranQuanHam) - Cột 8 (tính bằng số tháng nghỉ sớm)
        int monthsEarly = 0;
        if (birth != null && retire != null) {
            YearMonth retireAgeLimit = birth.plusYears(rankAgeCeiling);
            monthsEarly = Math.max(0, DateParserUtil.monthsBetween(retire, retireAgeLimit));
        } else if (parsed.getMonthsEarly() != null && parsed.getMonthsEarly().compareTo(BigDecimal.ZERO) > 0) {
            monthsEarly = parsed.getMonthsEarly().intValue();
        }

        // 2. Cột 11 = Số năm nghỉ sớm quy đổi từ Cột 10 (phần lẻ < 6 tháng cộng 0.5 năm, >= 6 tháng cộng 1.0 năm)
        BigDecimal yearsEarly = (parsed.getYearsEarly() != null && parsed.getYearsEarly().compareTo(BigDecimal.ZERO) > 0)
                ? parsed.getYearsEarly()
                : NumberParserUtil.roundMonthsToYearsNd177(monthsEarly);

        // 3. Số năm đóng BHXH = Cột 8 - Cột 5 quy đổi ra năm
        int totalMonthsBhxh = 0;
        if (enlist != null && retire != null) {
            totalMonthsBhxh = Math.max(0, DateParserUtil.monthsBetween(enlist, retire));
        }
        BigDecimal yearsBhxh = (totalMonthsBhxh > 0)
                ? NumberParserUtil.roundMonthsToYears(totalMonthsBhxh)
                : (parsed.getYearsBhxh() != null ? parsed.getYearsBhxh() : BigDecimal.ZERO);

        // 4. Cột 12 = Cột 11 * 5 * Cột 9 (trợ cấp năm nghỉ sớm)
        BigDecimal c12 = yearsEarly.multiply(BigDecimal.valueOf(5)).multiply(salary).setScale(0, RoundingMode.HALF_UP);

        // 5. Cột 13 = 5 * Cột 9 (hoặc 4 * Cột 9 nếu từ 01/07/2025)
        boolean isBeforeJuly2025 = (retire == null || retire.isBefore(JULY_2025));
        BigDecimal c13 = isBeforeJuly2025 ? salary.multiply(BigDecimal.valueOf(5)) : salary.multiply(BigDecimal.valueOf(4));
        c13 = c13.setScale(0, RoundingMode.HALF_UP);

        // 6. Cột 14 = Cột 9 * 0.5 * (Cột BHXH - 20) nếu trước 01/07/2025; hoặc (Cột BHXH - 15) nếu từ 01/07/2025
        BigDecimal c14 = BigDecimal.ZERO;
        if (isBeforeJuly2025) {
            if (yearsBhxh.compareTo(BigDecimal.valueOf(20)) > 0) {
                BigDecimal extraYears = yearsBhxh.subtract(BigDecimal.valueOf(20));
                c14 = salary.multiply(new BigDecimal("0.5")).multiply(extraYears);
            }
        } else {
            if (yearsBhxh.compareTo(BigDecimal.valueOf(15)) > 0) {
                BigDecimal extraYears = yearsBhxh.subtract(BigDecimal.valueOf(15));
                c14 = salary.multiply(new BigDecimal("0.5")).multiply(extraYears);
            }
        }
        c14 = c14.setScale(0, RoundingMode.HALF_UP);

        // Cột 15 (Tổng cộng) = Cột 12 + Cột 13 + Cột 14
        BigDecimal total = c12.add(c13).add(c14);

        return CalculatedRecord.builder()
                .rankAgeCeiling(rankAgeCeiling)
                .monthsEarlyCalculated(monthsEarly)
                .yearsEarlyCalculated(yearsEarly)
                .totalMonthsBhxh(totalMonthsBhxh)
                .yearsBhxhCalculated(yearsBhxh)
                .isDT1(monthsEarly <= 60)
                .isTH1(true)
                .isBeforeJuly2025(isBeforeJuly2025)
                .c1Amount(c12)
                .c2Amount(BigDecimal.ZERO)
                .c3BaseAmount(c13)
                .c3ExtraAmount(c14)
                .jobCreationAmount(BigDecimal.ZERO)
                .totalAmount(total)
                .build();
    }
}
