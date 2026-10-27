package com.example.mobileproject.view;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mobileproject.R;
import com.example.mobileproject.adapter.ReportedPostAdapter;
import com.example.mobileproject.model.ReportedPostItem;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ReportedPostsActivity extends AppCompatActivity {

    private ImageView btnBack;
    private RecyclerView recyclerReportedPosts;
    private LinearLayout layoutEmpty;

    private ReportedPostAdapter adapter;
    private final List<ReportedPostItem> reportedList = new ArrayList<>();
    private FirebaseFirestore db;
    private String currentUid = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reported_posts);

        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        }
        db = FirebaseFirestore.getInstance();

        initViews();
        loadReportedPosts();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        recyclerReportedPosts = findViewById(R.id.recyclerReportedPosts);
        layoutEmpty = findViewById(R.id.layoutEmpty);

        recyclerReportedPosts.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ReportedPostAdapter(this, reportedList, item -> {
            Intent intent = new Intent(this, Detail.class);
            intent.putExtra("postId", item.targetId);
            startActivity(intent);
        });
        recyclerReportedPosts.setAdapter(adapter);
    }

    private void loadReportedPosts() {
        if (currentUid.isEmpty()) {
            showEmpty(true);
            return;
        }

        db.collection("reports")
                .whereEqualTo("reporterId", currentUid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    reportedList.clear();
                    if (snapshot.isEmpty()) {
                        showEmpty(true);
                        return;
                    }

                    for (QueryDocumentSnapshot doc : snapshot) {
                        String targetType = doc.getString("targetType");
                        if (targetType != null && !targetType.equalsIgnoreCase("POST")) continue;

                        String reportId = doc.getString("reportId");
                        String targetId = doc.getString("targetId");
                        String reason = doc.getString("reason");
                        String status = doc.getString("status");
                        Date createdAt = doc.getDate("createdAt");

                        if (targetId != null && !targetId.isEmpty()) {
                            ReportedPostItem item = new ReportedPostItem(reportId, targetId, reason, status, createdAt);
                            reportedList.add(item);
                        }
                    }

                    if (reportedList.isEmpty()) {
                        showEmpty(true);
                        return;
                    }

                    fetchPostDetails();
                })
                .addOnFailureListener(e -> {
                    showEmpty(true);
                    Toast.makeText(this, "Lỗi tải báo cáo: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void fetchPostDetails() {
        if (reportedList.isEmpty()) {
            showEmpty(true);
            return;
        }

        final int[] loadedCount = {0};
        for (ReportedPostItem item : reportedList) {
            db.collection("posts").document(item.targetId)
                    .get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            item.postTitle = doc.getString("title");
                            item.postAddress = doc.getString("address");
                            item.postImageUrl = doc.getString("imgUrl");
                            if (item.postImageUrl == null || item.postImageUrl.isEmpty()) {
                                item.postImageUrl = doc.getString("imageUrl");
                            }
                            Object priceObj = doc.get("price");
                            if (priceObj instanceof Number) {
                                item.postPrice = ((Number) priceObj).doubleValue();
                            }
                        }
                        if (item.postTitle == null || item.postTitle.trim().isEmpty()) {
                            item.postTitle = "Bài đăng #" + item.targetId;
                        }
                        loadedCount[0]++;
                        if (loadedCount[0] == reportedList.size()) {
                            showEmpty(false);
                            adapter.notifyDataSetChanged();
                        }
                    })
                    .addOnFailureListener(e -> {
                        item.postTitle = "Bài đăng #" + item.targetId;
                        loadedCount[0]++;
                        if (loadedCount[0] == reportedList.size()) {
                            showEmpty(false);
                            adapter.notifyDataSetChanged();
                        }
                    });
        }
    }

    private void showEmpty(boolean isEmpty) {
        if (isEmpty) {
            recyclerReportedPosts.setVisibility(View.GONE);
            layoutEmpty.setVisibility(View.VISIBLE);
        } else {
            recyclerReportedPosts.setVisibility(View.VISIBLE);
            layoutEmpty.setVisibility(View.GONE);
        }
    }
}
