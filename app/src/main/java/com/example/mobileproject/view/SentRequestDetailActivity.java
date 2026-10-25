
package com.example.mobileproject.view;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.mobileproject.R;
import com.example.mobileproject.util.PostImageUtils;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class SentRequestDetailActivity extends AppCompatActivity {

    public static final String EXTRA_EXCHANGE_ID = "exchangeId";

    private FirebaseFirestore db;

    private ImageView btnClose;
    private ImageView imgAvatar;
    private ImageView imgProposedPost;
    private TextView txtSenderName;
    private TextView txtRequestDate;
    private TextView txtProposedTitle;
    private TextView txtProposedDates;
    private TextView txtProposedRating;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_request_swap_detail);

        db = FirebaseFirestore.getInstance();

        btnClose = findViewById(R.id.btnCloseDialog);
        imgAvatar = findViewById(R.id.imgDialogAvatar);
        imgProposedPost = findViewById(R.id.imgProposedPost);
        txtSenderName = findViewById(R.id.txtDialogSenderName);
        txtRequestDate = findViewById(R.id.txtDialogRequestDate);
        txtProposedTitle = findViewById(R.id.txtProposedTitle);
        txtProposedDates = findViewById(R.id.txtProposedDates);
        txtProposedRating = findViewById(R.id.txtProposedRating);

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> finish());
        }

        // Màn hình chỉ xem chi tiết, ẩn các nút thao tác của dialog.
        hideView(R.id.btnDialogReject);
        hideView(R.id.btnDialogSwitchMoney);
        hideView(R.id.btnDialogAccept);

        String exchangeId = getIntent().getStringExtra(EXTRA_EXCHANGE_ID);

        if (exchangeId == null || exchangeId.trim().isEmpty()) {
            Toast.makeText(this, "Không xác định được yêu cầu.",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        loadRequestDetail(exchangeId);
    }

    private void hideView(int id) {
        View view = findViewById(id);
        if (view != null) {
            view.setVisibility(View.GONE);
        }
    }

    private void loadRequestDetail(String exchangeId) {
        db.collection("exchanges")
                .document(exchangeId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        Toast.makeText(this, "Yêu cầu không còn tồn tại.",
                                Toast.LENGTH_LONG).show();
                        finish();
                        return;
                    }

                    String postId = getStringField(doc, "postId", "targetPostId");
                    String proposedPostId =
                            getStringField(doc, "offeredPostId", "proposedPostId");

                    String status = doc.getString("status");
                    String type = doc.getString("exchangeType");

                    txtRequestDate.setText(
                            "Phương thức: " + getMethodLabel(type)
                                    + "\nTrạng thái: "
                                    + (status == null ? "PENDING" : status)
                    );

                    if (postId.isEmpty()) {
                        txtSenderName.setText("Không xác định được bài viết");
                    } else {
                        loadPost(postId, false);
                    }

                    if (proposedPostId.isEmpty()) {
                        txtProposedTitle.setText("Chưa chọn bài viết đề xuất");
                        txtProposedDates.setText("Thời gian chưa cập nhật");
                        txtProposedRating.setText("—");
                    } else {
                        loadPost(proposedPostId, true);
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Không tải được yêu cầu: " + e.getMessage(),
                                Toast.LENGTH_LONG).show()
                );
    }

    private void loadPost(String postId, boolean proposed) {
        db.collection("posts")
                .document(postId)
                .get()
                .addOnSuccessListener(post -> {
                    if (!post.exists()) {
                        if (proposed) {
                            txtProposedTitle.setText("Bài viết đề xuất không còn tồn tại");
                        } else {
                            txtSenderName.setText("Bài viết không còn tồn tại");
                        }
                        return;
                    }

                    String title = valueOrDefault(
                            post.getString("title"), "Bài viết");
                    String image = getStringField(post, "imageBase64", "imageUrl");

                    Object ratingValue = post.get("rating");
                    double rating = ratingValue instanceof Number
                            ? ((Number) ratingValue).doubleValue() : 5.0;

                    if (proposed) {
                        txtProposedTitle.setText(title);
                        txtProposedRating.setText(
                                String.format(java.util.Locale.US, "%.1f", rating));

                        txtProposedDates.setText("Thời gian chưa cập nhật");

                        if (!image.isEmpty() && imgProposedPost != null) {
                            Glide.with(this)
                                    .load(PostImageUtils.asGlideModel(image))
                                    .into(imgProposedPost);
                        }
                    } else {
                        txtSenderName.setText("Yêu cầu gửi tới: " + title);

                        if (!image.isEmpty() && imgAvatar != null) {
                            Glide.with(this)
                                    .load(PostImageUtils.asGlideModel(image))
                                    .into(imgAvatar);
                        }
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Không tải được thông tin bài viết.",
                                Toast.LENGTH_SHORT).show()
                );
    }

    private String getStringField(
            DocumentSnapshot doc, String first, String second) {
        String value = doc.getString(first);
        if (value == null || value.trim().isEmpty()) {
            value = doc.getString(second);
        }
        return value == null ? "" : value;
    }

    private String getMethodLabel(String type) {
        if ("NON_RECIPROCAL".equalsIgnoreCase(type)
                || "MONEY".equalsIgnoreCase(type)) {
            return "Thanh toán bằng tiền";
        }
        return "Trao đổi chỗ ở";
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }
}
