package com.example.mobileproject.viewmodel;

import android.util.Log;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.mobileproject.model.ReportStatus;
import com.example.mobileproject.data.LocalCache;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

public class DashboardViewModel extends AndroidViewModel {
    private static final String TAG = "DashboardViewModel";
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final LocalCache localCache;
    private static final String CACHE_SCOPE = "dashboard_admin";

    public DashboardViewModel(Application application) {
        super(application);
        localCache = new LocalCache(application.getApplicationContext());
        restoreCount("users", totalUsers);
        restoreCount("posts", totalPosts);
        restoreCount("exchanges", totalExchanges);
        restoreCount("reports", pendingReports);
    }

    private void restoreCount(String key, MutableLiveData<Long> target) {
        String value = localCache.read(CACHE_SCOPE).get(key);
        if (value == null) return;
        try { target.setValue(Long.parseLong(value)); }
        catch (NumberFormatException ignored) { }
    }

    private void cacheCount(String key, Long value) {
        if (value != null) localCache.write(CACHE_SCOPE, key, String.valueOf(value));
    }
    private final MutableLiveData<Long> totalUsers = new MutableLiveData<>();
    private final MutableLiveData<Long> totalPosts = new MutableLiveData<>();
    private final MutableLiveData<Long> totalExchanges = new MutableLiveData<>();
    private final MutableLiveData<Long> pendingReports = new MutableLiveData<>();
    private ListenerRegistration userListener;
    private ListenerRegistration postListener;
    private ListenerRegistration exchangeListener;
    private ListenerRegistration reportListener;
    private boolean isListening = false;
    public LiveData<Long> getTotalUsers() {
        return totalUsers;
    }
    public LiveData<Long> getTotalPosts() {
        return totalPosts;
    }
    public LiveData<Long> getTotalExchanges() {
        return totalExchanges;
    }
    public LiveData<Long> getPendingReports() {
        return pendingReports;
    }

    public void startListening() {
        if (isListening) {
            return;
        }
        isListening = true;

        userListener = db.collection("accounts")
                .whereEqualTo("role", "USER")
                .addSnapshotListener((snapshot, error) -> {
                    if (!isListening) {
                        return;
                    }
                    if (error != null) {
                        Log.e(TAG, "Loi tai User", error);
                        return;
                    }
                    if (snapshot != null) {
                        long count = snapshot.size();
                        totalUsers.setValue(count);
                        cacheCount("users", count);
                    }
                });

        postListener = db.collection("posts")
                .addSnapshotListener((snapshot, error) -> {
                    if (!isListening) {
                        return;
                    }
                    if (error != null) {
                        Log.e(TAG, "Loi tai Post", error);
                        return;
                    }
                    if (snapshot != null) {
                        long count = snapshot.size();
                        totalPosts.setValue(count);
                        cacheCount("posts", count);
                    }
                });

        exchangeListener = db.collection("exchanges")
                .addSnapshotListener((snapshot, error) -> {
                    if (!isListening) {
                        return;
                    }
                    if (error != null) {
                        Log.e(TAG, "Loi tai Exchange", error);
                        return;
                    }
                    if (snapshot != null) {
                        long count = snapshot.size();
                        totalExchanges.setValue(count);
                        cacheCount("exchanges", count);
                    }
                });

        reportListener = db.collection("reports")
                .whereEqualTo("status", ReportStatus.PENDING.name())
                .addSnapshotListener((snapshot, error) -> {
                    if (!isListening) {
                        return;
                    }
                    if (error != null) {
                        Log.e(TAG, "Loi tai Report", error);
                        return;
                    }
                    if (snapshot != null) {
                        long count = snapshot.size();
                        pendingReports.setValue(count);
                        cacheCount("reports", count);
                    }
                });
    }

    public void stopListening() {
        isListening = false;
        if (userListener != null) {
            userListener.remove();
            userListener = null;
        }
        if (postListener != null) {
            postListener.remove();
            postListener = null;
        }
        if (exchangeListener != null) {
            exchangeListener.remove();
            exchangeListener = null;
        }
        if (reportListener != null) {
            reportListener.remove();
            reportListener = null;
        }
    }

    public void clearData() {
        stopListening();
        totalUsers.setValue(null);
        totalPosts.setValue(null);
        totalExchanges.setValue(null);
        pendingReports.setValue(null);
    }

    @Override
    protected void onCleared() {
        stopListening();
        super.onCleared();
    }
}
