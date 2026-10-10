package com.example.mobileproject.model;

import androidx.annotation.NonNull;

public class Report {
    public String getReportId() {
        return reportId;
    }

    public void setReportId(String reportId) {
        this.reportId = reportId;
    }

    public String getReporterId() {
        return reporterId;
    }

    public void setReporterId(String reporterId) {
        this.reporterId = reporterId;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ReportStatus getStatus() {
        return status;
    }

    public void setStatus(ReportStatus status) {
        this.status = status;
    }

    private String reportId;
    private String reporterId;
    private String targetType;
    private String targetId;
    private String reason;
    private String description;
    private ReportStatus status;

    public Report(String reportId, String reporterId, String targetType, String targetId, String reason, String description, ReportStatus status) {
        this.reportId = reportId;
        this.reporterId = reporterId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.reason = reason;
        this.description = description;
        this.status = status;
    }
    public void submit(){
        this.status = ReportStatus.PENDING;
    }
    public void startReview(){
        this.status = ReportStatus.REVIEWING;
    }
    public void resolved(String action){
        System.out.println("Report"+reportId+" resolved: " + action);
        this.status = ReportStatus.RESOLVED;
    }
    public void rejected(){
        this.status = ReportStatus.REJECTED;
    }

    @NonNull
    @Override
    public String toString() {
        return "Report{" +
                "reportId='" + reportId + '\'' +
                ", reporterId='" + reporterId + '\'' +
                ", targetId='" + targetId + '\'' +
                ", targetType='" + targetType + '\'' +
                ", reason='" + reason + '\'' +
                ", description='" + description + '\'' +
                ", status=" + status +
                '}';
    }
}
