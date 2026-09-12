package com.bqpvalidateexcel.service;

import com.bqpvalidateexcel.model.*;
import com.bqpvalidateexcel.util.DateParserUtil;
import com.bqpvalidateexcel.util.NumberParserUtil;
import com.bqpvalidateexcel.util.RankUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class AuditService {

    private final PdfParserService pdfParserService;
    private final ExcelParserService excelParserService;
    private final CalculationService calculationService;

    @Autowired
    public AuditService(PdfParserService pdfParserService,
                        ExcelParserService excelParserService,
                        CalculationService calculationService) {
        this.pdfParserService = pdfParserService;
        this.excelParserService = excelParserService;
        this.calculationService = calculationService;
    }

    public AuditService(PdfParserService pdfParserService, CalculationService calculationService) {
        this(pdfParserService, new ExcelParserService(), calculationService);
    }

    /**
     * Rà soát đối soát 1 file đơn lẻ (hỗ trợ cả PDF và Excel .xlsx/.xls)
     */
    public AppendixAuditResult auditSingleFile(File file, AppendixType forcedType) throws Exception {
        RecordParserService.ExtractedData extracted;
        if (excelParserService.supports(file.getName())) {
            extracted = excelParserService.parse(file, forcedType);
        } else {
            extracted = pdfParserService.parse(file, forcedType);
        }

        AppendixType type = extracted.appendixType;

        List<AuditRecord> auditRecords = new ArrayList<>();
        BigDecimal totalDeclared = BigDecimal.ZERO;
        BigDecimal totalCalculated = BigDecimal.ZERO;
        BigDecimal totalDiff = BigDecimal.ZERO;
        int errorCount = 0;

        for (ParsedRecord parsed : extracted.records) {
            CalculatedRecord calculated = calculationService.calculate(parsed, type);
            AuditDifference diff = compare(parsed, calculated, type);

            if (diff.isHasError()) {
                errorCount++;
            }

            if (parsed.getDeclaredTotal() != null) {
                totalDeclared = totalDeclared.add(parsed.getDeclaredTotal());
            }
            if (calculated.getTotalAmount() != null) {
                totalCalculated = totalCalculated.add(calculated.getTotalAmount());
            }
            if (diff.getDiffAmount() != null) {
                totalDiff = totalDiff.add(diff.getDiffAmount());
            }

            auditRecords.add(AuditRecord.builder()
                    .appendixType(type)
                    .parsed(parsed)
                    .calculated(calculated)
                    .difference(diff)
                    .build());
        }

        int totalRec = auditRecords.size();
        int validRec = totalRec - errorCount;

        return AppendixAuditResult.builder()
                .appendixType(type)
                .originalFileName(file.getName())
                .inputFormat(extracted.inputFormat != null ? extracted.inputFormat : (file.getName().toLowerCase().endsWith(".pdf") ? "PDF" : "EXCEL"))
                .totalRecords(totalRec)
                .validRecords(validRec)
                .errorRecords(errorCount)
                .totalDeclaredMoney(totalDeclared)
                .totalCalculatedMoney(totalCalculated)
                .totalDifferenceMoney(totalDiff)
                .records(auditRecords)
                .headerRows(extracted.headerRows)
                .build();
    }

    /**
     * Alias tương thích ngược cho auditSinglePdf
     */
    public AppendixAuditResult auditSinglePdf(File pdfFile, AppendixType forcedType) throws Exception {
        return auditSingleFile(pdfFile, forcedType);
    }

    /**
     * Rà soát đa phụ lục (nộp 1, 2 hoặc 3 file PDF/Excel)
     */
    public MultiAuditReport auditMultiple(List<File> files, List<AppendixType> types) throws Exception {
        MultiAuditReport report = MultiAuditReport.builder()
                .reportId(UUID.randomUUID().toString())
                .createdAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")))
                .appendixResults(new ArrayList<>())
                .build();

        for (int i = 0; i < files.size(); i++) {
            File file = files.get(i);
            if (file == null || !file.exists()) continue;

            AppendixType type = (types != null && i < types.size()) ? types.get(i) : null;
            AppendixAuditResult result = auditSingleFile(file, type);
            report.getAppendixResults().add(result);
        }

        report.recalculateTotals();
        return report;
    }

    /**
     * So khớp dữ liệu khai báo trên PDF với dữ liệu do Engine tính toán
     */
    public AuditDifference compare(ParsedRecord parsed, CalculatedRecord calc, AppendixType type) {
        List<String> errors = new ArrayList<>();
        Set<Integer> wrongCols = new HashSet<>();

        boolean errCriteria = false;
        boolean errEarlyTime = false;
        boolean errBhxh = false;
        boolean errSalary = false;
        boolean errRegime = false;
        boolean errTotal = false;

        BigDecimal declaredTotal = (parsed.getDeclaredTotal() != null) ? parsed.getDeclaredTotal() : BigDecimal.ZERO;
        BigDecimal calcTotal = (calc.getTotalAmount() != null) ? calc.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal diffMoney = calcTotal.subtract(declaredTotal);

        // 1. Kiểm tra số tháng nghỉ sớm (Cột 10)
        if (parsed.getMonthsEarly() != null && parsed.getMonthsEarly().compareTo(BigDecimal.ZERO) > 0 && parsed.getBirthDate() != null && parsed.getRetirementDate() != null) {
            int declaredMonths = parsed.getMonthsEarly().intValue();
            int calcMonths = calc.getMonthsEarlyCalculated();
            if (Math.abs(declaredMonths - calcMonths) > 0) {
                errEarlyTime = true;
                wrongCols.add(10);
                errors.add(String.format("Sai số tháng nghỉ sớm: Khai %d tháng -> Đúng %d tháng", declaredMonths, calcMonths));
            }
        }

        // 2. Kiểm tra số năm nghỉ sớm (Cột 11)
        if (parsed.getYearsEarly() != null && parsed.getYearsEarly().compareTo(BigDecimal.ZERO) > 0) {
            if (parsed.getYearsEarly().compareTo(calc.getYearsEarlyCalculated()) != 0) {
                errEarlyTime = true;
                wrongCols.add(11);
                errors.add(String.format("Sai số năm nghỉ sớm: Khai %s năm -> Đúng %s năm",
                        NumberParserUtil.formatDecimal(parsed.getYearsEarly()),
                        NumberParserUtil.formatDecimal(calc.getYearsEarlyCalculated())));
            }
        }

        // 3. Kiểm tra số năm BHXH (Cột 12)
        if (parsed.getYearsBhxh() != null && parsed.getYearsBhxh().compareTo(BigDecimal.ZERO) > 0 && calc.getYearsBhxhCalculated() != null) {
            if (parsed.getYearsBhxh().compareTo(calc.getYearsBhxhCalculated()) != 0) {
                errBhxh = true;
                wrongCols.add(12);
                errors.add(String.format("Sai thâm niên BHXH: Khai %s năm -> Đúng %s năm",
                        NumberParserUtil.formatDecimal(parsed.getYearsBhxh()),
                        NumberParserUtil.formatDecimal(calc.getYearsBhxhCalculated())));
            }
        }

        // 4. Kiểm tra mức lương (Cột 9)
        if (parsed.getSalary() == null || parsed.getSalary().compareTo(BigDecimal.valueOf(1000)) < 0) {
            errSalary = true;
            wrongCols.add(9);
            errors.add("Mức lương khai báo không hợp lệ hoặc thiếu dữ liệu");
        }

        // 5. Kiểm tra các chế độ thành phần
        if (type == AppendixType.PHU_LUC_I1) {
            // C1 (Cột 13/14/15/16)
            int expectedC1Col = calc.isDT1() ? (calc.isTH1() ? 13 : 15) : (calc.isTH1() ? 14 : 16);
            if (parsed.getDeclaredC1() != null && parsed.getDeclaredC1().compareTo(BigDecimal.ZERO) > 0) {
                if (parsed.getDeclaredC1().subtract(calc.getC1Amount()).abs().compareTo(BigDecimal.valueOf(1000)) > 0) {
                    errRegime = true;
                    wrongCols.add(expectedC1Col);
                    errors.add(String.format("Sai Chế độ 1 (tháng nghỉ sớm): Khai %sđ -> Đúng %sđ",
                            NumberParserUtil.formatMoney(parsed.getDeclaredC1()),
                            NumberParserUtil.formatMoney(calc.getC1Amount())));
                }
            }

            // C2 (Cột 17 / 20)
            int expectedC2Col = calc.isDT1() ? 17 : 20;
            if (parsed.getDeclaredC2() != null && parsed.getDeclaredC2().compareTo(BigDecimal.ZERO) > 0) {
                if (parsed.getDeclaredC2().subtract(calc.getC2Amount()).abs().compareTo(BigDecimal.valueOf(1000)) > 0) {
                    errRegime = true;
                    wrongCols.add(expectedC2Col);
                    errors.add(String.format("Sai Chế độ 2 (năm nghỉ sớm): Khai %sđ -> Đúng %sđ",
                            NumberParserUtil.formatMoney(parsed.getDeclaredC2()),
                            NumberParserUtil.formatMoney(calc.getC2Amount())));
                }
            }

            // C3 Base (Cột 18 / 21) & C3 Extra (Cột 19 / 22)
            int expectedC3BaseCol = calc.isDT1() ? 18 : 21;
            int expectedC3ExtraCol = calc.isDT1() ? 19 : 22;
            if (parsed.getDeclaredC3Base() != null && parsed.getDeclaredC3Base().compareTo(BigDecimal.ZERO) > 0) {
                if (parsed.getDeclaredC3Base().subtract(calc.getC3BaseAmount()).abs().compareTo(BigDecimal.valueOf(1000)) > 0) {
                    errRegime = true;
                    wrongCols.add(expectedC3BaseCol);
                    errors.add(String.format("Sai BHXH cơ sở: Khai %sđ -> Đúng %sđ",
                            NumberParserUtil.formatMoney(parsed.getDeclaredC3Base()),
                            NumberParserUtil.formatMoney(calc.getC3BaseAmount())));
                }
            }
            if (parsed.getDeclaredC3Extra() != null && parsed.getDeclaredC3Extra().compareTo(BigDecimal.ZERO) > 0) {
                if (parsed.getDeclaredC3Extra().subtract(calc.getC3ExtraAmount()).abs().compareTo(BigDecimal.valueOf(1000)) > 0) {
                    errRegime = true;
                    wrongCols.add(expectedC3ExtraCol);
                    errors.add(String.format("Sai BHXH tăng thêm: Khai %sđ -> Đúng %sđ",
                            NumberParserUtil.formatMoney(parsed.getDeclaredC3Extra()),
                            NumberParserUtil.formatMoney(calc.getC3ExtraAmount())));
                }
            }

            // Tổng tiền Phụ lục I.1: Cột 23
            if (diffMoney.abs().compareTo(BigDecimal.valueOf(1000)) > 0) {
                errTotal = true;
                wrongCols.add(23);
                String sign = diffMoney.compareTo(BigDecimal.ZERO) > 0 ? "+" : "";
                errors.add(String.format("Lệch Tổng tiền: Khai %sđ, Tính đúng %sđ (Chênh lệch: %s%sđ)",
                        NumberParserUtil.formatMoney(declaredTotal),
                        NumberParserUtil.formatMoney(calcTotal),
                        sign,
                        NumberParserUtil.formatMoney(diffMoney)));
            }

        } else if (type == AppendixType.PHU_LUC_I2) {
            // Kiểm tra điều kiện tuổi đời còn lại > 2 năm
            int rankAgeCeiling = RankUtil.getRankAgeCeiling(parsed.getRank());
            YearMonth birth = parsed.getBirthDate();
            YearMonth retire = parsed.getRetirementDate();
            if (birth != null && retire != null) {
                int remMonths = DateParserUtil.monthsBetween(retire, birth.plusYears(rankAgeCeiling));
                if (remMonths <= 24) {
                    errCriteria = true;
                    wrongCols.add(2);
                    errors.add(String.format("Không đủ điều kiện thôi việc NĐ 178: Tuổi đời còn lại %d tháng (yêu cầu > 24 tháng)", remMonths));
                }
            }

            int colMonth = calc.isTH1() ? 12 : 15;
            int colYear = calc.isTH1() ? 13 : 16;
            int colJob = calc.isTH1() ? 14 : 17;

            if (parsed.getDeclaredC1() != null && parsed.getDeclaredC1().compareTo(BigDecimal.ZERO) > 0) {
                if (parsed.getDeclaredC1().subtract(calc.getC1Amount()).abs().compareTo(BigDecimal.valueOf(1000)) > 0) {
                    errRegime = true;
                    wrongCols.add(colMonth);
                    errors.add(String.format("Sai trợ cấp tháng BHXH: Khai %sđ -> Đúng %sđ",
                            NumberParserUtil.formatMoney(parsed.getDeclaredC1()),
                            NumberParserUtil.formatMoney(calc.getC1Amount())));
                }
            }
            if (parsed.getDeclaredC2() != null && parsed.getDeclaredC2().compareTo(BigDecimal.ZERO) > 0) {
                if (parsed.getDeclaredC2().subtract(calc.getC2Amount()).abs().compareTo(BigDecimal.valueOf(1000)) > 0) {
                    errRegime = true;
                    wrongCols.add(colYear);
                    errors.add(String.format("Sai trợ cấp năm công tác: Khai %sđ -> Đúng %sđ",
                            NumberParserUtil.formatMoney(parsed.getDeclaredC2()),
                            NumberParserUtil.formatMoney(calc.getC2Amount())));
                }
            }
            if (parsed.getDeclaredJobCreation() != null && parsed.getDeclaredJobCreation().compareTo(BigDecimal.ZERO) > 0) {
                if (parsed.getDeclaredJobCreation().subtract(calc.getJobCreationAmount()).abs().compareTo(BigDecimal.valueOf(1000)) > 0) {
                    errRegime = true;
                    wrongCols.add(colJob);
                    errors.add("Sai trợ cấp tạo việc làm");
                }
            }

            // Tổng tiền Phụ lục I.2: Cột 18
            if (diffMoney.abs().compareTo(BigDecimal.valueOf(1000)) > 0) {
                errTotal = true;
                wrongCols.add(18);
                String sign = diffMoney.compareTo(BigDecimal.ZERO) > 0 ? "+" : "";
                errors.add(String.format("Lệch Tổng tiền: Khai %sđ, Tính đúng %sđ (Chênh lệch: %s%sđ)",
                        NumberParserUtil.formatMoney(declaredTotal),
                        NumberParserUtil.formatMoney(calcTotal),
                        sign,
                        NumberParserUtil.formatMoney(diffMoney)));
            }

        } else if (type == AppendixType.PHU_LUC_I3) {
            // Cột 12: Trợ cấp năm nghỉ sớm
            if (parsed.getDeclaredC1() != null && parsed.getDeclaredC1().compareTo(BigDecimal.ZERO) > 0) {
                if (parsed.getDeclaredC1().subtract(calc.getC1Amount()).abs().compareTo(BigDecimal.valueOf(1000)) > 0) {
                    errRegime = true;
                    wrongCols.add(12);
                    errors.add(String.format("Sai trợ cấp năm nghỉ sớm: Khai %sđ -> Đúng %sđ",
                            NumberParserUtil.formatMoney(parsed.getDeclaredC1()),
                            NumberParserUtil.formatMoney(calc.getC1Amount())));
                }
            }
            // Cột 13: BHXH cơ sở
            if (parsed.getDeclaredC3Base() != null && parsed.getDeclaredC3Base().compareTo(BigDecimal.ZERO) > 0) {
                if (parsed.getDeclaredC3Base().subtract(calc.getC3BaseAmount()).abs().compareTo(BigDecimal.valueOf(1000)) > 0) {
                    errRegime = true;
                    wrongCols.add(13);
                    errors.add(String.format("Sai BHXH cơ sở: Khai %sđ -> Đúng %sđ",
                            NumberParserUtil.formatMoney(parsed.getDeclaredC3Base()),
                            NumberParserUtil.formatMoney(calc.getC3BaseAmount())));
                }
            }
            // Cột 14: BHXH tăng thêm
            if (parsed.getDeclaredC3Extra() != null && parsed.getDeclaredC3Extra().compareTo(BigDecimal.ZERO) > 0) {
                if (parsed.getDeclaredC3Extra().subtract(calc.getC3ExtraAmount()).abs().compareTo(BigDecimal.valueOf(1000)) > 0) {
                    errRegime = true;
                    wrongCols.add(14);
                    errors.add(String.format("Sai BHXH tăng thêm: Khai %sđ -> Đúng %sđ",
                            NumberParserUtil.formatMoney(parsed.getDeclaredC3Extra()),
                            NumberParserUtil.formatMoney(calc.getC3ExtraAmount())));
                }
            }

            // Tổng tiền Phụ lục I.3: Cột 15
            if (diffMoney.abs().compareTo(BigDecimal.valueOf(1000)) > 0) {
                errTotal = true;
                wrongCols.add(15);
                String sign = diffMoney.compareTo(BigDecimal.ZERO) > 0 ? "+" : "";
                errors.add(String.format("Lệch Tổng tiền: Khai %sđ, Tính đúng %sđ (Chênh lệch: %s%sđ)",
                        NumberParserUtil.formatMoney(declaredTotal),
                        NumberParserUtil.formatMoney(calcTotal),
                        sign,
                        NumberParserUtil.formatMoney(diffMoney)));
            }
        }

        boolean hasError = !wrongCols.isEmpty() || errCriteria || errEarlyTime || errBhxh || errSalary || errRegime || errTotal;

        String detailedExplanation = hasError ? String.join("; ", errors) : "Hồ sơ chính xác, khớp số liệu";
        String reviewNote;
        if (!hasError) {
            reviewNote = "Hợp lệ (Khớp 100%)";
        } else {
            String sign = diffMoney.compareTo(BigDecimal.ZERO) > 0 ? "+" : "";
            reviewNote = String.format("SAI LỆCH: %s | Chênh lệch: %s%sđ",
                    String.join(", ", errors), sign, NumberParserUtil.formatMoney(diffMoney));
        }

        return AuditDifference.builder()
                .hasError(hasError)
                .errCriteria(errCriteria)
                .errEarlyTime(errEarlyTime)
                .errBhxhSeniority(errBhxh)
                .errSalary(errSalary)
                .errRegimeFormula(errRegime)
                .errTotalAmount(errTotal)
                .wrongColumnIndices(wrongCols)
                .diffAmount(diffMoney)
                .errorDetails(errors)
                .detailedExplanation(detailedExplanation)
                .reviewNote(reviewNote)
                .build();
    }
}
