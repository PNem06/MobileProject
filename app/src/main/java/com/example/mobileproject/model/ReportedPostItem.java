package com.example.mobileproject.model;

import java.util.Date;

public class ReportedPostItem {
    public String reportId;
    public String targetId; // postId
    public String reason;
    public String status; // PENDING, REVIEWING, RESOLVED, REJECTED
    public Date createdAt;
    public String postTitle;
    public String postAddress;
    public String postImageUrl;
    public double postPrice;

    public ReportedPostItem(String reportId, String targetId, String reason, String status, Date createdAt) {
        this.reportId = reportId;
        this.targetId = targetId;
        this.reason = reason;
        this.status = status;
        this.createdAt = createdAt;
    }
}
