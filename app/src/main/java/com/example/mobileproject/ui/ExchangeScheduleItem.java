package com.example.mobileproject.ui;

import java.util.Date;

/** Dữ liệu đã ghép từ Exchange + Post + Account + PostImage để hiển thị trên UI. */
public class ExchangeScheduleItem {
    public String exchangeId = "";
    public String postId = "";
    public String hostId = "";
    public String title = "Chỗ ở";
    public String hostName = "Chủ nhà";
    public String address = "";
    public String imageUrl = "";
    public String status = "";
    public Date startDate;
    public Date endDate;
    public int guestCount;
}
