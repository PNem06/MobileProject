package com.example.mobileproject.notifications;

import android.content.Context;
import android.content.SharedPreferences;

/** Delivery is separate from read status and is retained across app restarts per user/device. */
public final class NotificationDeliveryStore {
    private final SharedPreferences prefs;
    public NotificationDeliveryStore(Context context) {
        prefs = context.getSharedPreferences("com.example.mobileproject.notification_delivery_v1", Context.MODE_PRIVATE);
    }
    private String key(String uid, String id) { return uid.length() + ":" + uid + ":" + id; }
    public boolean shown(String uid, String id) { return prefs.getBoolean(key(uid, id), false); }
    public synchronized boolean claim(String uid, String id) {
        if (uid.isEmpty() || id.isEmpty() || shown(uid, id)) return false;
        prefs.edit().putBoolean(key(uid, id), true).apply();
        return true;
    }
    public void release(String uid, String id) { prefs.edit().remove(key(uid, id)).apply(); }
}
