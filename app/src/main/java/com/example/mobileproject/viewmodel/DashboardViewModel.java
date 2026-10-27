package com.example.mobileproject.viewmodel;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.mobileproject.model.ReportStatus;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

public class DashboardViewModel extends ViewModel {
    private static final String TAG = "DashboardViewModel";
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
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
                        totalUsers.setValue((long) snapshot.size());
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
                        totalPosts.setValue((long) snapshot.size());
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
                        totalExchanges.setValue((long) snapshot.size());
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
                        pendingReports.setValue(
                                (long) snapshot.size()
                        );
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
