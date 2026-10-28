package com.example.mobileproject.view;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.mobileproject.R;
import com.example.mobileproject.data.LocalCache;
import com.example.mobileproject.adapter.PostCardAdapter;
import com.example.mobileproject.adapter.PostCardAdapter.PostViewHolder;
import com.example.mobileproject.util.PostImageUtils;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;

/** Displays the signed-in user's posts in Pending, Active, and Hidden categories. */
public class All_Post extends AppCompatActivity {
    private static final int PENDING = 0, ACTIVE = 1, HIDDEN = 2;
    private final Map<String, JSONObject> currentOwnerPosts = new LinkedHashMap<>();
    private final Map<String, JSONObject> legacyOwnerPosts = new LinkedHashMap<>();
    private final Map<String, String> photoCache = new HashMap<>();
    private FirebaseFirestore db;
    private LocalCache localCache;
    private String cacheScope;
    private String uid;
    private ListenerRegistration currentListener, legacyListener;
    private RecyclerView recycler;
    private View empty;
    private TextView emptyMessage;
    private int selectedCategory = PENDING;
    private boolean hasLoadedAny;
    private String loadError = "";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_all_post);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        uid = FirebaseAuth.getInstance().getUid();
        if (uid == null || uid.isEmpty()) {
            startActivity(new Intent(this, Login.class));
            finish();
            return;
        }
        if (savedInstanceState != null) selectedCategory = savedInstanceState.getInt("category", PENDING);
        db = FirebaseFirestore.getInstance();
        localCache = new LocalCache(getApplicationContext());
        cacheScope = "all_posts:" + uid;
        restoreCachedPosts();
        recycler = findViewById(R.id.recyclerRequests);
        empty = findViewById(R.id.layoutEmpty);
        emptyMessage = findViewById(R.id.txtEmptyMessage);
        recycler.setLayoutManager(new GridLayoutManager(this, 2));
        recycler.setNestedScrollingEnabled(true);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.layoutSummaryReceived).setVisibility(View.GONE);
        setupCategoryTabs();
        updateCategoryUi();
        renderPosts();
    }

    @Override protected void onStart() { super.onStart(); attachPostListeners(); }

    @Override protected void onStop() {
        removePostListeners();
        super.onStop();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putInt("category", selectedCategory);
        super.onSaveInstanceState(out);
    }

    private void setupCategoryTabs() {
        findViewById(R.id.tabPENDING).setOnClickListener(v -> selectCategory(PENDING));
        findViewById(R.id.tabAPPROVED).setOnClickListener(v -> selectCategory(ACTIVE));
        findViewById(R.id.tabHIDDEN).setOnClickListener(v -> selectCategory(HIDDEN));
    }

    private void selectCategory(int category) {
        selectedCategory = category;
        updateCategoryUi();
        renderPosts();
    }

    private void updateCategoryUi() {
        updateTab(R.id.txtPENDING, R.id.indicatorPENDING, selectedCategory == PENDING);
        updateTab(R.id.txtAPPROVED, R.id.indicatorAPPROVED, selectedCategory == ACTIVE);
        updateTab(R.id.txtHIDDEN, R.id.indicatorHIDDEN, selectedCategory == HIDDEN);
    }

    private void updateTab(int textId, int indicatorId, boolean selected) {
        TextView text = findViewById(textId);
        text.setTextColor(selected ? 0xFF0B1354 : 0xFF888888);
        text.setTypeface(null, selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        findViewById(indicatorId).setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
    }

    private void attachPostListeners() {
        if (currentListener != null || legacyListener != null) return;
        currentListener = db.collection("posts").whereEqualTo("accountId", uid)
                .addSnapshotListener((snapshot, error) -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (error != null) {
                        loadError = error.getLocalizedMessage() == null ? "" : error.getLocalizedMessage();
                        hasLoadedAny = true;
                    } else if (snapshot != null) {
                        currentOwnerPosts.clear();
                        for (DocumentSnapshot doc : snapshot.getDocuments()) currentOwnerPosts.put(doc.getId(), toPost(doc));
                        saveCachedPosts();
                        hasLoadedAny = true;
                        loadError = "";
                    }
                    renderPosts();
                });
        legacyListener = db.collection("posts").whereEqualTo("ownerId", uid)
                .addSnapshotListener((snapshot, error) -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (error != null) {
                        if (loadError.isEmpty())
                            loadError = error.getLocalizedMessage() == null ? "" : error.getLocalizedMessage();
                        hasLoadedAny = true;
                    } else if (snapshot != null) {
                        legacyOwnerPosts.clear();
                        for (DocumentSnapshot doc : snapshot.getDocuments()) legacyOwnerPosts.put(doc.getId(), toPost(doc));
                        saveCachedPosts();
                        hasLoadedAny = true;
                    }
                    renderPosts();
                });
    }

    private void removePostListeners() {
        if (currentListener != null) currentListener.remove();
        if (legacyListener != null) legacyListener.remove();
        currentListener = null;
        legacyListener = null;
    }

    private void renderPosts() {
        if (recycler == null) return;
        if (!hasLoadedAny) {
            showEmpty("Đang tải bài đăng...");
            return;
        }
        Map<String, JSONObject> combined = new LinkedHashMap<>(legacyOwnerPosts);
        combined.putAll(currentOwnerPosts);
        List<JSONObject> posts = new ArrayList<>();
        for (JSONObject post : combined.values()) {
            String status = post.optString("status");
            if (belongsToCategory(status, selectedCategory)) posts.add(post);
        }
        posts.sort((a, b) -> Long.compare(b.optLong("createdAt"), a.optLong("createdAt")));
        if (posts.isEmpty()) {
            String message = loadError.isEmpty()
                    ? emptyText(selectedCategory)
                    : "Không tải được bài đăng. Kiểm tra kết nối rồi thử lại.";
            showEmpty(message);
            return;
        }
        empty.setVisibility(View.GONE);
        recycler.setVisibility(View.VISIBLE);
        int gap = dp(12);
        int available = getResources().getDisplayMetrics().widthPixels - dp(40);
        int width = Math.max(dp(130), (available - gap) / 2);
        recycler.setAdapter(new PostCardAdapter(posts, width, gap, dp(126), this::bindPostCard));
    }

    private void restoreCachedPosts() {
        if (localCache == null || cacheScope == null) return;
        Map<String, String> saved = localCache.read(cacheScope);
        readCachedArray(saved.get("current"), currentOwnerPosts);
        readCachedArray(saved.get("legacy"), legacyOwnerPosts);
        String photos = saved.get("photos");
        if (photos != null) {
            try {
                org.json.JSONArray array = new org.json.JSONArray(photos);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject item = array.optJSONObject(i);
                    if (item != null) photoCache.put(item.optString("id"), item.optString("url"));
                }
            } catch (Exception ignored) { }
        }
        hasLoadedAny = !currentOwnerPosts.isEmpty() || !legacyOwnerPosts.isEmpty();
        renderPosts();
    }

    private void readCachedArray(String raw, Map<String, JSONObject> target) {
        if (raw == null) return;
        try {
            org.json.JSONArray array = new org.json.JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject post = array.optJSONObject(i);
                if (post != null && !post.optString("id").isEmpty()) target.put(post.optString("id"), post);
            }
        } catch (Exception ignored) { }
    }

    private void saveCachedPosts() {
        if (localCache == null || cacheScope == null) return;
        try {
            org.json.JSONArray current = new org.json.JSONArray();
            for (JSONObject post : currentOwnerPosts.values()) current.put(post);
            org.json.JSONArray legacy = new org.json.JSONArray();
            for (JSONObject post : legacyOwnerPosts.values()) legacy.put(post);
            localCache.write(cacheScope, "current", current.toString());
            localCache.write(cacheScope, "legacy", legacy.toString());
            org.json.JSONArray photos = new org.json.JSONArray();
            for (Map.Entry<String, String> entry : photoCache.entrySet()) {
                JSONObject photo = new JSONObject();
                photo.put("id", entry.getKey());
                photo.put("url", entry.getValue());
                photos.put(photo);
            }
            localCache.write(cacheScope, "photos", photos.toString());
        } catch (Exception ignored) { }
    }

    private boolean belongsToCategory(String raw, int category) {
        String status = raw == null ? "" : raw.trim().toUpperCase(java.util.Locale.ROOT);
        if (category == PENDING) return "PENDING".equals(status);
        if (category == ACTIVE) return "APPROVED".equals(status);
        return "HIDDEN".equals(status) || "BOOKED".equals(status);
    }

    private JSONObject toPost(DocumentSnapshot doc) {
        JSONObject post = new JSONObject();
        try {
            post.put("id", doc.getId());
            Map<String, Object> data = doc.getData();
            if (data != null) for (Map.Entry<String, Object> entry : data.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Timestamp) value = ((Timestamp) value).toDate().getTime();
                if (value instanceof Number || value instanceof Boolean || value instanceof String)
                    post.put(entry.getKey(), value);
            }
            String title = doc.getString("title");
            post.put("title", title == null || title.trim().isEmpty() ? "Chỗ ở chưa có tên" : title);
        } catch (org.json.JSONException ignored) { }
        return post;
    }

    private void bindPostCard(PostViewHolder holder, JSONObject post) {
        String postId = post.optString("id");
        holder.title.setText(PostCardData.title(post));
        holder.price.setText(PostCardData.priceLabel(post));
        String rating = PostCardData.ratingLabel(post);
        holder.rating.setText(rating);
        holder.rating.setVisibility(rating.isEmpty() ? View.GONE : View.VISIBLE);
        holder.favoriteButton.setVisibility(View.GONE);
        holder.badge.setText(categoryBadge(post.optString("status")));
        holder.badge.setVisibility(View.VISIBLE);
        holder.badge.setTextColor(Color.WHITE);
        holder.badge.setBackground(background(0xCC171719));
        String name = post.optString("fullName", post.optString("ownerName", ""));
        holder.initials.setText(PostCardData.initials(name));
        String avatar = post.optString("avatarUrl", "");
        if (!avatar.isEmpty()) Glide.with(this).load(avatar).circleCrop().into(holder.avatar);
        else { Glide.with(this).clear(holder.avatar); holder.avatar.setImageDrawable(null); }
        holder.photo.setTag(postId);
        String cached = photoCache.get(postId);
        if (cached != null) showPhoto(holder, postId, cached);
        else {
            String fallback = post.optString("imgUrl", post.optString("imageUrl", ""));
            if (!fallback.isEmpty()) {
                photoCache.put(postId, fallback);
                showPhoto(holder, postId, fallback);
            } else loadPhoto(postId, holder);
        }
        holder.itemView.setOnClickListener(v -> {
            if (postId.isEmpty()) return;
            startActivity(new Intent(this, Detail.class).putExtra(Detail.EXTRA_POST_ID, postId));
        });
    }

    private void loadPhoto(String postId, PostViewHolder holder) {
        db.collection("images").whereEqualTo("postId", postId).get()
                .addOnSuccessListener(rows -> {
                    if (rows.isEmpty()) {
                        db.collection("postImages").whereEqualTo("postId", postId).get()
                                .addOnSuccessListener(legacy -> finishPhotoLookup(postId, holder, legacy.getDocuments()))
                                .addOnFailureListener(error -> finishPhotoLookup(postId, holder, new ArrayList<>()));
                    } else finishPhotoLookup(postId, holder, rows.getDocuments());
                }).addOnFailureListener(error -> finishPhotoLookup(postId, holder, new ArrayList<>()));
    }

    private void finishPhotoLookup(String postId, PostViewHolder holder, List<DocumentSnapshot> photos) {
        photos.sort(Comparator.comparingInt(doc -> {
            Long index = doc.getLong("orderIndex");
            return index == null ? 0 : index.intValue();
        }));
        String image = photos.isEmpty() ? "" : photos.get(0).getString("imgUrl");
        photoCache.put(postId, image == null ? "" : image);
        saveCachedPosts();
        showPhoto(holder, postId, image == null ? "" : image);
    }

    private void showPhoto(PostViewHolder holder, String postId, String image) {
        if (!postId.equals(holder.photo.getTag())) return;
        Object model = PostImageUtils.asGlideModel(image);
        if (model == null) {
            Glide.with(this).clear(holder.photo);
            holder.photo.setImageDrawable(null);
            holder.imageState.setText("Chưa có ảnh");
            holder.imageState.setVisibility(View.VISIBLE);
            return;
        }
        holder.imageState.setText("Đang tải ảnh…");
        holder.imageState.setVisibility(View.VISIBLE);
        Glide.with(this).load(model).centerCrop().into(holder.photo);
        holder.imageState.setVisibility(View.GONE);
    }

    private String categoryBadge(String status) {
        if ("APPROVED".equalsIgnoreCase(status)) return "Đang hoạt động";
        if ("BOOKED".equalsIgnoreCase(status)) return "Đã đặt";
        if ("HIDDEN".equalsIgnoreCase(status)) return "Đã ẩn";
        return "Chờ duyệt";
    }

    private void showEmpty(String message) {
        recycler.setVisibility(View.GONE);
        emptyMessage.setText(message);
        empty.setVisibility(View.VISIBLE);
    }

    private String emptyText(int category) {
        if (category == PENDING) return "Không có bài đăng đang chờ duyệt";
        if (category == ACTIVE) return "Không có bài đăng đang hoạt động";
        return "Không có bài đăng đã ẩn hoặc đã đặt";
    }

    private GradientDrawable background(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(12));
        return drawable;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
