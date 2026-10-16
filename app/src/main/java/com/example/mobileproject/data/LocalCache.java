package com.example.mobileproject.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.HashMap;
import java.util.Map;

public class LocalCache extends SQLiteOpenHelper {

    public LocalCache(Context context) {
        super(context, "long_home_cache.db", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE pages (" +
                        "scope TEXT NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "payload TEXT NOT NULL, " +
                        "PRIMARY KEY(scope,name))"
        );
    }

    @Override
    public void onUpgrade(
            SQLiteDatabase db,
            int oldVersion,
            int newVersion
    ) {
        // Thêm migration ở đây khi tăng phiên bản database.
    }

    public Map<String, String> read(String scope) {
        Map<String, String> result = new HashMap<>();

        try (Cursor c = getReadableDatabase().query(
                "pages",
                new String[]{"name", "payload"},
                "scope=?",
                new String[]{scope},
                null,
                null,
                null
        )) {
            while (c.moveToNext()) {
                result.put(c.getString(0), c.getString(1));
            }
        }

        return result;
    }

    public void write(
            String scope,
            String name,
            String payload
    ) {
        ContentValues values = new ContentValues();
        values.put("scope", scope);
        values.put("name", name);
        values.put("payload", payload);

        getWritableDatabase().insertWithOnConflict(
                "pages",
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
        );
    }

    public void clear(String scope) {
        getWritableDatabase().delete(
                "pages",
                "scope=?",
                new String[]{scope}
        );
    }
}