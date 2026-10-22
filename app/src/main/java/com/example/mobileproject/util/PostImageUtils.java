package com.example.mobileproject.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.widget.ImageView;

import com.bumptech.glide.Glide;

/**
 * Hỗ trợ cả ảnh URL cũ (Firebase Storage) và ảnh Base64 mới lưu trong Firestore.
 */
public final class PostImageUtils {
    private PostImageUtils() {}

    public static Object asGlideModel(String value) {
        if (value == null) return null;
        String image = value.trim();
        if (image.isEmpty()) return null;

        // Ảnh cũ vẫn là URL; tiếp tục hỗ trợ để không làm hỏng bài đăng cũ.
        if (image.startsWith("https://") || image.startsWith("http://")) {
            return image;
        }

        // Hỗ trợ cả data URI nếu dữ liệu cũ/mới dùng định dạng này.
        if (image.startsWith("data:image/")) {
            int comma = image.indexOf(',');
            if (comma >= 0) image = image.substring(comma + 1);
        }

        try {
            byte[] bytes = Base64.decode(image, Base64.DEFAULT);
            if (bytes.length == 0) return null;
            return bytes;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static void loadInto(ImageView imageView, String value, int placeholderRes) {
        if (imageView == null) return;
        Object model = asGlideModel(value);
        if (model == null) {
            imageView.setImageResource(placeholderRes);
            return;
        }
        Glide.with(imageView)
                .load(model)
                .placeholder(placeholderRes)
                .error(placeholderRes)
                .into(imageView);
    }

    public static Bitmap decodeBitmap(String value) {
        Object model = asGlideModel(value);
        if (model instanceof byte[]) {
            byte[] bytes = (byte[]) model;
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        }
        return null;
    }
}
