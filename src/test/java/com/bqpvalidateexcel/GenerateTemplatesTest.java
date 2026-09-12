package com.bqpvalidateexcel;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileOutputStream;

public class GenerateTemplatesTest {

    private static final String TEMPLATE_DIR = "src/main/resources/excel_templates/";

    @Test
    void generateAllTemplates() throws Exception {
        new File(TEMPLATE_DIR).mkdirs();
        generatePhuLucI1();
        generatePhuLucI2();
        generatePhuLucI3();
        generatePhuLucII();
        System.out.println("-> Successfully generated all 4 templates in " + TEMPLATE_DIR);
    }

    private void generatePhuLucI1() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet sheet = wb.createSheet("PhuLucI.1");
            sheet.getPrintSetup().setLandscape(true);
            sheet.getPrintSetup().setPaperSize(PrintSetup.A3_PAPERSIZE);

            XSSFCellStyle hStyle = createHeaderStyle(wb);
            XSSFCellStyle metaBold = createMetaBold(wb);
            XSSFCellStyle metaItalic = createMetaItalic(wb);

            // Dòng 0..4: Metadata
            Row r0 = sheet.createRow(0);
            Cell c0 = r0.createCell(0);
            c0.setCellValue("BỘ QUỐC PHÒNG");
            c0.setCellStyle(metaBold);

            Cell c0R = r0.createCell(15);
            c0R.setCellValue("CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM");
            c0R.setCellStyle(metaBold);

            Row r1 = sheet.createRow(1);
            Cell c1R = r1.createCell(15);
            c1R.setCellValue("Độc lập - Tự do - Hạnh phúc");
            c1R.setCellStyle(metaItalic);

            Row rTitle = sheet.createRow(3);
            Cell cTitle = rTitle.createCell(0);
            cTitle.setCellValue("TỔNG HỢP SỐ LIỆU THỰC HIỆN CHẾ ĐỘ, CHÍNH SÁCH NGHỈ HƯU TRƯỚC TUỔI DO SẮP XẾP TỔ CHỨC BỘ MÁY");
            XSSFCellStyle titleSt = wb.createCellStyle();
            XSSFFont tf = wb.createFont();
            tf.setBold(true);
            tf.setFontHeightInPoints((short) 13);
            titleSt.setFont(tf);
            cTitle.setCellStyle(titleSt);

            Row rSub = sheet.createRow(4);
            Cell cSub = rSub.createCell(0);
            cSub.setCellValue("(Phụ lục I.1 kèm theo Nghị định số 178/2024/NĐ-CP của Chính phủ)");
            cSub.setCellStyle(metaItalic);

            // Bảng Header từ dòng 5 đến dòng 8
            Row hr5 = sheet.createRow(5);
            Row hr6 = sheet.createRow(6);
            Row hr7 = sheet.createRow(7);
            Row hr8 = sheet.createRow(8);
            Row hr9 = sheet.createRow(9); // Dòng 10: đánh số 1..25

            // Cột 0..12: Merge dòng 5..7
            String[] headers0To12 = {
                    "Số TT", "Họ và tên", "Tháng, năm sinh", "Cấp bậc", "Chức vụ", "Nhập ngũ",
                    "Kết quả đánh giá xếp loại", "Thời gian sáp nhập, giải thể", "Thời điểm nghỉ hưu",
                    "Lương tháng hiện hưởng", "Số tháng nghỉ trước tuổi", "Số năm nghỉ trước tuổi", "Số năm BHXH"
            };

            for (int i = 0; i < headers0To12.length; i++) {
                Cell cell = hr5.createCell(i);
                cell.setCellValue(headers0To12[i]);
                cell.setCellStyle(hStyle);
                for (int r = 6; r <= 8; r++) {
                    sheet.getRow(r).createCell(i).setCellStyle(hStyle);
                }
                sheet.addMergedRegion(new CellRangeAddress(5, 8, i, i));
            }

            // Cột 13..16: TRỢ CẤP MỘT LẦN CHO THỜI GIAN NGHỈ SỚM
            Cell cNghiSom = hr5.createCell(13);
            cNghiSom.setCellValue("TRỢ CẤP MỘT LẦN CHO THỜI GIAN NGHỈ SỚM");
            cNghiSom.setCellStyle(hStyle);
            for (int c = 14; c <= 16; c++) hr5.createCell(c).setCellStyle(hStyle);
            sheet.addMergedRegion(new CellRangeAddress(5, 5, 13, 16));

