package com.example.mobileproject.model;

public class RequestItem {
    public String exchangeId = "";
    public String requesterId = "";
    public String hostId = "";
    public String postId = "";
    public String postTitle = "Căn hộ chỗ ở";
    public String postImageUrl = "";
    public double rating = 5.0;
    public String startDateText = "";
    public String endDateText = "";
    public String dateRangeText = "";
    public String priceOrPointsText = "";
    public String status = "PENDING"; // PENDING, ACCEPTED, CANCELLED, REJECTED
    public String exchangeType = "RECIPROCAL"; // RECIPROCAL (Trao đổi nhà), MONEY / NON_RECIPROCAL (Bằng tiền)
    public boolean isSentTab = true; // true = Đã Gửi, false = Đã Nhận
    public String requesterName = "";
    public String requesterPostTitle = "";
    public String requesterPostImageUrl = "";
    public boolean isPaid = false;

    public RequestItem() {
    }
}