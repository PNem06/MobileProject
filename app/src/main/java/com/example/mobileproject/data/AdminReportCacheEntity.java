
package com.example.mobileproject.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "admin_report_cache")
public class AdminReportCacheEntity {

    @PrimaryKey
    @NonNull
    public String reportId = "";

    public String reporterId = "";
    public String targetType = "";
    public String targetId = "";
    public String targetName = "";
    public String reason = "";
    public String description = "";
    public String status = "PENDING";
    public long updatedAt = 0L;
}
