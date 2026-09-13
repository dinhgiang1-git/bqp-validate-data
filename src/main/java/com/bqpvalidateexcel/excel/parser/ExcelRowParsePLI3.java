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

        // Kiểm tra xem sheet có cột 'Đơn vị' ở cột index 5 hay không
        boolean hasDonVi = false;
        org.apache.poi.ss.usermodel.Row headerRow6 = row.getSheet().getRow(5);
        if (headerRow6 != null) {
            String h5 = ExcelParserUtils.getString(headerRow6, 5, formulaEvaluator).toLowerCase();
            if (h5.contains("đơn vị") || h5.contains("don vi")) {
                hasDonVi = true;
            }
        }
        int offset = hasDonVi ? 1 : 0;

        PhuLucI3 data = PhuLucI3.builder()
                .rowIndex(rowIndex)
                .hoTen(ExcelParserUtils.getString(row, 1, formulaEvaluator))
                .ngaySinh(ExcelParserUtils.getDate(row, 2, formulaEvaluator))
                .capBac(ExcelParserUtils.getString(row, 3, formulaEvaluator))
                .chucVu(ExcelParserUtils.getString(row, 4, formulaEvaluator))
                .donVi(hasDonVi ? ExcelParserUtils.getString(row, 5, formulaEvaluator) : null)
                .nhapNgu(ExcelParserUtils.getDate(row, 5 + offset, formulaEvaluator))
                .kQDanhGiaCB(ExcelParserUtils.getString(row, 6 + offset, formulaEvaluator))
                .thoiGianDonViSapNhapGiaiThe(ExcelParserUtils.getDate(row, 7 + offset, formulaEvaluator))
                .thoiDiemNghiHuuHuongTroCap(ExcelParserUtils.getDate(row, 8 + offset, formulaEvaluator))
                .luongThangHienThuongTheoHuongDan(ExcelParserUtils.getBigDecimal(row, 9 + offset, formulaEvaluator))
                .soThangThoiViecTheoHuongDan(ExcelParserUtils.getBigDecimal(row, 10 + offset, formulaEvaluator))
                .soNamHuongTroCapTheoHuongDan(ExcelParserUtils.getBigDecimal(row, 11 + offset, formulaEvaluator))
                .G5ThangTienLuongHienHuongCho1NamNghiSom(ExcelParserUtils.getBigDecimal(row, 12 + offset, formulaEvaluator))
                .G5ThangTienLuongHienHuongCho20NamDauCongTac(ExcelParserUtils.getBigDecimal(row, 13 + offset, formulaEvaluator))
                .tuNamThu21TroDiCuMoiNamCongTacHuong12ThangLuongHienHuong(ExcelParserUtils.getBigDecimal(row, 14 + offset, formulaEvaluator))
                .tongCongSoTienNghiThoiViecTheoNghiDinhSo177(ExcelParserUtils.getBigDecimal(row, 15 + offset, formulaEvaluator))
                .build();

        if (data.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(data);
    }
}
