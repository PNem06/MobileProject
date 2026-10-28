package com.example.mobileproject.model;

public class RecentSearch {
    private String locationName;
    private String details;
    private long timestamp;

    public RecentSearch() {
    }

    public RecentSearch(String locationName, String details) {
        this.locationName = locationName;
        this.details = details;
        this.timestamp = System.currentTimeMillis();
    }

    public RecentSearch(String locationName, String details, long timestamp) {
        this.locationName = locationName;
        this.details = details;
        this.timestamp = timestamp;
    }

    public String getLocationName() {
        return locationName;
    }

    public void setLocationName(String locationName) {
        this.locationName = locationName;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
