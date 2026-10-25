package com.example.mobileproject.view;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
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

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import java.text.ParseException;
import android.graphics.Color;
import androidx.core.content.ContextCompat;

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
    private boolean isMyPost = false;
    private boolean postReserved, viewerAdmin;
    private String viewerAccountId = "";
    private String postOwnerId = "";

    private ViewPager2 viewPagerImages;
    private TextView txtRoomName, txtStatusBadge, txtAddress, txtDestination, txtBedInfo;
    private TextView txtRating, txtReviewCount, txtHostName, txtHostEmail, txtHostPhone, txtHostDescription;
    private TextView txtDescription, txtDates, txtRoomCounts;
    private TextView txtAmenities, txtAdditionalInformation, txtLocation;
    private TextView txtPrice, txtImageCount, txtReviews;
    private TextView btnFavorite, btnShowMore, btnAllReviews;

    private ImageView imgHostAvatar;
    private View layoutHostInfo, layoutGuestActions, layoutOwnerActions, layoutEditActions;
    private TextView btnDeletePost, btnEditPost, btnCancelEdit, btnSubmitEdit;
    private EditText edtRoomName, edtAddress, edtDestination, edtDescription, edtPrice;

    private ActivityResultLauncher<Intent> exchangeLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_detail);

        exchangeLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    String uid = FirebaseAuth.getInstance().getUid();
                    checkExistingSentRequest(uid, postId);
                }
        );

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
        String viewerUid = FirebaseAuth.getInstance().getUid();
        if (viewerUid == null) { finish(); return; }
        db.collection("accounts").document(viewerUid).get().addOnSuccessListener(profile -> {
            if (isFinishing() || isDestroyed()) return;
            viewerAccountId = profile.getString("accountId") == null ? viewerUid : profile.getString("accountId");
            viewerAdmin = "ADMIN".equals(profile.getString("role")) && "ACTIVE".equals(profile.getString("status"));
            listenToRoomRealtime();
        }).addOnFailureListener(error -> { if (!isFinishing()) { showMessage("Không thể xác nhận tài khoản. Vui lòng mở lại bài đăng."); finish(); } });
    }

    private void initViews() {
        txtRoomName = findViewById(R.id.txtRoomName);
        txtStatusBadge = findViewById(R.id.txtStatusBadge);
        txtAddress = findViewById(R.id.txtAddress);
        txtDestination = findViewById(R.id.txtDestination);
        txtBedInfo = findViewById(R.id.txtBedInfo);
        txtRating = findViewById(R.id.txtRating);
        txtReviewCount = findViewById(R.id.txtReviewCount);

        layoutHostInfo = findViewById(R.id.layoutHostInfo);
        imgHostAvatar = findViewById(R.id.imgHostAvatar);
        txtHostName = findViewById(R.id.txtHostName);
        txtHostEmail = findViewById(R.id.txtHostEmail);
        txtHostPhone = findViewById(R.id.txtHostPhone);
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

        layoutGuestActions = findViewById(R.id.layoutGuestActions);
        layoutOwnerActions = findViewById(R.id.layoutOwnerActions);
        layoutEditActions = findViewById(R.id.layoutEditActions);

        btnDeletePost = findViewById(R.id.btnDeletePost);
        btnEditPost = findViewById(R.id.btnEditPost);
        btnCancelEdit = findViewById(R.id.btnCancelEdit);
        btnSubmitEdit = findViewById(R.id.btnSubmitEdit);

        edtRoomName = findViewById(R.id.edtRoomName);
        edtAddress = findViewById(R.id.edtAddress);
        edtDestination = findViewById(R.id.edtDestination);
        edtDescription = findViewById(R.id.edtDescription);
        edtPrice = findViewById(R.id.edtPrice);
    }

    private void listenToRoomRealtime() {
        if (postId.isEmpty()) return;

        roomListener = db.collection("posts")
                .document(postId)
                .addSnapshotListener((snapshot, error) -> {
                    if (isFinishing() || isDestroyed()) return;

                    if (error != null) {
                        if (error.getCode() == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                            showMessage("Bài đăng không còn mở cho tài khoản này."); finish(); return;
                        }
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
        postReserved = !stringValue(doc,"bookingKey").isEmpty() || "BOOKED".equals(doc.getString("status"));
        String uid = FirebaseAuth.getInstance().getUid();
        java.util.List<?> parties = doc.get("bookingPartyIds") instanceof java.util.List ? (java.util.List<?>)doc.get("bookingPartyIds") : java.util.Collections.emptyList();
        String owner = nonEmpty(doc.getString("accountId"),doc.getString("ownerId"));
        if (postReserved && !viewerAdmin && !parties.contains(uid) && !parties.contains(viewerAccountId)
                && (uid == null || (!uid.equals(owner) && !viewerAccountId.equals(owner)))) {
            showMessage("Bài đăng đã có trao đổi và không còn mở đặt phòng."); finish(); return;
        }
        String title = stringValue(doc, "title");
        String address = stringValue(doc, "address");
        String destination = stringValue(doc, "destination");
        String description = stringValue(doc, "description");
        String additionalInfo = stringValue(doc, "additionalInformation");

        txtRoomName.setText(nonEmpty(title, "Chi tiết chỗ ở"));

        String status = stringValue(doc, "status");
        if (status.isEmpty()) status = "PENDING";

        if (txtStatusBadge != null) {
            txtStatusBadge.setVisibility(View.GONE);

            if ("ACTIVE".equalsIgnoreCase(status)
                    || "APPROVED".equalsIgnoreCase(status)
                    || "PUBLIC".equalsIgnoreCase(status)) {

                txtStatusBadge.setText("● Đang hoạt động");
                txtStatusBadge.setTextColor(Color.parseColor("#1B5E20"));
                txtStatusBadge.setBackgroundColor(Color.parseColor("#E8F5E9"));

            } else if ("REJECTED".equalsIgnoreCase(status)
                    || "PRIVATE".equalsIgnoreCase(status)) {

                txtStatusBadge.setText("● Bị từ chối");
                txtStatusBadge.setTextColor(Color.parseColor("#B3261E"));
                txtStatusBadge.setBackgroundColor(Color.parseColor("#FDECEA"));

            } else {
                txtStatusBadge.setText("● Chờ Admin duyệt");
                txtStatusBadge.setTextColor(Color.parseColor("#8D4E00"));
                txtStatusBadge.setBackgroundColor(Color.parseColor("#FFF3E0"));
            }
        }

        txtAddress.setText("Địa chỉ: " + nonEmpty(address, "Chưa cập nhật"));
        txtDestination.setText(
                "Điểm đến: " + nonEmpty(destination, nonEmpty(address, "Chưa cập nhật")));

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

        postOwnerId = stringValue(doc, "accountId");
        if (postOwnerId.isEmpty()) {
            postOwnerId = stringValue(doc, "ownerId");
        }

        String currentUid = FirebaseAuth.getInstance().getUid();
        isMyPost = currentUid != null && !currentUid.isEmpty() && (currentUid.equals(postOwnerId) || viewerAccountId.equals(postOwnerId));

        if (isMyPost) {
            if (txtStatusBadge != null) {
                txtStatusBadge.setVisibility(View.VISIBLE);
            }
            if (layoutHostInfo != null) layoutHostInfo.setVisibility(View.GONE);
            if (layoutGuestActions != null) layoutGuestActions.setVisibility(View.GONE);
            if (layoutOwnerActions != null) layoutOwnerActions.setVisibility(View.VISIBLE);
            if (layoutEditActions != null) layoutEditActions.setVisibility(View.GONE);
        } else {
            if (txtStatusBadge != null) {
                txtStatusBadge.setVisibility(View.GONE);
            }
            if (layoutHostInfo != null) {
                layoutHostInfo.setVisibility(View.VISIBLE);
                layoutHostInfo.setOnLongClickListener(v -> {
                    showBlockDialogForOwner(postOwnerId, txtHostName.getText().toString());
                    return true;
                });
            }
            if (layoutGuestActions != null) layoutGuestActions.setVisibility(View.VISIBLE);
            if (layoutOwnerActions != null) layoutOwnerActions.setVisibility(View.GONE);
            if (layoutEditActions != null) layoutEditActions.setVisibility(View.GONE);
            loadHostInfo(postOwnerId);
            checkExistingSentRequest(currentUid, postId);
        }

        if (postReserved) {
            if (layoutGuestActions != null) layoutGuestActions.setVisibility(View.GONE);
            if (layoutOwnerActions != null) layoutOwnerActions.setVisibility(View.GONE);
            if (txtStatusBadge != null) { txtStatusBadge.setVisibility(View.VISIBLE); txtStatusBadge.setText("● Đã có trao đổi"); }
        }
        txtRating.setText("—");
        txtReviewCount.setText("0");
        txtPrice.setText(priceLabel(doc.get("price")));
        txtReviews.setText("Chưa có dữ liệu đánh giá.");
        btnAllReviews.setText("Xem đánh giá");

        loadRoomImages(postId);
    }


    private void showBlockDialogForOwner(String ownerId, String name) {
        String currentUid = FirebaseAuth.getInstance().getUid();
        if (currentUid == null || currentUid.isEmpty() || ownerId == null || ownerId.isEmpty() || currentUid.equals(ownerId)) return;

        String docId = currentUid + "_" + ownerId;
        db.collection("blocks").whereEqualTo("blockerId",currentUid).whereEqualTo("blockedUserId",ownerId).get()
                .addOnSuccessListener(docs -> {
                    boolean isBlocked = !docs.isEmpty();
                    String actionText = isBlocked ? "Bỏ chặn " + name : "Chặn " + name;

                    new AlertDialog.Builder(this)
                            .setTitle(name)
                            .setItems(new String[]{actionText}, (dialog, which) -> {
                                if (isBlocked) {
                                    db.collection("blocks").document(docId).delete()
                                            .addOnSuccessListener(aVoid -> Toast.makeText(this, "Đã bỏ chặn " + name, Toast.LENGTH_SHORT).show());
                                } else {
                                    Map<String, Object> blockData = new HashMap<>();
                                    blockData.put("blockId", docId);
                                    blockData.put("blockerId", currentUid);
                                    blockData.put("blockedUserId", ownerId);
                                    blockData.put("createdAt", FieldValue.serverTimestamp());

                                    db.collection("blocks").document(docId).set(blockData)
                                            .addOnSuccessListener(aVoid -> Toast.makeText(this, "Đã chặn " + name, Toast.LENGTH_SHORT).show());
                                }
                            })
                            .setNegativeButton("Hủy", null)
                            .show();
                });
    }

    private void checkExistingSentRequest(String currentUid, String postId) {
        if (currentUid == null || currentUid.isEmpty()
                || postId == null || postId.isEmpty()) {
            return;
        }

        db.collection("exchanges")
                .whereEqualTo("requester_accountId", currentUid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (isFinishing() || isDestroyed()) return;

                    DocumentSnapshot activeRequest = null;

                    if (snapshot != null && !snapshot.isEmpty()) {
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            String targetId = doc.getString("postId");

                            if (targetId == null || targetId.isEmpty()) {
                                targetId = doc.getString("targetPostId");
                            }

                            if (!postId.equals(targetId)) {
                                continue;
                            }

                            String status = doc.getString("status");

                            if (!"CANCELLED".equalsIgnoreCase(status)
                                    && !"REJECTED".equalsIgnoreCase(status)) {
                                activeRequest = doc;
                                break;
                            }
                        }
                    }

                    TextView btnBooking = findViewById(R.id.btnBooking);

                    if (btnBooking == null) return;

                    if (activeRequest != null) {
                        // Lấy ID chính xác của yêu cầu trong Firestore.
                        String exchangeId = activeRequest.getId();

                        btnBooking.setText("Xem yêu cầu đã gửi");
                        btnBooking.setBackground(
                                ContextCompat.getDrawable(
                                        Detail.this,
                                        R.drawable.bg_btn_primary_blue
                                )
                        );
                        btnBooking.setTextColor(Color.WHITE);

                        // Nhấn một lần để mở thẳng chi tiết yêu cầu.
                        btnBooking.setOnClickListener(v -> {
                            Intent intent = new Intent(
                                    Detail.this,
                                    SentRequestDetailActivity.class
                            );

                            intent.putExtra(
                                    SentRequestDetailActivity.EXTRA_EXCHANGE_ID,
                                    exchangeId
                            );

                            startActivity(intent);
                        });

                    } else {
                        // Không có yêu cầu đang hoạt động thì giữ chức năng đặt phòng.
                        btnBooking.setText("Đặt phòng");
                        btnBooking.setOnClickListener(v -> showBookingOptions());
                    }
                })
                .addOnFailureListener(error -> {
                    if (isFinishing() || isDestroyed()) return;

                    Toast.makeText(
                            Detail.this,
                            "Không kiểm tra được yêu cầu đã gửi: "
                                    + error.getLocalizedMessage(),
                            Toast.LENGTH_SHORT
                    ).show();
                });
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
        return format.format(amount) + " VNĐ / chuyến";
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
        if (isMyPost) {
            if (layoutHostInfo != null) layoutHostInfo.setVisibility(View.GONE);
            return;
        }

        if (hostId == null || hostId.trim().isEmpty()) {
            txtHostName.setText("Chủ nhà");
            if (txtHostEmail != null) txtHostEmail.setText("Email: Chưa có");
            if (txtHostPhone != null) txtHostPhone.setText("SĐT: Chưa có");
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
                        String avatar = doc.getString("avatarUrl");

                        if (txtHostEmail != null) {
                            txtHostEmail.setText("Email: " + (email != null && !email.trim().isEmpty() ? email.trim() : "Chưa cập nhật"));
                        }
                        if (txtHostPhone != null) {
                            txtHostPhone.setText("SĐT: " + (phone != null && !phone.trim().isEmpty() ? phone.trim() : "Chưa cập nhật"));
                        }

                        if (imgHostAvatar != null && avatar != null && !avatar.trim().isEmpty()) {
                            Glide.with(this)
                                    .load(avatar.trim())
                                    .placeholder(R.drawable.ic_user)
                                    .error(R.drawable.ic_user)
                                    .into(imgHostAvatar);
                        } else if (imgHostAvatar != null) {
                            imgHostAvatar.setImageResource(R.drawable.ic_user);
                        }
                    } else {
                        txtHostName.setText("Chủ nhà");
                        if (txtHostEmail != null) txtHostEmail.setText("Email: Chưa cập nhật");
                        if (txtHostPhone != null) txtHostPhone.setText("SĐT: Chưa cập nhật");
                    }
                })
                .addOnFailureListener(e -> {
                    if (isFinishing() || isDestroyed()) return;
                    txtHostName.setText("Chủ nhà");
                    if (txtHostEmail != null) txtHostEmail.setText("Email: Chưa cập nhật");
                    if (txtHostPhone != null) txtHostPhone.setText("SĐT: Chưa cập nhật");
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
            Toast.makeText(this, "Vui lòng đăng nhập để yêu thích", Toast.LENGTH_SHORT).show();
            return;
        }

        if (postId.isEmpty()) return;

        isFavorite = !isFavorite;
        updateFavoriteIcon();

        String favDocId = uid + "_" + postId;

        if (isFavorite) {
            Map<String, Object> favoriteData = new HashMap<>();
            favoriteData.put("userId", uid);
            favoriteData.put("postId", postId);
            favoriteData.put("createdAt", FieldValue.serverTimestamp());

            db.collection("favorites").document(favDocId).set(favoriteData);
        } else {
            db.collection("favorites").document(favDocId).delete();
        }
    }

    private void updateFavoriteIcon() {
        if (btnFavorite != null) {
            btnFavorite.setText(isFavorite ? "♥" : "♡");
            btnFavorite.setTextColor(isFavorite ? Color.RED : Color.parseColor("#111111"));
        }
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

        findViewById(R.id.btnMessage).setOnClickListener(v -> {
            Intent intent = new Intent(Detail.this, ChatDetailActivity.class);
            if (postOwnerId != null && !postOwnerId.isEmpty()) {
                intent.putExtra(ChatDetailActivity.EXTRA_RECIPIENT_ID, postOwnerId);
            }
            startActivity(intent);
        });

        findViewById(R.id.btnBooking).setOnClickListener(
                v -> showBookingOptions()
        );

        btnAllReviews.setOnClickListener(
                v -> showMessage("Cần kết nối dữ liệu đánh giá để xem chi tiết.")
        );

        if (btnDeletePost != null) {
            btnDeletePost.setOnClickListener(v -> confirmDeletePost());
        }

        if (btnEditPost != null) {
            btnEditPost.setOnClickListener(v -> {
                Intent intent = new Intent(Detail.this, New_Post.class);
                intent.putExtra("editPostId", postId);
                startActivity(intent);
            });
        }

        if (btnCancelEdit != null) {
            btnCancelEdit.setOnClickListener(v -> exitEditMode());
        }

        if (btnSubmitEdit != null) {
            btnSubmitEdit.setOnClickListener(v -> submitEditPost());
        }
    }

    private void confirmDeletePost() {
        new AlertDialog.Builder(this)
                .setTitle("Xóa bài đăng")
                .setMessage("Bạn có chắc chắn muốn xóa toàn bộ thông tin bài đăng này không?")
                .setPositiveButton("Xóa", (dialog, which) -> deletePost())
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void deletePost() {
        if (postReserved) { showMessage("Bài đã có trao đổi. Không thể xóa bài đang lưu giao dịch."); return; }
        if (postId.isEmpty()) return;

        db.collection("posts").document(postId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    db.collection("images")
                            .whereEqualTo("postId", postId)
                            .get()
                            .addOnSuccessListener(snapshot -> {
                                if (snapshot != null && !snapshot.isEmpty()) {
                                    for (DocumentSnapshot imgDoc : snapshot.getDocuments()) {
                                        imgDoc.getReference().delete();
                                    }
                                }
                            });

                    Toast.makeText(this, "Đã xóa bài đăng thành công!", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Xóa bài đăng thất bại: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                });
    }


    private void enterEditMode() {
        if (postId == null || postId.trim().isEmpty()) {
            showMessage("Không tìm thấy mã bài đăng.");
            return;
        }

        db.collection("posts").document(postId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (isFinishing() || isDestroyed()) return;

                    if (doc == null || !doc.exists()) {
                        showMessage("Bài đăng không tồn tại.");
                        return;
                    }

                    // Chỉ chủ bài đăng mới được chỉnh sửa.
                    String uid = FirebaseAuth.getInstance().getUid();
                    String owner = doc.getString("accountId");
                    if (owner == null || owner.isEmpty()) {
                        owner = doc.getString("ownerId");
                    }

                    if (uid == null || !uid.equals(owner)) {
                        showMessage("Bạn không có quyền sửa bài đăng này.");
                        return;
                    }

                    showFullEditDialog(doc);
                })
                .addOnFailureListener(e ->
                        showMessage("Không tải được bài đăng: " + e.getMessage()));
    }

    private void showFullEditDialog(DocumentSnapshot doc) {
        ScrollView scroll = new ScrollView(this);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = dpEdit(16);
        form.setPadding(pad, pad, pad, pad);
        scroll.addView(form);

        Map<String, EditText> fields = new HashMap<>();

        addEditField(form, fields, "title", "Tên chỗ ở",
                stringValue(doc, "title"), false);
        addEditField(form, fields, "address", "Địa chỉ",
                stringValue(doc, "address"), false);
        addEditField(form, fields, "destination", "Điểm đến",
                stringValue(doc, "destination"), false);
        addEditField(form, fields, "description", "Mô tả",
                stringValue(doc, "description"), true);
        addEditField(form, fields, "additionalInformation",
                "Thông tin bổ sung",
                stringValue(doc, "additionalInformation"), true);

        addEditField(form, fields, "propertyType", "Loại chỗ ở",
                stringValue(doc, "propertyType"), false);

        addEditField(form, fields, "bedroomCount", "Số phòng ngủ",
                String.valueOf(intValue(doc, "bedroomCount", 0)), false);
        addEditField(form, fields, "bathroomCount", "Số phòng tắm",
                String.valueOf(intValue(doc, "bathroomCount", 0)), false);
        addEditField(form, fields, "maxGuests", "Số khách tối đa",
                String.valueOf(intValue(doc, "maxGuests", 1)), false);

        Object priceValue = doc.get("price");
        addEditField(form, fields, "price", "Giá (VNĐ)",
                priceValue == null ? "" : String.valueOf(priceValue), false);

        addEditField(form, fields, "startDate", "Ngày bắt đầu (dd/MM/yyyy HH:mm)",
                dateForEdit(dateValue(doc, "startDate")), false);
        addEditField(form, fields, "endDate", "Ngày kết thúc (dd/MM/yyyy HH:mm)",
                dateForEdit(dateValue(doc, "endDate")), false);

        addEditField(form, fields, "latitude", "Vĩ độ",
                String.valueOf(doubleValue(doc, "latitude", 0)), false);
        addEditField(form, fields, "longitude", "Kinh độ",
                String.valueOf(doubleValue(doc, "longitude", 0)), false);

        List<String> amenityIds = stringList(doc, "amenityIds");
        addEditField(form, fields, "amenityIds",
                "Mã tiện nghi (các mã cách nhau bằng dấu phẩy)",
                TextUtils.join(", ", amenityIds), true);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Chỉnh sửa toàn bộ bài đăng")
                .setView(scroll)
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Lưu thay đổi", null)
                .create();

        dialog.setOnShowListener(v -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(
                    button -> saveFullEdit(doc, fields, dialog));
        });

        dialog.show();
    }

    private void addEditField(
            LinearLayout parent,
            Map<String, EditText> fields,
            String key,
            String label,
            String value,
            boolean multiline) {

        TextView title = new TextView(this);
        title.setText(label);
        title.setTextSize(14);
        title.setTypeface(null, Typeface.BOLD);

        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = dpEdit(8);
        parent.addView(title, titleParams);

        EditText input = new EditText(this);
        input.setSingleLine(!multiline);
        input.setText(value == null ? "" : value);
        input.setTextSize(15);
        input.setPadding(dpEdit(10), dpEdit(8), dpEdit(10), dpEdit(8));

        if (multiline) {
            input.setMinLines(2);
            input.setGravity(Gravity.TOP | Gravity.START);
        }

        parent.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        fields.put(key, input);
    }

    private String dateForEdit(Date date) {
        if (date == null) return "";

        SimpleDateFormat format = new SimpleDateFormat(
                "dd/MM/yyyy HH:mm", new Locale("vi", "VN"));
        format.setTimeZone(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        return format.format(date);
    }

    private Date parseEditDate(String value) throws ParseException {
        if (value == null || value.trim().isEmpty()) return null;

        SimpleDateFormat format = new SimpleDateFormat(
                "dd/MM/yyyy HH:mm", new Locale("vi", "VN"));
        format.setLenient(false);
        format.setTimeZone(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));

        return format.parse(value.trim());
    }

    private int dpEdit(int value) {
        return Math.round(value * getResources()
                .getDisplayMetrics().density);
    }

    private void saveFullEdit(
            DocumentSnapshot original,
            Map<String, EditText> fields,
            AlertDialog dialog) {

        try {
            String title = editValue(fields, "title");
            String address = editValue(fields, "address");
            String destination = editValue(fields, "destination");
            String description = editValue(fields, "description");

            if (title.isEmpty() || address.isEmpty()
                    || destination.isEmpty() || description.isEmpty()) {
                showMessage("Tên chỗ ở, địa chỉ, điểm đến và mô tả không được để trống.");
                return;
            }

            int bedrooms = Integer.parseInt(editValue(fields, "bedroomCount"));
            int bathrooms = Integer.parseInt(editValue(fields, "bathroomCount"));
            int guests = Integer.parseInt(editValue(fields, "maxGuests"));

            if (bedrooms < 0 || bathrooms < 0 || guests <= 0) {
                showMessage("Số phòng không được âm và số khách phải lớn hơn 0.");
                return;
            }

            String priceText = editValue(fields, "price");
            long price = priceText.isEmpty() ? 0L : Long.parseLong(priceText);
            if (price < 0) {
                showMessage("Giá không được âm.");
                return;
            }

            double latitude = Double.parseDouble(editValue(fields, "latitude"));
            double longitude = Double.parseDouble(editValue(fields, "longitude"));

            if (latitude < -90 || latitude > 90
                    || longitude < -180 || longitude > 180) {
                showMessage("Vĩ độ hoặc kinh độ không hợp lệ.");
                return;
            }

            Date startDate = parseEditDate(editValue(fields, "startDate"));
            Date endDate = parseEditDate(editValue(fields, "endDate"));

            if ((startDate == null) != (endDate == null)) {
                showMessage("Hãy nhập cả ngày bắt đầu và ngày kết thúc.");
                return;
            }

            if (startDate != null && !endDate.after(startDate)) {
                showMessage("Ngày kết thúc phải sau ngày bắt đầu.");
                return;
            }

            List<String> amenities = new ArrayList<>();
            String amenityText = editValue(fields, "amenityIds");
            if (!amenityText.isEmpty()) {
                for (String id : amenityText.split(",")) {
                    String trimmed = id.trim();
                    if (!trimmed.isEmpty() && !amenities.contains(trimmed)) {
                        amenities.add(trimmed);
                    }
                }
            }

            Map<String, Object> updates = new HashMap<>();
            updates.put("title", title);
            updates.put("address", address);
            updates.put("destination", destination);
            updates.put("description", description);
            updates.put("additionalInformation",
                    editValue(fields, "additionalInformation"));
            updates.put("propertyType", editValue(fields, "propertyType"));
            updates.put("bedroomCount", bedrooms);
            updates.put("bathroomCount", bathrooms);
            updates.put("maxGuests", guests);
            updates.put("price", price);
            updates.put("latitude", latitude);
            updates.put("longitude", longitude);
            updates.put("amenityIds", amenities);

            // Không thay đổi các trường chủ sở hữu và thời điểm tạo.
            updates.put("status", "PENDING");

            if (startDate != null) {
                updates.put("startDate", new Timestamp(startDate));
                updates.put("endDate", new Timestamp(endDate));
            } else {
                updates.put("startDate", FieldValue.delete());
                updates.put("endDate", FieldValue.delete());
            }

            db.collection("posts").document(postId)
                    .update(updates)
                    .addOnSuccessListener(unused -> {
                        if (isFinishing() || isDestroyed()) return;

                        Toast.makeText(this,
                                "Đã lưu toàn bộ thông tin. Bài đăng được chuyển về trạng thái chờ duyệt.",
                                Toast.LENGTH_LONG).show();
                        dialog.dismiss();
                    })
                    .addOnFailureListener(e -> {
                        if (isFinishing() || isDestroyed()) return;
                        showMessage("Lưu thất bại: " + e.getLocalizedMessage());
                    });

        } catch (NumberFormatException e) {
            showMessage("Số phòng, số khách, giá hoặc tọa độ không hợp lệ.");
        } catch (ParseException e) {
            showMessage("Ngày phải đúng định dạng dd/MM/yyyy HH:mm, ví dụ 25/10/2026 14:00.");
        }
    }

    private String editValue(Map<String, EditText> fields, String key) {
        EditText input = fields.get(key);
        return input == null ? "" : input.getText().toString().trim();
    }


    private void exitEditMode() {
        txtRoomName.setVisibility(View.VISIBLE);
        txtAddress.setVisibility(View.VISIBLE);
        txtDestination.setVisibility(View.VISIBLE);
        txtDescription.setVisibility(View.VISIBLE);
        txtPrice.setVisibility(View.VISIBLE);

        edtRoomName.setVisibility(View.GONE);
        edtAddress.setVisibility(View.GONE);
        edtDestination.setVisibility(View.GONE);
        edtDescription.setVisibility(View.GONE);
        edtPrice.setVisibility(View.GONE);

        if (layoutOwnerActions != null) layoutOwnerActions.setVisibility(View.VISIBLE);
        if (layoutEditActions != null) layoutEditActions.setVisibility(View.GONE);
    }

    private void submitEditPost() {
        String newTitle = edtRoomName.getText().toString().trim();
        String newAddress = edtAddress.getText().toString().trim();
        String newDest = edtDestination.getText().toString().trim();
        String newDesc = edtDescription.getText().toString().trim();
        String priceStr = edtPrice.getText().toString().trim();

        if (newTitle.isEmpty()) {
            edtRoomName.setError("Nhập tên chỗ ở");
            return;
        }
        if (newAddress.isEmpty()) {
            edtAddress.setError("Nhập địa chỉ");
            return;
        }
        if (newDest.isEmpty()) {
            edtDestination.setError("Nhập điểm đến");
            return;
        }
        if (newDesc.isEmpty()) {
            edtDescription.setError("Nhập mô tả");
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("title", newTitle);
        updates.put("address", newAddress);
        updates.put("destination", newDest);
        updates.put("description", newDesc);
        updates.put("status", "PENDING"); // Đưa về PENDING để chờ Admin duyệt

        if (!priceStr.isEmpty()) {
            try {
                long priceVal = Long.parseLong(priceStr);
                if (priceVal > 0) {
                    updates.put("price", priceVal);
                }
            } catch (NumberFormatException ignored) {}
        }

        db.collection("posts").document(postId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Đã cập nhật bài đăng! Vui lòng chờ Admin kiểm duyệt.", Toast.LENGTH_LONG).show();
                    exitEditMode();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Cập nhật thất bại: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                });
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

    private void showBookingOptions() {
        if (postReserved) { showMessage("Bài đăng đã có trao đổi và không còn mở đặt phòng."); return; }
        String[] options = {
                "Trao đổi phòng",
                "Thuê phòng"
        };

        new AlertDialog.Builder(this)
                .setTitle("Bạn muốn làm gì?")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        openExchangePage();
                    } else if (which == 1) {
                        openRentalPage();
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        String uid = FirebaseAuth.getInstance().getUid();
        checkExistingSentRequest(uid, postId);
    }

    private void openExchangePage() {
        Intent intent = new Intent(this, ExchangeActivity.class);
        intent.putExtra("targetPostId", postId);
        if (exchangeLauncher != null) {
            exchangeLauncher.launch(intent);
        } else {
            startActivity(intent);
        }
    }

    private void openRentalPage() {
        Intent intent = new Intent(this, RentalActivity.class);
        intent.putExtra("targetPostId", postId);
        startActivity(intent);
    }
}
