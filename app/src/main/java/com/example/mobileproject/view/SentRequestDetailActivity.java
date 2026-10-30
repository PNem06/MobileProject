
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
    public static final String EXTRA_SOURCE_COLLECTION = "sourceCollection";
    private String sourceCollection = "exchanges";

    private FirebaseFirestore db;

    private ImageView btnClose;
    private ImageView imgAvatar;
    private ImageView imgProposedPost;
    private TextView txtSenderName;
    private TextView txtRequestDate;
    private TextView txtProposedTitle;
    private TextView txtProposedAddress;
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
        txtProposedAddress = findViewById(R.id.txtProposedAddress);
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

        String source = getIntent().getStringExtra(EXTRA_SOURCE_COLLECTION);
        if ("exchangeRequests".equals(source)) sourceCollection = source;
        loadRequestDetail(exchangeId);
    }

    private void hideView(int id) {
        View view = findViewById(id);
        if (view != null) {
            view.setVisibility(View.GONE);
        }
    }

    private void loadRequestDetail(String exchangeId) {
        db.collection(sourceCollection)
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

                    String startDateStr = dateText(doc.get("startDate"));
                    String endDateStr = dateText(doc.get("endDate"));
                    String docDates = !startDateStr.isEmpty() ? startDateStr + (endDateStr.isEmpty() ? "" : " - " + endDateStr) : "";

                    if (postId.isEmpty()) {
                        txtSenderName.setText("Không xác định được bài viết");
                    } else {
                        loadPost(postId, false, docDates);
                    }

                    if (proposedPostId.isEmpty()) {
                        txtProposedTitle.setText("Chưa chọn bài viết đề xuất");
                        if (txtProposedAddress != null) txtProposedAddress.setText("Chưa cập nhật địa chỉ");
                        txtProposedDates.setText(!docDates.isEmpty() ? docDates : "Thời gian chưa cập nhật");
                        txtProposedRating.setText("—");
                    } else {
                        loadPost(proposedPostId, true, docDates);
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Không tải được yêu cầu: " + e.getMessage(),
                                Toast.LENGTH_LONG).show()
                );
    }

    private void loadPost(String postId, boolean proposed, String fallbackDates) {
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

                    String title = valueOrDefault(post.getString("title"), "Bài viết");
                    String image = getStringField(post, "imageBase64", "imageUrl");
                    if (image.isEmpty()) image = getStringField(post, "image", "imgUrl");

                    String location = valueOrDefault(post.getString("address"), post.getString("location"));

                    String start = dateText(post.get("startDate"));
                    if (start.isEmpty()) start = dateText(post.get("availableFrom"));
                    String end = dateText(post.get("endDate"));
                    if (end.isEmpty()) end = dateText(post.get("availableTo"));
                    String dates = !start.isEmpty() ? start + (end.isEmpty() ? "" : " - " + end) : fallbackDates;

                    Object ratingValue = post.get("rating");
                    double rating = ratingValue instanceof Number
                            ? ((Number) ratingValue).doubleValue() : 5.0;

                    if (proposed) {
                        txtProposedTitle.setText(title);
                        if (txtProposedAddress != null) {
                            txtProposedAddress.setText(valueOrDefault(location, "Chưa cập nhật địa chỉ"));
                        }
                        txtProposedDates.setText(valueOrDefault(dates, "Thời gian chưa cập nhật"));
                        txtProposedRating.setText(
                                String.format(java.util.Locale.US, "%.1f", rating));

                        if (!image.isEmpty() && imgProposedPost != null) {
                            PostImageUtils.loadInto(imgProposedPost, image, R.drawable.bg_blank_canvas);
                        } else {
                            fetchImageFromCollection(postId, imgProposedPost);
                        }
                    } else {
                        txtSenderName.setText("Yêu cầu gửi tới: " + title);

                        if (!image.isEmpty() && imgAvatar != null) {
                            PostImageUtils.loadInto(imgAvatar, image, R.drawable.ic_user);
                        } else {
                            fetchImageFromCollection(postId, imgAvatar);
                        }
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Không tải được thông tin bài viết.",
                                Toast.LENGTH_SHORT).show()
                );
    }

    private void fetchImageFromCollection(String postId, ImageView targetImageView) {
        if (targetImageView == null) return;
        db.collection("images")
                .whereEqualTo("postId", postId).limit(1).get()
                .addOnSuccessListener(images -> {
                    if (images != null && !images.isEmpty()) {
                        DocumentSnapshot doc = images.getDocuments().get(0);
                        String url = doc.getString("imgUrl");
                        if (url == null || url.isEmpty()) url = doc.getString("imageUrl");
                        if (url == null || url.isEmpty()) url = doc.getString("imageBase64");
                        if (url != null && !url.isEmpty()) {
                            PostImageUtils.loadInto(targetImageView, url, R.drawable.bg_blank_canvas);
                            return;
                        }
                    }
                    db.collection("postImages")
                            .whereEqualTo("postId", postId).limit(1).get()
                            .addOnSuccessListener(pImages -> {
                                if (pImages != null && !pImages.isEmpty()) {
                                    DocumentSnapshot doc = pImages.getDocuments().get(0);
                                    String url = doc.getString("imgUrl");
                                    if (url == null || url.isEmpty()) url = doc.getString("imageUrl");
                                    if (url == null || url.isEmpty()) url = doc.getString("imageBase64");
                                    if (url != null && !url.isEmpty()) {
                                        PostImageUtils.loadInto(targetImageView, url, R.drawable.bg_blank_canvas);
                                    }
                                }
                            });
                });
    }

    private String dateText(Object value) {
        if (value instanceof com.google.firebase.Timestamp) {
            return new java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(((com.google.firebase.Timestamp) value).toDate());
        }
        if (value instanceof java.util.Date) {
            return new java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format((java.util.Date) value);
        }
        if (value instanceof Long) {
            return new java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(new java.util.Date((Long) value));
        }
        if (value instanceof String) {
            return (String) value;
        }
        return "";
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
