package com.bqpvalidateexcel.excel.service.rules;

import com.bqpvalidateexcel.excel.model.dto.PhuLucI1;
import com.bqpvalidateexcel.excel.model.expected.PLI1ExpectedResult;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;

public class PLI1Calculator {

    public static PLI1ExpectedResult calculateExpected(PhuLucI1 data) {
        int tran = getTran(data.getCapBac(), data.getChucVu());
        int cot10 = 0;
        if (tran > 0 && data.getNgaySinh() != null && data.getThoiDiemNghiHuuHuongTroCap() != null) {
            java.util.Calendar calDob = java.util.Calendar.getInstance();
            calDob.setTime(data.getNgaySinh());
            int expectedRetireYear = calDob.get(java.util.Calendar.YEAR) + tran;
            int expectedRetireMonth = calDob.get(java.util.Calendar.MONTH); // Trở về đúng tháng sinh, KHÔNG cộng thêm 1
            
            java.util.Calendar calRetire = java.util.Calendar.getInstance();
            calRetire.setTime(data.getThoiDiemNghiHuuHuongTroCap());
            int retireYear = calRetire.get(java.util.Calendar.YEAR);
            int retireMonth = calRetire.get(java.util.Calendar.MONTH);
            
            int diff = (expectedRetireYear * 12 + expectedRetireMonth) - (retireYear * 12 + retireMonth);
            // Tính số tháng nghỉ hưu trước tuổi bao gồm cả 2 đầu tháng (cộng thêm 1)
            cot10 = diff > 0 ? diff + 1 : 0;
        }
        if (cot10 < 0) cot10 = 0;
        int cappedCot10 = Math.min(cot10, 60);

        BigDecimal exp11 = calcNamLamTron(cot10);
        int monthsC12 = calcThang(data.getThoiDiemNghiHuuHuongTroCap(), data.getNhapNgu());
        BigDecimal exp12 = calcNamLamTron(monthsC12);

        BigDecimal luong = data.getLuongThangHienThuongTheoThongTu() != null ? data.getLuongThangHienThuongTheoThongTu() : BigDecimal.ZERO;

        boolean nghiTruoc172025 = true;
        if (data.getThoiDiemNghiHuuHuongTroCap() != null) {
            try {
                Date threshold = new SimpleDateFormat("dd/MM/yyyy").parse("01/07/2025");
                if (data.getThoiDiemNghiHuuHuongTroCap().after(threshold)) {
                    nghiTruoc172025 = false;
                }
            } catch (Exception ignored) {}
        }

        int timeDiff = 0;
        if (data.getThoiDiemNghiHuuHuongTroCap() != null && data.getThoiGianDonViSapNhapGiaiThe() != null) {
            timeDiff = calcThang(data.getThoiDiemNghiHuuHuongTroCap(), data.getThoiGianDonViSapNhapGiaiThe());
        }
        boolean nhoHon12 = (timeDiff < 12);

        BigDecimal expectedCol13 = BigDecimal.ZERO;
        BigDecimal expectedCol14 = BigDecimal.ZERO;
        BigDecimal expectedCol15 = BigDecimal.ZERO;
        BigDecimal expectedCol16 = BigDecimal.ZERO;

        if (nhoHon12) {
            if (cot10 <= 60) {
                expectedCol13 = BigDecimal.valueOf(cappedCot10).multiply(BigDecimal.valueOf(1.0)).multiply(luong);
            } else {
                expectedCol14 = BigDecimal.valueOf(cappedCot10).multiply(BigDecimal.valueOf(0.9)).multiply(luong);
            }
        } else {
            if (cot10 <= 60) {
                expectedCol15 = BigDecimal.valueOf(cappedCot10).multiply(BigDecimal.valueOf(0.5)).multiply(luong);
            } else {
                expectedCol16 = BigDecimal.valueOf(cappedCot10).multiply(BigDecimal.valueOf(0.45)).multiply(luong);
            }
        }

        BigDecimal expectedCol17 = BigDecimal.ZERO;
        BigDecimal expectedCol18 = BigDecimal.ZERO;
        BigDecimal expectedCol19 = BigDecimal.ZERO;
        BigDecimal expectedCol20 = BigDecimal.ZERO;
        BigDecimal expectedCol21 = BigDecimal.ZERO;
        BigDecimal expectedCol22 = BigDecimal.ZERO;

        BigDecimal val18_21 = BigDecimal.ZERO;
        BigDecimal val19_22 = BigDecimal.ZERO;
        
        if (nghiTruoc172025) {
            if (exp12.compareTo(BigDecimal.valueOf(20)) > 0) {
                val18_21 = luong.multiply(BigDecimal.valueOf(5));
                val19_22 = luong.multiply(BigDecimal.valueOf(0.5)).multiply(exp12.subtract(BigDecimal.valueOf(20)));
            }
        } else {
            if (exp12.compareTo(BigDecimal.valueOf(15)) > 0) {
                val18_21 = luong.multiply(BigDecimal.valueOf(4));
                val19_22 = luong.multiply(BigDecimal.valueOf(0.5)).multiply(exp12.subtract(BigDecimal.valueOf(15)));
            }
        }

        boolean cond17_19 = exp11.compareTo(BigDecimal.valueOf(2)) >= 0 && exp11.compareTo(BigDecimal.valueOf(5)) <= 0;
        boolean cond20_22 = exp11.compareTo(BigDecimal.valueOf(5)) > 0 && exp11.compareTo(BigDecimal.valueOf(10)) <= 0;

        if (cond17_19) {
            expectedCol17 = exp11.multiply(BigDecimal.valueOf(5)).multiply(luong);
            expectedCol18 = val18_21;
            expectedCol19 = val19_22;
        } else if (cond20_22) {
            expectedCol20 = exp11.multiply(BigDecimal.valueOf(4)).multiply(luong);
            expectedCol21 = val18_21;
            expectedCol22 = val19_22;
        }

        return PLI1ExpectedResult.builder()
                .cot10(cot10)
                .cot11(exp11)
                .cot12(exp12)
                .cot13(expectedCol13)
                .cot14(expectedCol14)
                .cot15(expectedCol15)
                .cot16(expectedCol16)
                .cot17(expectedCol17)
                .cot18(expectedCol18)
                .cot19(expectedCol19)
                .cot20(expectedCol20)
                .cot21(expectedCol21)
                .cot22(expectedCol22)
                .build();
    }

