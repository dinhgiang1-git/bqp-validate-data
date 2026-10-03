package com.bqpvalidateexcel.security;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/security")
public class PasswordController {

    private final PasswordService passwordService;

    public PasswordController(PasswordService passwordService) {
        this.passwordService = passwordService;
    }

    /** Kiểm tra tính năng đổi mật khẩu có khả dụng không. */
    @GetMapping("/password-status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
            "available", passwordService.isPasswordManagementAvailable()
        ));
    }

    /** Đổi mật khẩu launcher. */
    @PostMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePassword(
            @RequestBody ChangePasswordRequest req) {
        try {
            passwordService.changePassword(
                req.getCurrentPassword(),
                req.getNewPassword(),
                req.getConfirmPassword()
            );
            return ResponseEntity.ok(Map.of(
                "status", "ok",
                "message", "Đổi mật khẩu thành công. Mật khẩu mới sẽ có hiệu lực từ lần mở ứng dụng tiếp theo."
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(401).body(Map.of(
                "status", "error",
                "message", e.getMessage()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", e.getMessage()
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(503).body(Map.of(
                "status", "error",
                "message", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Lỗi hệ thống: " + e.getMessage()
            ));
        }
    }
}
