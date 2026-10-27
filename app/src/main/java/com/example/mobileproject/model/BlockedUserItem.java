package com.example.mobileproject.model;

import java.util.Date;

public class BlockedUserItem {
    public String blockedUserId;
    public String fullName;
    public String avatarUrl;
    public Date createdAt;

    public BlockedUserItem(String blockedUserId, String fullName, String avatarUrl, Date createdAt) {
        this.blockedUserId = blockedUserId;
        this.fullName = fullName;
        this.avatarUrl = avatarUrl;
        this.createdAt = createdAt;
    }
}
