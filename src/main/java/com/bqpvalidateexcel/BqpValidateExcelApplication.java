package com.bqpvalidateexcel;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.net.URI;

@SpringBootApplication
public class BqpValidateExcelApplication {

    @Value("${server.port:8080}")
    private String serverPort;

    @Value("${app.auto-open-browser:true}")
    private boolean autoOpenBrowser;

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "false");
        SpringApplication.run(BqpValidateExcelApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!autoOpenBrowser) {
            return;
        }

        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(800);
                String url = "http://localhost:" + serverPort;
                System.out.println();
                System.out.println("==================================================================");
                System.out.println("  PHẦN MỀM THẨM ĐỊNH DỮ LIỆU EXCEL CHÍNH SÁCH BỘ QUỐC PHÒNG");
                System.out.println("  Ứng dụng đã khởi động thành công!");
                System.out.println("  Đang tự động mở trình duyệt: " + url);
                System.out.println("  (Nếu trình duyệt không tự mở, vui lòng truy cập đường dẫn trên)");
                System.out.println("==================================================================");
                System.out.println();

                String os = System.getProperty("os.name", "").toLowerCase();
                if (os.contains("win")) {
                    Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", url});
                } else if (!GraphicsEnvironment.isHeadless() && Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(new URI(url));
                }
            } catch (Exception ignored) {}
        });
    }
}
