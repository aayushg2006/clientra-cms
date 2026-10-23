package io.clientra.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Small formatting helpers shared by timeline text, emails and PDF exports. */
public final class Formats {

    private Formats() {
    }

    /** Indian grouping without decimals: 482000 -> "₹4,82,000". */
    public static String inr(BigDecimal amount) {
        long value = amount == null ? 0 : amount.setScale(0, RoundingMode.HALF_UP).longValue();
        boolean negative = value < 0;
        String digits = Long.toString(Math.abs(value));
        StringBuilder sb = new StringBuilder();
        if (digits.length() <= 3) {
            sb.append(digits);
        } else {
            String head = digits.substring(0, digits.length() - 3);
            String tail = digits.substring(digits.length() - 3);
            for (int i = 0; i < head.length(); i++) {
                if (i > 0 && (head.length() - i) % 2 == 0) sb.append(',');
                sb.append(head.charAt(i));
            }
            sb.append(',').append(tail);
        }
        return (negative ? "-₹" : "₹") + sb;
    }

    /** Lakh/crore short form used on KPI tiles: 1840000 -> "₹18.4L". */
    public static String inrShort(BigDecimal amount) {
        double v = amount == null ? 0 : amount.doubleValue();
        if (Math.abs(v) >= 1e7) return "₹" + trim(v / 1e7) + "Cr";
        if (Math.abs(v) >= 1e5) return "₹" + trim(v / 1e5) + "L";
        if (Math.abs(v) >= 1e3) return "₹" + trim(v / 1e3) + "K";
        return inr(amount);
    }

    private static String trim(double v) {
        String s = String.format("%.1f", v);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    /** Normalises Indian phone input ("+91 99300 12000", "099300-12000") to 10 digits; other formats keep digits only. */
    public static String phone(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.length() == 12 && digits.startsWith("91")) digits = digits.substring(2);
        if (digits.length() == 11 && digits.startsWith("0")) digits = digits.substring(1);
        return digits.isEmpty() ? null : digits;
    }

    public static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public static String label(Enum<?> e) {
        if (e == null) return "";
        if (e.name().equals("SMB")) return "SMB";
        StringBuilder sb = new StringBuilder();
        for (String word : e.name().toLowerCase().split("_")) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
    }
}
