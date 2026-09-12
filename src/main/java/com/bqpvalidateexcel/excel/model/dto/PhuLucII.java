package com.bqpvalidateexcel.excel.model.dto;

import com.bqpvalidateexcel.excel.model.dto.*;
import com.bqpvalidateexcel.excel.model.expected.*;
import com.bqpvalidateexcel.excel.parser.*;
import com.bqpvalidateexcel.excel.service.rules.*;
import com.bqpvalidateexcel.excel.service.*;


import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.Date;

@Getter
@Builder
public class PhuLucII {
    private int rowIndex;

    private String hoTen;
    private Date ngaySinh;
    private String capBac;
    private String chucVu;
    private Date nhapNgu;
    private Date thoiGianDonViSapNhapGiaiThe;
    
    // Trường hợp nghỉ hưởng chế độ (thường được đánh dấu x hoặc để trống)
    private String nghiHuuTheoND177;
    private String nghiHuuTheoND178ND67;
    private String nghiThoiViec;

    // Nguyên nhân tính sai chế độ (thường được đánh dấu x hoặc để trống)
    private String thuocDonViTacDongTrucTiep;
    private String thuocDonViTacDongGianTiep;
    private String saiThoiGianDuocHuong;
    private String saiTienLuongThangLamCanCu;
    private String tinhTrungCheDoDoiDu;
    private String nguyenNhanKhac;

    private BigDecimal soTienTinhSai;
    private String giaiThich;

    public boolean isBlank() {
        return (hoTen == null || hoTen.trim().isEmpty())
                && (capBac == null || capBac.trim().isEmpty())
                && (chucVu == null || chucVu.trim().isEmpty());
    }
}
