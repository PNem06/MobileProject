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
import androidx.viewpager2.widget.ViewPager2;

import com.example.mobileproject.R;
import com.example.mobileproject.adapter.DetailAdapter;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.SimpleDateFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class Detail extends AppCompatActivity {
    public static final String EXTRA_POST_ID = "postId";

    private FirebaseFirestore db;
    private ListenerRegistration roomListener;
    private ListenerRegistration favoriteListener;
    private String postId = "";
    private SharedPreferences preferences;

    private boolean isFavorite;
    private boolean isExpanded;
    private String fullDescription = "";

    private ViewPager2 viewPagerImages;
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

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.main),
                (v, insets) -> {
                    Insets systemBars = insets.getInsets(
                            WindowInsetsCompat.Type.systemBars()
                    );

                    v.setPadding(
                            systemBars.left,
                            systemBars.top,
                            systemBars.right,
                            systemBars.bottom
                    );

                    return insets;
                }
        );

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
        viewPagerImages = findViewById(R.id.viewPagerImages);
    }

    private void listenToRoomRealtime() {
        if (postId.isEmpty()) return;

        roomListener = db.collection("posts")
                .document(postId)
                .addSnapshotListener((snapshot, error) -> {
                    if (isFinishing() || isDestroyed()) return;

                    if (error != null) {
                        Toast.makeText(
                                this,
                                "Lỗi đọc Firestore: " + error.getLocalizedMessage(),
                                Toast.LENGTH_LONG
                        ).show();
                        return;
                    }

                    if (snapshot == null || !snapshot.exists()) {
                        Toast.makeText(
                                this,
                                "Bài đăng không tồn tại hoặc đã bị xóa",
                                Toast.LENGTH_SHORT
                        ).show();
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

        txtBedInfo.setText(
                bedrooms + " phòng ngủ • "
                        + bathrooms + " phòng tắm • Tối đa "
                        + maxGuests + " khách"
        );

        Date startDate = dateValue(doc, "startDate");
        Date endDate = dateValue(doc, "endDate");

        txtDates.setText(
                "Ngày bắt đầu: " + formatDate(startDate)
                        + "\nNgày kết thúc: " + formatDate(endDate)
        );

        txtRoomCounts.setText(
                "Phòng ngủ: " + bedrooms
                        + "  |  Phòng tắm: " + bathrooms
                        + "\nSố khách tối đa: " + maxGuests
        );

        double latitude = doubleValue(doc, "latitude", 0);
        double longitude = doubleValue(doc, "longitude", 0);

        txtLocation.setText("Vị trí: " + latitude + ", " + longitude);

        txtAdditionalInformation.setText(
                nonEmpty(additionalInfo, "Chưa có thông tin bổ sung.")
        );

        List<String> amenityIds = stringList(doc, "amenityIds");
        loadAmenities(amenityIds);

        fullDescription = nonEmpty(
                description,
                "Chưa có mô tả cho chỗ ở này."
        );

        updateDescription();

        String accountId = stringValue(doc, "accountId");
        if (accountId.isEmpty()) {
            accountId = stringValue(doc, "ownerId");
        }
        loadHostInfo(accountId);

        txtRating.setText("—");
        txtReviewCount.setText("0");
        txtPrice.setText(priceLabel(doc.get("price")));
        txtReviews.setText("Chưa có dữ liệu đánh giá.");
        btnAllReviews.setText("Xem đánh giá");

        loadRoomImages(postId);
    }

    private String stringValue(DocumentSnapshot doc, String field) {
        Object value = doc.get(field);
        return value == null ? "" : String.valueOf(value);
    }

    private String nonEmpty(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private String priceLabel(Object value) {
        if (!(value instanceof Number)) return "Giá chuyến đi chưa được cung cấp.";
        double amount = ((Number) value).doubleValue();
        if (!Double.isFinite(amount) || amount <= 0) return "Giá chuyến đi chưa được cung cấp.";
        NumberFormat format = NumberFormat.getNumberInstance(new Locale("vi", "VN"));
        format.setMaximumFractionDigits(0);
        return format.format(amount) + "VNĐ / chuyến";
    }

    private int intValue(DocumentSnapshot doc, String field, int fallback) {
        Object value = doc.get(field);

        if (value instanceof Number) {
            return ((Number) value).intValue();
        }

        return fallback;
    }

    private double doubleValue(
            DocumentSnapshot doc,
            String field,
            double fallback
    ) {
        Object value = doc.get(field);

        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }

        return fallback;
    }

    private Date dateValue(DocumentSnapshot doc, String field) {
        Object value = doc.get(field);

        if (value instanceof Timestamp) {
            return ((Timestamp) value).toDate();
        }

        if (value instanceof Date) {
            return (Date) value;
        }

        return null;
    }

    private List<String> stringList(DocumentSnapshot doc, String field) {
        List<String> result = new ArrayList<>();
        Object value = doc.get(field);

        if (value instanceof List<?>) {
            for (Object item : (List<?>) value) {
                if (item != null) {
                    result.add(String.valueOf(item));
                }
            }
        }

        return result;
    }

    private void loadRoomImages(String postId) {
        if (postId == null || postId.trim().isEmpty()) {
            txtImageCount.setVisibility(View.GONE);
            return;
        }

        db.collection("images")
                .whereEqualTo("postId", postId)
                .get()
                .addOnSuccessListener(snap -> {
                    if (isFinishing() || isDestroyed()) return;
                    List<String> urls = new ArrayList<>();
                    if (snap != null && !snap.isEmpty()) {
                        for (DocumentSnapshot doc : snap.getDocuments()) {
                            String url = doc.getString("imgUrl");
                            if (url == null || url.trim().isEmpty()) {
                                url = doc.getString("imageUrl");
                            }
                            if (url != null && !url.trim().isEmpty()) {
                                urls.add(url.trim());
                            }
                        }
                    }

                    if (urls.isEmpty()) {
                        db.collection("postImages")
                                .whereEqualTo("postId", postId)
                                .get()
                                .addOnSuccessListener(snap2 -> {
                                    if (isFinishing() || isDestroyed()) return;
                                    if (snap2 != null && !snap2.isEmpty()) {
                                        for (DocumentSnapshot doc : snap2.getDocuments()) {
                                            String url = doc.getString("imgUrl");
                                            if (url == null || url.trim().isEmpty()) {
                                                url = doc.getString("imageUrl");
                                            }
                                            if (url != null && !url.trim().isEmpty()) {
                                                urls.add(url.trim());
                                            }
                                        }
                                    }
                                    setupImagesAdapter(urls);
                                })
                                .addOnFailureListener(e -> setupImagesAdapter(urls));
                    } else {
                        setupImagesAdapter(urls);
                    }
                })
                .addOnFailureListener(e -> setupImagesAdapter(new ArrayList<>()));
    }

    private void setupImagesAdapter(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            txtImageCount.setVisibility(View.GONE);
            return;
        }

        DetailAdapter adapter = new DetailAdapter(urls, true);
        viewPagerImages.setAdapter(adapter);

        txtImageCount.setVisibility(View.VISIBLE);
        txtImageCount.setText("1/" + urls.size());

        viewPagerImages.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                txtImageCount.setText((position + 1) + "/" + urls.size());
            }
        });
    }

    private void loadHostInfo(String hostId) {
        if (hostId == null || hostId.trim().isEmpty()) {
            txtHostName.setText("Chủ nhà");
            txtHostDescription.setText("Thông tin chủ nhà chưa được cung cấp.");
            return;
        }

        db.collection("accounts").document(hostId.trim()).get()
                .addOnSuccessListener(doc -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (doc.exists()) {
                        String name = doc.getString("fullName");
                        if (name == null || name.trim().isEmpty()) {
                            name = doc.getString("username");
                        }
                        if (name == null || name.trim().isEmpty()) {
                            name = "Chủ nhà";
                        }
                        txtHostName.setText(name);

                        String email = doc.getString("email");
                        String phone = doc.getString("phone");
                        StringBuilder desc = new StringBuilder();
                        if (email != null && !email.trim().isEmpty()) {
                            desc.append("Email: ").append(email.trim());
                        }
                        if (phone != null && !phone.trim().isEmpty()) {
                            if (desc.length() > 0) desc.append("\n");
                            desc.append("ĐT: ").append(phone.trim());
                        }
                        if (desc.length() == 0) {
                            desc.append("Thành viên StayExchange");
                        }
                        txtHostDescription.setText(desc.toString());
                    } else {
                        txtHostName.setText("Chủ nhà");
                        txtHostDescription.setText("Thông tin chủ nhà chưa được cung cấp.");
                    }
                })
                .addOnFailureListener(e -> {
                    if (isFinishing() || isDestroyed()) return;
                    txtHostName.setText("Chủ nhà");
                    txtHostDescription.setText("Thông tin chủ nhà chưa được cung cấp.");
                });
    }

    private void loadAmenities(List<String> amenityIds) {
        if (amenityIds == null || amenityIds.isEmpty()) {
            txtAmenities.setText("Chưa có thông tin tiện nghi.");
            return;
        }

        List<Task<DocumentSnapshot>> tasks = new ArrayList<>();
        for (String id : amenityIds) {
            if (id != null && !id.trim().isEmpty()) {
                tasks.add(db.collection("amenities").document(id.trim()).get());
            }
        }

        if (tasks.isEmpty()) {
            txtAmenities.setText("Chưa có thông tin tiện nghi.");
            return;
        }

        Tasks.whenAllSuccess(tasks)
                .addOnSuccessListener(results -> {
                    if (isFinishing() || isDestroyed()) return;
                    List<String> amenityNames = new ArrayList<>();
                    for (Object res : results) {
                        if (res instanceof DocumentSnapshot) {
                            DocumentSnapshot doc = (DocumentSnapshot) res;
                            if (doc.exists()) {
                                String name = doc.getString("name");
                                if (name != null && !name.trim().isEmpty()) {
                                    amenityNames.add(name.trim());
                                }
                            }
                        }
                    }

                    if (amenityNames.isEmpty()) {
                        txtAmenities.setText("Mã tiện nghi:\n• " + TextUtils.join("\n• ", amenityIds));
                    } else {
                        txtAmenities.setText("Tiện nghi:\n• " + TextUtils.join("\n• ", amenityNames));
                    }
                })
                .addOnFailureListener(e -> {
                    if (isFinishing() || isDestroyed()) return;
                    txtAmenities.setText("Mã tiện nghi:\n• " + TextUtils.join("\n• ", amenityIds));
                });
    }

    private String formatDate(Date date) {
        if (date == null) return "Chưa có";

        SimpleDateFormat formatter = new SimpleDateFormat(
                "dd/MM/yyyy HH:mm",
                new Locale("vi", "VN")
        );

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

            favoriteListener = db.collection("favorites")
                    .document(favDocId)
                    .addSnapshotListener((snapshot, error) -> {
                        if (isFinishing() || isDestroyed()) return;

                        if (snapshot != null) {
                            isFavorite = snapshot.exists();

                            preferences.edit()
                                    .putBoolean(postId, isFavorite)
                                    .apply();

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

            db.collection("favorites")
                    .document(favDocId)
                    .set(fav)
                    .addOnSuccessListener(
                            aVoid -> showMessage("Đã thêm vào yêu thích")
                    )
                    .addOnFailureListener(
                            e -> showMessage(
                                    "Lỗi thêm yêu thích: " + e.getMessage()
                            )
                    );
        } else {
            db.collection("favorites")
                    .document(favDocId)
                    .delete()
                    .addOnSuccessListener(
                            aVoid -> showMessage("Đã bỏ khỏi yêu thích")
                    )
                    .addOnFailureListener(
                            e -> showMessage(
                                    "Lỗi xóa yêu thích: " + e.getMessage()
                            )
                    );
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

            intent.putExtra(
                    Intent.EXTRA_TEXT,
                    txtRoomName.getText() + "\n"
                            + txtAddress.getText() + "\n"
                            + txtDescription.getText()
            );

            startActivity(Intent.createChooser(intent, "Chia sẻ chỗ ở"));
        });

        btnShowMore.setOnClickListener(v -> {
            isExpanded = !isExpanded;
            updateDescription();
        });


        findViewById(R.id.btnMessage).setOnClickListener(
                v -> showMessage(
                        "Cần triển khai chức năng nhắn tin với chủ nhà."
                )
        );

        findViewById(R.id.btnBooking).setOnClickListener(
                v -> showMessage(
                        "Cần triển khai quy trình đặt phòng."
                )
        );

        btnAllReviews.setOnClickListener(
                v -> showMessage(
                        "Cần kết nối dữ liệu đánh giá để xem chi tiết."
                )
        );
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
