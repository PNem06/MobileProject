package com.example.mobileproject.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.mobileproject.data.ExchangeCacheEntity;
import com.example.mobileproject.repository.AdminManagementRepository;

import java.util.List;

public class AdminExchangeViewModel extends AndroidViewModel {
    private final AdminManagementRepository repository;
    private final LiveData<List<ExchangeCacheEntity>> exchanges;

    public AdminExchangeViewModel(@NonNull Application application) {
        super(application);
        repository = new AdminManagementRepository(application);
        exchanges = repository.observeExchanges();
    }

    public LiveData<List<ExchangeCacheEntity>> getExchanges() { return exchanges; }
    public void startListening() { repository.startExchangeListening(); }
    public void stopListening() { repository.stopExchangeListening(); }
    public void decideCancellation(ExchangeCacheEntity item, boolean accept, AdminManagementRepository.ActionCallback callback) {
        repository.decideCancellation(item, accept, callback);
    }
}
