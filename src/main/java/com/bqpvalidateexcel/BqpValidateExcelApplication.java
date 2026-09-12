package com.bqpvalidateexcel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

@SpringBootApplication
public class BqpValidateExcelApplication {

    public static void main(String[] args) {
        SpringApplication.run(BqpValidateExcelApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        // Chỉ tự động mở trình duyệt khi chạy thực tế (không phải lúc chạy Maven unit test)
        if (System.getProperty("org.springframework.boot.test.context.SpringBootTestContextBootstrapper") != null) {
            return;
        }
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win")) {
                new ProcessBuilder("cmd", "/c", "start", "http://localhost:8080/").start();
            }
        } catch (Throwable ignored) {
        }
    }
}

