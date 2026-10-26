package com.example.mobileproject.util;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import com.example.mobileproject.notifications.Notice;
import com.example.mobileproject.notifications.NotificationBannerController;
import com.example.mobileproject.notifications.NotificationDeliveryStore;
import com.example.mobileproject.notifications.NotificationNavigator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import java.util.ArrayList;
import java.util.List;

public final class GlobalNotificationListener {
    private static NotificationBannerController banners;
    private static ListenerRegistration listener;
    private static String user = "";
    private static int generation;
    private GlobalNotificationListener() { }
    public static void init(Application app) {
        if (banners != null) return;
        banners = new NotificationBannerController(new NotificationDeliveryStore(app),NotificationNavigator::open);
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityResumed(Activity activity) { banners.resumed(activity); }
            @Override public void onActivityPaused(Activity activity) { banners.paused(activity); }
            @Override public void onActivityCreated(Activity a,Bundle state) { }
            @Override public void onActivityStarted(Activity a) { }
            @Override public void onActivityStopped(Activity a) { }
            @Override public void onActivitySaveInstanceState(Activity a,Bundle state) { }
            @Override public void onActivityDestroyed(Activity a) { }
        });
        FirebaseAuth.getInstance().addAuthStateListener(auth -> {
            if (auth.getCurrentUser() == null) stopListening();
            else startListening(auth.getCurrentUser().getUid());
        });
    }
    public static void startListening(String uid) {
        if (banners == null || (uid.equals(user) && listener != null)) return;
        stopListening(); user = uid; banners.switchUser(uid);
        final int token = ++generation;
        listener = FirebaseFirestore.getInstance().collection("notifications").whereEqualTo("userId",uid)
                .addSnapshotListener(MetadataChanges.INCLUDE,(snapshot,error) -> {
                    if (error != null || snapshot == null || token != generation || !uid.equals(user)) return;
                    List<Notice> records = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) records.add(new Notice(doc.getId(),doc.getData()));
                    boolean confirmed = !snapshot.getMetadata().isFromCache() && !snapshot.getMetadata().hasPendingWrites();
                    banners.snapshot(uid,records,confirmed);
                });
    }
    public static void stopListening() {
        generation++;
        if (listener != null) listener.remove();
        listener = null; user = "";
        if (banners != null) banners.switchUser("");
    }
}
