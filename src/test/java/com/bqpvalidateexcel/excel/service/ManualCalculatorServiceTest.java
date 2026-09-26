package com.bqpvalidateexcel.excel.service;

import com.bqpvalidateexcel.excel.model.dto.ManualCalculateRequestDto;
import com.bqpvalidateexcel.excel.model.dto.ManualCalculateResponseDto;
import com.bqpvalidateexcel.excel.util.VietnameseNumberToWords;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

public class ManualCalculatorServiceTest {

    private final ManualCalculatorService service = new ManualCalculatorService();

    @Test
    void testVietnameseNumberToWords() {
        assertEquals("Một trăm năm mươi triệu đồng chẵn", VietnameseNumberToWords.toWords(150000000L));
        assertEquals("Hai triệu ba trăm bốn mươi nghìn đồng chẵn", VietnameseNumberToWords.toWords(2340000L));
        assertEquals("Không đồng", VietnameseNumberToWords.toWords(0L));
    }

    @Test
    void testCalculatePLI1() {
        ManualCalculateRequestDto req = ManualCalculateRequestDto.builder()
                .sheetType("I.1")
                .hoTen("Nguyễn Văn A")
                .capBac("Đại tá")
                .chucVu("Chỉ huy trưởng")
                .ngaySinh("10/1970")
                .nhapNgu("09/1988")
                .thoiDiemNghi("01/2025")
                .luongThang(BigDecimal.valueOf(25000000))
                .build();

        ManualCalculateResponseDto res = service.calculatePLI1(req);
        assertNotNull(res);
        assertTrue(res.isSuccess());
        assertEquals("I.1", res.getSheetType());
        assertEquals(58, res.getTranTuoi());
        assertNotNull(res.getTotalAmount());
        assertNotNull(res.getTotalAmountWords());
        assertTrue(res.getTotalAmountWords().contains("đồng chẵn"));
        assertFalse(res.getItems().isEmpty());
    }

    @Test
    void testCalculatePLI2() {
        ManualCalculateRequestDto req = ManualCalculateRequestDto.builder()
                .sheetType("I.2")
                .hoTen("Trần Thị B")
                .capBac("Trung tá")
                .chucVu("Nhân viên")
                .ngaySinh("05/1975")
                .nhapNgu("03/1995")
                .thoiDiemNghi("03/2025")
                .thoiGianDonViSapNhapGiaiThe("01/2025")
                .luongThang(BigDecimal.valueOf(18000000))
                .build();

        ManualCalculateResponseDto res = service.calculatePLI2(req);
        assertNotNull(res);
        assertTrue(res.isSuccess());
        assertEquals("I.2", res.getSheetType());
        assertEquals(54, res.getTranTuoi());
        assertNotNull(res.getTotalAmount());
    }

    @Test
    void testCalculatePLI3() {
        ManualCalculateRequestDto req = ManualCalculateRequestDto.builder()
                .sheetType("I.3")
                .hoTen("Lê Văn C")
                .capBac("4//")
                .chucVu("Trưởng phòng")
                .ngaySinh("08/1969")
                .nhapNgu("02/1987")
                .thoiDiemNghi("06/2025")
                .luongThang(BigDecimal.valueOf(22000000))
                .build();

        ManualCalculateResponseDto res = service.calculatePLI3(req);
        assertNotNull(res);
        assertTrue(res.isSuccess());
        assertEquals("I.3", res.getSheetType());
        assertEquals(58, res.getTranTuoi());
        assertNotNull(res.getTotalAmount());
    }
}
