package com.example.mobileproject.view;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.mobileproject.R;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class AdminManagePostActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private LinearLayout layoutPendingPosts;
    private TextView txtPendingCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_admin_manage_post);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        db = FirebaseFirestore.getInstance();
        layoutPendingPosts = findViewById(R.id.layoutPendingPosts);
        txtPendingCount = findViewById(R.id.txtPendingCount);

        loadPosts();
    }

    private void loadPosts() {
        db.collection("posts")
                .get()
                .addOnSuccessListener(snapshot -> {
                    layoutPendingPosts.removeAllViews();
                    int count = 0;
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        String status = doc.getString("status");
                        if (status == null || status.trim().isEmpty()) status = "PENDING";
                        count++;
                        addPostCard(doc);
                    }
                    txtPendingCount.setText("Tổng số bài đăng: " + count);
                })
                .addOnFailureListener(e -> {
                    txtPendingCount.setText("Không tải được danh sách bài đăng.");
                    Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void addPostCard(DocumentSnapshot doc) {
        String postId = doc.getId();
        String title = doc.getString("title");
        if (title == null || title.isEmpty()) title = "Không có tiêu đề";
        String address = doc.getString("address");
        if (address == null || address.isEmpty()) address = doc.getString("addressText");
        if (address == null) address = "Chưa có địa chỉ";
        String ownerId = doc.getString("accountId");
        if (ownerId == null) ownerId = doc.getString("ownerId");
        if (ownerId == null) ownerId = "";
        String status = doc.getString("status");
        if (status == null) status = "PENDING";

        View card = getLayoutInflater().inflate(R.layout.item_admin_post_card, layoutPendingPosts, false);
        TextView txtTitle = card.findViewById(R.id.txtPostTitle);
        TextView txtAddress = card.findViewById(R.id.txtPostAddress);
        TextView txtStatus = card.findViewById(R.id.txtPostStatus);
        View btnApprove = card.findViewById(R.id.btnApprove);
        View btnReject = card.findViewById(R.id.btnReject);

        txtTitle.setText(title);
        txtAddress.setText("Địa chỉ: " + address);
        txtStatus.setText("Trạng thái: " + status);

        final String finalOwnerId = ownerId;
        final String finalTitle = title;

        btnApprove.setOnClickListener(v -> updatePostStatus(postId, "APPROVED", finalOwnerId, finalTitle, true));
        btnReject.setOnClickListener(v -> updatePostStatus(postId, "REJECTED", finalOwnerId, finalTitle, false));

        layoutPendingPosts.addView(card);
    }

    private void updatePostStatus(String postId, String newStatus, String ownerId, String postTitle, boolean isApproved) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", newStatus);

        db.collection("posts").document(postId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    // Gửi thông báo cho người dùng
                    if (ownerId != null && !ownerId.isEmpty()) {
                        Map<String, Object> notif = new HashMap<>();
                        notif.put("userId", ownerId);
                        notif.put("type", isApproved ? "post_approved" : "post_rejected");
                        notif.put("title", isApproved ? "Bài viết đã được duyệt" : "Bài viết bị từ chối");
                        notif.put("body", isApproved
                                ? "Bài đăng '" + postTitle + "' của bạn đã được Admin duyệt và công khai."
                                : "Bài đăng '" + postTitle + "' của bạn đã bị từ chối và chuyển về riêng tư.");
                        notif.put("isRead", false);
                        notif.put("createdAt", FieldValue.serverTimestamp());

                        db.collection("notifications").add(notif);
                    }

                    Toast.makeText(this, isApproved ? "Đã duyệt bài đăng (APPROVED)" : "Đã từ chối bài đăng (REJECTED)", Toast.LENGTH_SHORT).show();
                    loadPosts();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Cập nhật thất bại: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}
