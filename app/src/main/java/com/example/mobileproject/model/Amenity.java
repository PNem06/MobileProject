package com.example.mobileproject.model;

public class Amenity {

    private String amenityId;
    private String name;
    private String iconUrl;

    public Amenity() {
    }

    public Amenity(String amenityId, String name, String iconUrl) {
        this.amenityId = amenityId;
        this.name = name;
        this.iconUrl = iconUrl;
    }

    public String getDisplayName() {
        return name == null ? "" : name;
    }

    public String getAmenityId() {
        return amenityId;
    }

    public void setAmenityId(String amenityId) {
        this.amenityId = amenityId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    public void setIconUrl(String iconUrl) {
        this.iconUrl = iconUrl;
    }
}