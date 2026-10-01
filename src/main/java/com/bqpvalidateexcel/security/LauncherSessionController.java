package com.bqpvalidateexcel.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Cung cấp hai endpoint không yêu cầu phiên:
 *
 * GET /launcher/health   – launcher dùng để chờ backend sẵn sàng.
 * GET /launcher/bootstrap?token=… – launcher mở một lần để đổi token lấy cookie phiên.
 */
@RestController
@RequestMapping("/launcher")
public class LauncherSessionController {

    private final LauncherSessionService sessionService;

    public LauncherSessionController(LauncherSessionService sessionService) {
        this.sessionService = sessionService;
    }

    /**
     * Health check – launcher gọi để chờ backend sẵn sàng.
     * Không yêu cầu phiên, không trả thông tin nhạy cảm.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> body = new java.util.LinkedHashMap<>();
        body.put("status", "UP");
        body.put("instanceId", sessionService.getLauncherInstanceId());
        return ResponseEntity.ok(body);
    }

    /**
     * Bootstrap endpoint – launcher mở chính xác một lần.
     * Nếu token đúng: đặt cookie phiên HttpOnly và chuyển hướng về trang chính.
     * Nếu token sai hoặc đã dùng: trả 403.
     */
    @GetMapping("/bootstrap")
    public void bootstrap(
            @RequestParam(name = "token", required = false) String token,
            HttpServletResponse response) throws Exception {

        String sessionId = sessionService.consumeBootstrapTokenAndCreateSession(token);
        if (sessionId == null) {
            System.out.println("[LauncherSession] Bootstrap bị từ chối – token không hợp lệ hoặc đã dùng.");
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Token không hợp lệ hoặc đã sử dụng.");
            return;
        }

        Cookie cookie = new Cookie(LauncherSessionService.SESSION_COOKIE, sessionId);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(sessionService.getSessionCookieMaxAge());
        // SameSite=Strict – chỉ gửi cookie trong cùng origin (localhost)
        // Lưu ý: Java Servlet API < 6.0 chưa hỗ trợ trực tiếp; đặt qua header thủ công.
        response.addCookie(cookie);
        response.setHeader("Set-Cookie",
                String.format("%s=%s; Path=/; HttpOnly; SameSite=Strict; Max-Age=%d",
                        LauncherSessionService.SESSION_COOKIE,
                        sessionId,
                        sessionService.getSessionCookieMaxAge()));

        System.out.println("[LauncherSession] Bootstrap thành công – phiên đã được tạo.");

        // Chuyển hướng về URL sạch (không còn token trên thanh địa chỉ)
        response.sendRedirect("/");
    }
}
