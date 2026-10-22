package com.example.mobileproject;

import android.app.Application;

import com.example.mobileproject.util.GlobalNotificationListener;

public class MobileProjectApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        GlobalNotificationListener.init(this);
    }
}
