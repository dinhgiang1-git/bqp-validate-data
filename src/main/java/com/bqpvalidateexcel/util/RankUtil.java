package com.bqpvalidateexcel.util;

public class RankUtil {

    /**
     * Xác định trần tuổi nghỉ hưu theo cấp bậc quân hàm:
     * - Đại tá (4//): 58 tuổi
     * - Thượng tá (3//): 56 tuổi
     * - Trung tá (2//): 54 tuổi
     * - Thiếu tá (1//): 52 tuổi
     * - Cấp úy (Đại úy, Thượng úy, Trung úy, Thiếu úy, 4/, 3/, 2/, 1/): 50 tuổi
     * - QNCN, CNQP, HSQ: Mặc định theo cấp hàm hoặc 50 tuổi nếu không rõ
     */
    public static int getRankAgeCeiling(String rankStr) {
        if (rankStr == null || rankStr.trim().isEmpty()) {
            return 50; // Mặc định cấp úy/khác
        }

        String normalized = rankStr.trim().toLowerCase()
                .replace(" ", "")
                .replace(".", "")
                .replace("-", "");

        // 1. Kiểm tra ký hiệu viết tắt quân hàm (4//, 3//, 2//, 1//, 4/, 3/, 2/, 1/)
        if (normalized.contains("4//")) {
            return 58;
        }
        if (normalized.contains("3//")) {
            return 56;
        }
        if (normalized.contains("2//")) {
            return 54;
        }
        if (normalized.contains("1//")) {
            return 52;
        }
        if (normalized.contains("4/") || normalized.contains("3/") || normalized.contains("2/") || normalized.contains("1/")) {
            return 50;
        }

        // 2. Kiểm tra theo tên tiếng Việt đầy đủ
        if (normalized.contains("đạitá") || normalized.contains("daita")) {
            return 58;
        }
        if (normalized.contains("thượngtá") || normalized.contains("thuongta")) {
            return 56;
        }
        if (normalized.contains("trungtá") || normalized.contains("trungta")) {
            return 54;
        }
        if (normalized.contains("thiếutá") || normalized.contains("thieuta")) {
            return 52;
        }
        if (normalized.contains("đạiúy") || normalized.contains("daiuy")
                || normalized.contains("thượngúy") || normalized.contains("thuonguy")
                || normalized.contains("trungúy") || normalized.contains("trunguy")
                || normalized.contains("thiếuúy") || normalized.contains("thieuuy")
                || normalized.contains("cấpúy") || normalized.contains("capuy")) {
            return 50;
        }

        // Nếu là số đơn thuần (ví dụ '1', '2' được parse từ PDF)
        if (normalized.equals("4")) return 58;
        if (normalized.equals("3")) return 56;
        if (normalized.equals("2")) return 54;
        if (normalized.equals("1")) return 52;

        return 50;
    }
}
