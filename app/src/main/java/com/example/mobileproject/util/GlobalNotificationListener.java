package com.example.mobileproject.util;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.mobileproject.view.Detail;
import com.example.mobileproject.view.WalletActivity;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.lang.ref.WeakReference;

public class GlobalNotificationListener {

    private static WeakReference<Activity> currentActivityRef = new WeakReference<>(null);
    private static ListenerRegistration notifListener = null;

    public static void init(Application app) {
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityResumed(@NonNull Activity activity) {
                currentActivityRef = new WeakReference<>(activity);
            }

            @Override
            public void onActivityPaused(@NonNull Activity activity) {
                if (currentActivityRef.get() == activity) {
                    currentActivityRef = new WeakReference<>(null);
                }
            }

            @Override public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {}
            @Override public void onActivityStarted(@NonNull Activity activity) {}
            @Override public void onActivityStopped(@NonNull Activity activity) {}
            @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}
            @Override public void onActivityDestroyed(@NonNull Activity activity) {}
        });

        FirebaseAuth.getInstance().addAuthStateListener(auth -> {
            FirebaseUser user = auth.getCurrentUser();
            if (user != null) {
                startListening(user.getUid());
            } else {
                stopListening();
            }
        });
    }

    public static void startListening(String uid) {
        stopListening();
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        notifListener = db.collection("notifications")
                .whereEqualTo("userId", uid)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null || snapshot == null) return;
                    if (snapshot.getMetadata().isFromCache()) return;

                    for (DocumentChange dc : snapshot.getDocumentChanges()) {
                        if (dc.getType() == DocumentChange.Type.ADDED) {
                            DocumentSnapshot doc = dc.getDocument();
                            Boolean isRead = doc.getBoolean("isRead");
                            if (isRead != null && !isRead) {
                                String title = doc.getString("title");
                                String body = doc.getString("body");
                                if (title == null || title.isEmpty()) {
                                    title = doc.getString("message");
                                }
                                String postId = doc.getString("postId");
                                if (postId == null || postId.isEmpty()) {
                                    postId = doc.getString("targetPostId");
                                }
                                if (postId == null || postId.isEmpty()) {
                                    postId = doc.getString("offeredPostId");
                                }

                                if (title != null && !title.isEmpty()) {
                                    showSlideDownBannerOnCurrentActivity(title, body, postId, Boolean.TRUE.equals(doc.getBoolean("walletManaged")), doc.getString("exchangeId"), doc.getId());
                                }
                            }
                        }
                    }
                });
    }

    public static void stopListening() {
        if (notifListener != null) {
            notifListener.remove();
            notifListener = null;
        }
    }

    private static void showSlideDownBannerOnCurrentActivity(String title, String body, String postId, boolean walletManaged, String exchangeId, String notificationId) {
        Activity act = currentActivityRef.get();
        if (act == null || act.isFinishing() || act.isDestroyed()) return;

        act.runOnUiThread(() -> {
            try {
                ViewGroup rootView = act.findViewById(android.R.id.content);
                if (rootView == null) return;

                // Card container màu trắng nổi lên trên
                MaterialCardView card = new MaterialCardView(act);
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                params.setMargins(dpToPx(act, 16), dpToPx(act, 12), dpToPx(act, 16), 0);
                params.gravity = Gravity.TOP;
                card.setLayoutParams(params);
                card.setCardBackgroundColor(Color.WHITE);
                card.setRadius(dpToPx(act, 16));
                card.setCardElevation(dpToPx(act, 10));
                card.setStrokeWidth(0);

                LinearLayout contentLayout = new LinearLayout(act);
                contentLayout.setOrientation(LinearLayout.VERTICAL);
                contentLayout.setPadding(dpToPx(act, 16), dpToPx(act, 12), dpToPx(act, 16), dpToPx(act, 12));

                TextView txtTitle = new TextView(act);
                txtTitle.setText("🔔 Bạn có thông báo mới: " + title);
                txtTitle.setTextColor(Color.parseColor("#111111"));
                txtTitle.setTextSize(14);
                txtTitle.setTypeface(null, Typeface.BOLD);

                contentLayout.addView(txtTitle);

                if (body != null && !body.trim().isEmpty()) {
                    TextView txtBody = new TextView(act);
                    txtBody.setText(body);
                    txtBody.setTextColor(Color.parseColor("#555555"));
                    txtBody.setTextSize(12);
                    txtBody.setPadding(0, dpToPx(act, 4), 0, 0);
                    contentLayout.addView(txtBody);
                }

                card.addView(contentLayout);

                // Đưa thẻ lên vị trí khuất ở trên màn hình
                card.setTranslationY(-dpToPx(act, 130));
                rootView.addView(card);

                // Hiệu ứng trượt từ trên xuống
                card.animate()
                        .translationY(0)
                        .setDuration(400)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();

                // Bấm vào banner để mở thẳng bài viết detail.xml
                card.setOnClickListener(v -> {
                    if (walletManaged) {
                        Intent intent = new Intent(act, WalletActivity.class);
                        if (exchangeId != null && !exchangeId.isEmpty()) intent.putExtra(WalletActivity.EXTRA_BOOKING_ID, exchangeId);
                        act.startActivity(intent);
                        FirebaseFirestore.getInstance().collection("notifications").document(notificationId).update("isRead", true);
                    } else if (postId != null && !postId.trim().isEmpty()) {
                        try {
                            Intent intent = new Intent(act, Detail.class);
                            intent.putExtra(Detail.EXTRA_POST_ID, postId);
                            intent.putExtra("postId", postId);
                            act.startActivity(intent);
                        } catch (Exception ignored) {}
                    }
                    dismissBanner(card, rootView, act);
                });

                // Tự động thu banner lướt lên trên sau 3.5 giây
                card.postDelayed(() -> dismissBanner(card, rootView, act), 3500);

            } catch (Exception ignored) {}
        });
    }

    private static void dismissBanner(View banner, ViewGroup parent, Activity act) {
        if (banner == null || parent == null || act == null) return;
        banner.animate()
                .translationY(-dpToPx(act, 130))
                .setDuration(300)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> {
                    try {
                        parent.removeView(banner);
                    } catch (Exception ignored) {}
                })
                .start();
    }

    private static int dpToPx(Activity act, int dp) {
        float density = act.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
