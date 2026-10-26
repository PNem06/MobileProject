package com.example.mobileproject.notifications;

import com.google.firebase.Timestamp;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.json.JSONObject;

public final class Notice {
    public final String id;
    private final Map<String,Object> data;
    public Notice(String id, Map<String,Object> values) {
        this.id = id == null ? "" : id;
        data = values == null ? Collections.emptyMap() : new HashMap<>(values);
    }
    public static Notice fromJson(JSONObject json) {
        Map<String,Object> data = new HashMap<>();
        java.util.Iterator<String> keys = json.keys();
        while (keys.hasNext()) { String key = keys.next(); data.put(key, json.opt(key)); }
        return new Notice(json.optString("id", json.optString("notificationId")), data);
    }
    public String text(String... keys) {
        for (String key : keys) {
            Object value = data.get(key);
            if (value instanceof String && !((String)value).trim().isEmpty()) return ((String)value).trim();
        }
        return "";
    }
    public boolean flag(String key) {
        return Boolean.TRUE.equals(data.get(key)) || "true".equalsIgnoreCase(String.valueOf(data.get(key)));
    }
    public boolean read() { return flag("isRead") || flag("read"); }
    public String type() { return text("type").toLowerCase(Locale.ROOT); }
    public String title() { String title = text("title", "message"); return title.isEmpty() ? "Thông báo" : title; }
    public String body() { return text("body", "message"); }
    public long createdAt() {
        Object value = data.get("createdAt");
        if (value instanceof Timestamp) return ((Timestamp)value).toDate().getTime();
        if (value instanceof java.util.Date) return ((java.util.Date)value).getTime();
        return value instanceof Number ? ((Number)value).longValue() : 0;
    }
}
