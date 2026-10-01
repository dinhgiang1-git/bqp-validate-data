package com.bqpvalidateexcel.storage.controller;

import com.bqpvalidateexcel.security.LauncherSessionService;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final ApplicationContext    applicationContext;
    private final LauncherSessionService sessionService;

    public SystemController(ApplicationContext applicationContext, LauncherSessionService sessionService) {
        this.applicationContext = applicationContext;
        this.sessionService     = sessionService;
    }

    /**
     * API tắt ứng dụng an toàn.
     * Chấp nhận:
     * - Request có cookie phiên hợp lệ (người dùng trên web), hoặc
     * - Request có header X-BQP-Launcher-Internal: true (launcher gọi nội bộ).
     * Từ chối mọi request khác với 403.
     */
    @PostMapping("/shutdown")
    public ResponseEntity<Map<String, String>> gracefulShutdown(
            @RequestHeader(value = LauncherSessionService.INTERNAL_HEADER, required = false) String internalHeader,
            HttpServletRequest request) {

        boolean isInternal = sessionService.isValidShutdownSecret(internalHeader);
        boolean hasSession  = hasValidSession(request);

        if (!isInternal && !hasSession) {
            System.out.println("[BQP System] Yêu cầu shutdown bị từ chối – không có phiên hợp lệ hoặc bí mật không đúng.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("status", "REJECTED", "message", "Không được phép."));
        }

        System.out.println("[BQP System] Nhận yêu cầu tắt êm (Graceful Shutdown)...");
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(500);
                System.out.println("[BQP System] Đang đóng ứng dụng và giải phóng cơ sở dữ liệu SQLite...");
                int exitCode = SpringApplication.exit(applicationContext, () -> 0);
                System.exit(exitCode);
            } catch (Exception e) {
                System.exit(0);
            }
        });

        return ResponseEntity.ok(Map.of("status", "SHUTTING_DOWN", "message", "Đang tắt hệ thống an toàn..."));
    }

    private boolean hasValidSession(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return false;
        for (Cookie c : cookies) {
            if (LauncherSessionService.SESSION_COOKIE.equals(c.getName())) {
                return sessionService.isValidSession(c.getValue());
            }
        }
        return false;
    }
}
