package com.bqpvalidateexcel.excel.parser;

import com.bqpvalidateexcel.excel.model.dto.*;
import com.bqpvalidateexcel.excel.model.expected.*;
import com.bqpvalidateexcel.excel.parser.*;
import com.bqpvalidateexcel.excel.service.rules.*;
import com.bqpvalidateexcel.excel.service.*;


import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ExcelRowParsePLI3 {

    public Optional<PhuLucI3> parse(Row row, int rowIndex, FormulaEvaluator formulaEvaluator) {
        if (row == null) {
            return Optional.empty();
        }

        PhuLucI3 data = PhuLucI3.builder()
                .rowIndex(rowIndex)
                .hoTen(ExcelParserUtils.getString(row, 1, formulaEvaluator))
                .ngaySinh(ExcelParserUtils.getDate(row, 2, formulaEvaluator))
                .capBac(ExcelParserUtils.getString(row, 3, formulaEvaluator))
                .chucVu(ExcelParserUtils.getString(row, 4, formulaEvaluator))
                .nhapNgu(ExcelParserUtils.getDate(row, 5, formulaEvaluator))
                .kQDanhGiaCB(ExcelParserUtils.getString(row, 6, formulaEvaluator))
                .thoiGianDonViSapNhapGiaiThe(ExcelParserUtils.getDate(row, 7, formulaEvaluator))
                .thoiDiemNghiHuuHuongTroCap(ExcelParserUtils.getDate(row, 8, formulaEvaluator))
                .luongThangHienThuongTheoHuongDan(ExcelParserUtils.getBigDecimal(row, 9, formulaEvaluator))
                .soThangThoiViecTheoHuongDan(ExcelParserUtils.getInteger(row, 10, formulaEvaluator))
                .soNamHuongTroCapTheoHuongDan(ExcelParserUtils.getInteger(row, 11, formulaEvaluator))
                .G5ThangTienLuongHienHuongCho1NamNghiSom(ExcelParserUtils.getBigDecimal(row, 12, formulaEvaluator))
                .G5ThangTienLuongHienHuongCho20NamDauCongTac(ExcelParserUtils.getBigDecimal(row, 13, formulaEvaluator))
                .tuNamThu21TroDiCuMoiNamCongTacHuong12ThangLuongHienHuong(ExcelParserUtils.getBigDecimal(row, 14, formulaEvaluator))
                .tongCongSoTienNghiThoiViecTheoNghiDinhSo177(ExcelParserUtils.getBigDecimal(row, 15, formulaEvaluator))
                .build();

        if (data.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(data);
    }
}
