
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

    // ==========================================
    // 1. KHAI BAO FIREBASE
    // ==========================================

    private final FirebaseFirestore db =
            FirebaseFirestore.getInstance();

    // ==========================================
    // 2. KHAI BAO LIVEDATA
    // ==========================================

    private final MutableLiveData<Long> totalUsers =
            new MutableLiveData<>();

    private final MutableLiveData<Long> totalPosts =
            new MutableLiveData<>();

    private final MutableLiveData<Long> totalExchanges =
            new MutableLiveData<>();

    private final MutableLiveData<Long> pendingReports =
            new MutableLiveData<>();

    // ==========================================
    // 3. KHAI BAO FIREBASE LISTENER
    // ==========================================

    private ListenerRegistration userListener;
    private ListenerRegistration postListener;
    private ListenerRegistration exchangeListener;
    private ListenerRegistration reportListener;

    private boolean isListening = false;

    // ==========================================
    // 4. GETTER LIVEDATA
    // ==========================================

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

    // ==========================================
    // 5. BAT DAU LANG NGHE FIREBASE
    // ==========================================

    public void startListening() {

        // Tranh dang ky Listener trung
        if (isListening) {
            return;
        }

        isListening = true;

        // --------------------------------------
        // 5.1. TONG SO USER
        // --------------------------------------

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

                        totalUsers.setValue(
                                (long) snapshot.size()
                        );
                    }
                });

        // --------------------------------------
        // 5.2. TONG SO BAI DANG
        // --------------------------------------

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

                        totalPosts.setValue(
                                (long) snapshot.size()
                        );
                    }
                });

        // --------------------------------------
        // 5.3. TONG SO YEU CAU TRAO DOI
        // --------------------------------------

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

                        totalExchanges.setValue(
                                (long) snapshot.size()
                        );
                    }
                });

        // --------------------------------------
        // 5.4. REPORT DANG CHO XU LY
        // --------------------------------------

        reportListener = db.collection("reports")
                .whereEqualTo(
                        "status",
                        ReportStatus.PENDING.name()
                )
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

    // ==========================================
    // 6. HUY FIREBASE LISTENER
    // ==========================================

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

    // ==========================================
    // 7. XOA DU LIEU KHI CAN
    // ==========================================

    public void clearData() {

        stopListening();

        totalUsers.setValue(null);
        totalPosts.setValue(null);
        totalExchanges.setValue(null);
        pendingReports.setValue(null);
    }

    // ==========================================
    // 8. VIEWMODEL BI HUY HOAN TOAN
    // ==========================================

    @Override
    protected void onCleared() {

        stopListening();

        super.onCleared();
    }
}
