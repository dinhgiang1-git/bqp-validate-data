package com.bqpvalidateexcel.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NumberParserUtil {

    private static final DecimalFormat MONEY_FORMAT;
    private static final DecimalFormat DECIMAL_FORMAT;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.GERMANY);
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        MONEY_FORMAT = new DecimalFormat("#,##0", symbols);
        DECIMAL_FORMAT = new DecimalFormat("#,##0.##", symbols);
    }

    /**
     * Parse chuỗi số tiền từ PDF sang BigDecimal
     * Hỗ trợ các định dạng: "14.816.561.327", "1.711.476.000", "31,253,040", "3 6.234.900", "-", ""...
     */
    public static BigDecimal parseMoney(String text) {
        if (text == null) return BigDecimal.ZERO;
        String clean = text.trim()
                .replace(" ", "")
                .replace("\u00A0", "")
                .replace("\r", "")
                .replace("\n", "")
                .replace("đ", "")
                .replace("VNĐ", "")
                .replace("VND", "");

        if (clean.isEmpty() || clean.equals("-") || clean.contains("#")) {
            return BigDecimal.ZERO;
        }

        try {
            // Nếu có cả dấu chấm và dấu phẩy
            if (clean.contains(".") && clean.contains(",")) {
                int lastDot = clean.lastIndexOf('.');
                int lastComma = clean.lastIndexOf(',');
                if (lastDot > lastComma) {
                    // Dấu phẩy là phân cách hàng nghìn, chấm là thập phân
                    clean = clean.replace(",", "");
                } else {
                    // Dấu chấm là phân cách hàng nghìn, phẩy là thập phân
                    clean = clean.replace(".", "").replace(",", ".");
                }
            } else if (clean.contains(".")) {
                // Kiểm tra xem là phân cách hàng nghìn hay thập phân
                // Nếu có nhiều hơn 1 dấu chấm -> chắc chắn là phân cách hàng nghìn
                int firstDot = clean.indexOf('.');
                int lastDot = clean.lastIndexOf('.');
                if (firstDot != lastDot) {
                    clean = clean.replace(".", "");
                } else {
                    // 1 dấu chấm: nếu sau dấu chấm có 3 chữ số -> hàng nghìn (ví dụ 500.000)
                    int after = clean.length() - 1 - lastDot;
                    if (after == 3) {
                        clean = clean.replace(".", "");
                    } else if (clean.length() >= 7) {
                        clean = clean.replace(".", "");
                    }
                }
            } else if (clean.contains(",")) {
                int firstComma = clean.indexOf(',');
                int lastComma = clean.lastIndexOf(',');
                if (firstComma != lastComma) {
                    clean = clean.replace(",", "");
                } else {
                    int after = clean.length() - 1 - lastComma;
                    if (after == 3) {
                        clean = clean.replace(",", "");
                    } else if (clean.length() >= 7) {
                        clean = clean.replace(",", "");
                    } else {
                        clean = clean.replace(",", ".");
                    }
                }
            }

            return new BigDecimal(clean);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * Parse số thập phân (số năm, số tháng...)
     * Ví dụ: "4,5", "4.5", "60", "38n10t", "34 năm, 6 tháng", "33,11" (33 năm 11 tháng)
     */
    public static BigDecimal parseDecimal(String text) {
        if (text == null) return BigDecimal.ZERO;
        String clean = text.trim()
                .replace(" ", "")
                .replace("\u00A0", "")
                .replace("\r", "")
                .replace("\n", "");

        if (clean.isEmpty() || clean.equals("-") || clean.contains("#")) {
            return BigDecimal.ZERO;
        }

        // Xử lý dạng "38n10t" hoặc "38 năm 10 tháng"
        Pattern nyPattern = Pattern.compile("(\\d+)[nNnăm]+(\\d+)[tTtháng]*");
        Matcher nyMatcher = nyPattern.matcher(clean);
        if (nyMatcher.find()) {
            int years = Integer.parseInt(nyMatcher.group(1));
            int months = Integer.parseInt(nyMatcher.group(2));
            int totalMonths = years * 12 + months;
            return roundMonthsToYears(totalMonths);
        }

        // Dạng chuỗi năm, tháng có dấu phẩy thể hiện tháng lẻ như "33,11" (33 năm 11 tháng)
        if (clean.contains(",")) {
            String[] parts = clean.split(",");
            if (parts.length == 2) {
                try {
                    int years = Integer.parseInt(parts[0]);
                    int months = Integer.parseInt(parts[1]);
                    if (months > 6 && months <= 11) {
                        return roundMonthsToYears(years * 12 + months);
                    }
                } catch (Exception ignored) {
                }
            }
            clean = clean.replace(",", ".");
        }

        try {
            return new BigDecimal(clean);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * Quy đổi số tháng sang số năm theo nguyên tắc:
     * - Từ 1 đến 6 tháng -> tính 0.5 năm
     * - Từ 7 đến 12 tháng -> tính 1.0 năm
     * - 0 tháng -> tính 0.0 năm
     */
    public static BigDecimal roundMonthsToYears(int totalMonths) {
        if (totalMonths <= 0) return BigDecimal.ZERO;
        int years = totalMonths / 12;
        int rem = totalMonths % 12;
        if (rem == 0) {
            return BigDecimal.valueOf(years);
        } else if (rem <= 6) {
            return BigDecimal.valueOf(years).add(new BigDecimal("0.5"));
        } else {
            return BigDecimal.valueOf(years + 1);
        }
    }

    /**
     * Quy đổi số tháng sang số năm theo NĐ 177:
     * - Phần lẻ < 6 tháng (1 đến 5 tháng) -> cộng 0.5 năm
     * - Phần lẻ >= 6 tháng (6 đến 11 tháng) -> cộng 1.0 năm
     * - 0 tháng lẻ -> giữ nguyên số năm chẵn
     */
    public static BigDecimal roundMonthsToYearsNd177(int totalMonths) {
        if (totalMonths <= 0) return BigDecimal.ZERO;
        int years = totalMonths / 12;
        int rem = totalMonths % 12;
        if (rem == 0) {
            return BigDecimal.valueOf(years);
        } else if (rem < 6) {
            return BigDecimal.valueOf(years).add(new BigDecimal("0.5"));
        } else {
            return BigDecimal.valueOf(years + 1);
        }
    }

    public static synchronized String formatMoney(BigDecimal amount) {
        if (amount == null) return "0";
        return MONEY_FORMAT.format(amount);
    }

    public static synchronized String formatDecimal(BigDecimal val) {
        if (val == null) return "0";
        return DECIMAL_FORMAT.format(val);
    }
}
