package com.example.mobileproject.view;

import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.Locale;

/** Quy tắc hiển thị thẻ; lấy dữ liệu trực tiếp từ bài đăng trên Firebase. */
public final class PostCardData {
    private static final Locale VI = new Locale("vi", "VN");

    private PostCardData() {}

    public static boolean matchesFilter(
            JSONObject post, int filter, java.util.Set<String> liked) {
        if (post == null) return false;
        if (filter == 2) return liked != null && liked.contains(post.optString("id"));
        return true;
    }

    public static String priceLabel(JSONObject post) {
        if (post == null) return "Chưa cập nhật giá";
        Object value = post.opt("price");
        if (!(value instanceof Number)) return "Chưa cập nhật giá";
        double amount = ((Number) value).doubleValue();
        if (!Double.isFinite(amount) || amount <= 0) return "Chưa cập nhật giá";
        NumberFormat format = NumberFormat.getNumberInstance(VI);
        format.setMaximumFractionDigits(0);
        return format.format(amount) + "đ / chuyến";
    }

    public static String ratingLabel(JSONObject post) {
        if (post == null) return "";
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
        if (post == null) return "Chỗ ở";
        String title = post.optString("title").trim();
        return title.isEmpty() ? "Chỗ ở chưa có tên" : title;
    }
}
