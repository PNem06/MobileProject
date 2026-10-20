
package com.example.mobileproject.view;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.mobileproject.R;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExchangeActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private ListView listPosts;
    private Button btnSend, btnCancel;

    private final List<DocumentSnapshot> posts = new ArrayList<>();
    private final List<String> postTitles = new ArrayList<>();

    private String targetPostId;
    private String selectedPostId;
    private String currentUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exchange);

        db = FirebaseFirestore.getInstance();
        currentUid = FirebaseAuth.getInstance().getUid();

        targetPostId = getIntent().getStringExtra("targetPostId");

        listPosts = findViewById(R.id.listPosts);
        btnSend = findViewById(R.id.btnSend);
        btnCancel = findViewById(R.id.btnCancel);

        btnSend.setEnabled(false);

        listPosts.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

        listPosts.setOnItemClickListener((parent, view, position, id) -> {
            selectedPostId = posts.get(position).getId();
            btnSend.setEnabled(true);
        });

        btnCancel.setOnClickListener(v -> finish());
        btnSend.setOnClickListener(v -> sendExchangeRequest());

        loadMyPosts();
    }

    private void loadMyPosts() {
        if (currentUid == null) {
            Toast.makeText(this,
                    "Vui lòng đăng nhập.",
                    Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        db.collection("posts")
                .whereEqualTo("accountId", currentUid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (isFinishing() || isDestroyed()) return;

                    posts.clear();
                    postTitles.clear();

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        // Do not recommend the same post being viewed.
                        if (doc.getId().equals(targetPostId)) continue;

                        posts.add(doc);
                        String title = doc.getString("title");

                        postTitles.add(title == null
                                ? "Bài đăng không có tiêu đề"
                                : title);
                    }

                    ArrayAdapter<String> adapter = new ArrayAdapter<>(
                            this,
                            android.R.layout.simple_list_item_single_choice,
                            postTitles
                    );

                    listPosts.setAdapter(adapter);

                    if (posts.isEmpty()) {
                        Toast.makeText(this,
                                "Bạn chưa có bài đăng phù hợp để trao đổi.",
                                Toast.LENGTH_LONG).show();
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Không thể tải bài đăng: " + e.getMessage(),
                                Toast.LENGTH_LONG).show());
    }

    private void sendExchangeRequest() {
        if (currentUid == null || selectedPostId == null) {
            Toast.makeText(this,
                    "Vui lòng chọn một bài đăng.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        btnSend.setEnabled(false);

        // Read the target post to find its owner.
        db.collection("posts")
                .document(targetPostId)
                .get()
                .addOnSuccessListener(target -> {
                    if (!target.exists()) {
                        btnSend.setEnabled(true);
                        Toast.makeText(this,
                                "Bài đăng cần trao đổi không còn tồn tại.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }


                    String ownerIdValue = target.getString("accountId");

                    if (ownerIdValue == null || ownerIdValue.trim().isEmpty()) {
                        ownerIdValue = target.getString("ownerId");
                    }

                    if (ownerIdValue == null || ownerIdValue.trim().isEmpty()) {
                        btnSend.setEnabled(true);
                        Toast.makeText(this,
                                "Không tìm thấy chủ bài đăng.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

// Create a final variable for use inside lambda expressions.
                    final String recipientId = ownerIdValue.trim();

                    if (recipientId.equals(currentUid)) {
                        btnSend.setEnabled(true);
                        Toast.makeText(this,
                                "Bạn không thể gửi yêu cầu cho chính mình.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Map<String, Object> request = new HashMap<>();
                    request.put("senderId", currentUid);
                    request.put("recipientId", recipientId);
                    request.put("targetPostId", targetPostId);
                    request.put("offeredPostId", selectedPostId);
                    request.put("status", "pending");
                    request.put("createdAt", FieldValue.serverTimestamp());

                    db.collection("exchangeRequests")
                            .add(request)
                            .addOnSuccessListener(ref -> {
                                Map<String, Object> notification = new HashMap<>();
                                notification.put("recipientId", recipientId);
                                notification.put("senderId", currentUid);
                                notification.put("type", "exchange_request");
                                notification.put("requestId", ref.getId());
                                notification.put("targetPostId", targetPostId);
                                notification.put("offeredPostId", selectedPostId);
                                notification.put("message",
                                        "Bạn nhận được một đề nghị trao đổi phòng.");
                                notification.put("isRead", false);
                                notification.put("createdAt", FieldValue.serverTimestamp());

                                db.collection("notifications")
                                        .add(notification)
                                        .addOnSuccessListener(n -> {
                                            Toast.makeText(this,
                                                    "Đã gửi đề nghị trao đổi!",
                                                    Toast.LENGTH_SHORT).show();
                                            finish();
                                        })
                                        .addOnFailureListener(e -> {
                                            Toast.makeText(this,
                                                    "Đề nghị đã lưu nhưng chưa tạo được thông báo.",
                                                    Toast.LENGTH_LONG).show();
                                            finish();
                                        });
                            })
                            .addOnFailureListener(e -> {
                                btnSend.setEnabled(true);
                                Toast.makeText(this,
                                        "Gửi thất bại: " + e.getMessage(),
                                        Toast.LENGTH_LONG).show();
                            });
                });
    };
}