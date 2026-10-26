package com.example.mobileproject.view;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RatingBar;
import android.widget.ImageView;
import android.widget.TextView;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mobileproject.R;
import com.example.mobileproject.adapter.ReviewAdapter;
import com.example.mobileproject.model.ReviewItem;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;

public class Review extends AppCompatActivity {
    private ListenerRegistration reviewsListener;
    private ReviewAdapter adapter;
    private String postId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        setContentView(R.layout.activity_review);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right,
                    Math.max(systemBars.bottom, ime.bottom));
            View reviewsSection = v.findViewById(R.id.reviewListSection);
            if (reviewsSection != null) {
                reviewsSection.setVisibility(insets.isVisible(WindowInsetsCompat.Type.ime())
                        ? View.GONE : View.VISIBLE);
            }
            return insets;
        });
        postId = getIntent().getStringExtra("postId");
        RecyclerView recycler = findViewById(R.id.recyclerReviews);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ReviewAdapter();
        recycler.setAdapter(adapter);
        ImageView back = findViewById(R.id.btnBack);
        back.setOnClickListener(v -> finish());
        RatingBar rating = findViewById(R.id.ratingReviewForm);
        EditText comment = findViewById(R.id.edtReviewComment);
        Button submit = findViewById(R.id.btnSubmitReview);
        submit.setOnClickListener(v -> submitReview(rating, comment, submit));
        if (postId == null || postId.trim().isEmpty()) {
            submit.setEnabled(false);
            return;
        }
        reviewsListener = FirebaseFirestore.getInstance().collection("reviews")
                .whereEqualTo("postId", postId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null || snapshot == null || isFinishing()) return;
                    ArrayList<ReviewItem> rows = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        ReviewItem item = new ReviewItem();
                        item.reviewerAccountId = doc.getString("reviewer_accountId");
                        if (item.reviewerAccountId == null) item.reviewerAccountId = "";
                        item.reviewerName = doc.getString("reviewerName");
                        if (item.reviewerName == null || item.reviewerName.trim().isEmpty()) item.reviewerName = "Người dùng";
                        item.comment = doc.getString("comment");
                        if (item.comment == null) item.comment = "";
                        Number ratingVal = (Number) doc.get("rating");
                        item.rating = ratingVal == null ? 0 : ratingVal.floatValue();
                        Date createdAt = doc.getDate("createdAt");
                        if (createdAt != null) {
                            item.createdAtMillis = createdAt.getTime();
                            item.date = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(createdAt);
                        }
                        rows.add(item);
                    }
                    rows.sort((a, b) -> Long.compare(b.createdAtMillis, a.createdAtMillis));
                    adapter.submit(rows);
                    TextView empty = findViewById(R.id.txtEmptyReviews);
                    empty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
                });
    }

    private void submitReview(RatingBar rating, EditText comment, Button submit) {
        String reviewerId = FirebaseAuth.getInstance().getUid();
        if (reviewerId == null || reviewerId.trim().isEmpty()) {
            Toast.makeText(this, "Vui lòng đăng nhập để gửi nhận xét.", Toast.LENGTH_SHORT).show();
            return;
        }
        int ratingValue = Math.round(rating.getRating());
        if (ratingValue < 1 || ratingValue > 5) {
            Toast.makeText(this, "Vui lòng chọn số sao đánh giá.", Toast.LENGTH_SHORT).show();
            return;
        }
        String reviewId = FirebaseFirestore.getInstance().collection("reviews").document().getId();
        Map<String, Object> data = new HashMap<>();
        data.put("reviewId", reviewId);
        data.put("postId", postId);
        data.put("reviewer_accountId", reviewerId);
        data.put("rating", ratingValue);
        data.put("comment", comment.getText().toString().trim());
        data.put("createdAt", Timestamp.now());
        submit.setEnabled(false);
        FirebaseFirestore.getInstance().collection("reviews").document(reviewId).set(data)
                .addOnSuccessListener(unused -> {
                    rating.setRating(0);
                    comment.setText("");
                    submit.setEnabled(true);
                    Toast.makeText(this, "Đã gửi nhận xét.", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(error -> {
                    submit.setEnabled(true);
                    Toast.makeText(this, "Không thể gửi nhận xét: " + error.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                });
    }

    @Override protected void onDestroy() {
        if (reviewsListener != null) reviewsListener.remove();
        super.onDestroy();
    }
}
