package com.bqpvalidateexcel.excel.parser;

import com.bqpvalidateexcel.excel.model.dto.*;
import com.bqpvalidateexcel.excel.model.expected.*;
import com.bqpvalidateexcel.excel.service.rules.*;
import com.bqpvalidateexcel.excel.service.*;


import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ExcelRowParsePLI1 {

    public Optional<PhuLucI1> parse(Row row, int rowIndex, FormulaEvaluator formulaEvaluator) {
        if (row == null) {
            return Optional.empty();
        }

        PhuLucI1 data = PhuLucI1.builder()
                .rowIndex(rowIndex)
                .hoTen(ExcelParserUtils.getString(row, 1, formulaEvaluator))
                .ngaySinh(ExcelParserUtils.getDate(row, 2, formulaEvaluator))
                .capBac(ExcelParserUtils.getString(row, 3, formulaEvaluator))
                .chucVu(ExcelParserUtils.getString(row, 4, formulaEvaluator))
                .nhapNgu(ExcelParserUtils.getDate(row, 5, formulaEvaluator))
                .kQDanhGiaCB(ExcelParserUtils.getString(row, 6, formulaEvaluator))
                .thoiGianDonViSapNhapGiaiThe(ExcelParserUtils.getDate(row, 7, formulaEvaluator))
                .thoiDiemNghiHuuHuongTroCap(ExcelParserUtils.getDate(row, 8, formulaEvaluator))
                .luongThangHienThuongTheoThongTu(ExcelParserUtils.getBigDecimal(row, 9, formulaEvaluator))
                .soThangNghiHuuTruocTuoiTheoThongTu(ExcelParserUtils.getInteger(row, 10, formulaEvaluator))
                .soNamNghiHuuTruocTuoiTheoThongTu(ExcelParserUtils.getInteger(row, 11, formulaEvaluator))
                .soNamCongTacDongBHXHTheoThongTu(ExcelParserUtils.getBigDecimal(row, 12, formulaEvaluator))
                .tuoiDoiCon5NamTroXuong1(ExcelParserUtils.getBigDecimal(row, 13, formulaEvaluator))
                .tuoiDoiConTren5NamDenDuoi10Nam1(ExcelParserUtils.getBigDecimal(row, 14, formulaEvaluator))
                .tuoiDoiCon5NamTroXuong2(ExcelParserUtils.getBigDecimal(row, 15, formulaEvaluator))
                .tuoiDoiConTren5NamDenDuoi10Nam2(ExcelParserUtils.getBigDecimal(row, 16, formulaEvaluator))
                .G5ThangTienLuongHienHuongCho1NamNghiSom(ExcelParserUtils.getBigDecimal(row, 17, formulaEvaluator))
                .G5ThangTienLuongHienHuongCho20NamDauCongTac1(ExcelParserUtils.getBigDecimal(row, 18, formulaEvaluator))
                .tuNamThu21TroDiCuMoiNamCongTacHuong12ThangLuongHienHuong1(ExcelParserUtils.getBigDecimal(row, 19, formulaEvaluator))
                .G4ThangTienLuongHienHuongCho1NamNghi(ExcelParserUtils.getBigDecimal(row, 20, formulaEvaluator))
                .G5ThangTienLuongHienHuongCho20NamDauCongTac2(ExcelParserUtils.getBigDecimal(row, 21, formulaEvaluator))
                .tuNamThu21TroDiCuMoiNamCongTacHuong12ThangLuongHienHuong2(ExcelParserUtils.getBigDecimal(row, 22, formulaEvaluator))
                .tongCongSoTienTheoNghiDinhSo178(ExcelParserUtils.getBigDecimal(row, 23, formulaEvaluator))
                .build();

        if (data.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(data);
    }
}
