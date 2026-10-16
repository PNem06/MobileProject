package com.example.mobileproject.view;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.mobileproject.R;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class DETAIL extends AppCompatActivity {
    public static final String EXTRA_POST_ID = "postId";

    private FirebaseFirestore db;
    private ListenerRegistration roomListener;
    private ListenerRegistration favoriteListener;
    private String postId = "";
    private SharedPreferences preferences;

    private boolean isFavorite;
    private boolean isExpanded;
    private String fullDescription = "";

    private TextView txtRoomName, txtAddress, txtBedInfo;
    private TextView txtRating, txtReviewCount, txtHostName, txtHostDescription;
    private TextView txtDescription, txtDates, txtRoomCounts;
    private TextView txtAmenities, txtAdditionalInformation, txtLocation;
    private TextView txtPrice, txtImageCount, txtReviews;
    private TextView btnFavorite, btnShowMore, btnAllReviews;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_detail);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        if (getIntent() != null) {
            postId = getIntent().getStringExtra(EXTRA_POST_ID);
            if (postId == null) {
                postId = getIntent().getStringExtra("postId");
            }
        }
        if (postId == null) postId = "";

        db = FirebaseFirestore.getInstance();
        preferences = getSharedPreferences("room_favorites", MODE_PRIVATE);

        initViews();
        setupEvents();
        setupFavorite();
        listenToRoomRealtime();
    }

    private void initViews() {
        txtRoomName = findViewById(R.id.txtRoomName);
        txtAddress = findViewById(R.id.txtAddress);
        txtBedInfo = findViewById(R.id.txtBedInfo);
        txtRating = findViewById(R.id.txtRating);
        txtReviewCount = findViewById(R.id.txtReviewCount);
        txtHostName = findViewById(R.id.txtHostName);
        txtHostDescription = findViewById(R.id.txtHostDescription);
        txtDescription = findViewById(R.id.txtDescription);
        txtDates = findViewById(R.id.txtDates);
        txtRoomCounts = findViewById(R.id.txtRoomCounts);
        txtAmenities = findViewById(R.id.txtAmenities);
        txtAdditionalInformation = findViewById(R.id.txtAdditionalInformation);
        txtLocation = findViewById(R.id.txtLocation);
        txtPrice = findViewById(R.id.txtPrice);
        txtImageCount = findViewById(R.id.txtImageCount);
        txtReviews = findViewById(R.id.txtReviews);
        btnFavorite = findViewById(R.id.btnFavorite);
        btnShowMore = findViewById(R.id.btnShowMore);
        btnAllReviews = findViewById(R.id.btnAllReviews);
    }

    private void listenToRoomRealtime() {
        if (postId.isEmpty()) return;
        roomListener = db.collection("posts")
                .document(postId)
                .addSnapshotListener((snapshot, error) -> {
                    if (isFinishing() || isDestroyed()) return;

                    if (error != null) {
                        Toast.makeText(this,
                                "Lỗi đọc Firestore: " + error.getLocalizedMessage(),
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    if (snapshot == null || !snapshot.exists()) {
                        Toast.makeText(this,
                                "Bài đăng không tồn tại hoặc đã bị xóa",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    renderRoom(snapshot);
                });
    }

    private void renderRoom(DocumentSnapshot doc) {
        String title = stringValue(doc, "title");
        String address = stringValue(doc, "address");
        String description = stringValue(doc, "description");
        String additionalInfo = stringValue(doc, "additionalInformation");

        txtRoomName.setText(nonEmpty(title, "Chi tiết chỗ ở"));
        txtAddress.setText(nonEmpty(address, "Chưa có địa chỉ"));

        int bedrooms = intValue(doc, "bedroomCount", 0);
        int bathrooms = intValue(doc, "bathroomCount", 0);
        int maxGuests = intValue(doc, "maxGuests", 0);

        txtBedInfo.setText(bedrooms + " phòng ngủ • "
                + bathrooms + " phòng tắm • Tối đa "
                + maxGuests + " khách");

        Date startDate = dateValue(doc, "startDate");
        Date endDate = dateValue(doc, "endDate");
        txtDates.setText("Ngày bắt đầu: " + formatDate(startDate)
                + "\nNgày kết thúc: " + formatDate(endDate));

        txtRoomCounts.setText("Phòng ngủ: " + bedrooms
                + "  |  Phòng tắm: " + bathrooms
                + "\nSố khách tối đa: " + maxGuests);

        double latitude = doubleValue(doc, "latitude", 0);
        double longitude = doubleValue(doc, "longitude", 0);
        txtLocation.setText("Vị trí: " + latitude + ", " + longitude);

        txtAdditionalInformation.setText(
                nonEmpty(additionalInfo, "Chưa có thông tin bổ sung."));

        List<String> amenityIds = stringList(doc, "amenityIds");
        txtAmenities.setText(amenityIds.isEmpty()
                ? "Chưa có thông tin tiện nghi."
                : "Mã tiện nghi:\n• " + TextUtils.join("\n• ", amenityIds));

        fullDescription = nonEmpty(description, "Chưa có mô tả cho chỗ ở này.");
        updateDescription();

        txtRating.setText("—");
        txtReviewCount.setText("0");
        txtHostName.setText("Chủ nhà");
        txtHostDescription.setText("Thông tin chủ nhà chưa được cung cấp.");
        txtPrice.setText("Giá phòng chưa được cung cấp.");
        txtReviews.setText("Chưa có dữ liệu đánh giá.");
        btnAllReviews.setText("Xem đánh giá");

        txtImageCount.setVisibility(View.GONE);
    }

    private String stringValue(DocumentSnapshot doc, String field) {
        Object value = doc.get(field);
        return value == null ? "" : String.valueOf(value);
    }

    private String nonEmpty(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private int intValue(DocumentSnapshot doc, String field, int fallback) {
        Number value = doc.get(field, Number.class);
        return value == null ? fallback : value.intValue();
    }

    private double doubleValue(DocumentSnapshot doc, String field, double fallback) {
        Number value = doc.get(field, Number.class);
        return value == null ? fallback : value.doubleValue();
    }

    private Date dateValue(DocumentSnapshot doc, String field) {
        Object value = doc.get(field);
        if (value instanceof Timestamp) return ((Timestamp) value).toDate();
        if (value instanceof Date) return (Date) value;
        return null;
    }

    private List<String> stringList(DocumentSnapshot doc, String field) {
        List<String> result = new ArrayList<>();
        Object value = doc.get(field);
        if (value instanceof List<?>) {
            for (Object item : (List<?>) value) {
                if (item != null) result.add(String.valueOf(item));
            }
        }
        return result;
    }

    private String formatDate(Date date) {
        if (date == null) return "Chưa có";
        SimpleDateFormat formatter = new SimpleDateFormat(
                "dd/MM/yyyy HH:mm", new Locale("vi", "VN"));
        formatter.setTimeZone(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        return formatter.format(date);
    }

    private void updateDescription() {
        txtDescription.setText(fullDescription);
        if (isExpanded) {
            txtDescription.setMaxLines(Integer.MAX_VALUE);
            txtDescription.setEllipsize(null);
            btnShowMore.setText("Thu gọn");
        } else {
            txtDescription.setMaxLines(5);
            txtDescription.setEllipsize(TextUtils.TruncateAt.END);
            btnShowMore.setText("Hiển thị thêm");
        }

        txtDescription.post(() -> {
            if (txtDescription.getLineCount() <= 5) {
                btnShowMore.setVisibility(View.GONE);
            } else {
                btnShowMore.setVisibility(View.VISIBLE);
            }
        });
    }

    private void setupFavorite() {
        isFavorite = preferences.getBoolean(postId, false);
        updateFavoriteIcon();

        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null && !postId.isEmpty()) {
            String favDocId = uid + "_" + postId;
            favoriteListener = db.collection("favorites").document(favDocId)
                    .addSnapshotListener((snapshot, error) -> {
                        if (isFinishing() || isDestroyed()) return;
                        if (snapshot != null) {
                            isFavorite = snapshot.exists();
                            preferences.edit().putBoolean(postId, isFavorite).apply();
                            updateFavoriteIcon();
                        }
                    });
        }

        btnFavorite.setOnClickListener(v -> toggleFavorite());
    }

    private void toggleFavorite() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            showMessage("Vui lòng đăng nhập để lưu yêu thích.");
            return;
        }
        if (postId.isEmpty()) {
            showMessage("Không xác định được bài đăng.");
            return;
        }

        boolean nextFavoriteState = !isFavorite;
        String favDocId = uid + "_" + postId;

        if (nextFavoriteState) {
            Map<String, Object> fav = new HashMap<>();
            fav.put("favoriteId", favDocId);
            fav.put("userId", uid);
            fav.put("postId", postId);
            fav.put("createdAt", FieldValue.serverTimestamp());

            db.collection("favorites").document(favDocId).set(fav)
                    .addOnSuccessListener(aVoid -> showMessage("Đã thêm vào yêu thích"))
                    .addOnFailureListener(e -> showMessage("Lỗi thêm yêu thích: " + e.getMessage()));
        } else {
            db.collection("favorites").document(favDocId).delete()
                    .addOnSuccessListener(aVoid -> showMessage("Đã bỏ khỏi yêu thích"))
                    .addOnFailureListener(e -> showMessage("Lỗi xóa yêu thích: " + e.getMessage()));
        }
    }

    private void updateFavoriteIcon() {
        btnFavorite.setText(isFavorite ? "♥" : "♡");
        btnFavorite.setTextColor(isFavorite ? Color.RED : Color.BLACK);
    }

    private void setupEvents() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        findViewById(R.id.btnShare).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT,
                    txtRoomName.getText() + "\n"
                            + txtAddress.getText() + "\n"
                            + txtDescription.getText());
            startActivity(Intent.createChooser(intent, "Chia sẻ chỗ ở"));
        });

        btnShowMore.setOnClickListener(v -> {
            isExpanded = !isExpanded;
            updateDescription();
        });

        findViewById(R.id.btnAvailability).setOnClickListener(v ->
                showMessage("Cần kết nối nghiệp vụ để kiểm tra phòng trống."));

        findViewById(R.id.btnMessage).setOnClickListener(v ->
                showMessage("Cần triển khai chức năng nhắn tin với chủ nhà."));

        findViewById(R.id.btnBooking).setOnClickListener(v ->
                showMessage("Cần triển khai quy trình đặt phòng."));

        btnAllReviews.setOnClickListener(v ->
                showMessage("Cần kết nối dữ liệu đánh giá để xem chi tiết."));
    }

    private void showMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        if (roomListener != null) {
            roomListener.remove();
            roomListener = null;
        }
        if (favoriteListener != null) {
            favoriteListener.remove();
            favoriteListener = null;
        }
        super.onDestroy();
    }
}
