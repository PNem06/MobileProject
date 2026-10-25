package com.example.mobileproject.view.Search;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mobileproject.R;
import com.example.mobileproject.adapter.SearchPostAdapter;
import com.example.mobileproject.model.ListingPost;
import com.example.mobileproject.view.Detail;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SearchMapActivity extends AppCompatActivity implements OnMapReadyCallback {
    private com.google.firebase.firestore.ListenerRegistration postsListener;

    private final List<ListingPost> posts = new ArrayList<>();
    private SearchPostAdapter adapter;
    private GoogleMap googleMap;
    private TextView txtMapTitle, txtMapListHeader, chipMapTime, chipMapGuests;
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
        RecyclerView recyclerMapPosts = findViewById(R.id.recyclerMapPosts);

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

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) mapFragment.getMapAsync(this);

    }

    @Override
    public void onMapReady(GoogleMap map) {
        googleMap = map;
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(10.7769, 106.7009), 10f));
        addMarkers();
    }

    private void loadPosts() {
        if (postsListener != null) postsListener.remove();
        postsListener=FirebaseFirestore.getInstance().collection("posts").whereEqualTo("status","APPROVED")
                .addSnapshotListener((snapshot,error)->{
                    if(isFinishing()||isDestroyed())return;
                    if(error!=null){
                        txtMapListHeader.setText("Không tải được danh sách chỗ ở");
                        Toast.makeText(this, "Lỗi tải bài đăng: " + error.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                        return;
                    }
                    if(snapshot==null)return;
                    posts.clear();
                    String query = normalize(selectedLocation);
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        ListingPost post = doc.toObject(ListingPost.class);
                        if (post == null) continue;
                        if (post.getPostId() == null || post.getPostId().trim().isEmpty()) post.setPostId(doc.getId());
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
        for (ListingPost post : posts) {
            double latitude = post.getLatitude();
            double longitude = post.getLongitude();
            if (latitude == 0 || longitude == 0) continue;
            LatLng point = new LatLng(latitude, longitude);
            String title = post.getTitle() == null || post.getTitle().trim().isEmpty() ? "Chỗ ở" : post.getTitle();
            googleMap.addMarker(new MarkerOptions().position(point).title(title));
            if (firstPoint == null) firstPoint = point;
        }
        if (firstPoint != null) googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(firstPoint, 12f));
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
        return value == null ? "" : value.trim().toLowerCase(new Locale("vi", "VN"));
    }
    @Override protected void onStart(){super.onStart();loadPosts();}
    @Override protected void onStop(){if(postsListener!=null){postsListener.remove();postsListener=null;}super.onStop();}
}
