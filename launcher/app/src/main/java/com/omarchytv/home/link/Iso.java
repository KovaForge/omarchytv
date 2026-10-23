package com.omarchytv.home.link;

/**
 * ISO-8601 timestamps without {@code java.time} (that package is API 26). The shapes match what
 * {@code LocalDateTime} and {@code OffsetDateTime} accepted before: UTC clock time, an optional fraction,
 * and for the offset form a trailing {@code Z} or {@code ±HH:MM}.
 */
public final class Iso {
    private Iso() {
    }

    /**
     * Epoch millis of a UTC timestamp ("2025-10-19T08:11:58Z", fractions allowed). A missing Z is still
     * read as UTC, matching the previous LocalDateTime path. -1 when the text is not that shape.
     */
    public static long utcMillis(String iso) {
        if (iso == null || iso.length() < 19) return -1;
        try {
            String t = iso;
            if (t.endsWith("Z")) t = t.substring(0, t.length() - 1);
            int dot = t.indexOf('.');
            String frac = "";
            if (dot > 0) {
                frac = t.substring(dot + 1);
                t = t.substring(0, dot);
            }
            long ms = clockUtc(t);
            if (!frac.isEmpty()) ms += Long.parseLong((frac + "000").substring(0, 3));
            return ms;
        } catch (RuntimeException e) {
            return -1;
        }
    }

    /**
     * Epoch millis of an offset timestamp ("2026-10-19T00:10:00Z" or "2026-10-19T00:10:00+02:00").
     * Null when it is not an offset date-time (the caller then treats the value as unknown).
     */
    public static Long offsetMillis(String text) {
        if (text == null) return null;
        try {
            String s = text.trim();
            if (s.length() < 20) return null;
            int offsetMin = 0;
            String body;
            if (s.endsWith("Z") || s.endsWith("z")) {
                body = s.substring(0, s.length() - 1);
            } else {
                int sign = -1;
                for (int i = 19; i < s.length(); i++) {
                    char c = s.charAt(i);
                    if (c == '+' || c == '-') sign = i;
                }
                if (sign < 0) return null;
                String off = s.substring(sign);
                if (off.length() != 6 || off.charAt(3) != ':') return null;
                int hh = Integer.parseInt(off.substring(1, 3));
                int mm = Integer.parseInt(off.substring(4, 6));
                if (hh > 18 || mm > 59) return null;
                offsetMin = (hh * 60 + mm) * (off.charAt(0) == '-' ? -1 : 1);
                body = s.substring(0, sign);
            }
            int dot = body.indexOf('.');
            String frac = "";
            if (dot > 0) {
                frac = body.substring(dot + 1);
                body = body.substring(0, dot);
                for (int i = 0; i < frac.length(); i++) {
                    char c = frac.charAt(i);
                    if (c < '0' || c > '9') return null;
                }
            }
            long ms = clockUtc(body);
            if (!frac.isEmpty()) ms += Long.parseLong((frac + "000").substring(0, 3));
            return ms - offsetMin * 60_000L;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static long clockUtc(String t) {
        if (t.length() != 19
                || t.charAt(4) != '-' || t.charAt(7) != '-' || t.charAt(10) != 'T'
                || t.charAt(13) != ':' || t.charAt(16) != ':') {
            throw new IllegalArgumentException("clock");
        }
        int y = Integer.parseInt(t.substring(0, 4));
        int mo = Integer.parseInt(t.substring(5, 7));
        int d = Integer.parseInt(t.substring(8, 10));
        int h = Integer.parseInt(t.substring(11, 13));
        int mi = Integer.parseInt(t.substring(14, 16));
        int sec = Integer.parseInt(t.substring(17, 19));
        if (mo < 1 || mo > 12 || d < 1 || d > daysInMonth(y, mo) || h > 23 || mi > 59 || sec > 59) {
            throw new IllegalArgumentException("range");
        }
        long days = daysFromCivil(y, mo, d);
        return days * 86_400_000L + h * 3_600_000L + mi * 60_000L + sec * 1_000L;
    }

    private static int daysInMonth(int y, int mo) {
        switch (mo) {
            case 2:
                return leap(y) ? 29 : 28;
            case 4:
            case 6:
            case 9:
            case 11:
                return 30;
            default:
                return 31;
        }
    }

    private static boolean leap(int y) {
        return y % 4 == 0 && (y % 100 != 0 || y % 400 == 0);
    }

    /** Howard Hinnant civil_from_days, inverted. Day 0 is 1970-01-01. */
    private static long daysFromCivil(int y, int m, int d) {
        y -= m <= 2 ? 1 : 0;
        int era = (y >= 0 ? y : y - 399) / 400;
        int yoe = y - era * 400;
        int doy = (153 * (m + (m > 2 ? -3 : 9)) + 2) / 5 + d - 1;
        int doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
        return era * 146097L + doe - 719468L;
    }
}
