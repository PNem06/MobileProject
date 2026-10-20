package com.example.mobileproject.data;

import com.google.firebase.Timestamp;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/** Chuyển dữ liệu nhóm về định dạng hiển thị Home; không ghi đổi dữ liệu gốc. */
public final class HomePostMapper {
    private HomePostMapper() {}

    public static JSONObject fromDocument(String id, Map<String, Object> fields) {
        JSONObject result = new JSONObject();
        if (fields == null) fields = java.util.Collections.emptyMap();
        try {
            result.put("id", id);
            for (String key :
                    new String[] {
                            "fullName",
                            "email",
                            "phone",
                            "avatarUrl",
                            "username",
                            "title",
                            "description",
                            "status",
                            "body",
                            "type"
                    }) {
                result.put(key, text(fields, key, ""));
            }
            result.put("ownerId", text(fields, "accountId", "ownerId"));
            result.put("addressText", text(fields, "address", "addressText"));
            result.put("destination", text(fields, "destination", "destination"));
            for (String key :
                    new String[] {
                            "maxGuests",
                            "bedroomCount",
                            "bathroomCount",
                            "price",
                            "ratingAverage",
                            "reviewCount"
                    }) {
                Object value = fields.get(key);
                if (value instanceof Number) result.put(key, value);
            }
            result.put("availableFrom", time(fields, "startDate", "availableFrom"));
            result.put("availableTo", time(fields, "endDate", "availableTo"));
            result.put("createdAt", millis(fields.get("createdAt")));
            String readField =
                    fields.containsKey("isRead") && !fields.containsKey("read") ? "isRead" : "read";
            result.put("readField", readField);
            result.put("read", Boolean.TRUE.equals(fields.get(readField)));
        } catch (JSONException error) {
            throw new IllegalArgumentException("Dữ liệu Home không hợp lệ", error);
        }
        return result;
    }

    private static String text(Map<String, Object> fields, String current, String legacy) {
        Object value = fields.get(current);
        if (value instanceof String && !((String) value).trim().isEmpty()) return (String) value;
        value = fields.get(legacy);
        return value instanceof String ? (String) value : "";
    }

    private static long time(Map<String, Object> fields, String current, String legacy) {
        // Nếu trường mới tồn tại thì ưu tiên nó, kể cả khi chưa có giá trị.
        return millis(fields.containsKey(current) ? fields.get(current) : fields.get(legacy));
    }

    private static long millis(Object value) {
        if (value instanceof Timestamp) return ((Timestamp) value).toDate().getTime();
        if (value instanceof Date) return ((Date) value).getTime();
        return 0;
    }

    public static JSONArray mergeMine(JSONArray current, JSONArray legacy, String uid) {
        Map<String, JSONObject> merged = new LinkedHashMap<>();
        for (JSONArray source : new JSONArray[] {current, legacy}) {
            for (int i = 0; i < source.length(); i++) {
                JSONObject post = source.optJSONObject(i);
                if (post == null || !uid.equals(post.optString("ownerId"))) continue;
                String id = post.optString("id");
                if (!id.isEmpty() && !merged.containsKey(id)) merged.put(id, post);
            }
        }
        java.util.List<JSONObject> sorted = new java.util.ArrayList<>(merged.values());
        sorted.sort(java.util.Comparator.comparing(p -> p.optString("title")));
        JSONArray result = new JSONArray();
        for (JSONObject post : sorted) result.put(post);
        return result;
    }

    public static String imageUrl(Map<String, Object> fields) {
        return fields == null ? "" : text(fields, "imgUrl", "imageUrl").trim();
    }
}
