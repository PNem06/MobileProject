
package com.example.mobileproject.view;

import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
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
import java.util.Locale;

public class AdminManagePostActivity extends AppCompatActivity {

    private FirebaseFirestore db;

    private LinearLayout layoutPendingPosts;
    private TextView txtPendingCount;

    private TextView btnPendingTab;
    private TextView btnPublicTab;
    private TextView btnUnpublicTab;

    private String currentTab = "PENDING";

    private static final String PENDING = "PENDING";
    private static final String PUBLIC = "APPROVED";
    private static final String UNPUBLIC = "UNPUBLIC";
    private static final String REJECTED = "REJECTED";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_admin_manage_post);

        View root = findViewById(R.id.main);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
            );

            v.setPadding(
                    bars.left + dp(16),
                    bars.top + dp(12),
                    bars.right + dp(16),
                    bars.bottom + dp(8)
            );

            return insets;
        });

        db = FirebaseFirestore.getInstance();

        layoutPendingPosts = findViewById(R.id.layoutPendingPosts);
        txtPendingCount = findViewById(R.id.txtPendingCount);

        btnPendingTab = findViewById(R.id.btnPendingTab);
        btnPublicTab = findViewById(R.id.btnPublicTab);
        btnUnpublicTab = findViewById(R.id.btnUnpublicTab);

        btnPendingTab.setOnClickListener(v -> selectTab(PENDING));
        btnPublicTab.setOnClickListener(v -> selectTab(PUBLIC));
        btnUnpublicTab.setOnClickListener(v -> selectTab(UNPUBLIC));

        selectTab(PENDING);
    }

    private int dp(int value) {
        return (int) (value * getResources()
                .getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable roundedBackground(
            int color, int radiusDp, int strokeColor
    ) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));

        if (strokeColor != Color.TRANSPARENT) {
            drawable.setStroke(dp(1), strokeColor);
        }

        return drawable;
    }

    private void selectTab(String tab) {
        currentTab = tab;

        styleTab(btnPendingTab, PENDING.equals(tab));
        styleTab(btnPublicTab, PUBLIC.equals(tab));
        styleTab(btnUnpublicTab, UNPUBLIC.equals(tab));

        loadPosts();
    }

    private void styleTab(TextView tab, boolean selected) {
        tab.setBackground(roundedBackground(
                selected ? Color.rgb(37, 99, 235) : Color.WHITE,
                9,
                selected ? Color.rgb(37, 99, 235)
                        : Color.rgb(229, 231, 235)
        ));

        tab.setTextColor(
                selected ? Color.WHITE : Color.rgb(75, 85, 99)
        );
        tab.setTypeface(null, Typeface.BOLD);
    }

    private String getStatus(DocumentSnapshot doc) {
        String status = doc.getString("status");

        if (status == null || status.trim().isEmpty()) {
            return PENDING;
        }

        status = status.trim().toUpperCase(Locale.ROOT);

        // Tương thích dữ liệu cũ: REJECTED cũng nằm trong Unpublic.
        if (REJECTED.equals(status)) {
            return UNPUBLIC;
        }

        return status;
    }

    private void loadPosts() {
        txtPendingCount.setText("Đang tải danh sách bài đăng...");
        layoutPendingPosts.removeAllViews();

        db.collection("posts")
                .get()
                .addOnSuccessListener(snapshot -> {
                    layoutPendingPosts.removeAllViews();

                    int pendingCount = 0;
                    int publicCount = 0;
                    int unpublicCount = 0;
                    int visibleCount = 0;

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        String status = getStatus(doc);

                        switch (status) {
                            case PENDING:
                                pendingCount++;
                                break;
                            case PUBLIC:
                                publicCount++;
                                break;
                            case UNPUBLIC:
                                unpublicCount++;
                                break;
                        }

                        if (currentTab.equals(status)) {
                            addPostCard(doc, status);
                            visibleCount++;
                        }
                    }

                    txtPendingCount.setText(
                            "Tổng bài đăng: " + snapshot.size()
                                    + "\nChờ duyệt: " + pendingCount
                                    + "    •    Public: " + publicCount
                                    + "    •    Unpublic: " + unpublicCount
                                    + "\nĐang xem: " + visibleCount + " bài"
                    );

                    if (visibleCount == 0) {
                        TextView empty = new TextView(this);
                        empty.setText("Chưa có bài đăng trong mục này");
                        empty.setTextColor(Color.rgb(107, 114, 128));
                        empty.setTextSize(14);
                        empty.setGravity(Gravity.CENTER);
                        empty.setPadding(0, dp(40), 0, dp(40));

                        layoutPendingPosts.addView(empty);
                    }
                })
                .addOnFailureListener(e -> {
                    txtPendingCount.setText(
                            "Không tải được danh sách bài đăng."
                    );

                    Toast.makeText(
                            this,
                            "Lỗi tải dữ liệu: " + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    private void addPostCard(DocumentSnapshot doc, String status) {
        String postId = doc.getId();

        String title = doc.getString("title");
        if (title == null || title.trim().isEmpty()) {
            title = "Không có tiêu đề";
        }

        String address = doc.getString("address");

        if (address == null || address.trim().isEmpty()) {
            address = doc.getString("addressText");
        }

        if (address == null || address.trim().isEmpty()) {
            address = "Chưa có địa chỉ";
        }

        String ownerId = doc.getString("accountId");

        if (ownerId == null) {
            ownerId = doc.getString("ownerId");
        }

        if (ownerId == null) {
            ownerId = "";
        }

        View card = getLayoutInflater().inflate(
                R.layout.item_admin_post_card,
                layoutPendingPosts,
                false
        );

        TextView txtTitle = card.findViewById(R.id.txtPostTitle);
        TextView txtAddress = card.findViewById(R.id.txtPostAddress);
        TextView txtStatus = card.findViewById(R.id.txtPostStatus);

        // XML dùng TextView làm nút theo giao diện cũ.
        TextView btnApprove = card.findViewById(R.id.btnApprove);
        TextView btnReject = card.findViewById(R.id.btnReject);
        TextView btnUnpublic = card.findViewById(R.id.btnUnpublic);
        TextView btnMakePublic = card.findViewById(R.id.btnMakePublic);

        LinearLayout layoutPendingActions =
                card.findViewById(R.id.layoutPendingActions);

        txtTitle.setText(title);
        txtAddress.setText("Địa chỉ: " + address);
        txtStatus.setText(getStatusLabel(status));
        txtStatus.setTextColor(getStatusColor(status));
        txtStatus.setBackground(roundedBackground(
                getStatusBackground(status), 8, Color.TRANSPARENT
        ));

        layoutPendingActions.setVisibility(View.GONE);
        btnUnpublic.setVisibility(View.GONE);
        btnMakePublic.setVisibility(View.GONE);

        final String finalOwnerId = ownerId;
        final String finalTitle = title;

        switch (status) {
            case PENDING:
                layoutPendingActions.setVisibility(View.VISIBLE);

                btnApprove.setOnClickListener(v ->
                        updatePostStatus(
                                postId, PUBLIC, finalOwnerId,
                                finalTitle, true
                        )
                );

                // Từ chối chuyển thẳng sang UNPUBLIC.
                btnReject.setText("Từ chối");
                btnReject.setOnClickListener(v ->
                        updatePostStatus(
                                postId, UNPUBLIC, finalOwnerId,
                                finalTitle, false
                        )
                );
                break;

            case PUBLIC:
                btnUnpublic.setVisibility(View.VISIBLE);

                btnUnpublic.setOnClickListener(v ->
                        updatePostStatus(
                                postId, UNPUBLIC, finalOwnerId,
                                finalTitle, null
                        )
                );
                break;

            case UNPUBLIC:
                btnMakePublic.setVisibility(View.VISIBLE);

                btnMakePublic.setOnClickListener(v ->
                        updatePostStatus(
                                postId, PUBLIC, finalOwnerId,
                                finalTitle, null
                        )
                );
                break;
        }

        layoutPendingPosts.addView(card);
    }

    private String getStatusLabel(String status) {
        switch (status) {
            case PENDING:
                return "●  PENDING - Chờ duyệt";
            case PUBLIC:
                return "●  APPROVED - Public";
            case UNPUBLIC:
                return "●  UNPUBLIC - Đã ẩn / từ chối";
            default:
                return status;
        }
    }

    private int getStatusColor(String status) {
        switch (status) {
            case PENDING:
                return Color.rgb(161, 98, 7);
            case PUBLIC:
                return Color.rgb(21, 128, 61);
            case UNPUBLIC:
                return Color.rgb(75, 85, 99);
            default:
                return Color.DKGRAY;
        }
    }

    private int getStatusBackground(String status) {
        switch (status) {
            case PENDING:
                return Color.rgb(254, 243, 199);
            case PUBLIC:
                return Color.rgb(220, 252, 231);
            case UNPUBLIC:
                return Color.rgb(243, 244, 246);
            default:
                return Color.LTGRAY;
        }
    }

    private void updatePostStatus(
            String postId,
            String newStatus,
            String ownerId,
            String postTitle,
            Boolean isApproval
    ) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", newStatus);
        updates.put("updatedAt", FieldValue.serverTimestamp());

        db.collection("posts").document(postId)
                .update(updates)
                .addOnSuccessListener(unused -> {
                    if (ownerId != null
                            && !ownerId.isEmpty()
                            && isApproval != null) {
                        sendNotification(
                                ownerId, postTitle, isApproval
                        );
                    }

                    String message;

                    if (PUBLIC.equals(newStatus)) {
                        message = "Bài đăng đã chuyển sang Public.";
                    } else if (UNPUBLIC.equals(newStatus)) {
                        message = Boolean.FALSE.equals(isApproval)
                                ? "Đã từ chối bài đăng và chuyển sang Unpublic."
                                : "Bài đăng đã chuyển sang Unpublic.";
                    } else {
                        message = "Đã cập nhật trạng thái bài đăng.";
                    }

                    Toast.makeText(
                            this, message, Toast.LENGTH_SHORT
                    ).show();

                    loadPosts();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(
                                this,
                                "Cập nhật thất bại: " + e.getMessage(),
                                Toast.LENGTH_LONG
                        ).show()
                );
    }

    private void sendNotification(
            String ownerId,
            String postTitle,
            boolean isApproved
    ) {
        Map<String, Object> notif = new HashMap<>();

        notif.put("userId", ownerId);
        notif.put(
                "type",
                isApproved ? "post_approved" : "post_rejected"
        );
        notif.put(
                "title",
                isApproved
                        ? "Bài viết đã được duyệt"
                        : "Bài viết bị từ chối"
        );
        notif.put(
                "body",
                isApproved
                        ? "Bài đăng '" + postTitle
                          + "' đã được duyệt và công khai."
                        : "Bài đăng '" + postTitle
                          + "' đã bị từ chối và chuyển sang Unpublic."
        );
        notif.put("isRead", false);
        notif.put("createdAt", FieldValue.serverTimestamp());

        db.collection("notifications")
                .add(notif)
                .addOnFailureListener(e ->
                        android.util.Log.e(
                                "AdminManagePost",
                                "Không gửi được thông báo",
                                e
                        )
                );
    }
}
