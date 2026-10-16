package com.example.mobileproject.ui;

import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.Locale;

/** Quy tắc hiển thị thẻ; không sửa dữ liệu bài đăng trên Firebase. */
public final class PostCardData {
    private static final Locale VI = new Locale("vi", "VN");

    private PostCardData() {}

    public static boolean isAvailable(JSONObject post, long now) {
        long from = post.optLong("availableFrom");
        long to = post.optLong("availableTo");
        // Đây là khoảng thời gian chủ nhà công bố, không phải kiểm tra booking trùng lịch.
        return from > 0 && to > from && from <= now && now < to;
    }

    public static boolean matchesFilter(
            JSONObject post, int filter, java.util.Set<String> liked, long now) {
        if (filter == 1) return isAvailable(post, now);
        if (filter == 2) return liked != null && liked.contains(post.optString("id"));
        return true;
    }

    public static String priceLabel(JSONObject post) {
        Object value = post.opt("pricePerNight");
        if (!(value instanceof Number)) return "Chưa cập nhật giá";
        double amount = ((Number) value).doubleValue();
        if (!Double.isFinite(amount) || amount <= 0) return "Chưa cập nhật giá";
        NumberFormat format = NumberFormat.getNumberInstance(VI);
        format.setMaximumFractionDigits(0);
        return format.format(amount) + "đ / đêm";
    }

    public static String ratingLabel(JSONObject post) {
        double rating = post.optDouble("ratingAverage", Double.NaN);
        if (!Double.isFinite(rating)
                || rating <= 0
                || rating > 5
                || post.optInt("reviewCount") <= 0) return "";
        NumberFormat format = NumberFormat.getNumberInstance(VI);
        format.setMinimumFractionDigits(1);
        format.setMaximumFractionDigits(2);
        return "★ " + format.format(rating);
    }

    public static String initials(String name) {
        if (name == null || name.trim().isEmpty()) return "CN";
        String[] words = name.trim().split("\\s+");
        String last = firstLetter(words[words.length - 1]);
        return (words.length == 1 ? last : firstLetter(words[words.length - 2]) + last)
                .toUpperCase(VI);
    }

    private static String firstLetter(String word) {
        return new String(Character.toChars(word.codePointAt(0)));
    }

    public static String title(JSONObject post) {
        String title = post.optString("title").trim();
        switch (title.toLowerCase(Locale.ROOT)) {
            case "affordable room in can tho":
                return "Phòng giá tốt tại Cần Thơ";
            case "bright room near metro":
                return "Phòng sáng thoáng gần ga metro";
            case "small studio for short stay":
                return "Studio nhỏ cho kỳ nghỉ ngắn";
            case "two-bedroom apartment":
                return "Căn hộ 2 phòng ngủ";
            default:
                return title.isEmpty() ? "Chỗ ở chưa có tên" : title;
        }
    }
}
