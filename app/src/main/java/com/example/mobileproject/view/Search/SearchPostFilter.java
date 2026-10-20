package com.example.mobileproject.view.Search;

import com.example.mobileproject.model.ListingPost;

import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Dùng chung bộ lọc cho trang kết quả và trang bản đồ để dữ liệu luôn đồng nhất. */
public final class SearchPostFilter {
    private SearchPostFilter() { }

    public static boolean matches(ListingPost post,
                                  String location,
                                  String timeMode,
                                  String startDateText,
                                  String endDateText,
                                  String tolerance,
                                  String flexDuration,
                                  String flexMonth) {
        return matchesLocation(post, location)
                && matchesTime(post, timeMode, startDateText, endDateText,
                tolerance, flexDuration, flexMonth);
    }

    public static boolean matchesLocation(ListingPost post, String location) {
        if (location == null || location.trim().isEmpty()) return true;
        String address = normalize(post == null ? null : post.getAddress());
        String selected = normalize(location);
        if (address.isEmpty() || selected.isEmpty()) return false;

        String[] parts = selected.split(",");
        String firstUseful = "";
        String lastUseful = "";
        for (String part : parts) {
            String value = part.trim();
            if (value.isEmpty() || value.equals("viet nam") || value.equals("vietnam")) continue;
            if (firstUseful.isEmpty()) firstUseful = value;
            lastUseful = value;
        }
        if (firstUseful.isEmpty()) return address.contains(selected);
        if (address.contains(firstUseful)) return true;
        return lastUseful.length() > 2 && address.contains(lastUseful);
    }

    public static boolean matchesTime(ListingPost post,
                                      String timeMode,
                                      String startDateText,
                                      String endDateText,
                                      String tolerance,
                                      String flexDuration,
                                      String flexMonth) {
        if (timeMode == null || timeMode.trim().isEmpty()) return true;
        if (post == null || post.getStartDate() == null || post.getEndDate() == null) return false;

        Date postStart = post.getStartDate().toDate();
        Date postEnd = post.getEndDate().toDate();
        if ("CUSTOM".equalsIgnoreCase(timeMode)) {
            Date wantedStart = parseDate(startDateText);
            Date wantedEnd = parseDate(endDateText);
            if (wantedStart == null || wantedEnd == null) return false;
            int days = toleranceDays(tolerance);
            return withinDays(postStart, wantedStart, days)
                    && withinDays(postEnd, wantedEnd, days);
        }

        if ("FLEXIBLE".equalsIgnoreCase(timeMode)) {
            Calendar monthStart = parseMonth(flexMonth);
            if (monthStart == null) return false;
            Date pStart = dateOnly(postStart);
            Date pEnd = dateOnly(postEnd);
            Calendar monthEnd = (Calendar) monthStart.clone();
            monthEnd.add(Calendar.MONTH, 1);
            monthEnd.add(Calendar.MILLISECOND, -1);

            Date from = dateOnly(monthStart.getTime());
            Date to = dateOnly(monthEnd.getTime());
            // Cả ngày bắt đầu và ngày kết thúc của bài đăng đều phải nằm trong tháng đã chọn.
            if (pStart.before(from) || pStart.after(to) || pEnd.before(from) || pEnd.after(to)) return false;
            if (pEnd.before(pStart)) return false;

            long durationDays = (pEnd.getTime() - pStart.getTime()) / 86400000L;
            long requiredDays = minDurationDays(flexDuration);
            return durationDays >= requiredDays;
        }
        return true;
    }

    private static long minDurationDays(String duration) {
        if (duration == null) return 7;
        String value = duration.toLowerCase(Locale.ROOT);
        if (value.contains("tháng")) return 28;
        if (value.contains("tuần") && (value.contains("2") || value.contains("hai"))) return 14;
        return 7;
    }

    private static int toleranceDays(String value) {
        if (value == null) return 0;
        String text = value.toLowerCase(Locale.ROOT);
        if (text.contains("7")) return 7;
        if (text.contains("3")) return 3;
        if (text.contains("1")) return 1;
        return 0;
    }

    private static boolean withinDays(Date actual, Date target, int days) {
        long difference = Math.abs(dateOnly(actual).getTime() - dateOnly(target).getTime());
        return difference <= days * 86400000L;
    }

    private static Date dateOnly(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    private static Date parseDate(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            format.setLenient(false);
            return format.parse(value.trim());
        } catch (Exception exception) {
            return null;
        }
    }

    private static Calendar parseMonth(String value) {
        if (value == null) return null;
        Matcher matcher = Pattern.compile("(\\d{1,2}).*?(\\d{4})").matcher(value);
        if (!matcher.find()) return null;
        try {
            int month = Integer.parseInt(matcher.group(1));
            int year = Integer.parseInt(matcher.group(2));
            if (month < 1 || month > 12) return null;
            Calendar calendar = Calendar.getInstance();
            calendar.clear();
            calendar.set(year, month - 1, 1, 0, 0, 0);
            return calendar;
        } catch (Exception exception) {
            return null;
        }
    }

    private static String normalize(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        normalized = Pattern.compile("\\p{InCombiningDiacriticalMarks}+")
                .matcher(normalized).replaceAll("");
        return normalized.replace('đ', 'd')
                .replaceAll("[^a-z0-9, ]", " ")
                .replaceAll("\\s+", " ").trim();
    }
}
