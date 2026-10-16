package com.example.mobileproject.viewmodel;

import android.app.Application;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
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
import java.util.List;
import java.util.Map;
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

    public String image(String postId) {
        JSONArray photos = rows("photos");
        for (int i = 0; i < photos.length(); i++) {
            JSONObject p = photos.optJSONObject(i);
            if (p != null && postId.equals(p.optString("id"))) return p.optString("url");
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
        watch(db.collection("posts").whereEqualTo("status", "APPROVED").limit(50), "feed", token);
        if (!uid.isEmpty()) {
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
            watch(db.collection("posts").whereEqualTo("ownerId", uid), "mine", token);
            watch(db.collection("notifications").whereEqualTo("userId", uid), "notices", token);
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
                            if ("notices".equals(key))
                                items.sort(
                                        (a, b) ->
                                                Long.compare(
                                                        b.optLong("createdAt"),
                                                        a.optLong("createdAt")));
                            else items.sort(Comparator.comparing(a -> a.optString("title")));
                            JSONArray array = new JSONArray();
                            for (JSONObject item : items) array.put(item);
                            store(key, array);
                            if (!"notices".equals(key)) {
                                watchImages(token);
                                watchOwners(token);
                            }
                        }));
    }

    private JSONObject convert(DocumentSnapshot doc) {
        JSONObject o = new JSONObject();
        try {
            o.put("id", doc.getId());
            // Chỉ đọc các trường giao diện cần.
            String[] fields = {
                    "fullName",
                    "email",
                    "phone",
                    "avatarUrl",
                    "username",
                    "title",
                    "description",
                    "addressText",
                    "ownerId",
                    "status",
                    "body",
                    "type"
            };
            for (String key : fields) {
                Object v = doc.get(key);
                o.put(key, v instanceof String ? v : "");
            }
            for (String key : new String[] {"maxGuests", "bedroomCount", "bathroomCount"}) {
                Object v = doc.get(key);
                o.put(key, v instanceof Number ? v : 0);
            }
            for (String key : new String[] {"pricePerNight", "ratingAverage", "reviewCount"}) {
                Object value = doc.get(key);
                if (value instanceof Number) o.put(key, value);
            }
            for (String key : new String[] {"availableFrom", "availableTo"}) {
                Object value = doc.get(key);
                long time =
                        value instanceof Timestamp
                                ? ((Timestamp) value).toDate().getTime()
                                : value instanceof java.util.Date
                                  ? ((java.util.Date) value).getTime()
                                  : 0;
                o.put(key, time);
            }
            Object when = doc.get("createdAt");
            o.put(
                    "createdAt",
                    when instanceof Timestamp ? ((Timestamp) when).toDate().getTime() : 0);
            String readField = doc.contains("isRead") && !doc.contains("read") ? "isRead" : "read";
            o.put("readField", readField);
            o.put("read", Boolean.TRUE.equals(doc.get(readField)));
        } catch (JSONException ignored) {
        }
        return o;
    }

    private void watchImages(int token) {
        for (String key : new String[] {"feed", "mine"}) {
            JSONArray posts = rows(key);
            for (int i = 0; i < posts.length(); i++) {
                JSONObject p = posts.optJSONObject(i);
                if (p == null) continue;
                String id = p.optString("id");
                if (imageListeners.containsKey(id)) continue;
                ListenerRegistration listener =
                        db.collection("postImages")
                                .whereEqualTo("postId", id)
                                .addSnapshotListener(
                                        MetadataChanges.INCLUDE,
                                        (snap, e) -> {
                                            if (!running || token != generation || !online())
                                                return;
                                            if (e != null) {
                                                error(
                                                        "Không tải được ảnh bài đăng: "
                                                                + e.getMessage());
                                                return;
                                            }
                                            if (snap == null || snap.getMetadata().isFromCache())
                                                return;
                                            long best = Long.MAX_VALUE;
                                            String url = "";
                                            for (DocumentSnapshot d : snap.getDocuments()) {
                                                Number order =
                                                        d.get("orderIndex") instanceof Number
                                                                ? (Number) d.get("orderIndex")
                                                                : null;
                                                long n =
                                                        order == null
                                                                ? Long.MAX_VALUE - 1
                                                                : order.longValue();
                                                if (n < best) {
                                                    best = n;
                                                    url = d.getString("imageUrl");
                                                }
                                            }
                                            JSONArray photos = new JSONArray();
                                            for (int j = 0; j < rows("photos").length(); j++) {
                                                JSONObject photo = rows("photos").optJSONObject(j);
                                                if (photo != null
                                                        && !id.equals(photo.optString("id")))
                                                    photos.put(photo);
                                            }
                                            JSONObject photo = new JSONObject();
                                            try {
                                                photo.put("id", id);
                                                photo.put("url", url == null ? "" : url);
                                            } catch (JSONException ignored) {
                                            }
                                            photos.put(photo);
                                            store("photos", photos);
                                        });
                imageListeners.put(id, listener);
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