            // Dòng 6: Nghỉ trong 12 tháng vs Nghỉ từ tháng 13
            Cell cTh1 = hr6.createCell(13);
            cTh1.setCellValue("Nghỉ trong 12 tháng đầu");
            cTh1.setCellStyle(hStyle);
            hr6.createCell(14).setCellStyle(hStyle);
            sheet.addMergedRegion(new CellRangeAddress(6, 6, 13, 14));

            Cell cTh2 = hr6.createCell(15);
            cTh2.setCellValue("Nghỉ từ tháng 13 trở đi");
            cTh2.setCellStyle(hStyle);
            hr6.createCell(16).setCellStyle(hStyle);
            sheet.addMergedRegion(new CellRangeAddress(6, 6, 15, 16));

            // Dòng 7..8: Tuổi đời DT1 / DT2
            String[] dtLabels = {"Tuổi đời còn 5 năm trở xuống", "Tuổi đời trên 5 năm đến 10 năm", "Tuổi đời còn 5 năm trở xuống", "Tuổi đời trên 5 năm đến 10 năm"};
            for (int i = 0; i < 4; i++) {
                Cell cell = hr7.createCell(13 + i);
                cell.setCellValue(dtLabels[i]);
                cell.setCellStyle(hStyle);
                hr8.createCell(13 + i).setCellStyle(hStyle);
                sheet.addMergedRegion(new CellRangeAddress(7, 8, 13 + i, 13 + i));
            }

            // Cột 17..22: TRỢ CẤP NGHỈ HƯU TRƯỚC TUỔI THEO BHXH VÀ NĂM NGHỈ SỚM
            Cell cBhxhGroup = hr5.createCell(17);
            cBhxhGroup.setCellValue("TRỢ CẤP NGHỈ HƯU TRƯỚC TUỔI THEO THỜI GIAN ĐÓNG BHXH VÀ SỐ NĂM NGHỈ SỚM");
            cBhxhGroup.setCellStyle(hStyle);
            for (int c = 18; c <= 22; c++) hr5.createCell(c).setCellStyle(hStyle);
            sheet.addMergedRegion(new CellRangeAddress(5, 5, 17, 22));

            // Dòng 6: DT1 (17..19) vs DT2 (20..22)
            Cell cDt1Group = hr6.createCell(17);
            cDt1Group.setCellValue("Tuổi đời từ đủ 2 năm đến 5 năm đến tuổi nghỉ hưu (DT1)");
            cDt1Group.setCellStyle(hStyle);
            hr6.createCell(18).setCellStyle(hStyle);
            hr6.createCell(19).setCellStyle(hStyle);
            sheet.addMergedRegion(new CellRangeAddress(6, 6, 17, 19));

            Cell cDt2Group = hr6.createCell(20);
            cDt2Group.setCellValue("Tuổi đời còn trên 5 năm đến 10 năm (DT2)");
            cDt2Group.setCellStyle(hStyle);
            hr6.createCell(21).setCellStyle(hStyle);
            hr6.createCell(22).setCellStyle(hStyle);
            sheet.addMergedRegion(new CellRangeAddress(6, 6, 20, 22));

            // Dòng 7..8: 5 tháng lương / 1 năm, 5 tháng lương 20 năm đầu, 1/2 tháng lương từ năm 21
            String[] subBhxh = {
                    "5 tháng lương cho 1 năm nghỉ sớm", "5 tháng lương cho 20 năm đầu", "Từ năm 21: 1/2 tháng/năm",
                    "4 tháng lương cho 1 năm nghỉ sớm", "5 tháng lương cho 20 năm đầu", "Từ năm 21: 1/2 tháng/năm"
            };
            for (int i = 0; i < 6; i++) {
                Cell cell = hr7.createCell(17 + i);
                cell.setCellValue(subBhxh[i]);
                cell.setCellStyle(hStyle);
                hr8.createCell(17 + i).setCellStyle(hStyle);
                sheet.addMergedRegion(new CellRangeAddress(7, 8, 17 + i, 17 + i));
            }

            // Cột 23: Tổng cộng số tiền theo NĐ 178
            Cell cTot = hr5.createCell(23);
            cTot.setCellValue("Tổng cộng số tiền theo Nghị định số 178");
            cTot.setCellStyle(hStyle);
            for (int r = 6; r <= 8; r++) sheet.getRow(r).createCell(23).setCellStyle(hStyle);
            sheet.addMergedRegion(new CellRangeAddress(5, 8, 23, 23));

