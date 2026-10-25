package com.example.mobileproject.data;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {ReportCacheEntity.AdminReportCacheEntity.class, ExchangeCacheEntity.class}, version = 2, exportSchema = false)
public abstract class AdminLocalDatabase extends RoomDatabase {
    private static volatile AdminLocalDatabase INSTANCE;
    public abstract AdminCacheDao adminCacheDao();
    public static AdminLocalDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AdminLocalDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(), AdminLocalDatabase.class, "stayexchange_admin_cache.db").fallbackToDestructiveMigration().build();
                }
            }
        }
        return INSTANCE;
    }
}
