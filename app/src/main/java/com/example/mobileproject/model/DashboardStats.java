package com.example.mobileproject.model;

public class DashboardStats {

    private long totalUsers;
    private long totalPosts;
    private long totalExchanges;
    private long pendingReports;

    // Constructor rỗng
    public DashboardStats() {
    }

    // Constructor đầy đủ
    public DashboardStats(
            long totalUsers,
            long totalPosts,
            long totalExchanges,
            long pendingReports
    ) {
        this.totalUsers = totalUsers;
        this.totalPosts = totalPosts;
        this.totalExchanges = totalExchanges;
        this.pendingReports = pendingReports;
    }

    // Getter / Setter
    public long getTotalUsers() {
        return totalUsers;
    }

    public void setTotalUsers(long totalUsers) {
        this.totalUsers = totalUsers;
    }

    public long getTotalPosts() {
        return totalPosts;
    }

    public void setTotalPosts(long totalPosts) {
        this.totalPosts = totalPosts;
    }

    public long getTotalExchanges() {
        return totalExchanges;
    }

    public void setTotalExchanges(long totalExchanges) {
        this.totalExchanges = totalExchanges;
    }

    public long getPendingReports() {
        return pendingReports;
    }

    public void setPendingReports(long pendingReports) {
        this.pendingReports = pendingReports;
    }
}