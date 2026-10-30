package com.example.mobileproject.view.Search;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSnapHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SnapHelper;

import com.example.mobileproject.R;
import com.example.mobileproject.adapter.SearchPostAdapter;
import com.example.mobileproject.model.ListingPost;
import com.example.mobileproject.view.Detail;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SearchMapActivity extends AppCompatActivity implements OnMapReadyCallback {

    private final List<ListingPost> posts = new ArrayList<>();
    private SearchPostAdapter adapter;
    private GoogleMap googleMap;
    private TextView txtMapTitle, txtMapListHeader, chipMapTime, chipMapGuests;
    private RecyclerView recyclerMapPosts;
    private String selectedLocation = "";
    private String timeSummary = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search_map);

        selectedLocation = getIntent().getStringExtra("SELECTED_LOCATION");
        if (selectedLocation == null) selectedLocation = "";
        timeSummary = SearchResultsActivity.buildTimeSummary(getIntent());

        ImageView btnBack = findViewById(R.id.btnBackMap);
        txtMapTitle = findViewById(R.id.txtMapTitle);
        txtMapListHeader = findViewById(R.id.txtMapListHeader);
        chipMapTime = findViewById(R.id.chipMapTime);
        chipMapGuests = findViewById(R.id.chipMapGuests);
        recyclerMapPosts = findViewById(R.id.recyclerMapPosts);

        txtMapTitle.setText(selectedLocation.isEmpty() ? "Xem trên bản đồ" : selectedLocation);
        chipMapTime.setText(timeSummary.isEmpty() ? "Thời gian" : timeSummary);
        chipMapGuests.setText("Chỗ ở phù hợp");
        btnBack.setOnClickListener(v -> finish());

        adapter = new SearchPostAdapter(this, posts, post -> {
            if (post.getPostId() == null || post.getPostId().trim().isEmpty()) return;
            Intent intent = new Intent(this, Detail.class);
            intent.putExtra(Detail.EXTRA_POST_ID, post.getPostId());
            startActivity(intent);
        });
        recyclerMapPosts.setLayoutManager(new LinearLayoutManager(this));
        recyclerMapPosts.setAdapter(adapter);

        SnapHelper snapHelper = new LinearSnapHelper();
        snapHelper.attachToRecyclerView(recyclerMapPosts);

        recyclerMapPosts.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    RecyclerView.LayoutManager lm = recyclerView.getLayoutManager();
                    if (lm != null) {
                        View snapView = snapHelper.findSnapView(lm);
                        if (snapView != null) {
                            int pos = lm.getPosition(snapView);
                            if (pos >= 0 && pos < posts.size()) {
                                ListingPost post = posts.get(pos);
                                panMapToPost(post);
                            }
                        }
                    }
                }
            }
        });
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) mapFragment.getMapAsync(this);
        loadPosts();
    }

    @Override
    public void onMapReady(GoogleMap map) {
        googleMap = map;
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(10.7769, 106.7009), 10f));
        addMarkers();
    }

    private void loadPosts() {
        com.example.mobileproject.util.AccountVisibilityHelper.getLockedAccountIds(lockedIds -> {
        FirebaseFirestore.getInstance().collection("posts")
                .whereEqualTo("status", "APPROVED")
                .get()
                .addOnSuccessListener(snapshot -> {
                    posts.clear();
                    String query = normalize(selectedLocation);
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        ListingPost post = doc.toObject(ListingPost.class);
                        if (post == null) continue;
                        String ownerId = post.getAccountId();
                        if (ownerId == null || ownerId.trim().isEmpty()) ownerId = doc.getString("ownerId");
                        if (lockedIds.contains(ownerId)) continue;
                        if (post.getPostId() == null || post.getPostId().trim().isEmpty()) {
                            post.setPostId(doc.getId());
                        }
                        Object priceObj = doc.get("price");

                        if (priceObj instanceof Number) {
                            post.setPricePerNight(((Number) priceObj).doubleValue());
                        }
                        String address = normalize(post.getAddress());
                        String title = normalize(post.getTitle());
                        if (query.isEmpty() || matchesLocation(query, address, title)) {
                            posts.add(post);
                            loadCoverImage(post);
                        }
                    }
                    adapter.notifyDataSetChanged();
                    txtMapListHeader.setText(posts.size() + " chỗ ở phù hợp");
                    addMarkers();
                })
                .addOnFailureListener(error -> {
                    txtMapListHeader.setText("Không tải được danh sách chỗ ở");
                    Toast.makeText(this, "Lỗi tải bài đăng: " + error.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                });
        });
    }

    private void loadCoverImage(ListingPost post) {
        if (post == null || post.getPostId() == null || post.getPostId().trim().isEmpty()) return;

        FirebaseFirestore.getInstance().collection("images")
                .whereEqualTo("postId", post.getPostId())
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (isFinishing() || isDestroyed() || snapshot == null || snapshot.isEmpty()) return;

                    DocumentSnapshot first = null;
                    long bestOrder = Long.MAX_VALUE;
                    for (DocumentSnapshot imageDoc : snapshot.getDocuments()) {
                        Object orderValue = imageDoc.get("orderIndex");
                        long order = orderValue instanceof Number
                                ? ((Number) orderValue).longValue()
                                : Long.MAX_VALUE - 1;
                        if (first == null || order < bestOrder) {
                            first = imageDoc;
                            bestOrder = order;
                        }
                    }

                    if (first != null) {
                        String image = first.getString("imgUrl");
                        if (image == null || image.isEmpty()) image = first.getString("imageUrl");
                        if (image != null && !image.isEmpty()) {
                            post.setCoverImageUrl(image);
                            if (adapter != null) adapter.notifyDataSetChanged();
                        }
                    }
                });
    }

    private void addMarkers() {
        if (googleMap == null) return;
        googleMap.clear();
        LatLng firstPoint = null;
        for (int i = 0; i < posts.size(); i++) {
            ListingPost post = posts.get(i);
            double latitude = post.getLatitude();
            double longitude = post.getLongitude();
            if (latitude == 0 || longitude == 0) continue;
            LatLng point = new LatLng(latitude, longitude);
            String title = post.getTitle() == null || post.getTitle().trim().isEmpty() ? "Chỗ ở" : post.getTitle();
            Marker marker = googleMap.addMarker(new MarkerOptions().position(point).title(title));
            if (marker != null) {
                marker.setTag(i);
            }
            if (firstPoint == null) firstPoint = point;
        }
        if (firstPoint != null) {
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(firstPoint, 12f));
        }

        googleMap.setOnMarkerClickListener(marker -> {
            Object tag = marker.getTag();
            if (tag instanceof Integer) {
                int pos = (Integer) tag;
                if (pos >= 0 && pos < posts.size()) {
                    if (recyclerMapPosts != null) {
                        recyclerMapPosts.smoothScrollToPosition(pos);
                    }
                    panMapToPost(posts.get(pos));
                }
            }
            return false;
        });
    }

    private void panMapToPost(ListingPost post) {
        if (post == null || googleMap == null) return;
        double latitude = post.getLatitude();
        double longitude = post.getLongitude();
        if (latitude == 0 || longitude == 0) return;
        LatLng point = new LatLng(latitude, longitude);
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(point, 14f));
    }

    private static boolean matchesLocation(String query, String address, String title) {
        if (address.contains(query) || title.contains(query) || (!address.isEmpty() && query.contains(address))) return true;
        for (String part : query.split(",")) {
            String token = part.trim();
            if (token.length() >= 3 && (address.contains(token) || title.contains(token))) return true;
        }
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
