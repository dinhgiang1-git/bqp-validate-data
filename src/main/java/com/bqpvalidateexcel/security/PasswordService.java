package com.bqpvalidateexcel.security;

import org.springframework.stereotype.Service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Xác thực và đổi mật khẩu launcher.
 * Dùng PBKDF2-HMAC-SHA256 với cùng thông số như launcher C# (200 000 vòng, 32 byte).
 * File bqp_password.dat: "PBKDF2-HMAC-SHA256-v1:<salt_base64>:<hash_base64>"
 */
@Service
public class PasswordService {

    private static final String ALGORITHM     = "PBKDF2WithHmacSHA256";
    private static final String FILE_HEADER   = "PBKDF2-HMAC-SHA256-v1";
    private static final int    ITERATIONS    = 200_000;
    private static final int    HASH_BYTES    = 32;
    private static final int    SALT_BYTES    = 16;
    private static final String PASSWORD_FILE = "bqp_password.dat";
    private static final int    MIN_LENGTH    = 4;

    /** Salt và hash hiện tại nhận từ launcher qua biến môi trường. */
    private final String activeSalt;
    private final String activeHash;

    /** Thư mục launcher (nơi ghi bqp_password.dat). */
    private final String launcherDir;

    public PasswordService() {
        this.activeSalt  = System.getenv("BQP_PWD_SALT");
        this.activeHash  = System.getenv("BQP_PWD_HASH");
        this.launcherDir = System.getenv("BQP_LAUNCHER_DIR");
    }

    /** Kiểm tra xem backend có nhận được thông tin mật khẩu từ launcher không. */
    public boolean isPasswordManagementAvailable() {
        return activeSalt != null && !activeSalt.isBlank()
            && activeHash != null && !activeHash.isBlank()
            && !activeSalt.startsWith("UNCONFIGURED_")
            && !activeHash.startsWith("UNCONFIGURED_");
    }

    /**
     * Xác thực mật khẩu hiện tại.
     * So sánh constant-time để tránh timing attack.
     */
    public boolean verifyCurrentPassword(String password) {
        if (password == null || password.isEmpty()) return false;
        if (!isPasswordManagementAvailable()) return false;
        try {
            byte[] salt         = Base64.getDecoder().decode(activeSalt);
            byte[] expectedHash = Base64.getDecoder().decode(activeHash);
            byte[] actualHash   = pbkdf2(password, salt);
            return MessageDigest.isEqual(actualHash, expectedHash);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Đổi mật khẩu: xác thực mật khẩu cũ, tạo salt/hash mới, ghi file.
     */
    public void changePassword(String currentPassword, String newPassword, String confirmPassword) {
        if (!isPasswordManagementAvailable()) {
            throw new IllegalStateException("Tính năng đổi mật khẩu chưa khả dụng. Vui lòng mở ứng dụng qua launcher.");
        }
        if (newPassword == null || newPassword.length() < MIN_LENGTH) {
            throw new IllegalArgumentException("Mật khẩu mới phải có ít nhất " + MIN_LENGTH + " ký tự.");
        }
        if (!newPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException("Mật khẩu xác nhận không khớp.");
        }
        if (!verifyCurrentPassword(currentPassword)) {
            throw new SecurityException("Mật khẩu hiện tại không đúng.");
        }

        try {
            byte[] newSalt = new byte[SALT_BYTES];
            new SecureRandom().nextBytes(newSalt);
            byte[] newHash    = pbkdf2(newPassword, newSalt);
            String saltBase64 = Base64.getEncoder().encodeToString(newSalt);
            String hashBase64 = Base64.getEncoder().encodeToString(newHash);
            String fileContent = FILE_HEADER + ":" + saltBase64 + ":" + hashBase64 + "\n";

            Path filePath = resolvePasswordFilePath();
            Files.writeString(filePath, fileContent,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.SYNC);
        } catch (SecurityException se) {
            throw se;
        } catch (Exception e) {
            throw new RuntimeException("Không thể lưu mật khẩu mới: " + e.getMessage(), e);
        }
    }

    private Path resolvePasswordFilePath() {
        if (launcherDir != null && !launcherDir.isBlank()) {
            Path dir = Paths.get(launcherDir.trim()).toAbsolutePath().normalize();
            if (Files.isDirectory(dir)) {
                return dir.resolve(PASSWORD_FILE);
            }
        }
        // Fallback: thư mục làm việc hiện tại (= thư mục jar)
        return Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize().resolve(PASSWORD_FILE);
    }

    private byte[] pbkdf2(String password, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(
            password.toCharArray(), salt, ITERATIONS, HASH_BYTES * 8);
        try {
            SecretKeyFactory skf = SecretKeyFactory.getInstance(ALGORITHM);
            return skf.generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }
}
