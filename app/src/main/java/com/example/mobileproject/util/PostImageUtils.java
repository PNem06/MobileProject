package com.example.mobileproject.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.LruCache;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

/**
 * Hỗ trợ cả ảnh URL cũ (Firebase Storage) và ảnh Base64 mới lưu trong Firestore.
 * Tối ưu bộ nhớ đệm (LruCache) và Glide caching để ứng dụng chạy siêu mượt.
 */
public final class PostImageUtils {
    private static final int MAX_CACHE_SIZE = (int) (Runtime.getRuntime().maxMemory() / 1024 / 8); // 1/8 max memory
    private static final LruCache<String, Object> MEMORY_CACHE = new LruCache<String, Object>(MAX_CACHE_SIZE > 0 ? MAX_CACHE_SIZE : 4096) {
        @Override
        protected int sizeOf(String key, Object value) {
            if (value instanceof byte[]) {
                return ((byte[]) value).length / 1024; // KB
            }
            return 1;
        }
    };

    private PostImageUtils() {}

    public static Object asGlideModel(String value) {
        if (value == null) return null;
        String image = value.trim();
        if (image.isEmpty()) return null;

        Object cached = MEMORY_CACHE.get(image);
        if (cached != null) return cached;

        // Ảnh cũ vẫn là URL; tiếp tục hỗ trợ để không làm hỏng bài đăng cũ.
        if (image.startsWith("https://") || image.startsWith("http://")) {
            MEMORY_CACHE.put(image, image);
            return image;
        }

        String rawBase64 = image;
        // Hỗ trợ cả data URI nếu dữ liệu cũ/mới dùng định dạng này.
        if (rawBase64.startsWith("data:image/")) {
            int comma = rawBase64.indexOf(',');
            if (comma >= 0) rawBase64 = rawBase64.substring(comma + 1);
        }

        try {
            byte[] bytes = Base64.decode(rawBase64, Base64.DEFAULT);
            if (bytes.length == 0) return null;
            MEMORY_CACHE.put(image, bytes);
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
        Glide.with(imageView.getContext())
                .load(model)
                .placeholder(placeholderRes)
                .error(placeholderRes)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .dontAnimate()
                .override(600, 600)
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