            // Cột 24: Ghi chú rà soát
            Cell cNote = hr5.createCell(24);
            cNote.setCellValue("GHI CHÚ RÀ SOÁT & ĐỐI SOÁT");
            cNote.setCellStyle(hStyle);
            for (int r = 6; r <= 8; r++) sheet.getRow(r).createCell(24).setCellStyle(hStyle);
            sheet.addMergedRegion(new CellRangeAddress(5, 8, 24, 24));

            // Dòng 9 (Dòng 10 Excel): Số thứ tự cột
            for (int i = 0; i < 25; i++) {
                Cell cell = hr9.createCell(i);
                cell.setCellValue(String.valueOf(i + 1));
                cell.setCellStyle(hStyle);
            }

            // Set column widths
            sheet.setColumnWidth(0, 1800);
            sheet.setColumnWidth(1, 5500);
            sheet.setColumnWidth(2, 3200);
            sheet.setColumnWidth(3, 2800);
            sheet.setColumnWidth(4, 3000);
            sheet.setColumnWidth(5, 2800);
            sheet.setColumnWidth(6, 3000);
            sheet.setColumnWidth(7, 3200);
            sheet.setColumnWidth(8, 3200);
            sheet.setColumnWidth(9, 3800);
            sheet.setColumnWidth(10, 2400);
            sheet.setColumnWidth(11, 2400);
            sheet.setColumnWidth(12, 2400);
            for (int i = 13; i <= 23; i++) sheet.setColumnWidth(i, 3800);
            sheet.setColumnWidth(24, 12000);

