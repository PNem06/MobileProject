
package com.example.mobileproject.view.Search;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mobileproject.R;
import com.example.mobileproject.adapter.SearchPostAdapter;
import com.example.mobileproject.model.ListingPost;
import com.example.mobileproject.view.Detail;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SearchResultsActivity extends AppCompatActivity {

    private final List<ListingPost> allApprovedPosts = new ArrayList<>();
    private final List<ListingPost> visiblePosts = new ArrayList<>();

    private SearchPostAdapter adapter;

    private TextView txtSelectedLocation;
    private TextView txtSelectedTime;
    private TextView txtResultCount;
    private TextView txtEmptyResults;
    private ProgressBar progressResults;

    private String selectedLocation = "";
    private String timeSummary = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search_results);

        selectedLocation = valueOrEmpty(
                getIntent().getStringExtra("SELECTED_LOCATION")
        );
        timeSummary = buildTimeSummary(getIntent());

        txtSelectedLocation = findViewById(R.id.txtSelectedLocation);
        txtSelectedTime = findViewById(R.id.txtSelectedTime);
        txtResultCount = findViewById(R.id.txtResultCount);
        txtEmptyResults = findViewById(R.id.txtEmptyResults);
        progressResults = findViewById(R.id.progressResults);

        RecyclerView recyclerPosts = findViewById(R.id.recyclerPosts);
        ImageView btnBackResults = findViewById(R.id.btnBack);
        View btnMap = findViewById(R.id.btnMap);

        txtSelectedLocation.setText(
                "Địa điểm: " +
                        (selectedLocation.isEmpty()
                                ? "Chưa chọn"
                                : selectedLocation)
        );

        txtSelectedTime.setText(
                "Thời gian: " +
                        (timeSummary.isEmpty()
                                ? "Chưa chọn"
                                : timeSummary)
        );

        adapter = new SearchPostAdapter(this, visiblePosts, post -> {
            if (post.getPostId() == null
                    || post.getPostId().trim().isEmpty()) {
                Toast.makeText(
                        this,
                        "Không xác định được bài đăng này",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            Intent detailIntent = new Intent(
                    SearchResultsActivity.this,
                    Detail.class
            );

            detailIntent.putExtra(
                    Detail.EXTRA_POST_ID,
                    post.getPostId()
            );

            startActivity(detailIntent);
        });

        recyclerPosts.setLayoutManager(
                new LinearLayoutManager(this)
        );
        recyclerPosts.setAdapter(adapter);

        btnBackResults.setOnClickListener(v -> finish());
        btnMap.setOnClickListener(v -> openMap());

        loadApprovedPosts();
    }

    private void loadApprovedPosts() {
        progressResults.setVisibility(View.VISIBLE);
        txtEmptyResults.setVisibility(View.GONE);
        recyclerViewClearIfNeeded();

        FirebaseFirestore.getInstance()
                .collection("posts")
                .whereEqualTo("status", "APPROVED")
                .get()
                .addOnSuccessListener(snapshot -> {
                    allApprovedPosts.clear();

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        ListingPost post = doc.toObject(ListingPost.class);

                        if (post == null) {
                            continue;
                        }

                        if (post.getPostId() == null
                                || post.getPostId().trim().isEmpty()) {
                            post.setPostId(doc.getId());
                        }

                        allApprovedPosts.add(post);
                        loadCoverImage(post);
                    }

                    applyLocationFilter();
                    progressResults.setVisibility(View.GONE);
                })
                .addOnFailureListener(error -> {
                    progressResults.setVisibility(View.GONE);

                    txtResultCount.setText("Không tải được kết quả");
                    txtEmptyResults.setText(
                            "Không thể tải bài đăng. " +
                                    "Kiểm tra kết nối mạng hoặc cấu hình Firebase rồi thử lại."
                    );
                    txtEmptyResults.setVisibility(View.VISIBLE);

                    Toast.makeText(
                            SearchResultsActivity.this,
                            "Lỗi tải bài đăng: " + error.getLocalizedMessage(),
                            Toast.LENGTH_LONG
                    ).show();
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
                            adapter.notifyDataSetChanged();
                        }
                    }
                });
    }

    private void recyclerViewClearIfNeeded() {
        visiblePosts.clear();

        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void applyLocationFilter() {
        visiblePosts.clear();

        String query = normalize(selectedLocation);

        for (ListingPost post : allApprovedPosts) {
            String address = normalize(post.getAddress());
            String title = normalize(post.getTitle());

            // Nếu chưa chọn địa điểm thì hiển thị tất cả bài đã duyệt.
            if (query.isEmpty()
                    || matchesLocation(query, address, title)) {
                visiblePosts.add(post);
            }
        }

        adapter.notifyDataSetChanged();

        txtResultCount.setText(
                visiblePosts.size() + " chỗ ở phù hợp"
        );

        if (visiblePosts.isEmpty()) {
            txtEmptyResults.setText(
                    "Không tìm thấy bài đăng phù hợp."
            );
            txtEmptyResults.setVisibility(View.VISIBLE);
        } else {
            txtEmptyResults.setVisibility(View.GONE);
        }
    }

    private void openMap() {
        Intent mapIntent = new Intent(
                SearchResultsActivity.this,
                SearchMapActivity.class
        );

        copySearchExtras(getIntent(), mapIntent);
        startActivity(mapIntent);
    }

    static void copySearchExtras(Intent source, Intent target) {
        if (source == null || target == null) {
            return;
        }

        String[] keys = {
                "SELECTED_LOCATION",
                "TIME_MODE",
                "START_DATE",
                "END_DATE",
                "TOLERANCE",
                "FLEX_DURATION",
                "FLEX_MONTH"
        };

        for (String key : keys) {
            String value = source.getStringExtra(key);

            if (value != null) {
                target.putExtra(key, value);
            }
        }
    }

    static String buildTimeSummary(Intent intent) {
        if (intent == null) {
            return "";
        }

        String mode = intent.getStringExtra("TIME_MODE");

        if ("CUSTOM".equals(mode)) {
            String start = intent.getStringExtra("START_DATE");
            String end = intent.getStringExtra("END_DATE");

            if (!TextUtils.isEmpty(start)
                    && !TextUtils.isEmpty(end)) {
                return start + " đến " + end;
            }

            return TextUtils.isEmpty(start)
                    ? "Ngày linh hoạt"
                    : start;
        }

        if ("FLEXIBLE".equals(mode)) {
            String duration = intent.getStringExtra("FLEX_DURATION");
            String month = intent.getStringExtra("FLEX_MONTH");

            if (!TextUtils.isEmpty(duration)
                    && !TextUtils.isEmpty(month)) {
                return duration + " · " + month;
            }

            if (!TextUtils.isEmpty(duration)) {
                return duration;
            }

            return valueOrEmpty(month);
        }

        return "";
    }

    private static boolean matchesLocation(
            String query,
            String address,
            String title
    ) {
        if (address.contains(query)
                || title.contains(query)
                || (!address.isEmpty() && query.contains(address))) {
            return true;
        }

        String[] parts = query.split(",");

        for (String part : parts) {
            String token = part.trim();

            if (token.length() >= 3
                    && (address.contains(token)
                    || title.contains(token))) {
                return true;
            }
        }

        return false;
    }

    private static String normalize(String value) {
        return value == null
                ? ""
                : value.trim().toLowerCase(new Locale("vi", "VN"));
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
