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
public class ExcelRowParsePLI2 {

    public Optional<PhuLucI2> parse(Row row, int rowIndex, FormulaEvaluator formulaEvaluator) {
        if (row == null) {
            return Optional.empty();
        }

        PhuLucI2 data = PhuLucI2.builder()
                .rowIndex(rowIndex)
                .hoTen(ExcelParserUtils.getString(row, 1, formulaEvaluator))
                .ngaySinh(ExcelParserUtils.getDate(row, 2, formulaEvaluator))
                .capBac(ExcelParserUtils.getString(row, 3, formulaEvaluator))
                .chucVu(ExcelParserUtils.getString(row, 4, formulaEvaluator))
                .nhapNgu(ExcelParserUtils.getDate(row, 5, formulaEvaluator))
                .kQDanhGiaCB(ExcelParserUtils.getString(row, 6, formulaEvaluator))
                .thoiGianDonViSapNhapGiaiThe(ExcelParserUtils.getDate(row, 7, formulaEvaluator))
                .thoiDiemThoiViecHuongTroCap(ExcelParserUtils.getDate(row, 8, formulaEvaluator))
                .luongThangHienThuongTheoThongTu(ExcelParserUtils.getBigDecimal(row, 9, formulaEvaluator))
                .soThangThoiViecTheoThongTu(ExcelParserUtils.getInteger(row, 10, formulaEvaluator))
                .soNamHuongTroCapTheoThongTu(ExcelParserUtils.getInteger(row, 11, formulaEvaluator))
                .troCap1LanChoSoThangCongTacCoDongBHXH1(ExcelParserUtils.getBigDecimal(row, 12, formulaEvaluator))
                .troCap1LanChoSoNamCongTacDongBHXH1(ExcelParserUtils.getBigDecimal(row, 13, formulaEvaluator))
                .troCapTaoViecLam1(ExcelParserUtils.getBigDecimal(row, 14, formulaEvaluator))
                .troCap1LanChoSoThangCongTacCoDongBHXH2(ExcelParserUtils.getBigDecimal(row, 15, formulaEvaluator))
                .troCap1LanChoSoNamCongTacDongBHXH2(ExcelParserUtils.getBigDecimal(row, 16, formulaEvaluator))
                .troCapTaoViecLam2(ExcelParserUtils.getBigDecimal(row, 17, formulaEvaluator))
                .tongCongSoTienNghiThoiViecTheoNghiDinhSo178(ExcelParserUtils.getBigDecimal(row, 18, formulaEvaluator))
                .build();

        if (data.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(data);
    }
}
