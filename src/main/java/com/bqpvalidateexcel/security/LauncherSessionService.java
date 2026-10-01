package com.bqpvalidateexcel.security;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quản lý:
 * 1. Token bootstrap một lần (launcher → backend khi khởi động).
 * 2. Bí mật shutdown nội bộ (launcher → backend khi tắt êm).
 * 3. Cookie phiên đang chạy trong bộ nhớ (không lưu ra đĩa).
 *
 * Luồng:
 * Launcher truyền LAUNCHER_SESSION_TOKEN và LAUNCHER_SHUTDOWN_SECRET qua biến môi trường.
 * Backend đọc tại khởi động và lưu trạng thái launcherModeEnabled bất biến.
 * Launcher mở URL /launcher/bootstrap?token=... một lần.
 * Backend xác nhận, hủy bootstrapToken, tạo cookie phiên và chuyển hướng về /.
 * LauncherSessionFilter kiểm tra cookie trên mọi request (trừ /launcher/**).
 * Shutdown chỉ chấp nhận cookie phiên hợp lệ HOẶC bí mật LAUNCHER_SHUTDOWN_SECRET.
 */
@Service
public class LauncherSessionService {

    /** Tên biến môi trường launcher truyền vào tiến trình Java cho bootstrap token. */
    public static final String ENV_TOKEN = "LAUNCHER_SESSION_TOKEN";

    /** Tên biến môi trường launcher truyền vào cho bí mật shutdown nội bộ. */
    public static final String ENV_SHUTDOWN_SECRET = "LAUNCHER_SHUTDOWN_SECRET";

    /** Tên biến môi trường launcher truyền vào cho định danh instance của lần chạy. */
    public static final String ENV_INSTANCE_ID = "LAUNCHER_INSTANCE_ID";

    /** Tên cookie phiên đặt trong trình duyệt. */
    public static final String SESSION_COOKIE = "bqp_session";

    /** Header nội bộ launcher dùng khi gọi API shutdown. */
    public static final String INTERNAL_HEADER = "X-BQP-Launcher-Internal";

    /** Thời gian hiệu lực cookie phiên (giây) = 12 giờ */
    private static final int SESSION_COOKIE_MAX_AGE_SECONDS = 12 * 60 * 60;

    // -----------------------------------------------------------------------
    // Trạng thái bất biến & trong bộ nhớ
    // -----------------------------------------------------------------------

    /** Cờ bất biến xác định ứng dụng chạy ở chế độ launcher (bắt buộc xác thực). */
    private final boolean launcherModeEnabled;

    /** Bí mật shutdown nội bộ dùng cho graceful shutdown từ launcher process. */
    private final String shutdownSecret;

    /** Định danh duy nhất của instance launcher lần chạy này. */
    private final String launcherInstanceId;

    /** Token bootstrap một lần nhận từ launcher qua môi trường, null sau khi đã dùng. */
    private volatile String bootstrapToken;

    /** Các cookie phiên hợp lệ: sessionId → thời điểm hết hạn. */
    private final Map<String, Instant> activeSessions = new ConcurrentHashMap<>();

    private final SecureRandom secureRandom = new SecureRandom();

    // -----------------------------------------------------------------------
    // Khởi tạo
    // -----------------------------------------------------------------------

    public LauncherSessionService() {
        this(System.getenv(ENV_TOKEN), System.getenv(ENV_SHUTDOWN_SECRET), System.getenv(ENV_INSTANCE_ID));
    }

    public LauncherSessionService(String envToken, String envShutdownSecret) {
        this(envToken, envShutdownSecret, System.getenv(ENV_INSTANCE_ID));
    }

    /** Constructor hỗ trợ cấu hình rõ ràng và phục vụ kiểm thử đơn vị / tích hợp. */
    public LauncherSessionService(String envToken, String envShutdownSecret, String envInstanceId) {
        this.launcherInstanceId = (envInstanceId != null) ? envInstanceId.trim() : "";

        if (envToken != null && !envToken.isBlank()) {
            this.launcherModeEnabled = true;
            this.bootstrapToken = envToken.trim();
            System.out.println("[LauncherSession] Bootstrap token đã được nạp từ môi trường. Chế độ launcher: BẬT.");
        } else {
            // Chạy độc lập không qua launcher – chế độ phát triển cục bộ.
            this.launcherModeEnabled = false;
            this.bootstrapToken = null;
            System.out.println("[LauncherSession] Không tìm thấy " + ENV_TOKEN + " – đang chạy ở chế độ phát triển (bỏ qua filter phiên).");
        }

        if (envShutdownSecret != null && !envShutdownSecret.isBlank()) {
            this.shutdownSecret = envShutdownSecret.trim();
        } else {
            this.shutdownSecret = null;
        }
    }

    public String getLauncherInstanceId() {
        return launcherInstanceId;
    }

    // -----------------------------------------------------------------------
    // Bootstrap & Chế độ hoạt động
    // -----------------------------------------------------------------------

    /**
     * Trả về true nếu backend được khởi động ở chế độ launcher (cờ bất biến).
     * Tuyệt đối không suy từ activeSessions để tránh lỗ hổng fail-open khi hết phiên.
     */
    public boolean isLauncherMode() {
        return launcherModeEnabled;
    }

    /**
     * Thao tác nguyên tử: Xác thực bootstrap token và tạo session ngay lập tức.
     * Sử dụng so sánh hằng thời gian (constant-time) để chống timing attack.
     * Trả về sessionId nếu thành công, null nếu token không hợp lệ hoặc đã dùng.
     */
    public synchronized String consumeBootstrapTokenAndCreateSession(String token) {
        if (bootstrapToken == null || token == null) {
            return null;
        }
        byte[] expected = bootstrapToken.getBytes(StandardCharsets.UTF_8);
        byte[] provided = token.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, provided)) {
            return null;
        }
        bootstrapToken = null; // Dùng một lần, hủy ngay
        return createSession();
    }

    /** Trả về true nếu token bootstrap khớp và chưa dùng. Token bị hủy ngay sau khi xác nhận. */
    public synchronized boolean consumeBootstrapToken(String token) {
        if (bootstrapToken == null || token == null) return false;
        byte[] expected = bootstrapToken.getBytes(StandardCharsets.UTF_8);
        byte[] provided = token.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, provided)) {
            return false;
        }
        bootstrapToken = null;  // Dùng một lần, hủy ngay
        return true;
    }

    /** Kiểm tra bí mật shutdown nội bộ bằng so sánh hằng thời gian. */
    public boolean isValidShutdownSecret(String candidate) {
        if (shutdownSecret == null || candidate == null || candidate.isBlank()) {
            return false;
        }
        byte[] expected = shutdownSecret.getBytes(StandardCharsets.UTF_8);
        byte[] provided = candidate.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, provided);
    }

    /** Tạo cookie phiên mới và trả về giá trị cookie. */
    public String createSession() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String sessionId = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiry = Instant.now().plusSeconds(SESSION_COOKIE_MAX_AGE_SECONDS);
        activeSessions.put(sessionId, expiry);
        return sessionId;
    }

    /** Trả về true nếu cookie phiên hợp lệ và chưa hết hạn. */
    public boolean isValidSession(String sessionId) {
        if (sessionId == null) return false;
        Instant expiry = activeSessions.get(sessionId);
        if (expiry == null) return false;
        if (Instant.now().isAfter(expiry)) {
            activeSessions.remove(sessionId);
            return false;
        }
        return true;
    }

    /** Hủy phiên khi người dùng thoát. */
    public void invalidateSession(String sessionId) {
        if (sessionId != null) activeSessions.remove(sessionId);
    }

    public int getSessionCookieMaxAge() {
        return SESSION_COOKIE_MAX_AGE_SECONDS;
    }
}
