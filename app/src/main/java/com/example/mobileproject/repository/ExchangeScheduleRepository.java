package com.example.mobileproject.repository;

import android.app.Application;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.mobileproject.data.ReportCacheEntity;
import com.example.mobileproject.view.ExchangeScheduleItem;
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
    private final ReportCacheEntity cache;
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
        cache = new ReportCacheEntity(application);
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
        if (item.postId.isEmpty()) item.postId = text(doc.get("targetPostId"));
        item.hostId = text(doc.get("hostId"));
        if (item.hostId.isEmpty()) item.hostId = text(doc.get("host_accountId"));
        if (item.hostId.isEmpty()) item.hostId = text(doc.get("recipientId"));
        item.status = text(doc.get("status"));

        item.startDate = date(doc.get("startDate"));
        if (item.startDate == null) item.startDate = date(doc.get("start_date"));
        if (item.startDate == null) item.startDate = date(doc.get("checkIn"));
        if (item.startDate == null) item.startDate = date(doc.get("availableFrom"));

        item.endDate = date(doc.get("endDate"));
        if (item.endDate == null) item.endDate = date(doc.get("end_date"));
        if (item.endDate == null) item.endDate = date(doc.get("checkOut"));
        if (item.endDate == null) item.endDate = date(doc.get("availableTo"));

        Number guests = doc.get("guestCount") instanceof Number
                ? (Number) doc.get("guestCount") : null;
        if (guests == null && doc.get("guests") instanceof Number) {
            guests = (Number) doc.get("guests");
        }
        item.guestCount = guests == null ? 0 : guests.intValue();

        // Hỗ trợ dữ liệu denormalized nếu nhóm có lưu trực tiếp trên Exchange.
        String title = text(doc.get("postTitle"));
        String address = text(doc.get("postAddress"));
        if (address.isEmpty()) address = text(doc.get("address"));
        if (address.isEmpty()) address = text(doc.get("location"));
        String image = text(doc.get("postImageUrl"));
        if (image.isEmpty()) image = text(doc.get("imageUrl"));
        if (image.isEmpty()) image = text(doc.get("imageBase64"));
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
                            if (address.isEmpty()) address = text(post.get("location"));
                            if (!title.isEmpty()) item.title = title;
                            if (!address.isEmpty()) item.address = address;

                            if (item.startDate == null) {
                                item.startDate = date(post.get("startDate"));
                                if (item.startDate == null) item.startDate = date(post.get("availableFrom"));
                            }
                            if (item.endDate == null) {
                                item.endDate = date(post.get("endDate"));
                                if (item.endDate == null) item.endDate = date(post.get("availableTo"));
                            }

                            String img = text(post.get("imageBase64"));
                            if (img.isEmpty()) img = text(post.get("imageUrl"));
                            if (img.isEmpty()) img = text(post.get("image"));
                            if (img.isEmpty()) img = text(post.get("imgUrl"));
                            if (!img.isEmpty()) {
                                item.imageUrl = img;
                            }
                            publishCurrent();
                        }

                        if (item.imageUrl.isEmpty()) {
                            db.collection("images")
                                    .whereEqualTo("postId", item.postId)
                                    .limit(1)
                                    .get()
                                    .addOnSuccessListener(snapshot -> {
                                        if (snapshot != null && !snapshot.isEmpty()) {
                                            DocumentSnapshot doc = snapshot.getDocuments().get(0);
                                            String url = text(doc.get("imgUrl"));
                                            if (url.isEmpty()) url = text(doc.get("imageUrl"));
                                            if (url.isEmpty()) url = text(doc.get("imageBase64"));
                                            if (!url.isEmpty()) {
                                                item.imageUrl = url;
                                                publishCurrent();
                                                return;
                                            }
                                        }
                                        db.collection("postImages")
                                                .whereEqualTo("postId", item.postId)
                                                .limit(1)
                                                .get()
                                                .addOnSuccessListener(pSnapshot -> {
                                                    if (pSnapshot != null && !pSnapshot.isEmpty()) {
                                                        DocumentSnapshot doc = pSnapshot.getDocuments().get(0);
                                                        String url = text(doc.get("imgUrl"));
                                                        if (url.isEmpty()) url = text(doc.get("imageUrl"));
                                                        if (url.isEmpty()) url = text(doc.get("imageBase64"));
                                                        if (!url.isEmpty()) {
                                                            item.imageUrl = url;
                                                            publishCurrent();
                                                        }
                                                    }
                                                });
                                    });
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
        if (value instanceof Number) {
            long millis = ((Number) value).longValue();
            if (millis > 0) return new Date(millis);
        }
        if (value instanceof String) {
            String str = ((String) value).trim();
            if (str.isEmpty()) return null;
            try {
                long millis = Long.parseLong(str);
                if (millis > 0) return new Date(millis);
            } catch (NumberFormatException ignored) {}
            for (String pattern : new String[]{"dd/MM/yyyy", "dd-MM-yyyy", "yyyy-MM-dd", "dd/MM/yyyy HH:mm"}) {
                try {
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault());
                    return sdf.parse(str);
                } catch (Exception ignored) {}
            }
        }
        return null;
    }
}