            try (FileOutputStream fos = new FileOutputStream(TEMPLATE_DIR + "PhuLucI1.xlsx")) {
                wb.write(fos);
            }
        }
    }

    private void generatePhuLucI2() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet sheet = wb.createSheet("PhuLucI.2");
            sheet.getPrintSetup().setLandscape(true);
            sheet.getPrintSetup().setPaperSize(PrintSetup.A3_PAPERSIZE);

            XSSFCellStyle hStyle = createHeaderStyle(wb);
            XSSFCellStyle metaBold = createMetaBold(wb);
            XSSFCellStyle metaItalic = createMetaItalic(wb);

            Row r0 = sheet.createRow(0);
            Cell c0 = r0.createCell(0);
            c0.setCellValue("BỘ QUỐC PHÒNG");
            c0.setCellStyle(metaBold);

            Row rTitle = sheet.createRow(3);
            Cell cTitle = rTitle.createCell(0);
            cTitle.setCellValue("TỔNG HỢP SỐ LIỆU THỰC HIỆN CHẾ ĐỘ NGHỈ THÔI VIỆC DO SẮP XẾP TỔ CHỨC BỘ MÁY");
            cTitle.setCellStyle(metaBold);

            Row hr5 = sheet.createRow(5);
            Row hr6 = sheet.createRow(6);
            Row hr7 = sheet.createRow(7);
            Row hr8 = sheet.createRow(8);
            Row hr9 = sheet.createRow(9);

            String[] h0To11 = {
                    "Số TT", "Họ và tên", "Tháng, năm sinh", "Cấp bậc", "Chức vụ", "Nhập ngũ",
                    "Đánh giá", "Thời gian sáp nhập", "Thời điểm thôi việc",
                    "Lương tháng hiện hưởng", "Số tháng thôi việc", "Số năm thôi việc"
            };

            for (int i = 0; i < h0To11.length; i++) {
                Cell c = hr5.createCell(i);
                c.setCellValue(h0To11[i]);
                c.setCellStyle(hStyle);
                for (int r = 6; r <= 8; r++) sheet.getRow(r).createCell(i).setCellStyle(hStyle);
                sheet.addMergedRegion(new CellRangeAddress(5, 8, i, i));
            }

            // Cột 12..17: TRỢ CẤP PHỤC VIÊN / THÔI VIỆC
            Cell cGrp = hr5.createCell(12);
            cGrp.setCellValue("TRỢ CẤP MỘT LẦN PHỤC VIÊN / THÔI VIỆC");
            cGrp.setCellStyle(hStyle);
            for (int c = 13; c <= 17; c++) hr5.createCell(c).setCellStyle(hStyle);
            sheet.addMergedRegion(new CellRangeAddress(5, 5, 12, 17));

            Cell cTh1 = hr6.createCell(12);
            cTh1.setCellValue("Nghỉ trong 12 tháng đầu (TH1)");
            cTh1.setCellStyle(hStyle);
            hr6.createCell(13).setCellStyle(hStyle);
            hr6.createCell(14).setCellStyle(hStyle);
            sheet.addMergedRegion(new CellRangeAddress(6, 6, 12, 14));

            Cell cTh2 = hr6.createCell(15);
            cTh2.setCellValue("Nghỉ từ tháng 13 trở đi (TH2)");
            cTh2.setCellStyle(hStyle);
            hr6.createCell(16).setCellStyle(hStyle);
            hr6.createCell(17).setCellStyle(hStyle);
            sheet.addMergedRegion(new CellRangeAddress(6, 6, 15, 17));

            String[] subI2 = {
                    "Trợ cấp tháng BHXH (0.8x)", "Trợ cấp năm (1.5x)", "Trợ cấp việc làm (3T)",
                    "Trợ cấp tháng BHXH (0.4x)", "Trợ cấp năm (1.5x)", "Trợ cấp việc làm (3T)"
            };
            for (int i = 0; i < 6; i++) {
                Cell cell = hr7.createCell(12 + i);
                cell.setCellValue(subI2[i]);
                cell.setCellStyle(hStyle);
                hr8.createCell(12 + i).setCellStyle(hStyle);
                sheet.addMergedRegion(new CellRangeAddress(7, 8, 12 + i, 12 + i));
            }

            Cell cTot = hr5.createCell(18);
            cTot.setCellValue("Tổng cộng số tiền theo NĐ 178");
            cTot.setCellStyle(hStyle);
            for (int r = 6; r <= 8; r++) sheet.getRow(r).createCell(18).setCellStyle(hStyle);
            sheet.addMergedRegion(new CellRangeAddress(5, 8, 18, 18));

            Cell cNote = hr5.createCell(19);
            cNote.setCellValue("GHI CHÚ RÀ SOÁT & ĐỐI SOÁT");
            cNote.setCellStyle(hStyle);
            for (int r = 6; r <= 8; r++) sheet.getRow(r).createCell(19).setCellStyle(hStyle);
            sheet.addMergedRegion(new CellRangeAddress(5, 8, 19, 19));

            for (int i = 0; i < 20; i++) {
                Cell c = hr9.createCell(i);
                c.setCellValue(String.valueOf(i + 1));
                c.setCellStyle(hStyle);
            }

            try (FileOutputStream fos = new FileOutputStream(TEMPLATE_DIR + "PhuLucI2.xlsx")) {
                wb.write(fos);
            }
        }
    }

    private void generatePhuLucI3() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet sheet = wb.createSheet("PhuLucI.3");
            sheet.getPrintSetup().setLandscape(true);
            sheet.getPrintSetup().setPaperSize(PrintSetup.A3_PAPERSIZE);

            XSSFCellStyle hStyle = createHeaderStyle(wb);
            XSSFCellStyle metaBold = createMetaBold(wb);

            Row rTitle = sheet.createRow(3);
            Cell cTitle = rTitle.createCell(0);
            cTitle.setCellValue("TỔNG HỢP SỐ LIỆU THỰC HIỆN CHẾ ĐỘ NGHỊ ĐỊNH SỐ 177/2024/NĐ-CP");
            cTitle.setCellStyle(metaBold);

            Row hr5 = sheet.createRow(5);
            Row hr6 = sheet.createRow(6);
            Row hr7 = sheet.createRow(7);
            Row hr8 = sheet.createRow(8);
            Row hr9 = sheet.createRow(9);

            String[] h0To11 = {
                    "Số TT", "Họ và tên", "Tháng, năm sinh", "Cấp bậc", "Chức vụ", "Nhập ngũ",
                    "Đánh giá", "Thời gian sáp nhập", "Thời điểm nghỉ",
                    "Lương tháng hiện hưởng", "Số tháng thôi việc", "Số năm hưởng trợ cấp"
            };

            for (int i = 0; i < h0To11.length; i++) {
                Cell c = hr5.createCell(i);
                c.setCellValue(h0To11[i]);
                c.setCellStyle(hStyle);
                for (int r = 6; r <= 8; r++) sheet.getRow(r).createCell(i).setCellStyle(hStyle);
                sheet.addMergedRegion(new CellRangeAddress(5, 8, i, i));
            }

            Cell cC12 = hr5.createCell(12);
            cC12.setCellValue("5 tháng lương cho 1 năm nghỉ sớm");
            cC12.setCellStyle(hStyle);
            for (int r = 6; r <= 8; r++) sheet.getRow(r).createCell(12).setCellStyle(hStyle);
            sheet.addMergedRegion(new CellRangeAddress(5, 8, 12, 12));

            Cell cC13 = hr5.createCell(13);
            cC13.setCellValue("5/4 tháng lương cho 20/15 năm đầu");
            cC13.setCellStyle(hStyle);
            for (int r = 6; r <= 8; r++) sheet.getRow(r).createCell(13).setCellStyle(hStyle);
            sheet.addMergedRegion(new CellRangeAddress(5, 8, 13, 13));

            Cell cC14 = hr5.createCell(14);
            cC14.setCellValue("Từ năm 21/16: 1/2 tháng/năm");
            cC14.setCellStyle(hStyle);
            for (int r = 6; r <= 8; r++) sheet.getRow(r).createCell(14).setCellStyle(hStyle);
            sheet.addMergedRegion(new CellRangeAddress(5, 8, 14, 14));

            Cell cTot = hr5.createCell(15);
            cTot.setCellValue("Tổng cộng số tiền theo NĐ 177");
            cTot.setCellStyle(hStyle);
            for (int r = 6; r <= 8; r++) sheet.getRow(r).createCell(15).setCellStyle(hStyle);
            sheet.addMergedRegion(new CellRangeAddress(5, 8, 15, 15));

            Cell cNote = hr5.createCell(16);
            cNote.setCellValue("GHI CHÚ RÀ SOÁT & ĐỐI SOÁT");
            cNote.setCellStyle(hStyle);
            for (int r = 6; r <= 8; r++) sheet.getRow(r).createCell(16).setCellStyle(hStyle);
            sheet.addMergedRegion(new CellRangeAddress(5, 8, 16, 16));

            for (int i = 0; i < 17; i++) {
                Cell c = hr9.createCell(i);
                c.setCellValue(String.valueOf(i + 1));
                c.setCellStyle(hStyle);
            }

            try (FileOutputStream fos = new FileOutputStream(TEMPLATE_DIR + "PhuLucI3.xlsx")) {
                wb.write(fos);
            }
        }
    }

    private void generatePhuLucII() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet sheet = wb.createSheet("PhuLucII");
            sheet.getPrintSetup().setLandscape(true);
            sheet.getPrintSetup().setPaperSize(PrintSetup.A3_PAPERSIZE);

            XSSFCellStyle hStyle = createHeaderStyle(wb);
            XSSFCellStyle metaBold = createMetaBold(wb);
            XSSFCellStyle metaItalic = createMetaItalic(wb);

            Row r0 = sheet.createRow(0);
            Cell c0 = r0.createCell(0);
            c0.setCellValue("BỘ QUỐC PHÒNG");
            c0.setCellStyle(metaBold);

            Cell c0R = r0.createCell(11);
            c0R.setCellValue("CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM");
            c0R.setCellStyle(metaBold);

            Row r1 = sheet.createRow(1);
            Cell c1R = r1.createCell(11);
            c1R.setCellValue("Độc lập - Tự do - Hạnh phúc");
            c1R.setCellStyle(metaItalic);

            Row rTitle = sheet.createRow(3);
            Cell cTitle = rTitle.createCell(0);
            cTitle.setCellValue("BẢNG TỔNG HỢP DANH SÁCH ĐỐI SOÁT VÀ ĐIỀU CHỈNH CHẾ ĐỘ CHÍNH SÁCH (PHỤ LỤC II)");
            XSSFCellStyle tSt = wb.createCellStyle();
            XSSFFont tf = wb.createFont();
            tf.setBold(true);
            tf.setFontHeightInPoints((short) 13);
            tSt.setFont(tf);
            cTitle.setCellStyle(tSt);

            Row rSub = sheet.createRow(4);
            Cell cSub = rSub.createCell(0);
            cSub.setCellValue("(Chỉ áp dụng đối với các trường hợp có số liệu chưa chính xác cần điều chỉnh)");
            cSub.setCellStyle(metaItalic);

            Row hr5 = sheet.createRow(5);
            Row hr6 = sheet.createRow(6);
            Row hr7 = sheet.createRow(7);
            Row hr8 = sheet.createRow(8);
            Row hr9 = sheet.createRow(9); // Dòng 10: đánh số 1..18

            String[] h0To6 = {"STT", "Họ và tên", "Tháng, năm sinh", "Cấp bậc", "Chức vụ", "Nhập ngũ", "Thời gian sáp nhập"};
            for (int i = 0; i < h0To6.length; i++) {
                Cell cell = hr5.createCell(i);
                cell.setCellValue(h0To6[i]);
                cell.setCellStyle(hStyle);
                for (int r = 6; r <= 8; r++) sheet.getRow(r).createCell(i).setCellStyle(hStyle);
                sheet.addMergedRegion(new CellRangeAddress(5, 8, i, i));
            }

            // Cột 7..9: Trường hợp hưởng (NĐ 177, NĐ 178 Hưu, Thôi việc)
            Cell cCase = hr5.createCell(7);
            cCase.setCellValue("Trường hợp hưởng chế độ");
            cCase.setCellStyle(hStyle);
            hr5.createCell(8).setCellStyle(hStyle);
            hr5.createCell(9).setCellStyle(hStyle);
            sheet.addMergedRegion(new CellRangeAddress(5, 6, 7, 9));

            Cell c7 = hr7.createCell(7); c7.setCellValue("NĐ 177"); c7.setCellStyle(hStyle);
            Cell c8 = hr7.createCell(8); c8.setCellValue("NĐ 178"); c8.setCellStyle(hStyle);
            Cell c9 = hr7.createCell(9); c9.setCellValue("Nghỉ thôi việc"); c9.setCellStyle(hStyle);
            for (int c = 7; c <= 9; c++) {
                hr8.createCell(c).setCellStyle(hStyle);
                sheet.addMergedRegion(new CellRangeAddress(7, 8, c, c));
            }

            // Cột 10..15: Nguyên nhân sai
            Cell cReason = hr5.createCell(10);
            cReason.setCellValue("Nguyên nhân sai lệch số liệu");
            cReason.setCellStyle(hStyle);
            for (int c = 11; c <= 15; c++) hr5.createCell(c).setCellStyle(hStyle);
            sheet.addMergedRegion(new CellRangeAddress(5, 6, 10, 15));

            String[] errLabels = {"Sai tiêu chí", "Sai thời gian hưởng", "Sai thâm niên BHXH", "Sai mức lương", "Sai công thức", "Sai tổng tiền"};
            for (int i = 0; i < errLabels.length; i++) {
                Cell cell = hr7.createCell(10 + i);
                cell.setCellValue(errLabels[i]);
                cell.setCellStyle(hStyle);
                hr8.createCell(10 + i).setCellStyle(hStyle);
                sheet.addMergedRegion(new CellRangeAddress(7, 8, 10 + i, 10 + i));
            }

            // Cột 16: Số tiền chênh lệch
            Cell cDiff = hr5.createCell(16);
            cDiff.setCellValue("Số tiền chênh lệch (VND)");
            cDiff.setCellStyle(hStyle);
            for (int r = 6; r <= 8; r++) sheet.getRow(r).createCell(16).setCellStyle(hStyle);
            sheet.addMergedRegion(new CellRangeAddress(5, 8, 16, 16));

            // Cột 17: Giải thích
            Cell cExp = hr5.createCell(17);
            cExp.setCellValue("Giải thích chi tiết ngắn gọn");
            cExp.setCellStyle(hStyle);
            for (int r = 6; r <= 8; r++) sheet.getRow(r).createCell(17).setCellStyle(hStyle);
            sheet.addMergedRegion(new CellRangeAddress(5, 8, 17, 17));

            for (int i = 0; i < 18; i++) {
                Cell c = hr9.createCell(i);
                c.setCellValue(String.valueOf(i + 1));
                c.setCellStyle(hStyle);
            }

            sheet.setColumnWidth(0, 1800);
            sheet.setColumnWidth(1, 5500);
            sheet.setColumnWidth(2, 3200);
            sheet.setColumnWidth(3, 2800);
            sheet.setColumnWidth(4, 3000);
            sheet.setColumnWidth(5, 2800);
            sheet.setColumnWidth(6, 3200);
            sheet.setColumnWidth(7, 2400);
            sheet.setColumnWidth(8, 2400);
            sheet.setColumnWidth(9, 2400);
            for (int i = 10; i <= 15; i++) sheet.setColumnWidth(i, 2600);
            sheet.setColumnWidth(16, 4500);
            sheet.setColumnWidth(17, 12000);

            try (FileOutputStream fos = new FileOutputStream(TEMPLATE_DIR + "PhuLucII.xlsx")) {
                wb.write(fos);
            }
        }
    }

    private XSSFCellStyle createHeaderStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 9.5);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private XSSFCellStyle createMetaBold(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        return style;
    }

    private XSSFCellStyle createMetaItalic(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setItalic(true);
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        return style;
    }
}
