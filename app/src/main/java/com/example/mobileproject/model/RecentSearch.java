package com.example.mobileproject.model;

public class RecentSearch {
    private String locationName;
    private String details;

    public RecentSearch() {
    }

    public RecentSearch(String locationName, String details) {
        this.locationName = locationName;
        this.details = details;
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
}
