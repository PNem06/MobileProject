package com.example.mobileproject.model;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;

public final class AdminUserItem {

    public final String documentId;
    public final String accountId;
    public final String fullName;
    public final String username;
    public final String email;
    public final String phone;
    public final String avatarUrl;
    public final String status;
    public final int postCount;

    public AdminUserItem(
            String documentId,
            Map<String, Object> data,
            int postCount
    ) {
        this.documentId = documentId;
        this.accountId = first(data, "accountId", documentId);
        this.fullName = first(
                data,
                "fullName",
                first(data, "username", documentId)
        );
        this.username = first(data, "username", "");
        this.email = first(
                data,
                "email",
                first(data, "e-mail", "")
        );
        this.phone = first(data, "phone", "");
        this.avatarUrl = first(data, "avatarUrl", "");
        this.status = first(data, "status", "UNKNOWN");
        this.postCount = postCount;
    }

    public boolean active() {
        return "ACTIVE".equals(status);
    }

    public boolean canChangeStatus() {
        return active() || "INACTIVE".equals(status);
    }

    public String statusLabel() {
        if (active()) {
            return "Đang hoạt động";
        }

        if ("INACTIVE".equals(status)) {
            return "Đã khóa";
        }

        return "Chưa rõ trạng thái";
    }

    public boolean matches(String query) {
        String content = fullName + " "
                + username + " "
                + email + " "
                + accountId + " "
                + documentId + " "
                + phone;

        return normalize(content).contains(normalize(query));
    }

    public static String normalize(String value) {
        return Normalizer.normalize(
                        value == null ? "" : value.trim(),
                        Normalizer.Form.NFD
                )
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replace('đ', 'd');
    }

    public static String first(
            Map<String, Object> data,
            String key,
            String fallback
    ) {
        Object value = data == null ? null : data.get(key);

        if (value instanceof String
                && !((String) value).trim().isEmpty()) {
            return ((String) value).trim();
        }

        return fallback;
    }
}