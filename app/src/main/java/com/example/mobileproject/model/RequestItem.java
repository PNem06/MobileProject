package com.example.mobileproject.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class RequestItem implements Serializable {
    public String exchangeId = "";
    public String sourceCollection = "exchanges";
    public String requesterId = "";
    public String hostId = "";
    public String postId = "";
    public String postTitle = "Căn hộ chỗ ở";
    public String postImageUrl = "";
    public double rating = 5.0;
    public String startDateText = "";
    public String endDateText = "";
    public String dateRangeText = "";
    public String locationText = "";
    public String priceOrPointsText = "";
    public String status = "PENDING"; // PENDING (Đang chờ), ACCEPTED (Đã chấp nhận), CANCELLED (Đã hủy), REJECTED (Đã từ chối)
    public String exchangeType = "RECIPROCAL"; // RECIPROCAL (Trao đổi chỗ ở), MONEY (Thanh toán bằng tiền)
    public boolean isSentTab = true; // true = Đã Gửi, false = Đã Nhận
    public boolean hasMoneyAlternativePending = false; // true = host muốn thanh toán bằng tiền -> hiện nút "Thanh toán"

    // Thống kê cho tab "Đã nhận"
    public int subRequestsCount = 0; // VD: 3 yêu cầu
    public int rejectedSubRequestsCount = 0; // VD: 1 đã từ chối

    // Chi tiết cho từng yêu cầu con (dùng ở màn hình chi tiết & dialog)
    public String subRequestId = "";
    public String proposedPostId = "";
    public String paymentStatus = "";
    public String paymentId = "";
    public String senderAvatarUrl = "";
    public String senderName = "";
    public String requestDateText = "";
    public String proposedPostTitle = "";
    public String proposedPostImageUrl = "";
    public double proposedPostRating = 4.8;
    public String proposedPostDates = "";
    public boolean isPaid = false;
    public boolean walletManaged = false;

    public List<RequestItem> subRequestsList = new ArrayList<>();

    public RequestItem() {
    }
    public boolean isMoneyRequest() { return "NON_RECIPROCAL".equalsIgnoreCase(exchangeType) || "MONEY".equalsIgnoreCase(exchangeType); }
    public boolean needsWalletPayment() { return !walletManaged && !isPaid && "PENDING".equalsIgnoreCase(status) && isMoneyRequest(); }
}
