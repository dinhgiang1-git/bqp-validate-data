package com.bqpvalidateexcel.excel.service.rules;

import com.bqpvalidateexcel.excel.model.dto.PhuLucI2;
import com.bqpvalidateexcel.excel.model.expected.PLI2ExpectedResult;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Date;

public class PLI2Calculator {

    public static PLI2ExpectedResult calculateExpected(PhuLucI2 data) {
        // Trần quân hàm
        int tran = getTran(data.getCapBac(), data.getChucVu());

        // Tuổi đời còn lại = (trần quân hàm + Cột 2) - Cột 8
        int thangConLai = 0;
        if (tran > 0 && data.getNgaySinh() != null && data.getThoiDiemThoiViecHuongTroCap() != null) {
            Calendar calDob = Calendar.getInstance();
            calDob.setTime(data.getNgaySinh());
            int dobMonth = calDob.get(Calendar.YEAR) * 12 + calDob.get(Calendar.MONTH);

            Calendar calRetire = Calendar.getInstance();
            calRetire.setTime(data.getThoiDiemThoiViecHuongTroCap());
            int retireMonth = calRetire.get(Calendar.YEAR) * 12 + calRetire.get(Calendar.MONTH);

            thangConLai = (dobMonth + tran * 12) - retireMonth;
        }

        // Điều kiện tuổi đời còn lại > 2 năm (24 tháng)
        boolean isOver2Years = thangConLai > 24;

        // Cột 10: Số tháng thôi việc (khống chế tối đa 60 tháng)
        int cot10 = (thangConLai > 0) ? Math.min(thangConLai, 60) : 60;

        // Cột 11 = Cột 8 - Cột 5 = ...(năm) (làm tròn: <= 0.5 -> +0.5, > 0.5 -> +1)
        int monthsCongTac = 0;
        if (data.getThoiDiemThoiViecHuongTroCap() != null && data.getNhapNgu() != null) {
            monthsCongTac = calcThang(data.getThoiDiemThoiViecHuongTroCap(), data.getNhapNgu());
            if (monthsCongTac < 0) monthsCongTac = 0;
        }
        BigDecimal cot11 = calcNamLamTron(monthsCongTac);

        // Cột 8 - Cột 7 (khoảng cách tháng so với thời gian sáp nhập, giải thể)
        int distance8_7 = 0;
        if (data.getThoiDiemThoiViecHuongTroCap() != null && data.getThoiGianDonViSapNhapGiaiThe() != null) {
            distance8_7 = calcThang(data.getThoiDiemThoiViecHuongTroCap(), data.getThoiGianDonViSapNhapGiaiThe());
        }

        // Nhóm nghỉ trong 12 tháng đầu vs nghỉ từ tháng 13 trở đi
        boolean isWithin12Months = distance8_7 <= 12;

        BigDecimal luong = data.getLuongThangHienThuongTheoThongTu() != null ? data.getLuongThangHienThuongTheoThongTu() : BigDecimal.ZERO;

        BigDecimal cot12 = BigDecimal.ZERO;
        BigDecimal cot13 = BigDecimal.ZERO;
        BigDecimal cot14 = BigDecimal.ZERO;
        BigDecimal cot15 = BigDecimal.ZERO;
        BigDecimal cot16 = BigDecimal.ZERO;
        BigDecimal cot17 = BigDecimal.ZERO;

        if (isOver2Years) {
            if (isWithin12Months) {
                // Cột 12 = Cột 10 * 0.8 tháng * Cột 9
                cot12 = BigDecimal.valueOf(cot10).multiply(BigDecimal.valueOf(0.8)).multiply(luong);
                // Cột 13 = Cột 11 * 1.5 tháng * Cột 9
                cot13 = cot11.multiply(BigDecimal.valueOf(1.5)).multiply(luong);
                // Cột 14 = 3 tháng * Cột 9
                cot14 = BigDecimal.valueOf(3).multiply(luong);
            } else {
                // Cột 15 = Cột 10 * 0.4 tháng * Cột 9
                cot15 = BigDecimal.valueOf(cot10).multiply(BigDecimal.valueOf(0.4)).multiply(luong);
                // Cột 16 = Cột 11 * 1.5 tháng * Cột 9
                cot16 = cot11.multiply(BigDecimal.valueOf(1.5)).multiply(luong);
                // Cột 17 = 3 tháng * Cột 9
                cot17 = BigDecimal.valueOf(3).multiply(luong);
            }
        }

        // Cột 18 = SUM(12..17)
        BigDecimal cot18 = cot12.add(cot13).add(cot14).add(cot15).add(cot16).add(cot17);

        return PLI2ExpectedResult.builder()
                .cot10(cot10)
                .cot11(cot11)
                .cot12(cot12)
                .cot13(cot13)
                .cot14(cot14)
                .cot15(cot15)
                .cot16(cot16)
                .cot17(cot17)
                .cot18(cot18)
                .isWithin12Months(isWithin12Months)
                .isOver2Years(isOver2Years)
                .distance8_7(distance8_7)
                .thangConLai(thangConLai)
                .build();
    }

    public static BigDecimal calcNamLamTron(int thang) {
        if (thang <= 0) return BigDecimal.ZERO;
        int years = thang / 12;
        int rem = thang % 12;
        if (rem == 0) return BigDecimal.valueOf(years);
        if (rem <= 6) return BigDecimal.valueOf(years).add(BigDecimal.valueOf(0.5));
        return BigDecimal.valueOf(years).add(BigDecimal.ONE);
    }

    private static int calcThang(Date d1, Date d2) {
        if (d1 == null || d2 == null) return 0;
        Calendar c1 = Calendar.getInstance(); c1.setTime(d1);
        Calendar c2 = Calendar.getInstance(); c2.setTime(d2);
        int m1 = c1.get(Calendar.YEAR) * 12 + c1.get(Calendar.MONTH);
        int m2 = c2.get(Calendar.YEAR) * 12 + c2.get(Calendar.MONTH);
        return m1 - m2;
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