    private static int calcThang(Date d1, Date d2) {
        if (d1 == null || d2 == null) return 0;
        java.util.Calendar c1 = java.util.Calendar.getInstance(); c1.setTime(d1);
        java.util.Calendar c2 = java.util.Calendar.getInstance(); c2.setTime(d2);
        int m1 = c1.get(java.util.Calendar.YEAR) * 12 + c1.get(java.util.Calendar.MONTH);
        int m2 = c2.get(java.util.Calendar.YEAR) * 12 + c2.get(java.util.Calendar.MONTH);
        return m1 - m2;
    }

    private static BigDecimal calcNamLamTron(int thang) {
        if (thang <= 0) return BigDecimal.ZERO;
        int years = thang / 12;
        int rem = thang % 12;
        if (rem == 0) return BigDecimal.valueOf(years);
        if (rem <= 6) return BigDecimal.valueOf(years).add(BigDecimal.valueOf(0.5));
        return BigDecimal.valueOf(years).add(BigDecimal.ONE);
    }
    
    private static int getTran(String capBac, String chucVu) {
        if (capBac == null) return 0;
        String cb = capBac.toLowerCase();
        String cv = (chucVu != null) ? chucVu.toLowerCase() : "";
        boolean isQNCN = cv.contains("nhân viên") || cv.contains("lái xe") || cv.contains("thợ") 
                      || cv.contains("chạm") || cv.contains("trạm") || cb.contains("qncn");
        
        if (cb.contains("đại tá")) return 58;
        if (cb.contains("thượng tá")) return isQNCN ? 56 : 56;
        if (cb.contains("trung tá")) return isQNCN ? 54 : 54;
        if (cb.contains("thiếu tá")) return isQNCN ? 54 : 52;
        if (cb.contains("uý") || cb.contains("úy")) return isQNCN ? 52 : 50;
        return 0;
    }
}
