package com.bqpvalidateexcel;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class BqpValidateExcelApplicationTests {

    @Test
    void contextLoads() {
    }

    @Test
    void testWhitespaceCleanup() {
        org.junit.jupiter.api.Assertions.assertEquals("Thượng tá", 
            com.bqpvalidateexcel.excel.parser.ExcelParserUtils.cleanString("  Thượng \u00A0 tá  "));
        org.junit.jupiter.api.Assertions.assertEquals("Nguyễn Văn A", 
            com.bqpvalidateexcel.excel.parser.ExcelParserUtils.cleanString(" Nguyễn   Văn   A "));
    }

    @Test
    void testPLI1Calculation() throws Exception {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM/yyyy");

        // TH1: Nghỉ hưu từ 01/07/2025 (ví dụ 07/2025, mốc 15 năm, hệ số 0.4)
        com.bqpvalidateexcel.excel.model.dto.PhuLucI1 dataSau2025 = com.bqpvalidateexcel.excel.model.dto.PhuLucI1.builder()
                .ngaySinh(sdf.parse("18/08/1974"))
                .capBac("Thượng tá")
                .chucVu("Trợ lý")
                .nhapNgu(sdf.parse("01/09/1992"))
                .thoiGianDonViSapNhapGiaiThe(sdf.parse("01/01/2026"))
                .thoiDiemNghiHuuHuongTroCap(sdf.parse("01/07/2025"))
                .luongThangHienThuongTheoThongTu(java.math.BigDecimal.valueOf(27_186_120))
                .build();

        com.bqpvalidateexcel.excel.model.expected.PLI1ExpectedResult resSau2025 =
                com.bqpvalidateexcel.excel.service.rules.PLI1Calculator.calculateExpected(dataSau2025);

        // Cột 12: 09/1992 đến 07/2025 = 394 tháng = 32 năm 10 tháng -> làm tròn 33 năm
        org.junit.jupiter.api.Assertions.assertEquals(java.math.BigDecimal.valueOf(33), resSau2025.getCot12());
        // Vì exp11 = 5.5 năm (> 5 năm và <= 10 năm) -> thuộc nhóm Cột 20, 21, 22
        // Cột 21: 27.186.120 * 4 = 108.744.480
        org.junit.jupiter.api.Assertions.assertEquals(0, java.math.BigDecimal.valueOf(108_744_480).compareTo(resSau2025.getCot21()));
        // Cột 22: 27.186.120 * 0.5 * (33 - 15) = 244.675.080
        org.junit.jupiter.api.Assertions.assertEquals(0, java.math.BigDecimal.valueOf(244_675_080).compareTo(resSau2025.getCot22()));

        // TH2: Nghỉ hưu trước 01/07/2025 (ví dụ 01/01/2025, mốc 20 năm, hệ số 0.5)
        com.bqpvalidateexcel.excel.model.dto.PhuLucI1 dataTruoc2025 = com.bqpvalidateexcel.excel.model.dto.PhuLucI1.builder()
                .ngaySinh(sdf.parse("18/08/1974"))
                .capBac("Thượng tá")
                .chucVu("Trợ lý")
                .nhapNgu(sdf.parse("01/09/1992"))
                .thoiGianDonViSapNhapGiaiThe(sdf.parse("01/01/2026"))
                .thoiDiemNghiHuuHuongTroCap(sdf.parse("01/01/2025"))
                .luongThangHienThuongTheoThongTu(java.math.BigDecimal.valueOf(20_000_000))
                .build();

        com.bqpvalidateexcel.excel.model.expected.PLI1ExpectedResult resTruoc2025 =
                com.bqpvalidateexcel.excel.service.rules.PLI1Calculator.calculateExpected(dataTruoc2025);

        // Cột 12: 09/1992 đến 01/2025 = 388 tháng = 32 năm 4 tháng -> làm tròn 32.5 năm
        org.junit.jupiter.api.Assertions.assertEquals(java.math.BigDecimal.valueOf(32.5), resTruoc2025.getCot12());
        // Cột 21: 20.000.000 * 5 = 100.000.000
        org.junit.jupiter.api.Assertions.assertEquals(0, java.math.BigDecimal.valueOf(100_000_000).compareTo(resTruoc2025.getCot21()));
        // Cột 22: 20.000.000 * 0.5 * (32.5 - 20) = 125.000.000
        org.junit.jupiter.api.Assertions.assertEquals(0, java.math.BigDecimal.valueOf(125_000_000).compareTo(resTruoc2025.getCot22()));

        // TH3: Tuổi đời từ 2 đến 5 năm (thuộc nhóm Cột 17, 18, 19), nghỉ sau 01/07/2025
        com.bqpvalidateexcel.excel.model.dto.PhuLucI1 dataTuoi2Den5 = com.bqpvalidateexcel.excel.model.dto.PhuLucI1.builder()
                .ngaySinh(sdf.parse("18/08/1972")) // Trần 56 -> 08/2028
                .capBac("Thượng tá")
                .chucVu("Trợ lý")
                .nhapNgu(sdf.parse("01/09/1992"))
                .thoiGianDonViSapNhapGiaiThe(sdf.parse("01/01/2026"))
                .thoiDiemNghiHuuHuongTroCap(sdf.parse("01/08/2025")) // còn 36 + 1 = 37 tháng -> 3.5 năm
                .luongThangHienThuongTheoThongTu(java.math.BigDecimal.valueOf(10_000_000))
                .build();

        com.bqpvalidateexcel.excel.model.expected.PLI1ExpectedResult resTuoi2Den5 =
                com.bqpvalidateexcel.excel.service.rules.PLI1Calculator.calculateExpected(dataTuoi2Den5);

        // Cột 18: 10.000.000 * 4 = 40.000.000
        org.junit.jupiter.api.Assertions.assertEquals(0, java.math.BigDecimal.valueOf(40_000_000).compareTo(resTuoi2Den5.getCot18()));
        // Cột 19: 10.000.000 * 0.5 * (33 - 15) = 90.000.000
        org.junit.jupiter.api.Assertions.assertEquals(0, java.math.BigDecimal.valueOf(90_000_000).compareTo(resTuoi2Den5.getCot19()));

        // TH4: Tuổi đời < 2 năm (Cột 11 < 2 năm) -> Cột 17..22 không cần tính (đều = 0)
        com.bqpvalidateexcel.excel.model.dto.PhuLucI1 dataDuoi2Nam = com.bqpvalidateexcel.excel.model.dto.PhuLucI1.builder()
                .ngaySinh(sdf.parse("18/08/1970")) // Trần 56 -> 08/2026
                .capBac("Thượng tá")
                .chucVu("Trợ lý")
                .nhapNgu(sdf.parse("01/09/1992"))
                .thoiGianDonViSapNhapGiaiThe(sdf.parse("01/01/2026"))
                .thoiDiemNghiHuuHuongTroCap(sdf.parse("01/08/2025")) // còn 12 + 1 = 13 tháng -> 1.5 năm
                .luongThangHienThuongTheoThongTu(java.math.BigDecimal.valueOf(10_000_000))
                .build();

        com.bqpvalidateexcel.excel.model.expected.PLI1ExpectedResult resDuoi2Nam =
                com.bqpvalidateexcel.excel.service.rules.PLI1Calculator.calculateExpected(dataDuoi2Nam);

        // Cột 11 < 2 năm -> Cột 17..22 không cần tính (đều bằng 0)
        org.junit.jupiter.api.Assertions.assertEquals(0, java.math.BigDecimal.ZERO.compareTo(resDuoi2Nam.getCot17()));
        org.junit.jupiter.api.Assertions.assertEquals(0, java.math.BigDecimal.ZERO.compareTo(resDuoi2Nam.getCot18()));
        org.junit.jupiter.api.Assertions.assertEquals(0, java.math.BigDecimal.ZERO.compareTo(resDuoi2Nam.getCot19()));
        org.junit.jupiter.api.Assertions.assertEquals(0, java.math.BigDecimal.ZERO.compareTo(resDuoi2Nam.getCot20()));
        org.junit.jupiter.api.Assertions.assertEquals(0, java.math.BigDecimal.ZERO.compareTo(resDuoi2Nam.getCot21()));
        org.junit.jupiter.api.Assertions.assertEquals(0, java.math.BigDecimal.ZERO.compareTo(resDuoi2Nam.getCot22()));
    }

    @Test
    void testPLI2Rounding() {
        // 240 tháng = 20 năm đúng
        org.junit.jupiter.api.Assertions.assertEquals(
            java.math.BigDecimal.valueOf(20),
            com.bqpvalidateexcel.excel.service.rules.PLI2Calculator.calcNamLamTron(240)
        );
        // 244 tháng = 20 năm 4 tháng (<= 6 tháng -> +0.5)
        org.junit.jupiter.api.Assertions.assertEquals(
            java.math.BigDecimal.valueOf(20.5),
            com.bqpvalidateexcel.excel.service.rules.PLI2Calculator.calcNamLamTron(244)
        );
        // 246 tháng = 20 năm 6 tháng (<= 6 tháng -> +0.5)
        org.junit.jupiter.api.Assertions.assertEquals(
            java.math.BigDecimal.valueOf(20.5),
            com.bqpvalidateexcel.excel.service.rules.PLI2Calculator.calcNamLamTron(246)
        );
        // 247 tháng = 20 năm 7 tháng (> 6 tháng -> +1.0)
        org.junit.jupiter.api.Assertions.assertEquals(
            java.math.BigDecimal.valueOf(21),
            com.bqpvalidateexcel.excel.service.rules.PLI2Calculator.calcNamLamTron(247)
        );
    }

    @Test
    void testPLI2Calculation() throws Exception {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM/yyyy");
        // Trường hợp 1: Nghỉ trong 12 tháng đầu và tuổi đời > 2 năm
        com.bqpvalidateexcel.excel.model.dto.PhuLucI2 data1 = com.bqpvalidateexcel.excel.model.dto.PhuLucI2.builder()
                .ngaySinh(sdf.parse("01/01/1975"))
                .capBac("Thiếu tá")
                .chucVu("Trợ lý")
                .nhapNgu(sdf.parse("01/01/2005"))
                .thoiGianDonViSapNhapGiaiThe(sdf.parse("01/06/2023"))
                .thoiDiemThoiViecHuongTroCap(sdf.parse("01/01/2024"))
                .luongThangHienThuongTheoThongTu(java.math.BigDecimal.valueOf(10_000_000))
                .build();

        com.bqpvalidateexcel.excel.model.expected.PLI2ExpectedResult res1 = 
            com.bqpvalidateexcel.excel.service.rules.PLI2Calculator.calculateExpected(data1);

        org.junit.jupiter.api.Assertions.assertTrue(res1.isWithin12Months());
        org.junit.jupiter.api.Assertions.assertTrue(res1.isOver2Years());
        org.junit.jupiter.api.Assertions.assertEquals(36, res1.getCot10()); // 36 tháng thôi việc trước tuổi (khống chế tối đa 60)
        org.junit.jupiter.api.Assertions.assertEquals(java.math.BigDecimal.valueOf(19), res1.getCot11()); // 19 năm công tác đóng BHXH
        org.junit.jupiter.api.Assertions.assertEquals(0, java.math.BigDecimal.valueOf(288_000_000).compareTo(res1.getCot12()));
        org.junit.jupiter.api.Assertions.assertEquals(0, java.math.BigDecimal.valueOf(285_000_000).compareTo(res1.getCot13()));
        org.junit.jupiter.api.Assertions.assertEquals(0, java.math.BigDecimal.valueOf(30_000_000).compareTo(res1.getCot14()));
        org.junit.jupiter.api.Assertions.assertEquals(0, java.math.BigDecimal.ZERO.compareTo(res1.getCot15()));
        org.junit.jupiter.api.Assertions.assertEquals(0, java.math.BigDecimal.ZERO.compareTo(res1.getCot16()));
        org.junit.jupiter.api.Assertions.assertEquals(0, java.math.BigDecimal.ZERO.compareTo(res1.getCot17()));
        org.junit.jupiter.api.Assertions.assertEquals(0, java.math.BigDecimal.valueOf(603_000_000).compareTo(res1.getCot18()));

        // Trường hợp 2: Tuổi đời <= 2 năm -> không được hưởng
        com.bqpvalidateexcel.excel.model.dto.PhuLucI2 data2 = com.bqpvalidateexcel.excel.model.dto.PhuLucI2.builder()
                .ngaySinh(sdf.parse("01/01/1973"))
                .capBac("Thiếu tá") // trần 52 -> 01/01/2025
                .nhapNgu(sdf.parse("01/01/2005"))
                .thoiGianDonViSapNhapGiaiThe(sdf.parse("01/06/2023"))
                .thoiDiemThoiViecHuongTroCap(sdf.parse("01/01/2024")) // còn 12 tháng <= 24 tháng
                .luongThangHienThuongTheoThongTu(java.math.BigDecimal.valueOf(10_000_000))
                .build();

        com.bqpvalidateexcel.excel.model.expected.PLI2ExpectedResult res2 = 
            com.bqpvalidateexcel.excel.service.rules.PLI2Calculator.calculateExpected(data2);
        org.junit.jupiter.api.Assertions.assertFalse(res2.isOver2Years());
        org.junit.jupiter.api.Assertions.assertEquals(0, java.math.BigDecimal.ZERO.compareTo(res2.getCot18()));
    }

    @org.springframework.beans.factory.annotation.Autowired
    private com.bqpvalidateexcel.excel.service.ExcelValidationService validationService;

    @Test
    void testValidateOfficialFilePosition() throws Exception {
        java.io.File file = new java.io.File("d:\\WorkSpace\\spring-master\\spring-microservice\\bqp-validate-excel\\11. PL BC Quân khu gửi BQP (sửa 10.9.2026).xlsx");
        if (!file.exists()) return;

        try (java.io.InputStream is = new java.io.FileInputStream(file)) {
            byte[] out = validationService.validateAndGenerateErrorReport(is);
            try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(out);
                 org.apache.poi.ss.usermodel.Workbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(bais)) {
                org.apache.poi.ss.usermodel.Sheet ws = wb.getSheet("Phụ lục I.2");
                org.junit.jupiter.api.Assertions.assertNotNull(ws);
                // Header row 9 (index 8):
                org.apache.poi.ss.usermodel.Row r9 = ws.getRow(8);
                // Col T (idx 19) should remain "19"
                org.apache.poi.ss.usermodel.FormulaEvaluator eval = wb.getCreationHelper().createFormulaEvaluator();
                org.junit.jupiter.api.Assertions.assertEquals("19", com.bqpvalidateexcel.excel.parser.ExcelParserUtils.getString(r9, 19, eval));
                // Col U (idx 20) should remain "20=18+19"
                org.junit.jupiter.api.Assertions.assertEquals("20=18+19", com.bqpvalidateexcel.excel.parser.ExcelParserUtils.getString(r9, 20, eval));
                // Col V (idx 21) should be "Ghi chú"
                org.junit.jupiter.api.Assertions.assertEquals("Ghi chú", com.bqpvalidateexcel.excel.parser.ExcelParserUtils.getString(r9, 21, eval));

                // Kiểm tra Phụ lục I.1 cho dòng Nguyễn Văn Chinh (row index 16): nghỉ hưu 07/2025 -> áp dụng hệ số 0.4 cho Cột 19
                org.apache.poi.ss.usermodel.Sheet ws1 = wb.getSheet("Phụ lục I.1");
                org.junit.jupiter.api.Assertions.assertNotNull(ws1);
                org.apache.poi.ss.usermodel.Row rChinh = ws1.getRow(16);
                String noteChinh = com.bqpvalidateexcel.excel.parser.ExcelParserUtils.getString(rChinh, 24, eval);
                org.junit.jupiter.api.Assertions.assertNotNull(noteChinh);
                org.junit.jupiter.api.Assertions.assertTrue(
                    noteChinh.contains("Cột 22. Kết quả đúng: 244.675.080. Công thức: Cột 9 * 0.5 * (Cột 12 - 15)"),
                    "Expected note to contain 0.5 formula and 244.675.080 result for Cột 22, but was: " + noteChinh
                );

                java.nio.file.Files.write(java.nio.file.Paths.get("Result_11. PL BC Quân khu gửi BQP (sửa 10.9.2026).xlsx"), out);
            }
        }
    }

    @Test
    void testPLI3Calculation() throws Exception {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM/yyyy");

        // TH1: Nghỉ hưu sau 01/07/2025 (ví dụ 02/2026, mốc 15 năm)
        com.bqpvalidateexcel.excel.model.dto.PhuLucI3 data1 = com.bqpvalidateexcel.excel.model.dto.PhuLucI3.builder()
                .ngaySinh(sdf.parse("30/08/1968"))
                .capBac("Đại tá")
                .chucVu("Trưởng phòng Pháo binh")
                .nhapNgu(sdf.parse("01/09/1985"))
                .thoiDiemNghiHuuHuongTroCap(sdf.parse("01/02/2026"))
                .luongThangHienThuongTheoHuongDan(java.math.BigDecimal.valueOf(30_625_920))
                .soNamHuongTroCapTheoHuongDan(java.math.BigDecimal.valueOf(1.0))
                .build();

        com.bqpvalidateexcel.excel.model.expected.PLI3ExpectedResult res1 =
                com.bqpvalidateexcel.excel.service.rules.PLI3Calculator.calculateExpected(data1);

        org.junit.jupiter.api.Assertions.assertEquals(java.math.BigDecimal.valueOf(40.5), res1.getCot10());
        org.junit.jupiter.api.Assertions.assertEquals(java.math.BigDecimal.valueOf(1.0), res1.getCot11());
        org.junit.jupiter.api.Assertions.assertEquals(0, java.math.BigDecimal.valueOf(153_129_600).compareTo(res1.getCot12()));
        org.junit.jupiter.api.Assertions.assertEquals(0, java.math.BigDecimal.valueOf(153_129_600).compareTo(res1.getCot13()));
        org.junit.jupiter.api.Assertions.assertEquals(0, java.math.BigDecimal.valueOf(390_480_480).compareTo(res1.getCot14()));
        org.junit.jupiter.api.Assertions.assertEquals(0, java.math.BigDecimal.valueOf(696_739_680).compareTo(res1.getCot15()));

        // TH2: Nghỉ hưu trước 01/07/2025 (ví dụ 01/01/2025, mốc 20 năm)
        com.bqpvalidateexcel.excel.model.dto.PhuLucI3 data2 = com.bqpvalidateexcel.excel.model.dto.PhuLucI3.builder()
                .ngaySinh(sdf.parse("30/08/1968"))
                .capBac("Đại tá")
                .nhapNgu(sdf.parse("01/09/1985"))
                .thoiDiemNghiHuuHuongTroCap(sdf.parse("01/01/2025"))
                .luongThangHienThuongTheoHuongDan(java.math.BigDecimal.valueOf(30_625_920))
                .soNamHuongTroCapTheoHuongDan(java.math.BigDecimal.valueOf(1.0))
                .build();

        com.bqpvalidateexcel.excel.model.expected.PLI3ExpectedResult res2 =
                com.bqpvalidateexcel.excel.service.rules.PLI3Calculator.calculateExpected(data2);

        // 01/1985 -> 01/2025: 472 tháng = 39 năm 4 tháng -> 39.5 năm
        org.junit.jupiter.api.Assertions.assertEquals(java.math.BigDecimal.valueOf(39.5), res2.getCot10());
        // Mốc 20 năm: 30.625.920 * 0.5 * (39.5 - 20) = 30.625.920 * 0.5 * 19.5 = 298.602.720
        org.junit.jupiter.api.Assertions.assertEquals(0, java.math.BigDecimal.valueOf(298_602_720).compareTo(res2.getCot14()));
    }

    @Test
    void testValidatePLI3FilePosition() throws Exception {
        java.io.File file = new java.io.File("d:\\WorkSpace\\spring-master\\spring-microservice\\bqp-validate-excel\\11. PL BC Quân khu gửi BQP (sửa 10.9.2026).xlsx");
        if (!file.exists()) return;

        try (java.io.InputStream is = new java.io.FileInputStream(file)) {
            byte[] out = validationService.validateAndGenerateErrorReport(is);
            try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(out);
                 org.apache.poi.ss.usermodel.Workbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(bais)) {
                org.apache.poi.ss.usermodel.Sheet ws = wb.getSheet("PL I.3");
                if (ws == null) ws = wb.getSheet("Phụ lục I.3");
                org.junit.jupiter.api.Assertions.assertNotNull(ws);

                org.apache.poi.ss.usermodel.FormulaEvaluator eval = wb.getCreationHelper().createFormulaEvaluator();
                
                // Trong file 11. PL BC: không có cột 'Đơn vị' -> Cột 15 là Col P (idx 15), Ghi chú là Col Q (idx 16)
                org.apache.poi.ss.usermodel.Row r8 = ws.getRow(7);
                org.junit.jupiter.api.Assertions.assertEquals("15=12 đến14", com.bqpvalidateexcel.excel.parser.ExcelParserUtils.getString(r8, 15, eval));
                org.junit.jupiter.api.Assertions.assertEquals("Ghi chú", com.bqpvalidateexcel.excel.parser.ExcelParserUtils.getString(r8, 16, eval));

                // Kiểm tra dòng Phạm Văn Chi (row index 10)
                org.apache.poi.ss.usermodel.Row rChi = ws.getRow(10);
                org.junit.jupiter.api.Assertions.assertEquals(
                    0,
                    java.math.BigDecimal.valueOf(696_739_680).compareTo(
                        com.bqpvalidateexcel.excel.parser.ExcelParserUtils.getBigDecimal(rChi, 15, eval)
                    )
                );
                // Kiểm tra Cột 10 không bị ghi đè lên cell (vẫn giữ nguyên trạng thái ban đầu của file)
                org.junit.jupiter.api.Assertions.assertNull(
                    com.bqpvalidateexcel.excel.parser.ExcelParserUtils.getBigDecimal(rChi, 10, eval)
                );
                // Kiểm tra cột Ghi chú (idx 16) ghi rõ lỗi và kết quả đúng của Cột 10
                org.junit.jupiter.api.Assertions.assertTrue(
                    com.bqpvalidateexcel.excel.parser.ExcelParserUtils.getString(rChi, 16, eval).contains("Cột 10. Kết quả đúng: 40,5")
                );
            }
        }

        // Test với file có cột 'Đơn vị' (như Result_Result_11... (4).xlsx)
        java.io.File fileDonVi = new java.io.File("d:\\WorkSpace\\spring-master\\spring-microservice\\bqp-validate-excel\\Result_Result_11. PL BC Quân khu gửi BQP (sửa 10.9.2026) (4).xlsx");
        if (fileDonVi.exists()) {
            try (java.io.InputStream is = new java.io.FileInputStream(fileDonVi)) {
                byte[] out = validationService.validateAndGenerateErrorReport(is);
                try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(out);
                     org.apache.poi.ss.usermodel.Workbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(bais)) {
                    org.apache.poi.ss.usermodel.Sheet ws = wb.getSheet("PL I.3");
                    org.junit.jupiter.api.Assertions.assertNotNull(ws);

                    org.apache.poi.ss.usermodel.FormulaEvaluator eval = wb.getCreationHelper().createFormulaEvaluator();

                    // File có cột 'Đơn vị': Cột 15 là Col Q (idx 16), Ghi chú là Col R (idx 17)
                    org.apache.poi.ss.usermodel.Row r8 = ws.getRow(7);
                    org.junit.jupiter.api.Assertions.assertEquals("15=12 đến14", com.bqpvalidateexcel.excel.parser.ExcelParserUtils.getString(r8, 16, eval));
                    org.junit.jupiter.api.Assertions.assertEquals("Ghi chú", com.bqpvalidateexcel.excel.parser.ExcelParserUtils.getString(r8, 17, eval));

                    // Kiểm tra dòng Phạm Văn Chi (row index 10 in POI, row 11 in Excel)
                    // Cột Q (idx 16) phải là số tiền 696739680, KHÔNG BỊ GHI CHÚ ĐÈ VÀO!
                    org.apache.poi.ss.usermodel.Row rChi = ws.getRow(10);
                    java.math.BigDecimal valQ = com.bqpvalidateexcel.excel.parser.ExcelParserUtils.getBigDecimal(rChi, 16, eval);
                    org.junit.jupiter.api.Assertions.assertNotNull(valQ);
                    org.junit.jupiter.api.Assertions.assertEquals(0, java.math.BigDecimal.valueOf(696_739_680).compareTo(valQ));

                    // Kiểm tra Cột 10 không bị ghi đè lên cell (vẫn null)
                    org.junit.jupiter.api.Assertions.assertNull(
                        com.bqpvalidateexcel.excel.parser.ExcelParserUtils.getBigDecimal(rChi, 11, eval)
                    );
                    // Kiểm tra cột Ghi chú (idx 17) ghi nhận kết quả đúng của Cột 10
                    org.junit.jupiter.api.Assertions.assertTrue(
                        com.bqpvalidateexcel.excel.parser.ExcelParserUtils.getString(rChi, 17, eval).contains("Cột 10. Kết quả đúng: 40,5")
                    );
                }
            }
        }
    }

    @Test
    void testExportErrorsToPhuLucII() throws Exception {
        // Test 1: File KHÔNG có sheet Phụ lục II ban đầu (4. Phụ lục TCCT.xlsx)
        java.io.File fileNoPLII = new java.io.File("d:\\WorkSpace\\spring-master\\spring-microservice\\bqp-validate-excel\\4. Phụ lục TCCT.xlsx");
        if (fileNoPLII.exists()) {
            try (java.io.InputStream is = new java.io.FileInputStream(fileNoPLII)) {
                byte[] out = validationService.validateAndGenerateErrorReport(is);
                try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(out);
                     org.apache.poi.ss.usermodel.Workbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(bais)) {
                    org.apache.poi.ss.usermodel.Sheet wsPLII = wb.getSheet("Phụ lục II");
                    org.junit.jupiter.api.Assertions.assertNotNull(wsPLII, "Sheet Phụ lục II phải tự động được tạo mới");

                    org.apache.poi.ss.usermodel.FormulaEvaluator eval = wb.getCreationHelper().createFormulaEvaluator();

                    // Tìm vị trí các cột trong Phụ lục II
                    int colHoTen = -1;
                    int colND178 = -1;
                    int colND177 = -1;
                    int colSaiThoiGian = -1;
                    int colThucTe = -1;
                    int colTinhLai = -1;
                    int colChenhLech = -1;
                    int colGiaiThich = -1;

                    for (int r = 0; r <= 9; r++) {
                        org.apache.poi.ss.usermodel.Row row = wsPLII.getRow(r);
                        if (row == null) continue;
                        for (int c = 0; c < row.getLastCellNum(); c++) {
                            String val = com.bqpvalidateexcel.excel.parser.ExcelParserUtils.getString(row, c, eval);
                            if (val == null) continue;
                            String lower = val.toLowerCase();
                            if (lower.contains("họ và tên") || lower.contains("họ tên")) colHoTen = c;
                            else if (lower.contains("178")) colND178 = c;
                            else if (lower.contains("177")) colND177 = c;
                            else if (lower.contains("sai thời gian")) colSaiThoiGian = c;
                            else if (lower.contains("thực tế")) colThucTe = c;
                            else if (lower.contains("tính lại")) colTinhLai = c;
                            else if (lower.contains("chênh lệch") || lower.contains("chenh lech")) colChenhLech = c;
                            else if (lower.contains("giải thích")) colGiaiThich = c;
                        }
                    }

                    org.junit.jupiter.api.Assertions.assertTrue(colHoTen >= 0, "Cột Họ và tên phải tồn tại");
                    org.junit.jupiter.api.Assertions.assertTrue(colND178 >= 0, "Cột Nghỉ hưu theo NĐ 178 phải tồn tại");
                    org.junit.jupiter.api.Assertions.assertTrue(colND177 >= 0, "Cột Nghỉ hưu theo NĐ 177 phải tồn tại");
                    org.junit.jupiter.api.Assertions.assertTrue(colSaiThoiGian >= 0, "Cột Sai thời gian được hưởng phải tồn tại");
                    org.junit.jupiter.api.Assertions.assertTrue(colThucTe >= 0, "Cột Thực tế phải tồn tại");
                    org.junit.jupiter.api.Assertions.assertTrue(colTinhLai >= 0, "Cột Tính lại phải tồn tại");
                    org.junit.jupiter.api.Assertions.assertTrue(colChenhLech >= 0, "Cột Chênh lệch phải tồn tại");
                    org.junit.jupiter.api.Assertions.assertEquals(colTinhLai + 1, colChenhLech, "Cột Chênh lệch phải nằm ngay bên cạnh cột Tính lại");
                    org.junit.jupiter.api.Assertions.assertTrue(colGiaiThich >= 0, "Cột Giải thích phải tồn tại");

                    // Kiểm tra các dòng dữ liệu lỗi được chèn vào
                    boolean foundI1 = false;
                    boolean foundI3 = false;

                    for (int r = 0; r <= wsPLII.getLastRowNum(); r++) {
                        org.apache.poi.ss.usermodel.Row row = wsPLII.getRow(r);
                        if (row == null) continue;
                        String name = com.bqpvalidateexcel.excel.parser.ExcelParserUtils.getString(row, colHoTen, eval);
                        if (name == null || name.isEmpty()) continue;
                        // Bỏ qua các hàng tiêu đề và hàng đánh số cột
                        if (name.matches("\\d+") || name.equalsIgnoreCase("Họ và tên") || name.equalsIgnoreCase("Họ tên")) continue;

                        String mark178 = com.bqpvalidateexcel.excel.parser.ExcelParserUtils.getString(row, colND178, eval);
                        String mark177 = com.bqpvalidateexcel.excel.parser.ExcelParserUtils.getString(row, colND177, eval);
                        String markTG = com.bqpvalidateexcel.excel.parser.ExcelParserUtils.getString(row, colSaiThoiGian, eval);
                        String valThucTe = com.bqpvalidateexcel.excel.parser.ExcelParserUtils.getString(row, colThucTe, eval);
                        String valTinhLai = com.bqpvalidateexcel.excel.parser.ExcelParserUtils.getString(row, colTinhLai, eval);
                        String valChenhLech = com.bqpvalidateexcel.excel.parser.ExcelParserUtils.getString(row, colChenhLech, eval);
                        String valGiaiThich = com.bqpvalidateexcel.excel.parser.ExcelParserUtils.getString(row, colGiaiThich, eval);

                        boolean isEmployeeRow = "x".equalsIgnoreCase(mark178) || "x".equalsIgnoreCase(mark177) || "x".equalsIgnoreCase(markTG);
                        if (isEmployeeRow) {
                            // Cột giải thích không được chèn dữ liệu
                            org.junit.jupiter.api.Assertions.assertTrue(valGiaiThich == null || valGiaiThich.trim().isEmpty(), "Cột giải thích không được chèn dữ liệu");
                            if (valThucTe != null && !valThucTe.isEmpty() && valTinhLai != null && !valTinhLai.isEmpty()) {
                                org.junit.jupiter.api.Assertions.assertNotNull(valChenhLech, "Cột Chênh lệch phải có giá trị khi có sai tổng tiền");
                            }
                        }

                        if ("x".equalsIgnoreCase(mark178)) {
                            foundI1 = true;
                            org.junit.jupiter.api.Assertions.assertTrue("x".equalsIgnoreCase(markTG) || (valThucTe != null && !valThucTe.isEmpty()));
                        }
                        if ("x".equalsIgnoreCase(mark177)) {
                            foundI3 = true;
                            org.junit.jupiter.api.Assertions.assertTrue("x".equalsIgnoreCase(markTG) || (valThucTe != null && !valThucTe.isEmpty()));
                        }
                    }

                    org.junit.jupiter.api.Assertions.assertTrue(foundI1, "Phải có bản ghi lỗi từ I.1 được chuyển sang Phụ lục II");
                    org.junit.jupiter.api.Assertions.assertTrue(foundI3, "Phải có bản ghi lỗi từ I.3 được chuyển sang Phụ lục II");

                    java.nio.file.Files.write(java.nio.file.Paths.get("Result_4. Phụ lục TCCT.xlsx"), out);
                }
            }
        }

        // Test 2: File có sẵn Phụ lục II và có nhiều người chênh lệch 24 tháng (11. PL BC Quân khu gửi BQP (sửa 10.9.2026).xlsx)
        java.io.File file11 = new java.io.File("d:\\WorkSpace\\spring-master\\spring-microservice\\bqp-validate-excel\\11. PL BC Quân khu gửi BQP (sửa 10.9.2026).xlsx");
        if (file11.exists()) {
            try (java.io.InputStream is = new java.io.FileInputStream(file11)) {
                byte[] out = validationService.validateAndGenerateErrorReport(is);
                try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(out);
                     org.apache.poi.ss.usermodel.Workbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(bais)) {
                    org.apache.poi.ss.usermodel.Sheet wsPLII = wb.getSheet("Phụ lục II");
                    org.junit.jupiter.api.Assertions.assertNotNull(wsPLII);
                    org.apache.poi.ss.usermodel.FormulaEvaluator eval = wb.getCreationHelper().createFormulaEvaluator();

                    int colThucTe = -1;
                    int colTinhLai = -1;
                    int colChenhLech = -1;
                    for (int r = 0; r <= 9; r++) {
                        org.apache.poi.ss.usermodel.Row row = wsPLII.getRow(r);
                        if (row == null) continue;
                        for (int c = 0; c < row.getLastCellNum(); c++) {
                            String val = com.bqpvalidateexcel.excel.parser.ExcelParserUtils.getString(row, c, eval);
                            if (val == null) continue;
                            String lower = val.toLowerCase();
                            if (lower.contains("thực tế")) colThucTe = c;
                            else if (lower.contains("tính lại")) colTinhLai = c;
                            else if (lower.contains("chênh lệch") || lower.contains("chenh lech")) colChenhLech = c;
                        }
                    }
                    org.junit.jupiter.api.Assertions.assertTrue(colThucTe >= 0, "Cột Thực tế phải tồn tại");
                    org.junit.jupiter.api.Assertions.assertTrue(colTinhLai >= 0, "Cột Tính lại phải tồn tại");
                    org.junit.jupiter.api.Assertions.assertTrue(colChenhLech >= 0, "Cột Chênh lệch phải tồn tại");
                    org.junit.jupiter.api.Assertions.assertEquals(colTinhLai + 1, colChenhLech, "Cột Chênh lệch phải nằm ngay bên cạnh cột Tính lại");

                    // Kiểm tra những người chỉ sai 24 tháng (như Nguyễn Văn Chinh row 16) KHÔNG được chèn thêm vào sheet Phụ lục II
                    for (int r = 0; r <= wsPLII.getLastRowNum(); r++) {
                        org.apache.poi.ss.usermodel.Row row = wsPLII.getRow(r);
                        if (row == null) continue;
                        String hoTen = com.bqpvalidateexcel.excel.parser.ExcelParserUtils.getString(row, 1, eval);
                        if (hoTen != null && hoTen.toLowerCase().contains("nguyễn văn chinh")) {
                            // Nguyễn Văn Chinh chỉ nằm ở dữ liệu cũ ban đầu của file gốc (r < 100), không bị chèn thêm ở phần cuối sheet (> 2600)
                            org.junit.jupiter.api.Assertions.assertTrue(
                                r < 100,
                                "Nguyễn Văn Chinh chênh lệch 24 tháng tuổi nghỉ hưu không được chèn thêm vào Phụ lục II"
                            );
                        }
                    }
                }
            }
        }
    }
}

