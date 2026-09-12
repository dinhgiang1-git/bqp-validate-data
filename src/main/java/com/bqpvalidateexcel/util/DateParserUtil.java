package com.bqpvalidateexcel.util;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DateParserUtil {

    private static final Pattern DMY_PATTERN = Pattern.compile("(\\d{1,2})[\\/\\-\\.\\s]+(\\d{1,2})[\\/\\-\\.\\s]+(\\d{2,4})");
    private static final Pattern MY_PATTERN = Pattern.compile("(\\d{1,2})[\\/\\-\\.\\s]+(\\d{2,4})");
    private static final Pattern Y_PATTERN = Pattern.compile("(\\d{4})");

    /**
     * Chuyển đổi chuỗi ngày tháng sang YearMonth.
     * Hỗ trợ các định dạng: dd/MM/yyyy, d/M/yyyy, MM/yyyy, M/yyyy, M/yy, yyyy...
     */
    public static YearMonth parseYearMonth(String str) {
        if (str == null || str.trim().isEmpty() || str.contains("#") || str.equals("-")) {
            return null;
        }

        String cleaned = str.trim().replace("\r", "").replace("\n", "").trim();

        // 1. Thử khớp định dạng ngày/tháng/năm: dd/MM/yyyy
        Matcher dmyMatcher = DMY_PATTERN.matcher(cleaned);
        if (dmyMatcher.find()) {
            int month = Integer.parseInt(dmyMatcher.group(2));
            int year = Integer.parseInt(dmyMatcher.group(3));
            year = normalizeYear(year);
            if (month >= 1 && month <= 12) {
                return YearMonth.of(year, month);
            }
        }

        // 2. Thử khớp định dạng tháng/năm: MM/yyyy hoặc M/yy
        Matcher myMatcher = MY_PATTERN.matcher(cleaned);
        if (myMatcher.find()) {
            int month = Integer.parseInt(myMatcher.group(1));
            int year = Integer.parseInt(myMatcher.group(2));
            year = normalizeYear(year);
            if (month >= 1 && month <= 12) {
                return YearMonth.of(year, month);
            }
        }

        // 3. Khớp năm đơn thuần: yyyy
        Matcher yMatcher = Y_PATTERN.matcher(cleaned);
        if (yMatcher.find()) {
            int year = Integer.parseInt(yMatcher.group(1));
            return YearMonth.of(year, 1);
        }

        return null;
    }

    /**
     * Chuyển đổi năm 2 chữ số sang 4 chữ số:
     * vd: 87 -> 1987, 89 -> 1989, 25 -> 2025
     */
    public static int normalizeYear(int year) {
        if (year < 100) {
            if (year >= 40) {
                return 1900 + year;
            } else {
                return 2000 + year;
            }
        }
        return year;
    }

    /**
     * Tính số tháng giữa 2 mốc thời gian (start đến end)
     */
    public static int monthsBetween(YearMonth start, YearMonth end) {
        if (start == null || end == null) return 0;
        return (end.getYear() - start.getYear()) * 12 + (end.getMonthValue() - start.getMonthValue());
    }

    /**
     * Định dạng YearMonth sang chuỗi MM/yyyy
     */
    public static String formatYearMonth(YearMonth ym) {
        if (ym == null) return "";
        return String.format("%02d/%d", ym.getMonthValue(), ym.getYear());
    }
}
