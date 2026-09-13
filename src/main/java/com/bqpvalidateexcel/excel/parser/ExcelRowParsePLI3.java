package com.bqpvalidateexcel.excel.parser;

import com.bqpvalidateexcel.excel.model.dto.*;
import com.bqpvalidateexcel.excel.model.expected.*;
import com.bqpvalidateexcel.excel.parser.*;
import com.bqpvalidateexcel.excel.service.rules.*;
import com.bqpvalidateexcel.excel.service.*;

import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Component
public class ExcelRowParsePLI3 {

    public Optional<PhuLucI3> parse(Row row, int rowIndex, FormulaEvaluator formulaEvaluator) {
        return parse(row, rowIndex, formulaEvaluator, null);
    }

    public Optional<PhuLucI3> parse(Row row, int rowIndex, FormulaEvaluator formulaEvaluator, Map<Integer, Integer> colMap) {
        if (row == null) {
            return Optional.empty();
        }

        // Kiểm tra xem sheet có cột 'Đơn vị' ở cột index 5 hay không (chỉ khi không có colMap)
        boolean hasDonVi = false;
        if (colMap == null || colMap.isEmpty()) {
            org.apache.poi.ss.usermodel.Row headerRow6 = row.getSheet().getRow(5);
            if (headerRow6 != null) {
                String h5 = ExcelParserUtils.getString(headerRow6, 5, formulaEvaluator).toLowerCase();
                if (h5.contains("đơn vị") || h5.contains("don vi")) {
                    hasDonVi = true;
                }
            }
        }
        int offset = hasDonVi ? 1 : 0;

        int c1 = getCol(colMap, 1, 1);
        int c2 = getCol(colMap, 2, 2);
        int c3 = getCol(colMap, 3, 3);
        int c4 = getCol(colMap, 4, 4);
        int cDonVi = hasDonVi ? 5 : -1;
        int c5 = getCol(colMap, 5, 5 + offset);
        int c6 = getCol(colMap, 6, 6 + offset);
        int c7 = getCol(colMap, 7, 7 + offset);
        int c8 = getCol(colMap, 8, 8 + offset);
        int c9 = getCol(colMap, 9, 9 + offset);
        int c10 = getCol(colMap, 10, 10 + offset);
        int c11 = getCol(colMap, 11, 11 + offset);
        int c12 = getCol(colMap, 12, 12 + offset);
        int c13 = getCol(colMap, 13, 13 + offset);
        int c14 = getCol(colMap, 14, 14 + offset);
        int c15 = getCol(colMap, 17, getCol(colMap, 15, 15 + offset));

        PhuLucI3 data = PhuLucI3.builder()
                .rowIndex(rowIndex)
                .hoTen(ExcelParserUtils.getString(row, c1, formulaEvaluator))
                .ngaySinh(ExcelParserUtils.getDate(row, c2, formulaEvaluator))
                .capBac(ExcelParserUtils.getString(row, c3, formulaEvaluator))
                .chucVu(ExcelParserUtils.getString(row, c4, formulaEvaluator))
                .donVi(cDonVi != -1 ? ExcelParserUtils.getString(row, cDonVi, formulaEvaluator) : null)
                .nhapNgu(ExcelParserUtils.getDate(row, c5, formulaEvaluator))
                .kQDanhGiaCB(ExcelParserUtils.getString(row, c6, formulaEvaluator))
                .thoiGianDonViSapNhapGiaiThe(ExcelParserUtils.getDate(row, c7, formulaEvaluator))
                .thoiDiemNghiHuuHuongTroCap(ExcelParserUtils.getDate(row, c8, formulaEvaluator))
                .luongThangHienThuongTheoHuongDan(ExcelParserUtils.getBigDecimal(row, c9, formulaEvaluator))
                .soThangThoiViecTheoHuongDan(ExcelParserUtils.getBigDecimal(row, c10, formulaEvaluator))
                .soNamHuongTroCapTheoHuongDan(ExcelParserUtils.getBigDecimal(row, c11, formulaEvaluator))
                .G5ThangTienLuongHienHuongCho1NamNghiSom(ExcelParserUtils.getBigDecimal(row, c12, formulaEvaluator))
                .G5ThangTienLuongHienHuongCho20NamDauCongTac(ExcelParserUtils.getBigDecimal(row, c13, formulaEvaluator))
                .tuNamThu21TroDiCuMoiNamCongTacHuong12ThangLuongHienHuong(ExcelParserUtils.getBigDecimal(row, c14, formulaEvaluator))
                .tongCongSoTienNghiThoiViecTheoNghiDinhSo177(ExcelParserUtils.getBigDecimal(row, c15, formulaEvaluator))
                .build();

        if (data.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(data);
    }

    private static int getCol(Map<Integer, Integer> colMap, int stdCol, int defaultCol) {
        return (colMap != null && colMap.containsKey(stdCol)) ? colMap.get(stdCol) : defaultCol;
    }
}
