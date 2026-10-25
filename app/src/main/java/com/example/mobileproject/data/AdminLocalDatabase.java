package com.example.mobileproject.data;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {ReportCacheEntity.AdminReportCacheEntity.class, ExchangeCacheEntity.class}, version = 3, exportSchema = false)
public abstract class AdminLocalDatabase extends RoomDatabase {
    private static volatile AdminLocalDatabase INSTANCE;
    public static final androidx.room.migration.Migration MIGRATION_2_3 = new androidx.room.migration.Migration(2,3) {
        @Override public void migrate(androidx.sqlite.db.SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE admin_exchange_cache ADD COLUMN walletManaged INTEGER NOT NULL DEFAULT 0");
        }
    };
    public abstract AdminCacheDao adminCacheDao();
    public static AdminLocalDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AdminLocalDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(), AdminLocalDatabase.class, "stayexchange_admin_cache.db").addMigrations(MIGRATION_2_3).fallbackToDestructiveMigration().build();
                }
            }
        }
        return INSTANCE;
    }
}
