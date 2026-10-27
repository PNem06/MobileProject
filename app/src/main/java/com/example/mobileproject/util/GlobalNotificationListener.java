package com.example.mobileproject.util;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import com.example.mobileproject.notifications.Notice;
import com.example.mobileproject.notifications.NotificationBannerController;
import com.example.mobileproject.notifications.NotificationDeliveryStore;
import com.example.mobileproject.notifications.NotificationNavigator;
import com.example.mobileproject.notifications.NotificationSessionGate;
import com.example.mobileproject.view.Login;
import com.example.mobileproject.view.Register;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.Source;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public final class GlobalNotificationListener {
    private static NotificationBannerController banners;
    private static ListenerRegistration listener;
    private static String user = "";
    private static int generation;
    private static final NotificationSessionGate session = new NotificationSessionGate();
    private static WeakReference<Activity> foreground = new WeakReference<>(null);
    private GlobalNotificationListener() { }
    public static void init(Application app) {
        if (banners != null) return;
        banners = new NotificationBannerController(new NotificationDeliveryStore(app),NotificationNavigator::open);
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityResumed(Activity activity) {
                foreground = new WeakReference<>(activity);
                boolean allowed = !(activity instanceof Login) && !(activity instanceof Register);
                session.authenticationChanged(FirebaseAuth.getInstance().getUid());
                session.screenChanged(allowed);
                if (!allowed) session.clearValidation();
                synchronize();
                if (allowed && !session.validated()) restoreForegroundSession(activity);
            }
            @Override public void onActivityPaused(Activity activity) {
                banners.paused(activity);
                if (foreground.get() == activity) {
                    foreground.clear(); session.screenChanged(false); synchronize();
                }
            }
            @Override public void onActivityCreated(Activity a,Bundle state) {
                if (a instanceof Login || a instanceof Register) stopListening();
            }
            @Override public void onActivityStarted(Activity a) { }
            @Override public void onActivityStopped(Activity a) { }
            @Override public void onActivitySaveInstanceState(Activity a,Bundle state) { }
            @Override public void onActivityDestroyed(Activity a) { }
        });
        FirebaseAuth.getInstance().addAuthStateListener(auth -> {
            session.authenticationChanged(auth.getUid());
            synchronize();
        });
    }
    /** Called only after AuthService has accepted the current account's role and status. */
    public static void onLoginValidated(String uid) {
        session.authenticationChanged(FirebaseAuth.getInstance().getUid());
        session.loginValidated(uid);
        synchronize();
    }
    private static void restoreForegroundSession(Activity host) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        int token = generation;
        FirebaseFirestore.getInstance().collection("accounts").document(uid).get(Source.SERVER)
                .addOnSuccessListener(profile -> {
                    if (token != generation || foreground.get() != host || host.isFinishing()
                            || host.isDestroyed() || !uid.equals(FirebaseAuth.getInstance().getUid())) return;
                    String role = profile.getString("role");
                    if (profile.exists() && "ACTIVE".equals(profile.getString("status"))
                            && ("USER".equals(role) || "ADMIN".equals(role))) onLoginValidated(uid);
                }).addOnFailureListener(error -> {
                    // Keep notifications disabled if a restored account cannot be verified.
                });
    }
    private static void synchronize() {
        if (banners == null) return;
        String uid = session.activeUid();
        Activity host = foreground.get();
        if (uid.isEmpty() || host == null) { detach(); return; }
        startListening(uid);
        banners.resumed(host);
    }
    private static void startListening(String uid) {
        if (banners == null || (uid.equals(user) && listener != null)) return;
        detach(); user = uid; banners.switchUser(uid);
        final int token = ++generation;
        listener = FirebaseFirestore.getInstance().collection("notifications").whereEqualTo("userId",uid)
                .addSnapshotListener(MetadataChanges.INCLUDE,(snapshot,error) -> {
                    if (error != null || snapshot == null || token != generation
                            || !uid.equals(user) || !uid.equals(session.activeUid())
                            || !uid.equals(FirebaseAuth.getInstance().getUid())) return;
                    List<Notice> records = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments())
                        if (uid.equals(doc.getString("userId"))) records.add(new Notice(doc.getId(),doc.getData()));
                    boolean confirmed = !snapshot.getMetadata().isFromCache() && !snapshot.getMetadata().hasPendingWrites();
                    banners.snapshot(uid,records,confirmed);
                });
    }
    public static void stopListening() {
        session.clearValidation();
        detach();
    }
    private static void detach() {
        generation++;
        if (listener != null) listener.remove();
        listener = null; user = "";
        if (banners != null) banners.switchUser("");
    }
}
