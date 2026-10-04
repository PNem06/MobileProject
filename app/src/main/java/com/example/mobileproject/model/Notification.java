package com.example.mobileproject.model;

import java.util.Date;
public class Notification {
    private String notificationId;
    private String userId;
    private String type;
    private String title;
    private String body;
    private boolean isRead;
    private Date createdAt;

    public Notification(String notificationId, String userId, String type, String title, String body, boolean isRead, Date createdAt) {
        this.notificationId = notificationId;
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.body = body;
        this.isRead = isRead;
        this.createdAt = createdAt;
    }

    public void markRead() {
        isRead = true;
    }

    public String getNotificationId() {
        return notificationId;
    }

    public String getUserId() {
        return userId;
    }

    public String getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public boolean isRead() {
        return isRead;
    }

    public Date getCreatedAt() {
        return createdAt;
    }
}
