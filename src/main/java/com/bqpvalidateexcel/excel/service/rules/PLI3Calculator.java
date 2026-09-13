package com.bqpvalidateexcel.excel.service.rules;

import com.bqpvalidateexcel.excel.model.dto.PhuLucI3;
import com.bqpvalidateexcel.excel.model.expected.PLI3ExpectedResult;

import java.math.BigDecimal;
import java.util.Date;

public class PLI3Calculator {

    public static PLI3ExpectedResult calculateExpected(PhuLucI3 data) {
        int tran = getTran(data.getCapBac(), data.getChucVu());
        
        BigDecimal cot10 = BigDecimal.ZERO;
        BigDecimal cot11 = BigDecimal.ZERO;
        
        // Tính Cột 10: cột 8 - cột 5 (Nghỉ hưu - Nhập ngũ)
        if (data.getNhapNgu() != null && data.getThoiDiemNghiHuuHuongTroCap() != null) {
            java.util.Calendar calN = java.util.Calendar.getInstance();
            calN.setTime(data.getNhapNgu());
            int nYear = calN.get(java.util.Calendar.YEAR);
            int nMonth = calN.get(java.util.Calendar.MONTH);
            
            java.util.Calendar calRetire = java.util.Calendar.getInstance();
            calRetire.setTime(data.getThoiDiemNghiHuuHuongTroCap());
            int rYear = calRetire.get(java.util.Calendar.YEAR);
            int rMonth = calRetire.get(java.util.Calendar.MONTH);
            
            int diffMonths = (rYear * 12 + rMonth) - (nYear * 12 + nMonth);
            if (diffMonths >= 0) {
                int years = diffMonths / 12;
                int months = diffMonths % 12;
                if (months == 0) {
                    cot10 = BigDecimal.valueOf(years);
                } else if (months <= 6) {
                    cot10 = BigDecimal.valueOf(years).add(BigDecimal.valueOf(0.5));
                } else {
                    cot10 = BigDecimal.valueOf(years).add(BigDecimal.valueOf(1.0));
                }
            }
        }
        
        // Tính Cột 11: (cột 2 + trần) - cột 8
        if (tran > 0 && data.getNgaySinh() != null && data.getThoiDiemNghiHuuHuongTroCap() != null) {
            java.util.Calendar calDob = java.util.Calendar.getInstance();
            calDob.setTime(data.getNgaySinh());
            int expectedRetireYear = calDob.get(java.util.Calendar.YEAR) + tran;
            int expectedRetireMonth = calDob.get(java.util.Calendar.MONTH);
            
            java.util.Calendar calRetire = java.util.Calendar.getInstance();
            calRetire.setTime(data.getThoiDiemNghiHuuHuongTroCap());
            int retireYear = calRetire.get(java.util.Calendar.YEAR);
            int retireMonth = calRetire.get(java.util.Calendar.MONTH);
            
            int diffMonths11 = (expectedRetireYear * 12 + expectedRetireMonth) - (retireYear * 12 + retireMonth);
            if (diffMonths11 >= 0) {
                int years11 = diffMonths11 / 12;
                int months11 = diffMonths11 % 12;
                if (months11 == 0) {
                    cot11 = BigDecimal.valueOf(years11);
                } else if (months11 < 6) {
                    cot11 = BigDecimal.valueOf(years11).add(BigDecimal.valueOf(0.5));
                } else {
                    cot11 = BigDecimal.valueOf(years11).add(BigDecimal.valueOf(1.0));
                }
            }
        }
        
        BigDecimal luong = data.getLuongThangHienThuongTheoHuongDan() != null ? data.getLuongThangHienThuongTheoHuongDan() : BigDecimal.ZERO;
        
        BigDecimal c11ForCalc = (data.getSoNamHuongTroCapTheoHuongDan() != null && data.getSoNamHuongTroCapTheoHuongDan().compareTo(BigDecimal.ZERO) > 0)
                ? data.getSoNamHuongTroCapTheoHuongDan()
                : cot11;

        BigDecimal expectedCol12 = c11ForCalc.multiply(BigDecimal.valueOf(5)).multiply(luong);
        BigDecimal expectedCol13 = luong.multiply(BigDecimal.valueOf(5));
        
        boolean nghiTruoc172025 = true;
        if (data.getThoiDiemNghiHuuHuongTroCap() != null) {
            try {
                Date threshold = new java.text.SimpleDateFormat("dd/MM/yyyy").parse("01/07/2025");
                if (!data.getThoiDiemNghiHuuHuongTroCap().before(threshold)) {
                    nghiTruoc172025 = false;
                }
            } catch (Exception ignored) {}
        }

        BigDecimal expectedCol14 = BigDecimal.ZERO;
        BigDecimal moc14 = nghiTruoc172025 ? BigDecimal.valueOf(20) : BigDecimal.valueOf(15);
        if (cot10.compareTo(moc14) > 0) {
            expectedCol14 = luong.multiply(BigDecimal.valueOf(0.5)).multiply(cot10.subtract(moc14));
        }
        
        BigDecimal expectedCol15 = expectedCol12.add(expectedCol13).add(expectedCol14);

        return PLI3ExpectedResult.builder()
                .cot10(cot10)
                .cot11(c11ForCalc)
                .cot12(expectedCol12)
                .cot13(expectedCol13)
                .cot14(expectedCol14)
                .cot15(expectedCol15)
                .build();
    }
    
    private static int getTran(String capBac, String chucVu) {
        if (capBac == null) return 0;
        String cb = capBac.replace('\u00A0', ' ').trim().toLowerCase().replaceAll("\\s+", " ");
        String cv = (chucVu != null) ? chucVu.replace('\u00A0', ' ').trim().toLowerCase().replaceAll("\\s+", " ") : "";
        boolean isQNCN = cv.contains("nhân viên") || cv.contains("lái xe") || cv.contains("thợ") 
                      || cv.contains("chạm") || cv.contains("trạm") || cb.contains("qncn") || cv.contains("qncn");
        
        if (cb.contains("đại tá")) return 58;
        if (cb.contains("thượng tá")) return isQNCN ? 56 : 56;
        if (cb.contains("trung tá")) return isQNCN ? 54 : 54;
        if (cb.contains("thiếu tá")) return isQNCN ? 54 : 52;
        if (cb.contains("uý") || cb.contains("úy")) return isQNCN ? 52 : 50;
        return 0;
    }
}
