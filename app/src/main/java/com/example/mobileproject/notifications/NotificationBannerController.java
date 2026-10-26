package com.example.mobileproject.notifications;

import android.app.Activity;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.card.MaterialCardView;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/** One foreground banner at a time. Merely displaying it never marks a notice as read. */
public final class NotificationBannerController {
    public interface OpenNotice { void open(Activity activity, Notice notice); }
    public static final String BANNER_TAG = "stayexchange-notification-banner";
    private final NotificationDeliveryStore delivered;
    private final OpenNotice opener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final LinkedHashMap<String,Notice> pending = new LinkedHashMap<>();
    private WeakReference<Activity> activity = new WeakReference<>(null);
    private String uid = "";
    private View banner;
    private Notice current;
    private final Runnable timeout = () -> { dismiss(); showNext(); };

    public NotificationBannerController(NotificationDeliveryStore delivered, OpenNotice opener) {
        this.delivered = delivered; this.opener = opener;
    }
    public void switchUser(String user) {
        if (uid.equals(user)) return;
        dismiss(); pending.clear(); uid = user;
    }
    public void resumed(Activity host) { activity = new WeakReference<>(host); showNext(); }
    public void paused(Activity host) {
        if (activity.get() == host) { dismiss(); activity.clear(); }
    }
    public void snapshot(String user, List<Notice> notices, boolean confirmed) {
        if (!uid.equals(user) || uid.isEmpty()) return;
        Set<String> unread = new HashSet<>(), read = new HashSet<>();
        for (Notice notice : notices) { if (notice.read()) read.add(notice.id); else unread.add(notice.id); }
        pending.entrySet().removeIf(entry -> read.contains(entry.getKey()) || (confirmed && !unread.contains(entry.getKey())));
        if (current != null && (read.contains(current.id) || (confirmed && !unread.contains(current.id)))) dismiss();
        if (confirmed) {
            List<Notice> sorted = new ArrayList<>(notices);
            sorted.sort((a,b) -> Long.compare(b.createdAt(), a.createdAt()));
            for (Notice notice : sorted) if (!notice.read() && !notice.id.isEmpty() && !delivered.shown(uid, notice.id)) pending.put(notice.id, notice);
        }
        showNext();
    }
    private void showNext() {
        Activity host = activity.get();
        if (banner != null || host == null || host.isFinishing() || host.isDestroyed() || uid.isEmpty()) return;
        ViewGroup root = host.findViewById(android.R.id.content);
        if (root == null) return;
        while (!pending.isEmpty()) {
            Notice notice = pending.remove(pending.keySet().iterator().next());
            if (!delivered.claim(uid, notice.id)) continue;
            try {
                MaterialCardView card = new MaterialCardView(host);
                card.setTag(BANNER_TAG); card.setCardBackgroundColor(Color.WHITE);
                card.setRadius(dp(host,16)); card.setCardElevation(dp(host,8));
                FrameLayout.LayoutParams layout = new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);
                WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(root);
                int top = insets == null ? 0 : insets.getInsets(WindowInsetsCompat.Type.systemBars()).top;
                layout.setMargins(dp(host,12),top+dp(host,12),dp(host,12),0);
                LinearLayout content = new LinearLayout(host); content.setOrientation(LinearLayout.VERTICAL);
                content.setPadding(dp(host,16),dp(host,12),dp(host,16),dp(host,12));
                TextView title = new TextView(host); title.setText(notice.title()); title.setTextColor(Color.BLACK);
                title.setTypeface(null,android.graphics.Typeface.BOLD); title.setTextSize(15); title.setMaxLines(2); content.addView(title);
                if (!notice.body().isEmpty()) { TextView body = new TextView(host); body.setText(notice.body()); body.setTextColor(Color.DKGRAY); body.setMaxLines(3); content.addView(body); }
                card.addView(content); root.addView(card,layout); banner = card; current = notice;
                card.setOnClickListener(v -> { dismiss(); opener.open(host,notice); });
                handler.postDelayed(timeout,3500);
                return;
            } catch (RuntimeException error) { delivered.release(uid,notice.id); pending.put(notice.id,notice); return; }
        }
    }
    private int dp(Activity host,int value) { return Math.round(value*host.getResources().getDisplayMetrics().density); }
    private void dismiss() {
        handler.removeCallbacks(timeout);
        if (banner != null && banner.getParent() instanceof ViewGroup) ((ViewGroup)banner.getParent()).removeView(banner);
        banner = null; current = null;
    }
}
