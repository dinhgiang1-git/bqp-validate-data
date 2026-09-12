package com.bqpvalidateexcel.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.awt.Desktop;
import java.net.URI;

@Component
public class BrowserLauncher {

    private static final Logger log = LoggerFactory.getLogger(BrowserLauncher.class);

    @EventListener(ApplicationReadyEvent.class)
    public void launchBrowser() {
        String url = "http://localhost:8080";
        log.info("Ứng dụng đã khởi động thành công! Đang tự động mở trình duyệt đến {}", url);

        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
                return;
            }
        } catch (Throwable t) {
            log.warn("Desktop.browse không khả dụng, thử lệnh dòng lệnh Windows: {}", t.getMessage());
        }

        // Fallback mở trình duyệt qua lệnh Windows CMD
        try {
            Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", url});
        } catch (Throwable t) {
            log.error("Không thể tự động mở trình duyệt: {}. Vui lòng truy cập thủ công vào: {}", t.getMessage(), url);
        }
    }
}
