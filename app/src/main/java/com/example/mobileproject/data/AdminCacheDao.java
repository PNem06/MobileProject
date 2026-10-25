package com.example.mobileproject.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

@Dao
public abstract class AdminCacheDao {
    @Query("SELECT * FROM admin_report_cache ORDER BY updatedAt DESC")
    public abstract LiveData<List<ReportCacheEntity.AdminReportCacheEntity>> observeReports();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void insertReports(List<ReportCacheEntity.AdminReportCacheEntity> reports);

    @Query("DELETE FROM admin_report_cache")
    public abstract void deleteReports();

    @Transaction
    public void replaceReports(List<ReportCacheEntity.AdminReportCacheEntity> reports) {
        deleteReports();
        if (reports != null && !reports.isEmpty()) insertReports(reports);
    }

    @Query("SELECT * FROM admin_exchange_cache ORDER BY createdAt DESC")
    public abstract LiveData<List<ExchangeCacheEntity>> observeExchanges();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void insertExchanges(List<ExchangeCacheEntity> exchanges);

    @Query("DELETE FROM admin_exchange_cache")
    public abstract void deleteExchanges();

    @Transaction
    public void replaceExchanges(List<ExchangeCacheEntity> exchanges) {
        deleteExchanges();
        if (exchanges != null && !exchanges.isEmpty()) insertExchanges(exchanges);
    }
}
