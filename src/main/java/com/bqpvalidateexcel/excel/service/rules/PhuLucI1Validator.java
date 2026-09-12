package com.bqpvalidateexcel.excel.service.rules;

import com.bqpvalidateexcel.excel.model.dto.PhuLucI1;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class PhuLucI1Validator {

    public List<String> validate(PhuLucI1 data) {
        List<String> errors = new ArrayList<>();



        if (data.getKQDanhGiaCB() != null && !data.getKQDanhGiaCB().trim().isEmpty()) {
            // Kiểm tra định dạng T,T,T (ví dụ)
            String[] parts = data.getKQDanhGiaCB().split(",");
            List<String> validGrades = Arrays.asList("XS", "T", "HT");
            for (String part : parts) {
                if (!validGrades.contains(part.trim().toUpperCase())) {
                    errors.add("Kết quả đánh giá cán bộ không hợp lệ ('" + part.trim() + "'). Phải là XS, T, hoặc HT.");
                }
            }
            if (parts.length > 3) {
                errors.add("Kết quả đánh giá cán bộ chỉ được nhập tối đa 3 năm (ví dụ: T,T,T).");
            }
        }

        // Validate basic calculations or logic here if needed
        if (data.getSoThangNghiHuuTruocTuoiTheoThongTu() != null && data.getSoThangNghiHuuTruocTuoiTheoThongTu() < 0) {
            errors.add("Số tháng nghỉ hưu trước tuổi không được âm.");
        }

        if (data.getSoNamNghiHuuTruocTuoiTheoThongTu() != null && data.getSoNamNghiHuuTruocTuoiTheoThongTu() < 0) {
            errors.add("Số năm nghỉ hưu trước tuổi không được âm.");
        }

        return errors;
    }
}
