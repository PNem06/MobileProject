package com.example.mobileproject.model;

import java.util.Date;
import java.util.List;
import java.util.UUID;

public class Favorite {

    private String favoriteId;
    private String userId;
    private String postId;
    private Date createdAt;

    public Favorite() {
    }

    public Favorite(String favoriteId, String userId,
                    String postId, Date createdAt) {
        this.favoriteId = favoriteId;
        this.userId = userId;
        this.postId = postId;
        this.createdAt = createdAt;
    }

    // Thêm vào danh sách đang có trong bộ nhớ, chưa lưu lên Firebase.
    public boolean addTo(List<Favorite> favorites) {
        validate(favorites);

        for (Favorite item : favorites) {
            if (item != null && userId.equals(item.getUserId())
                    && postId.equals(item.getPostId())) {
                return false;
            }
        }

        if (favoriteId == null || favoriteId.trim().isEmpty()) {
            favoriteId = UUID.randomUUID().toString();
        }

        if (createdAt == null) {
            createdAt = new Date();
        }

        favorites.add(this);
        return true;
    }

    // Bỏ liên kết yêu thích của đúng người dùng và bài đăng.
    public boolean removeFrom(List<Favorite> favorites) {
        validate(favorites);

        boolean removed = false;

        for (int i = favorites.size() - 1; i >= 0; i--) {
            Favorite item = favorites.get(i);

            if (item != null && userId.equals(item.getUserId())
                    && postId.equals(item.getPostId())) {
                favorites.remove(i);
                removed = true;
            }
        }

        return removed;
    }

    private void validate(List<Favorite> favorites) {
        if (favorites == null) {
            throw new IllegalArgumentException(
                    "Danh sách yêu thích không được null."
            );
        }

        if (userId == null || userId.trim().isEmpty()
                || postId == null || postId.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Phải có mã người dùng và mã bài đăng."
            );
        }
    }

    public String getFavoriteId() {
        return favoriteId;
    }

    public void setFavoriteId(String favoriteId) {
        this.favoriteId = favoriteId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getPostId() {
        return postId;
    }

    public void setPostId(String postId) {
        this.postId = postId;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
}