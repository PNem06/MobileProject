package com.example.mobileproject.data;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {ReportCacheEntity.AdminReportCacheEntity.class, ExchangeCacheEntity.class}, version = 4, exportSchema = false)
public abstract class AdminLocalDatabase extends RoomDatabase {
    private static volatile AdminLocalDatabase INSTANCE;

    private static boolean columnExists(androidx.sqlite.db.SupportSQLiteDatabase db, String tableName, String columnName) {
        try (android.database.Cursor cursor = db.query("PRAGMA table_info(" + tableName + ")")) {
            int nameIndex = cursor.getColumnIndex("name");
            if (nameIndex != -1) {
                while (cursor.moveToNext()) {
                    if (columnName.equalsIgnoreCase(cursor.getString(nameIndex))) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            // Ignored
        }
        return false;
    }

    public static final androidx.room.migration.Migration MIGRATION_2_3 = new androidx.room.migration.Migration(2,3) {
        @Override public void migrate(@androidx.annotation.NonNull androidx.sqlite.db.SupportSQLiteDatabase db) {
            if (!columnExists(db, "admin_exchange_cache", "walletManaged")) {
                try {
                    db.execSQL("ALTER TABLE admin_exchange_cache ADD COLUMN walletManaged INTEGER NOT NULL DEFAULT 0");
                } catch (Exception e) {
                    // Ignored if column already exists
                }
            }
        }
    };

    public static final androidx.room.migration.Migration MIGRATION_3_4 =
            new androidx.room.migration.Migration(3, 4) {
                @Override
                public void migrate(
                        @androidx.annotation.NonNull androidx.sqlite.db.SupportSQLiteDatabase db) {

                    if (!columnExists(db, "admin_report_cache", "reporterName")) {
                        try {
                            db.execSQL("ALTER TABLE admin_report_cache ADD COLUMN reporterName TEXT");
                        } catch (Exception e) {
                            // Ignored if column already exists
                        }
                    }

                    if (!columnExists(db, "admin_report_cache", "targetType")) {
                        try {
                            db.execSQL("ALTER TABLE admin_report_cache ADD COLUMN targetType TEXT");
                        } catch (Exception e) {
                            // Ignored if column already exists
                        }
                    }
                }
            };

    public abstract AdminCacheDao adminCacheDao();
    public static AdminLocalDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AdminLocalDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(), AdminLocalDatabase.class, "stayexchange_admin_cache.db")
                            .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
