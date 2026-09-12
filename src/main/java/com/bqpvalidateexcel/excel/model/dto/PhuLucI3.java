package com.bqpvalidateexcel.excel.model.dto;

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
public class PhuLucI3 {
    private int rowIndex;

    private String hoTen;
    private Date ngaySinh;
    private String capBac;
    private String chucVu;
    private Date nhapNgu;
    private String kQDanhGiaCB;
    private Date thoiGianDonViSapNhapGiaiThe;
    private Date thoiDiemNghiHuuHuongTroCap;
    
    private BigDecimal luongThangHienThuongTheoHuongDan;
    private Integer soThangThoiViecTheoHuongDan;
    private Integer soNamHuongTroCapTheoHuongDan;

    // Tuổi đời từ đủ 12 tháng đến 60 tháng đến tuổi nghỉ hưu
    private BigDecimal G5ThangTienLuongHienHuongCho1NamNghiSom;
    private BigDecimal G5ThangTienLuongHienHuongCho20NamDauCongTac;
    private BigDecimal tuNamThu21TroDiCuMoiNamCongTacHuong12ThangLuongHienHuong;

    private BigDecimal tongCongSoTienNghiThoiViecTheoNghiDinhSo177;

    public boolean isBlank() {
        return (hoTen == null || hoTen.isEmpty())
                && (capBac == null || capBac.isEmpty())
                && (chucVu == null || chucVu.isEmpty())
                && (kQDanhGiaCB == null || kQDanhGiaCB.isEmpty());
    }
}
