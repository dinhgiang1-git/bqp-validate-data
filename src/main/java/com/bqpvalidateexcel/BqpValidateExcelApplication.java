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
        killPortIfOccupied(8080);
        SpringApplication.run(BqpValidateExcelApplication.class, args);
    }

    /**
     * Tự động kiểm tra và tắt các tiến trình chiếm cổng trước khi khởi động ứng dụng
     */
    private static void killPortIfOccupied(int port) {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("win")) return;

        try {
            long currentPid = ProcessHandle.current().pid();
            Process netstat = Runtime.getRuntime().exec(new String[]{"cmd", "/c", "netstat -ano -p tcp | findstr :" + port});
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(netstat.getInputStream()))) {
                String line;
                java.util.Set<String> killedPids = new java.util.HashSet<>();
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.contains("LISTENING")) {
                        String[] parts = line.split("\\s+");
                        if (parts.length > 0) {
                            String pid = parts[parts.length - 1];
                            if (pid.matches("\\d+") && !pid.equals("0") && Long.parseLong(pid) != currentPid && !killedPids.contains(pid)) {
                                System.out.println("[TỰ ĐỘNG GIẢI PHÓNG CỔNG " + port + "] Phát hiện tiến trình PID " + pid + " đang chiếm cổng. Đang tắt tiến trình...");
                                Runtime.getRuntime().exec(new String[]{"cmd", "/c", "taskkill /F /PID " + pid}).waitFor();
                                killedPids.add(pid);
                            }
                        }
                    }
                }
                if (!killedPids.isEmpty()) {
                    System.out.println("[TỰ ĐỘNG GIẢI PHÓNG CỔNG " + port + "] Đã giải phóng xong " + killedPids.size() + " tiến trình chiếm cổng. Cổng " + port + " đã sẵn sàng!");
                    Thread.sleep(800);
                }
            }
        } catch (Exception e) {
            System.err.println("Cảnh báo khi kiểm tra/giải phóng cổng " + port + ": " + e.getMessage());
        }
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
                System.out.println("  BO QUOC PHONG - HE THONG THAM DINH DU LIEU CHINH SACH");
                System.out.println("  Trang chu: " + url);
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
