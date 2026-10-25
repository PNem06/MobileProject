package com.example.mobileproject.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "admin_exchange_cache")
public class ExchangeCacheEntity {
    @PrimaryKey @NonNull public String exchangeId = "";
    public String requesterId = "";
    public String hostId = "";
    public String requesterUsername = "";
    public String hostUsername = "";
    public String postId = "";
    public String exchangeType = "";
    public String status = "";
    public String cancelReason = "";
    public String cancelRequestStatus = "";
    public String paymentStatus = "CHƯA CÓ DỮ LIỆU";
    public double paymentAmount = 0.0;
    public int guestCount = 0;
    public String note = "";
    public long startDate = 0L;
    public long endDate = 0L;
    public long paidAt = 0L;
    public long createdAt = 0L;
}
