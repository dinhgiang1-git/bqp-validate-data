package com.bqpvalidateexcel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Điểm khởi động ứng dụng Spring Boot BQP.
 *
 * Lưu ý: Java KHÔNG tự mở trình duyệt nữa.
 * Launcher (Chay_CongCu_BQP.exe) kiểm soát việc mở URL bootstrap sau khi backend sẵn sàng.
 * Điều này đảm bảo chỉ một tab trình duyệt được mở và luôn có token phiên hợp lệ.
 */
@SpringBootApplication
@EnableScheduling
public class BqpValidateExcelApplication {

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "false");
        SpringApplication.run(BqpValidateExcelApplication.class, args);
    }
}
