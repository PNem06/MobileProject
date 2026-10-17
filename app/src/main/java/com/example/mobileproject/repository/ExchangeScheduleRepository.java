package com.example.mobileproject.repository;

import android.app.Application;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.mobileproject.data.OfflineCache;
import com.example.mobileproject.ui.ExchangeScheduleItem;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Repository = nguồn dữ liệu duy nhất cho màn Lịch trao đổi.
 * - Đọc cache SQLite trước để vẫn xem được dữ liệu đã xem khi offline.
 * - Nghe Firestore realtime bằng addSnapshotListener().
 * - Khi server có dữ liệu mới thì cập nhật UI + ghi lại SQLite.
 */
public class ExchangeScheduleRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final OfflineCache cache;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private final MutableLiveData<List<ExchangeScheduleItem>> items =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<Boolean> showingOfflineCache = new MutableLiveData<>(false);

    private final Map<String, DocumentSnapshot> requestedDocs = new LinkedHashMap<>();
    private final Map<String, DocumentSnapshot> hostedDocs = new LinkedHashMap<>();
    private final Map<String, ExchangeScheduleItem> currentItems = new LinkedHashMap<>();

    private ListenerRegistration requesterListener;
    private ListenerRegistration hostListener;
    private String uid = "";

    public ExchangeScheduleRepository(Application application) {
        cache = new OfflineCache(application);
    }

    public LiveData<List<ExchangeScheduleItem>> getItems() {
        return items;
    }

    public LiveData<String> getError() {
        return error;
    }

    public LiveData<Boolean> getShowingOfflineCache() {
        return showingOfflineCache;
    }

    public void start() {
        if (auth.getCurrentUser() == null) {
            error.setValue("Bạn cần đăng nhập để xem lịch trao đổi.");
            items.setValue(Collections.emptyList());
            return;
        }

        String nextUid = auth.getCurrentUser().getUid();
        if (!nextUid.equals(uid)) {
            stopListeners();
            uid = nextUid;
            requestedDocs.clear();
            hostedDocs.clear();
            currentItems.clear();
        }

        loadCache(uid);
        attachRealtime(uid);
    }

    public void stop() {
        stopListeners();
    }

    private void stopListeners() {
        if (requesterListener != null) {
            requesterListener.remove();
            requesterListener = null;
        }
        if (hostListener != null) {
            hostListener.remove();
            hostListener = null;
        }
    }

    private void attachRealtime(String userId) {
        if (requesterListener != null || hostListener != null) return;

        requesterListener = db.collection("exchanges")
                .whereEqualTo("requesterId", userId)
                .addSnapshotListener(MetadataChanges.INCLUDE,
                        (snapshot, e) -> handleSnapshot(snapshot, e, requestedDocs));

        hostListener = db.collection("exchanges")
                .whereEqualTo("hostId", userId)
                .addSnapshotListener(MetadataChanges.INCLUDE,
                        (snapshot, e) -> handleSnapshot(snapshot, e, hostedDocs));
    }

    private void handleSnapshot(
            @Nullable QuerySnapshot snapshot,
            @Nullable Exception e,
            Map<String, DocumentSnapshot> target
    ) {
        if (e != null) {
            error.setValue("Không đồng bộ được lịch trao đổi: " + e.getMessage());
            return;
        }
        if (snapshot == null) return;

        target.clear();
        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            target.put(doc.getId(), doc);
        }

        rebuildFromFirebase();

        // Snapshot từ server mới được xem là dữ liệu online mới nhất.
        if (!snapshot.getMetadata().isFromCache()) {
            showingOfflineCache.setValue(false);
        }
    }

    private void rebuildFromFirebase() {
        Map<String, DocumentSnapshot> merged = new LinkedHashMap<>();
        merged.putAll(requestedDocs);
        merged.putAll(hostedDocs);

        currentItems.clear();
        for (DocumentSnapshot doc : merged.values()) {
            ExchangeScheduleItem item = fromExchange(doc);
            if (!isScheduleStatus(item.status)) continue;
            currentItems.put(item.exchangeId, item);
        }

        publishCurrent();

        // Ghép thêm dữ liệu hiển thị từ Post / Account / PostImage.
        for (ExchangeScheduleItem item : new ArrayList<>(currentItems.values())) {
            enrich(item);
        }
    }

    private ExchangeScheduleItem fromExchange(DocumentSnapshot doc) {
        ExchangeScheduleItem item = new ExchangeScheduleItem();
        item.exchangeId = doc.getId();
        item.postId = text(doc.get("postId"));
        item.hostId = text(doc.get("hostId"));
        item.status = text(doc.get("status"));
        item.startDate = date(doc.get("startDate"));
        item.endDate = date(doc.get("endDate"));
        Number guests = doc.get("guestCount") instanceof Number
                ? (Number) doc.get("guestCount") : null;
        item.guestCount = guests == null ? 0 : guests.intValue();

        // Hỗ trợ dữ liệu denormalized nếu nhóm có lưu trực tiếp trên Exchange.
        String title = text(doc.get("postTitle"));
        String address = text(doc.get("postAddress"));
        String image = text(doc.get("postImageUrl"));
        String hostName = text(doc.get("hostName"));
        if (!title.isEmpty()) item.title = title;
        if (!address.isEmpty()) item.address = address;
        if (!image.isEmpty()) item.imageUrl = image;
        if (!hostName.isEmpty()) item.hostName = hostName;
        return item;
    }

    private void enrich(ExchangeScheduleItem item) {
        if (!item.postId.isEmpty()) {
            db.collection("posts").document(item.postId).get()
                    .addOnSuccessListener(post -> {
                        if (post.exists()) {
                            String title = text(post.get("title"));
                            String address = text(post.get("addressText"));
                            if (address.isEmpty()) address = text(post.get("address"));
                            if (!title.isEmpty()) item.title = title;
                            if (!address.isEmpty()) item.address = address;
                            publishCurrent();
                        }
                    });

            db.collection("postImages")
                    .whereEqualTo("postId", item.postId)
                    .get()
                    .addOnSuccessListener(snapshot -> {
                        long best = Long.MAX_VALUE;
                        String url = "";
                        for (DocumentSnapshot image : snapshot.getDocuments()) {
                            Number order = image.get("orderIndex") instanceof Number
                                    ? (Number) image.get("orderIndex") : null;
                            long n = order == null ? Long.MAX_VALUE - 1 : order.longValue();
                            String candidate = text(image.get("imageUrl"));
                            if (n < best && !candidate.isEmpty()) {
                                best = n;
                                url = candidate;
                            }
                        }
                        if (!url.isEmpty()) {
                            item.imageUrl = url;
                            publishCurrent();
                        }
                    });
        }

        if (!item.hostId.isEmpty()) {
            db.collection("accounts").document(item.hostId).get()
                    .addOnSuccessListener(account -> {
                        if (account.exists()) {
                            String name = text(account.get("fullName"));
                            if (name.isEmpty()) name = text(account.get("username"));
                            if (!name.isEmpty()) {
                                item.hostName = name;
                                publishCurrent();
                            }
                        }
                    });
        }
    }

    private void publishCurrent() {
        List<ExchangeScheduleItem> list = new ArrayList<>(currentItems.values());
        list.sort((a, b) -> {
            long ta = a.startDate == null ? Long.MAX_VALUE : a.startDate.getTime();
            long tb = b.startDate == null ? Long.MAX_VALUE : b.startDate.getTime();
            return Long.compare(ta, tb);
        });
        items.setValue(list);
        saveCache(uid, list);
    }

    private void loadCache(String userId) {
        final String scope = scope(userId);
        io.execute(() -> {
            Map<String, String> rows = cache.readScope(scope);
            if (rows.isEmpty()) return;
            List<ExchangeScheduleItem> cached = new ArrayList<>();
            for (String payload : rows.values()) {
                ExchangeScheduleItem item = fromJson(payload);
                if (item != null) cached.add(item);
            }
            cached.sort((a, b) -> {
                long ta = a.startDate == null ? Long.MAX_VALUE : a.startDate.getTime();
                long tb = b.startDate == null ? Long.MAX_VALUE : b.startDate.getTime();
                return Long.compare(ta, tb);
            });
            showingOfflineCache.postValue(true);
            items.postValue(cached);
        });
    }

    private void saveCache(String userId, List<ExchangeScheduleItem> list) {
        if (userId == null || userId.isEmpty()) return;
        final String scope = scope(userId);
        final Map<String, String> rows = new LinkedHashMap<>();
        for (ExchangeScheduleItem item : list) {
            rows.put(item.exchangeId, toJson(item));
        }
        io.execute(() -> cache.replaceScope(scope, rows));
    }

    private String scope(String userId) {
        return "exchange_schedule:" + userId;
    }

    private boolean isScheduleStatus(String status) {
        return "ACCEPTED".equals(status)
                || "ONGOING".equals(status)
                || "COMPLETED".equals(status);
    }

    private String toJson(ExchangeScheduleItem item) {
        JSONObject o = new JSONObject();
        try {
            o.put("exchangeId", item.exchangeId);
            o.put("postId", item.postId);
            o.put("hostId", item.hostId);
            o.put("title", item.title);
            o.put("hostName", item.hostName);
            o.put("address", item.address);
            o.put("imageUrl", item.imageUrl);
            o.put("status", item.status);
            o.put("guestCount", item.guestCount);
            o.put("startDate", item.startDate == null ? 0 : item.startDate.getTime());
            o.put("endDate", item.endDate == null ? 0 : item.endDate.getTime());
        } catch (JSONException ignored) {
        }
        return o.toString();
    }

    @Nullable
    private ExchangeScheduleItem fromJson(String payload) {
        try {
            JSONObject o = new JSONObject(payload);
            ExchangeScheduleItem item = new ExchangeScheduleItem();
            item.exchangeId = o.optString("exchangeId");
            item.postId = o.optString("postId");
            item.hostId = o.optString("hostId");
            item.title = o.optString("title", "Chỗ ở");
            item.hostName = o.optString("hostName", "Chủ nhà");
            item.address = o.optString("address");
            item.imageUrl = o.optString("imageUrl");
            item.status = o.optString("status");
            item.guestCount = o.optInt("guestCount", 0);
            long start = o.optLong("startDate", 0);
            long end = o.optLong("endDate", 0);
            item.startDate = start == 0 ? null : new Date(start);
            item.endDate = end == 0 ? null : new Date(end);
            return item;
        } catch (JSONException e) {
            return null;
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    @Nullable
    private Date date(Object value) {
        if (value instanceof Timestamp) return ((Timestamp) value).toDate();
        if (value instanceof Date) return new Date(((Date) value).getTime());
        if (value instanceof Number) return new Date(((Number) value).longValue());
        return null;
    }
}
