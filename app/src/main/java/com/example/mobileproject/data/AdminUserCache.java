package com.example.mobileproject.data;

import android.content.Context;

import com.example.mobileproject.model.AdminUserItem;
import com.example.mobileproject.model.AdminUserPostItem;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AdminUserCache {

    private final OfflineCache cache;

    public AdminUserCache(Context context) {
        cache = new OfflineCache(context.getApplicationContext());
    }

    public List<AdminUserItem> read(String uid) throws JSONException {
        String payload = cache.readScope(scope(uid)).get("users");

        if (payload == null) {
            return null;
        }

        JSONArray rows = new JSONArray(payload);
        List<AdminUserItem> result = new ArrayList<>();

        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            Map<String, Object> fields = new HashMap<>();

            for (String key : new String[]{
                    "accountId",
                    "fullName",
                    "username",
                    "email",
                    "phone",
                    "avatarUrl",
                    "status"
            }) {
                fields.put(key, row.optString(key, ""));
            }

            result.add(new AdminUserItem(
                    row.getString("documentId"),
                    fields,
                    row.optInt("postCount", -1)
            ));
        }

        return result;
    }

    public void write(
            String uid,
            List<AdminUserItem> users
    ) throws JSONException {
        JSONArray rows = new JSONArray();

        for (AdminUserItem user : users) {
            JSONObject row = new JSONObject();

            row.put("documentId", user.documentId);
            row.put("accountId", user.accountId);
            row.put("fullName", user.fullName);
            row.put("username", user.username);
            row.put("email", user.email);
            row.put("phone", user.phone);
            row.put("avatarUrl", user.avatarUrl);
            row.put("status", user.status);
            row.put("postCount", user.postCount);

            rows.put(row);
        }

        cache.replaceScope(
                scope(uid),
                Collections.singletonMap("users", rows.toString())
        );
    }

    public List<AdminUserPostItem> readPosts(String uid) throws JSONException {
        String payload = cache.readScope("admin_user_posts:" + uid)
                .get("posts");

        if (payload == null) {
            return null;
        }

        JSONArray rows = new JSONArray(payload);
        List<AdminUserPostItem> result = new ArrayList<>();

        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            Map<String, Object> fields = new HashMap<>();

            java.util.Iterator<String> keys = row.keys();

            while (keys.hasNext()) {
                String key = keys.next();
                fields.put(key, row.get(key));
            }

            result.add(new AdminUserPostItem(
                    row.getString("documentId"),
                    fields
            ));
        }

        return result;
    }

    public void writePosts(
            String uid,
            List<AdminUserPostItem> posts
    ) throws JSONException {
        JSONArray rows = new JSONArray();

        for (AdminUserPostItem post : posts) {
            JSONObject row = new JSONObject(post.fields());
            row.put("documentId", post.documentId);
            rows.put(row);
        }

        cache.replaceScope(
                "admin_user_posts:" + uid,
                Collections.singletonMap("posts", rows.toString())
        );
    }

    public void clear(String uid) {
        cache.replaceScope(scope(uid), Collections.emptyMap());

        cache.replaceScope(
                "admin_user_posts:" + uid,
                Collections.emptyMap()
        );
    }

    public void close() {
        cache.close();
    }

    private String scope(String uid) {
        return "admin_users:" + uid;
    }
}