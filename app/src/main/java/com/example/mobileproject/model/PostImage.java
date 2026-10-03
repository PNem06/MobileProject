package com.example.mobileproject.model;

public class PostImage {

    private String imageId;
    private String postId;
    private String imageUrl;
    private int orderIndex;

    public PostImage() {
    }

    public PostImage(String imageId, String postId,
                     String imageUrl, int orderIndex) {
        this.imageId = imageId;
        this.postId = postId;
        this.imageUrl = imageUrl;
        setOrderIndex(orderIndex);
    }

    public void changeOrder(int newIndex) {
        setOrderIndex(newIndex);
    }

    public String getImageId() {
        return imageId;
    }

    public void setImageId(String imageId) {
        this.imageId = imageId;
    }

    public String getPostId() {
        return postId;
    }

    public void setPostId(String postId) {
        this.postId = postId;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(int orderIndex) {
        if (orderIndex < 0) {
            throw new IllegalArgumentException("Thứ tự ảnh không được âm.");
        }
        this.orderIndex = orderIndex;
    }
}