package com.example.mobileproject.model;

import java.util.HashMap;
import java.util.Map;

public final class AdminUserPostItem {
    private static final String[] FIELDS = {
            "postId", "accountId", "title", "address", "status", "description",
            "additionalInformation", "propertyType", "coverImageUrl",
            "maxGuests", "bedroomCount", "bathroomCount"
    };

    public final String documentId;
    public final String postId;
    public final String ownerId;
    public final String title;
    public final String address;
    public final String status;
    public final String description;
    public final String additionalInformation;
    public final String propertyType;
    public final String coverImageUrl;
    public final String maxGuests;
    public final String bedroomCount;
    public final String bathroomCount;

    public AdminUserPostItem(String documentId, Map<String, Object> data) {
        this.documentId = documentId;

        postId = text(data, "postId", documentId);

        ownerId = text(
                data,
                "accountId",
                text(data, "ownerId", "")
        );

        title = text(data, "title", "Bài đăng chưa có tiêu đề");

        address = text(
                data,
                "address",
                text(data, "addressText", "Chưa có địa chỉ")
        );

        status = text(data, "status", "UNKNOWN");
        description = text(data, "description", "Chưa có mô tả.");

        additionalInformation = text(
                data,
                "additionalInformation",
                "Chưa cập nhật."
        );

        propertyType = text(data, "propertyType", "Chưa cập nhật");

        coverImageUrl = text(
                data,
                "coverImageUrl",
                text(data, "imgUrl", text(data, "imageUrl", ""))
        );

        maxGuests = number(data, "maxGuests");
        bedroomCount = number(data, "bedroomCount");
        bathroomCount = number(data, "bathroomCount");
    }

    public boolean belongsTo(AdminUserItem user) {
        return user != null
                && !ownerId.isEmpty()
                && (ownerId.equals(user.documentId)
                || ownerId.equals(user.accountId));
    }

    public String statusLabel() {
        switch (status) {
            case "APPROVED":
                return "Đã duyệt";
            case "PENDING":
                return "Chờ duyệt";
            case "REJECTED":
                return "Đã từ chối";
            case "HIDDEN":
                return "Đã ẩn";
            case "PUBLIC":
                return "Công khai";
            case "PRIVATE":
                return "Riêng tư";
            default:
                return "Chưa rõ trạng thái (" + status + ")";
        }
    }

    public Map<String, Object> fields() {
        String[] values = {
                postId,
                ownerId,
                title,
                address,
                status,
                description,
                additionalInformation,
                propertyType,
                coverImageUrl,
                maxGuests,
                bedroomCount,
                bathroomCount
        };

        Map<String, Object> result = new HashMap<>();

        for (int i = 0; i < FIELDS.length; i++) {
            result.put(FIELDS[i], values[i]);
        }

        return result;
    }

    public AdminUserPostItem withImage(String url) {
        if (!coverImageUrl.isEmpty() || url == null || url.isEmpty()) {
            return this;
        }

        Map<String, Object> data = fields();
        data.put("coverImageUrl", url);

        return new AdminUserPostItem(documentId, data);
    }

    private static String number(Map<String, Object> data, String key) {
        Object value = data == null ? null : data.get(key);

        if (value instanceof Number) {
            double n = ((Number) value).doubleValue();

            if (!Double.isFinite(n) || n < 0) {
                return "Chưa cập nhật";
            }

            return n == Math.rint(n)
                    ? String.valueOf(((Number) value).longValue())
                    : String.valueOf(n);
        }

        return text(data, key, "Chưa cập nhật");
    }

    private static String text(
            Map<String, Object> data,
            String key,
            String fallback
    ) {
        return AdminUserItem.first(data, key, fallback);
    }
}