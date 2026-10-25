package com.example.mobileproject.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.LinkedHashMap;
import java.util.Map;

public class ReportCacheEntity extends SQLiteOpenHelper {
    private static final String DB_NAME = "stayexchange_offline.db";
    private static final int DB_VERSION = 1;
    public ReportCacheEntity(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE cache_items (" +
                        "scope TEXT NOT NULL," +
                        "item_key TEXT NOT NULL," +
                        "payload TEXT NOT NULL," +
                        "updated_at INTEGER NOT NULL," +
                        "PRIMARY KEY(scope, item_key))");
    }
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Thêm migration khi tăng DB_VERSION.
    }
    public Map<String, String> readScope(String scope) {
        Map<String, String> result = new LinkedHashMap<>();
        try (Cursor cursor = getReadableDatabase().query(
                "cache_items",
                new String[]{"item_key", "payload"},
                "scope=?",
                new String[]{scope},
                null,
                null,
                "updated_at DESC"
        )) { while (cursor.moveToNext()) {result.put(cursor.getString(0), cursor.getString(1));}}
        return result;
    }

    public void replaceScope(String scope, Map<String, String> rows) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("cache_items", "scope=?", new String[]{scope});
            long now = System.currentTimeMillis();
            for (Map.Entry<String, String> entry : rows.entrySet()) {
                ContentValues values = new ContentValues();
                values.put("scope", scope);
                values.put("item_key", entry.getKey());
                values.put("payload", entry.getValue());
                values.put("updated_at", now);
                db.insertOrThrow("cache_items", null, values);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }
    @Entity(tableName = "admin_report_cache")
    public static class AdminReportCacheEntity {
        @PrimaryKey
        @NonNull
        public String reportId = "";
        public String reporterId = "";
        public String targetType = "";
        public String targetId = "";
        public String targetName = "";
        public String reason = "";
        public String description = "";
        public String status = "PENDING";
        public long updatedAt = 0L;
    }
}
