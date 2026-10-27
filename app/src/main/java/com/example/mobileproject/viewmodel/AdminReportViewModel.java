package com.example.mobileproject.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.mobileproject.data.ReportCacheEntity;
import com.example.mobileproject.repository.AdminManagementRepository;

import java.util.List;

public class AdminReportViewModel extends AndroidViewModel {
    private final AdminManagementRepository repository;
    private final LiveData<List<ReportCacheEntity.AdminReportCacheEntity>> reports;
    public AdminReportViewModel(@NonNull Application application) {
        super(application);
        repository = new AdminManagementRepository(application);
        reports = repository.observeReports();
    }

    public LiveData<List<ReportCacheEntity.AdminReportCacheEntity>> getReports() { return reports; }
    public void startListening() { repository.startReportListening(); }
    public void stopListening() { repository.stopReportListening(); }
    public void confirmViolation(ReportCacheEntity.AdminReportCacheEntity item, AdminManagementRepository.ActionCallback callback) {
        repository.confirmReportViolation(item, callback);
    }
    public void reject(ReportCacheEntity.AdminReportCacheEntity item, AdminManagementRepository.ActionCallback callback) {
        repository.rejectReport(item, callback);
    }
}
