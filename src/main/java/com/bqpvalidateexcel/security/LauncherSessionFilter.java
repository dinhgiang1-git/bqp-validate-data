package com.bqpvalidateexcel.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Kiểm tra cookie phiên launcher trên mọi request trừ các endpoint được miễn.
 *
 * Endpoints được miễn (không cần phiên):
 *   - GET /launcher/health    – launcher dùng để chờ backend sẵn sàng
 *   - GET /launcher/bootstrap – launcher dùng một lần để kích hoạt phiên
 *
 * Khi backend KHÔNG chạy qua launcher (không có LAUNCHER_SESSION_TOKEN),
 * filter bỏ qua toàn bộ để không ảnh hưởng môi trường phát triển.
 *
 * Header nội bộ X-BQP-Launcher-Internal cho phép launcher gọi API shutdown
 * mà không cần cookie (launcher giữ header này nội bộ, không lộ ra web).
 */
@Component
@Order(1)
public class LauncherSessionFilter extends OncePerRequestFilter {

    private final LauncherSessionService sessionService;

    public LauncherSessionFilter(LauncherSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest  request,
            HttpServletResponse response,
            FilterChain         filterChain) throws ServletException, IOException {

        // Chế độ phát triển – bỏ qua filter
        if (!sessionService.isLauncherMode()) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();

        // Cho phép các endpoint bootstrap và health đi qua không cần phiên
        if (path.startsWith("/launcher/")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Ngoại lệ shutdown: CHỈ cho phép POST /api/system/shutdown nếu kèm bí mật ngẫu nhiên hợp lệ
        if ("POST".equalsIgnoreCase(request.getMethod()) && "/api/system/shutdown".equals(path)) {
            String internalHeader = request.getHeader(LauncherSessionService.INTERNAL_HEADER);
            if (sessionService.isValidShutdownSecret(internalHeader)) {
                filterChain.doFilter(request, response);
                return;
            }
        }

        // Kiểm tra cookie phiên
        String sessionId = extractSessionCookie(request);
        if (sessionService.isValidSession(sessionId)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Không có phiên hợp lệ
        if (isApiRequest(path)) {
            // Trả JSON 401 cho các API
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"Phiên không hợp lệ. Vui lòng mở ứng dụng qua launcher.\",\"status\":401}");
        } else {
            // Giao diện web – trả trang thông báo đơn giản
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().write(buildUnauthorizedPage());
        }
    }

    private String extractSessionCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (LauncherSessionService.SESSION_COOKIE.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }

    private boolean isApiRequest(String path) {
        return path.startsWith("/api/");
    }

    private String buildUnauthorizedPage() {
        return "<!DOCTYPE html><html lang='vi'><head><meta charset='UTF-8'>" +
               "<title>Bộ Quốc Phòng – Chưa xác thực</title>" +
               "<style>body{margin:0;background:#0f172a;display:flex;align-items:center;justify-content:center;height:100vh;font-family:'Segoe UI',sans-serif;}" +
               ".card{background:#1e293b;border:1px solid #334155;border-radius:12px;padding:40px 48px;max-width:480px;text-align:center;}" +
               "h2{color:#f8fafc;font-size:18px;margin-bottom:12px;}" +
               "p{color:#94a3b8;font-size:14px;line-height:1.6;}" +
               "</style></head><body>" +
               "<div class='card'>" +
               "<h2>🔒 Bộ Quốc Phòng – Hệ thống thẩm định</h2>" +
               "<p>Phần mềm chỉ có thể truy cập khi được mở qua <strong style='color:#60a5fa'>Chay_CongCu_BQP.exe</strong>.<br>" +
               "Vui lòng đóng tab này và mở lại ứng dụng qua launcher.</p>" +
               "</div></body></html>";
    }
}
