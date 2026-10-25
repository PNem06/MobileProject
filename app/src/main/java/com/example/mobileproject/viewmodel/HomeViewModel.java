package com.example.mobileproject.viewmodel;

import android.app.Application;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.example.mobileproject.data.HomePostMapper;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.Query;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class HomeViewModel extends AndroidViewModel {
    public final MutableLiveData<Integer> changed = new MutableLiveData<>(0);
    public final MutableLiveData<String> event = new MutableLiveData<>();
    public String page = "home", search = "", draftName, draftPhone, draftAvatar;
    public int scrollY;
    public boolean busy, uiInitialized;
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final Map<String, JSONArray> data = new HashMap<>();
    private final List<ListenerRegistration> listeners = new ArrayList<>();
    private final Map<String, ListenerRegistration> imageListeners = new HashMap<>();
    private final Map<String, ListenerRegistration> ownerListeners = new HashMap<>();
    private final Map<String, JSONObject> ownerProfiles = new HashMap<>();
    private final Set<String> favoritePosts = new HashSet<>();
    private final Map<String, Boolean> pendingFavorites = new HashMap<>();
    private boolean favoritesReady;
    private boolean warnedOwnerRead;
    private String uid = "", lastError = "";
    private boolean running, cleared, active;
    private int generation;

    public HomeViewModel(Application app) {
        super(app);
        // Dọn database SQLite của phiên bản Home cũ.
        app.deleteDatabase("long_home_cache.db");
    }

    public JSONArray rows(String key) {
        JSONArray result = data.get(key);
        return result == null ? new JSONArray() : result;
    }

    public JSONObject profile() {
        JSONObject p = rows("profile").optJSONObject(0);
        return p == null ? new JSONObject() : p;
    }

    public boolean isFavorite(String postId) {
        Boolean pending = pendingFavorites.get(postId);
        return pending == null ? favoritePosts.contains(postId) : pending;
    }

    public Set<String> favoriteIds() {
        Set<String> result = new HashSet<>(favoritePosts);
        for (Map.Entry<String, Boolean> entry : pendingFavorites.entrySet()) {
            if (entry.getValue()) result.add(entry.getKey());
            else result.remove(entry.getKey());
        }
        return result;
    }

    public boolean favoritePending(String postId) {
        return pendingFavorites.containsKey(postId);
    }

    public boolean favoritesLoaded() {
        return favoritesReady;
    }

    public void toggleFavorite(String postId) {
        FirebaseUser user = auth.getCurrentUser();
        if (busy || user == null || postId == null || postId.isEmpty()) return;
        if (!online()) {
            tell("Kết nối mạng để cập nhật yêu thích.");
            return;
        }
        if (!running || !user.getUid().equals(uid) || !favoritesReady) {
            tell("Danh sách yêu thích chưa sẵn sàng. Vui lòng thử lại sau.");
            return;
        }
        if (favoritePending(postId)) return;
        String userId = uid;
        int token = generation;
        boolean next = !favoritePosts.contains(postId);
        pendingFavorites.put(postId, next);
        refreshUi();
        com.google.firebase.firestore.DocumentReference ref =
                db.collection("favorites").document(userId + "_" + postId);
        com.google.android.gms.tasks.Task<Void> operation;
        if (next) {
            Map<String, Object> values = new HashMap<>();
            values.put("favoriteId", userId + "_" + postId);
            values.put("userId", userId);
            values.put("postId", postId);
            values.put("createdAt", FieldValue.serverTimestamp());
            operation = ref.set(values);
        } else operation = ref.delete();
        operation.addOnCompleteListener(
                task -> {
                    finishFavorite(
                            postId, next, userId, token, task.isSuccessful(), task.getException());
                });
    }

    private void finishFavorite(
            String postId,
            boolean next,
            String userId,
            int token,
            boolean success,
            Exception failure) {
        if (!running || token != generation || !userId.equals(uid)) return;
        pendingFavorites.remove(postId);
        if (success) {
            if (next) favoritePosts.add(postId);
            else favoritePosts.remove(postId);
        } else {
            tell(
                    "Không lưu được yêu thích: "
                            + (failure == null ? "Vui lòng thử lại." : failure.getMessage()));
        }
        refreshUi();
    }

    public String image(String postId) {
        // Ảnh theo code đăng bài hiện tại được ưu tiên; hỗ trợ dữ liệu cũ.
        for (String source : new String[] {"images", "postImages"}) {
            JSONArray photos = rows("photos");
            for (int i = 0; i < photos.length(); i++) {
                JSONObject photo = photos.optJSONObject(i);
                if (photo != null
                        && postId.equals(photo.optString("id"))
                        && source.equals(photo.optString("source"))
                        && !photo.optString("url").isEmpty()) return photo.optString("url");
            }
        }
        return "";
    }

    public boolean signedIn() {
        return auth.getCurrentUser() != null;
    }

    public boolean online() {
        ConnectivityManager cm = getApplication().getSystemService(ConnectivityManager.class);
        NetworkCapabilities caps =
                cm == null ? null : cm.getNetworkCapabilities(cm.getActiveNetwork());
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    public void refreshUi() {
        Integer n = changed.getValue();
        changed.setValue(n == null ? 1 : n + 1);
    }

    public void tell(String message) {
        event.setValue(message);
    }

    private void error(String message) {
        if (!message.equals(lastError)) tell(message);
        lastError = message;
    }

    public void setActive(boolean value) {
        active = value;
        if (value) start();
        else stop();
    }

    public void onConnectionChanged() {
        if (!active || cleared) return;
        if (!online()) {
            stop();
            clearData();
            refreshUi();
        } else if (!running) start();
    }

    private void start() {
        if (running || cleared || !active) return;
        if (!online()) {
            clearData();
            refreshUi();
            return;
        }
        running = true;
        int token = ++generation;
        FirebaseUser user = auth.getCurrentUser();
        String nextUid = user == null ? "" : user.getUid();
        if (!nextUid.equals(uid)) {
            clearData();
            draftName = draftPhone = draftAvatar = null;
            scrollY = 0;
        }
        uid = nextUid;
        lastError = "";
        warnedOwnerRead = false;
        refreshUi();
        attach(token);
    }

    private void attach(int token) {
        watch(db.collection("posts").whereEqualTo("status", "APPROVED").limit(50), "feedPublic", token);
        if (!uid.isEmpty()) {
            watch(db.collection("posts").whereArrayContains("bookingPartyIds", uid), "involvedPosts", token);
            listeners.add(
                    db.collection("accounts")
                            .document(uid)
                            .addSnapshotListener(
                                    MetadataChanges.INCLUDE,
                                    (doc, e) -> {
                                        if (!running || token != generation || !online()) return;
                                        if (e != null) {
                                            error("Không đọc được hồ sơ: " + e.getMessage());
                                            return;
                                        }
                                        if (doc == null || doc.getMetadata().isFromCache()) return;
                                        if (!doc.exists()
                                                || !"ACTIVE".equals(doc.getString("status"))) {
                                            logout();
                                            tell(
                                                    "Tài khoản không còn hoạt động. Vui lòng đăng"
                                                            + " nhập lại.");
                                            return;
                                        }
                                        store("profile", new JSONArray().put(convert(doc)));
                                    }));
            watch(db.collection("posts").whereEqualTo("accountId", uid), "mineCurrent", token);
            watch(db.collection("posts").whereEqualTo("ownerId", uid), "mineLegacy", token);
            watch(db.collection("notifications").whereEqualTo("userId", uid), "notices", token);
            watchFavorites(token);
        }
        watchImages(token);
    }

    private void watch(Query query, String key, int token) {
        listeners.add(
                query.addSnapshotListener(
                        MetadataChanges.INCLUDE,
                        (snap, e) -> {
                            if (!running || token != generation || !online()) return;
                            if (e != null) {
                                error("Không đồng bộ được " + key + ": " + e.getMessage());
                                return;
                            }
                            // Chỉ hiển thị snapshot đã được xác nhận từ máy chủ.
                            if (snap == null || snap.getMetadata().isFromCache()) return;
                            List<JSONObject> items = new ArrayList<>();
                            for (DocumentSnapshot doc : snap.getDocuments())
                                items.add(convert(doc));
                            if ("notices".equals(key)) {
                                items.sort(
                                        (a, b) ->
                                                Long.compare(
                                                        b.optLong("createdAt"),
                                                        a.optLong("createdAt")));
                                if (!snap.getMetadata().hasPendingWrites()) {
                                    for (DocumentChange dc : snap.getDocumentChanges()) {
                                        if (dc.getType() == DocumentChange.Type.ADDED) {
                                            DocumentSnapshot newDoc = dc.getDocument();
                                            Boolean isRead = newDoc.getBoolean("isRead");
                                            if (isRead != null && !isRead) {
                                                String notifTitle = newDoc.getString("title");
                                                String notifBody = newDoc.getString("body");
                                                if (notifTitle != null && !notifTitle.isEmpty()) {
                                                    tell("🔔 " + notifTitle + (notifBody != null && !notifBody.isEmpty() ? "\n" + notifBody : ""));
                                                }
                                            }
                                        }
                                    }
                                }
                            } else items.sort(Comparator.comparing(a -> a.optString("title")));
                            JSONArray array = new JSONArray();
                            for (JSONObject item : items) array.put(item);
                            if ("mineCurrent".equals(key) || "mineLegacy".equals(key)) {
                                data.put(key, array);
                                store(
                                        "mine",
                                        HomePostMapper.mergeMine(
                                                rows("mineCurrent"), rows("mineLegacy"), uid));
                            } else if ("feedPublic".equals(key) || "involvedPosts".equals(key)) {
                                data.put(key,array);
                                java.util.Map<String,JSONObject> merged = new java.util.LinkedHashMap<>();
                                for (String source : new String[]{"feedPublic","involvedPosts"}) {
                                    JSONArray values = rows(source);
                                    for(int i=0;i<values.length();i++) {
                                        JSONObject post=values.optJSONObject(i);
                                        if(post==null || ("involvedPosts".equals(source) && !"BOOKED".equals(post.optString("status")))) continue;
                                        merged.put(post.optString("id"),post);
                                    }
                                }
                                JSONArray combined=new JSONArray(); for(JSONObject post:merged.values()) combined.put(post);
                                store("feed",combined);
                            } else store(key,array);
                            if (!"notices".equals(key)) {
                                watchImages(token);
                                watchOwners(token);
                            }
                        }));
    }

    private JSONObject convert(DocumentSnapshot doc) {
        return HomePostMapper.fromDocument(doc.getId(), doc.getData());
    }

    private void watchFavorites(int token) {
        String userId = uid;
        listeners.add(
                db.collection("favorites")
                        .whereEqualTo("userId", userId)
                        .addSnapshotListener(
                                MetadataChanges.INCLUDE,
                                (snap, failure) -> {
                                    if (!running
                                            || token != generation
                                            || !userId.equals(uid)
                                            || !online()) return;
                                    if (failure != null) {
                                        favoritesReady = false;
                                        error("Không đọc được yêu thích: " + failure.getMessage());
                                        refreshUi();
                                        return;
                                    }
                                    if (snap == null
                                            || snap.getMetadata().isFromCache()
                                            || snap.getMetadata().hasPendingWrites()) return;
                                    favoritePosts.clear();
                                    for (DocumentSnapshot doc : snap.getDocuments()) {
                                        Object value = doc.get("postId");
                                        if (value instanceof String && !((String) value).isEmpty())
                                            favoritePosts.add((String) value);
                                    }
                                    favoritesReady = true;
                                    refreshUi();
                                }));
    }

    private void watchImages(int token) {
        java.util.Set<String> needed = new java.util.HashSet<>();
        for (String key : new String[] {"feed", "mine"}) {
            JSONArray posts = rows(key);
            for (int i = 0; i < posts.length(); i++) {
                JSONObject post = posts.optJSONObject(i);
                if (post != null) needed.add(post.optString("id"));
            }
        }
        java.util.Iterator<Map.Entry<String, ListenerRegistration>> iterator =
                imageListeners.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, ListenerRegistration> entry = iterator.next();
            String postId = entry.getKey().substring(entry.getKey().indexOf('/') + 1);
            if (!needed.contains(postId)) {
                entry.getValue().remove();
                iterator.remove();
            }
        }
        JSONArray retained = new JSONArray();
        for (int i = 0; i < rows("photos").length(); i++) {
            JSONObject photo = rows("photos").optJSONObject(i);
            if (photo != null && needed.contains(photo.optString("id"))) retained.put(photo);
        }
        data.put("photos", retained);
        for (String postId : needed) {
            for (String source : new String[] {"images", "postImages"}) {
                String listenerKey = source + "/" + postId;
                if (imageListeners.containsKey(listenerKey)) continue;
                ListenerRegistration listener =
                        db.collection(source)
                                .whereEqualTo("postId", postId)
                                .addSnapshotListener(
                                        MetadataChanges.INCLUDE,
                                        (snap, error) -> {
                                            if (!running
                                                    || token != generation
                                                    || !online()
                                                    || !imageListeners.containsKey(listenerKey))
                                                return;
                                            if (error != null) {
                                                // Quyền collection cũ có thể bị bỏ sau khi nhóm đổi
                                                // cấu trúc.
                                                if ("images".equals(source))
                                                    error(
                                                            "Không tải được ảnh bài đăng: "
                                                                    + error.getMessage());
                                                return;
                                            }
                                            if (snap == null || snap.getMetadata().isFromCache())
                                                return;
                                            long best = Long.MAX_VALUE;
                                            String url = "", bestId = "";
                                            for (DocumentSnapshot doc : snap.getDocuments()) {
                                                String candidate =
                                                        HomePostMapper.imageUrl(doc.getData());
                                                if (candidate.isEmpty()) continue;
                                                Object value = doc.get("orderIndex");
                                                long order =
                                                        value instanceof Number
                                                                ? ((Number) value).longValue()
                                                                : Long.MAX_VALUE - 1;
                                                if (order < best
                                                        || (order == best
                                                        && doc.getId().compareTo(bestId)
                                                        < 0)) {
                                                    best = order;
                                                    bestId = doc.getId();
                                                    url = candidate;
                                                }
                                            }
                                            JSONArray photos = new JSONArray();
                                            for (int i = 0; i < rows("photos").length(); i++) {
                                                JSONObject photo = rows("photos").optJSONObject(i);
                                                if (photo != null
                                                        && !(postId.equals(photo.optString("id"))
                                                        && source.equals(
                                                        photo.optString("source"))))
                                                    photos.put(photo);
                                            }
                                            try {
                                                photos.put(
                                                        new JSONObject()
                                                                .put("id", postId)
                                                                .put("source", source)
                                                                .put("url", url));
                                            } catch (JSONException ignored) {
                                            }
                                            store("photos", photos);
                                        });
                imageListeners.put(listenerKey, listener);
            }
        }
    }

    private void store(String key, JSONArray value) {
        if (!online()) return;
        data.put(key, value);
        refreshUi();
    }

    public JSONObject owner(JSONObject post) {
        String ownerId = post.optString("ownerId");
        if (!ownerId.isEmpty() && ownerId.equals(uid)) return profile();
        JSONObject owner = ownerProfiles.get(ownerId);
        return owner == null ? new JSONObject() : owner;
    }

    private void clearData() {
        data.clear();
        ownerProfiles.clear();
        favoritePosts.clear();
        pendingFavorites.clear();
        favoritesReady = false;
    }

    private void watchOwners(int token) {
        java.util.Set<String> needed = new java.util.HashSet<>();
        for (String key : new String[] {"feed", "mine"}) {
            JSONArray posts = rows(key);
            for (int i = 0; i < posts.length(); i++) {
                JSONObject post = posts.optJSONObject(i);
                if (post == null) continue;
                String ownerId = post.optString("ownerId");
                if (!ownerId.isEmpty() && !ownerId.equals(uid)) needed.add(ownerId);
            }
        }
        java.util.Iterator<Map.Entry<String, ListenerRegistration>> iterator =
                ownerListeners.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, ListenerRegistration> entry = iterator.next();
            if (!needed.contains(entry.getKey())) {
                entry.getValue().remove();
                ownerProfiles.remove(entry.getKey());
                iterator.remove();
            }
        }
        for (String ownerId : needed) {
            if (ownerListeners.containsKey(ownerId)) continue;
            ListenerRegistration listener =
                    db.collection("accounts")
                            .document(ownerId)
                            .addSnapshotListener(
                                    MetadataChanges.INCLUDE,
                                    (doc, e) -> {
                                        if (!running || token != generation || !online()) return;
                                        if (e != null) {
                                            ownerProfiles.remove(ownerId);
                                            refreshUi();
                                            if (!warnedOwnerRead) {
                                                warnedOwnerRead = true;
                                                tell(
                                                        "Chưa đọc được avatar chủ nhà. Đang hiển"
                                                                + " thị chữ viết tắt.");
                                            }
                                            return;
                                        }
                                        if (doc == null || doc.getMetadata().isFromCache()) return;
                                        JSONObject owner = new JSONObject();
                                        if (doc.exists()) {
                                            try {
                                                // Chỉ lấy tên và avatar để hiển thị trên thẻ phòng.
                                                for (String key :
                                                        new String[] {"fullName", "avatarUrl"}) {
                                                    Object value = doc.get(key);
                                                    owner.put(
                                                            key,
                                                            value instanceof String ? value : "");
                                                }
                                            } catch (JSONException ignored) {
                                            }
                                        }
                                        ownerProfiles.put(ownerId, owner);
                                        refreshUi();
                                    });
            ownerListeners.put(ownerId, listener);
        }
    }

    public void stop() {
        running = false;
        generation++;
        pendingFavorites.clear();
        favoritesReady = false;
        for (ListenerRegistration listener : listeners) listener.remove();
        for (ListenerRegistration listener : imageListeners.values()) listener.remove();
        for (ListenerRegistration listener : ownerListeners.values()) listener.remove();
        ownerListeners.clear();
        listeners.clear();
        imageListeners.clear();
    }

    public void markRead(JSONObject notice) {
        if (!signedIn() || !online()) {
            tell("Cần kết nối mạng để cập nhật thông báo.");
            return;
        }
        db.collection("notifications")
                .document(notice.optString("id"))
                .update(notice.optString("readField", "read"), true)
                .addOnFailureListener(e -> tell("Không đánh dấu được: " + e.getMessage()));
    }

    public void saveProfile() {
        FirebaseUser user = auth.getCurrentUser();
        if (busy || user == null) return;
        if (!online()) {
            tell("Kết nối mạng để lưu thay đổi.");
            return;
        }
        String name = draftName == null ? "" : draftName.trim();
        String phone = draftPhone == null ? "" : draftPhone.trim();
        if (name.isEmpty() || name.length() > 100) {
            tell("Họ tên cần từ 1 đến 100 ký tự.");
            return;
        }
        if (!phone.isEmpty() && !phone.matches("[+0-9 ()-]{8,20}")) {
            tell("Số điện thoại không hợp lệ.");
            return;
        }
        busy = true;
        refreshUi();
        Map<String, Object> values = new HashMap<>();
        values.put("fullName", name);
        values.put("phone", phone);
        if (draftAvatar == null) {
            updateProfile(user.getUid(), values, null);
            return;
        }
        StorageReference ref =
                FirebaseStorage.getInstance()
                        .getReference()
                        .child("avatars/" + user.getUid() + "/" + UUID.randomUUID());
        ref.putFile(Uri.parse(draftAvatar))
                .continueWithTask(
                        task -> {
                            if (!task.isSuccessful()) throw task.getException();
                            return ref.getDownloadUrl();
                        })
                .addOnSuccessListener(
                        url -> {
                            values.put("avatarUrl", url.toString());
                            updateProfile(user.getUid(), values, ref);
                        })
                .addOnFailureListener(
                        e -> {
                            busy = false;
                            tell("Tải ảnh thất bại: " + e.getMessage());
                            refreshUi();
                        });
    }

    private void updateProfile(
            String userId, Map<String, Object> values, StorageReference uploaded) {
        db.collection("accounts")
                .document(userId)
                .update(values)
                .addOnSuccessListener(
                        unused -> {
                            JSONObject profile = profile();
                            try {
                                for (Map.Entry<String, Object> v : values.entrySet())
                                    profile.put(v.getKey(), v.getValue());
                            } catch (JSONException ignored) {
                            }
                            store("profile", new JSONArray().put(profile));
                            busy = false;
                            draftAvatar = null;
                            page = "account";
                            scrollY = 0;
                            tell("Đã lưu thông tin.");
                            refreshUi();
                        })
                .addOnFailureListener(
                        e -> {
                            if (uploaded != null) uploaded.delete();
                            busy = false;
                            tell("Không lưu được hồ sơ: " + e.getMessage());
                            refreshUi();
                        });
    }

    public void deleteAccount(AuthCredential credential) {
        FirebaseUser user = auth.getCurrentUser();
        if (busy || user == null) return;
        if (!online()) {
            tell("Kết nối mạng để xóa tài khoản.");
            return;
        }
        busy = true;
        refreshUi();
        user.reauthenticate(credential)
                .continueWithTask(
                        task -> {
                            if (!task.isSuccessful()) throw task.getException();
                            return user.delete();
                        })
                .addOnSuccessListener(
                        unused -> {
                            busy = false;
                            logout();
                            tell("Đã xóa tài khoản đăng nhập.");
                        })
                .addOnFailureListener(
                        e -> {
                            busy = false;
                            tell("Không xóa được tài khoản: " + e.getMessage());
                            refreshUi();
                        });
    }

    public void logout() {
        if (busy) return;
        stop();
        auth.signOut();
        clearData();
        uid = "";
        page = "home";
        scrollY = 0;
        draftName = draftPhone = draftAvatar = null;
        refreshUi();
        start();
    }

    @Override
    protected void onCleared() {
        cleared = true;
        stop();
        clearData();
        super.onCleared();
    }
}
