package com.example.mobileproject.model;

import com.google.firebase.Timestamp;
import java.io.Serializable;
import java.util.List;

public class ListingPost implements Serializable {
    private String postId;
    private String accountId;
    private String title;
    private String description;
    private String address;
    private String additionalInformation;
    private String status;
    private long bedroomCount;
    private long bathroomCount;
    private long maxGuests;
    private String propertyType; // "Căn hộ/Chung cư", "Nhà nguyên căn",...
    private double latitude;
    private double longitude;
    private Timestamp startDate;
    private Timestamp endDate;
    private List<String> amenityIds;

    // Trường bổ sung để hiển thị hình ảnh (lấy từ bảng images hoặc gán URL trực tiếp)
    private String coverImageUrl;
    private double pricePerNight; // Nếu có trường giá, có thể gán mặc định hoặc đọc từ DB

    public ListingPost() {}

    // Getters & Setters đầy đủ
    public String getPostId() { return postId; }
    public void setPostId(String postId) { this.postId = postId; }

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getAdditionalInformation() { return additionalInformation; }
    public void setAdditionalInformation(String additionalInformation) { this.additionalInformation = additionalInformation; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public long getBedroomCount() { return bedroomCount; }
    public void setBedroomCount(long bedroomCount) { this.bedroomCount = bedroomCount; }

    public long getBathroomCount() { return bathroomCount; }
    public void setBathroomCount(long bathroomCount) { this.bathroomCount = bathroomCount; }

    public long getMaxGuests() { return maxGuests; }
    public void setMaxGuests(long maxGuests) { this.maxGuests = maxGuests; }

    public String getPropertyType() { return propertyType; }
    public void setPropertyType(String propertyType) { this.propertyType = propertyType; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public Timestamp getStartDate() { return startDate; }
    public void setStartDate(Timestamp startDate) { this.startDate = startDate; }

    public Timestamp getEndDate() { return endDate; }
    public void setEndDate(Timestamp endDate) { this.endDate = endDate; }

    public List<String> getAmenityIds() { return amenityIds; }
    public void setAmenityIds(List<String> amenityIds) { this.amenityIds = amenityIds; }

    public String getCoverImageUrl() { return coverImageUrl; }
    public void setCoverImageUrl(String coverImageUrl) { this.coverImageUrl = coverImageUrl; }

    public double getPricePerNight() { return pricePerNight; }
    public void setPricePerNight(double pricePerNight) { this.pricePerNight = pricePerNight; }
}