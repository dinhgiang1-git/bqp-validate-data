package com.bqpvalidateexcel.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.Cookie;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LauncherSessionSecurityTest {

    @Test
    @DisplayName("P0-02: Chế độ launcher là bất biến; hết phiên không bị fail-open thành chế độ phát triển")
    void testFailClosedWhenSessionExpiresOrInvalidated() {
        String token = "test-token-12345678901234567890123456789012";
        String shutdownSecret = "test-shutdown-secret-12345678901234567890";
        LauncherSessionService service = new LauncherSessionService(token, shutdownSecret);

        // 1. Kiểm tra ban đầu
        assertTrue(service.isLauncherMode(), "Phải ở chế độ launcher khi có bootstrap token");

        // 2. Consume token để lấy session
        String sessionId = service.consumeBootstrapTokenAndCreateSession(token);
        assertNotNull(sessionId, "Phải tạo được session");
        assertTrue(service.isValidSession(sessionId), "Session vừa tạo phải hợp lệ");

        // 3. Token dùng một lần - replay phải bị từ chối
        assertNull(service.consumeBootstrapTokenAndCreateSession(token), "Token không thể dùng lại lần 2");

        // 4. Hủy phiên cuối cùng (mô phỏng người dùng logout hoặc phiên hết hạn)
        service.invalidateSession(sessionId);
        assertFalse(service.isValidSession(sessionId), "Session đã bị hủy");

        // CRITICAL CHECK (P0-02): isLauncherMode() PHẢI VẪN LÀ TRUE
        assertTrue(service.isLauncherMode(), "P0-02: isLauncherMode() phải giữ nguyên true khi hết phiên, không được fail-open");

        // Thử với filter: Request tiếp theo không có cookie PHẢI BỊ 401, không được lọt qua
        LauncherSessionFilter filter = new LauncherSessionFilter(service);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/storage/status");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        assertDoesNotThrow(() -> filter.doFilter(request, response, filterChain));
        assertEquals(401, response.getStatus(), "P0-02: Request sau khi hết phiên không có cookie phải nhận 401");
    }

    @Test
    @DisplayName("P0-01: Header X-BQP-Launcher-Internal: true bị từ chối trên toàn bộ API")
    void testLegacyStaticHeaderRejectedEverywhere() throws Exception {
        String token = "test-token-12345678901234567890123456789012";
        String shutdownSecret = "test-shutdown-secret-12345678901234567890";
        LauncherSessionService service = new LauncherSessionService(token, shutdownSecret);
        LauncherSessionFilter filter = new LauncherSessionFilter(service);

        // 1. Gửi header 'true' tới API thường
        MockHttpServletRequest request1 = new MockHttpServletRequest("GET", "/api/storage/status");
        request1.addHeader("X-BQP-Launcher-Internal", "true");
        MockHttpServletResponse response1 = new MockHttpServletResponse();
        filter.doFilter(request1, response1, new MockFilterChain());
        assertEquals(401, response1.getStatus(), "P0-01: Header 'true' không được phép bypass API thường");

        // 2. Gửi header 'true' tới POST /api/system/shutdown
        MockHttpServletRequest request2 = new MockHttpServletRequest("POST", "/api/system/shutdown");
        request2.addHeader("X-BQP-Launcher-Internal", "true");
        MockHttpServletResponse response2 = new MockHttpServletResponse();
        filter.doFilter(request2, response2, new MockFilterChain());
        assertEquals(401, response2.getStatus(), "P0-01: Header 'true' không được phép bypass API shutdown");
    }

    @Test
    @DisplayName("P0-01: Bí mật shutdown chỉ có hiệu lực tại đúng POST /api/system/shutdown")
    void testShutdownSecretOnlyAllowedOnShutdownEndpoint() throws Exception {
        String token = "test-token-12345678901234567890123456789012";
        String shutdownSecret = "correct-shutdown-secret-999";
        LauncherSessionService service = new LauncherSessionService(token, shutdownSecret);
        LauncherSessionFilter filter = new LauncherSessionFilter(service);

        // 1. Gửi bí mật shutdown tới API dữ liệu GET /api/records/all -> PHẢI BỊ 401
        MockHttpServletRequest apiReq = new MockHttpServletRequest("GET", "/api/records/all");
        apiReq.addHeader("X-BQP-Launcher-Internal", shutdownSecret);
        MockHttpServletResponse apiResp = new MockHttpServletResponse();
        filter.doFilter(apiReq, apiResp, new MockFilterChain());
        assertEquals(401, apiResp.getStatus(), "Bí mật shutdown không được phép bypass API dữ liệu");

        // 2. Gửi bí mật shutdown sai tới POST /api/system/shutdown -> PHẢI BỊ 401
        MockHttpServletRequest wrongReq = new MockHttpServletRequest("POST", "/api/system/shutdown");
        wrongReq.addHeader("X-BQP-Launcher-Internal", "wrong-secret");
        MockHttpServletResponse wrongResp = new MockHttpServletResponse();
        filter.doFilter(wrongReq, wrongResp, new MockFilterChain());
        assertEquals(401, wrongResp.getStatus(), "Bí mật sai phải bị từ chối");

        // 3. Gửi bí mật shutdown đúng tới GET /api/system/shutdown (sai method) -> PHẢI BỊ 401
        MockHttpServletRequest getReq = new MockHttpServletRequest("GET", "/api/system/shutdown");
        getReq.addHeader("X-BQP-Launcher-Internal", shutdownSecret);
        MockHttpServletResponse getResp = new MockHttpServletResponse();
        filter.doFilter(getReq, getResp, new MockFilterChain());
        assertEquals(401, getResp.getStatus(), "Method GET với bí mật shutdown phải bị từ chối");

        // 4. Gửi bí mật shutdown đúng tới POST /api/system/shutdown -> CHO PHÉP QUA FILTER
        MockHttpServletRequest validReq = new MockHttpServletRequest("POST", "/api/system/shutdown");
        validReq.addHeader("X-BQP-Launcher-Internal", shutdownSecret);
        MockHttpServletResponse validResp = new MockHttpServletResponse();
        MockFilterChain successChain = new MockFilterChain();
        filter.doFilter(validReq, validResp, successChain);
        // Khi filter cho qua, response status mặc định là 200 (SC_OK) do chain xử lý tiếp
        assertEquals(200, validResp.getStatus(), "Bí mật hợp lệ phải được đi qua filter tới controller");
    }

    @Test
    @DisplayName("P0-01/P0-02: Phiên cookie hợp lệ truy cập thành công")
    void testValidSessionAccessSuccess() throws Exception {
        String token = "valid-token-321";
        LauncherSessionService service = new LauncherSessionService(token, "secret");
        LauncherSessionFilter filter = new LauncherSessionFilter(service);

        String sessionId = service.consumeBootstrapTokenAndCreateSession(token);
        assertNotNull(sessionId);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/storage/status");
        request.setCookies(new Cookie(LauncherSessionService.SESSION_COOKIE, sessionId));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);
        assertEquals(200, response.getStatus(), "Cookie hợp lệ phải được đi qua filter");
    }

    @Test
    @DisplayName("Bootstrap đồng thời: Chỉ duy nhất 1 luồng tạo được session")
    void testConcurrentBootstrapOnlyOnce() throws Exception {
        String token = "unique-bootstrap-token";
        LauncherSessionService service = new LauncherSessionService(token, "secret");

        int threads = 10;
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    latch.await();
                    String sess = service.consumeBootstrapTokenAndCreateSession(token);
                    if (sess != null) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    done.countDown();
                }
            }).start();
        }

        latch.countDown();
        done.await();

        assertEquals(1, successCount.get(), "Chỉ duy nhất một request đổi token thành session thành công");
    }

    @Test
    @DisplayName("Health check trả về status UP và đúng launcher instanceId")
    void testHealthCheckReturnsInstanceId() {
        String token = "test-token-12345678901234567890123456789012";
        String shutdownSecret = "test-shutdown-secret-12345678901234567890";
        String instanceId = "instance-abc-12345";
        LauncherSessionService service = new LauncherSessionService(token, shutdownSecret, instanceId);
        LauncherSessionController controller = new LauncherSessionController(service);

        var response = controller.health();
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("UP", response.getBody().get("status"));
        assertEquals("instance-abc-12345", response.getBody().get("instanceId"));
    }
}
