package com.example.mobileproject.view;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.mobileproject.R;
import com.example.mobileproject.util.PostImageUtils;
import com.example.mobileproject.adapter.ReceivedSubRequestAdapter;
import com.example.mobileproject.model.RequestItem;
import com.example.mobileproject.repository.ExchangeRepository;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import java.util.HashMap;
import java.util.Map;
import com.google.android.material.imageview.ShapeableImageView;

import java.util.ArrayList;
import java.util.List;

public class ReceivedRequestsDetailActivity extends AppCompatActivity {
    public static final String EXTRA_REQUEST_ITEM = "EXTRA_REQUEST_ITEM";
    public static final String EXTRA_FOCUS_REQUEST_ID = "focusRequestId";
    public static final String EXTRA_FOCUS_COLLECTION = "focusRequestCollection";
    private boolean focusOpened;

    private ImageView btnBack;
    private TextView txtHeaderPostTitle;
    private ImageView imgMainPost;
    private TextView txtPostDateRange, txtPostRating, txtPostLocation;
    private TextView chipFilterAll, chipFilterSwap, chipFilterMoney;
    private RecyclerView recyclerSubRequests;

    private ReceivedSubRequestAdapter adapter;
    private final List<RequestItem> allSubRequests = new ArrayList<>();
    private final List<RequestItem> visibleSubRequests = new ArrayList<>();

    private RequestItem mainRequestItem;
    private final ExchangeRepository repository = new ExchangeRepository();
    private int loadGeneration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_received_requests_detail);

        focusOpened = savedInstanceState != null && savedInstanceState.getBoolean("noticeFocusOpened");
        initViews();
        loadIntentData();
        setupEvents();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        txtHeaderPostTitle = findViewById(R.id.txtHeaderPostTitle);
        imgMainPost = findViewById(R.id.imgMainPost);
        txtPostDateRange = findViewById(R.id.txtPostDateRange);
        txtPostRating = findViewById(R.id.txtPostRating);
        txtPostLocation = findViewById(R.id.txtPostLocation);

        chipFilterAll = findViewById(R.id.chipFilterAll);
        chipFilterSwap = findViewById(R.id.chipFilterSwap);
        chipFilterMoney = findViewById(R.id.chipFilterMoney);

        recyclerSubRequests = findViewById(R.id.recyclerSubRequests);
        recyclerSubRequests.setLayoutManager(new LinearLayoutManager(this));

        adapter = new ReceivedSubRequestAdapter(this, visibleSubRequests, this::showDetailDialog);
        recyclerSubRequests.setAdapter(adapter);
    }

    private void loadIntentData() {
        final int generation = ++loadGeneration;
        mainRequestItem = (RequestItem) getIntent()
                .getSerializableExtra(EXTRA_REQUEST_ITEM);

        if (mainRequestItem == null
                || mainRequestItem.postId == null
                || mainRequestItem.postId.isEmpty()) {
            Toast.makeText(this, "Không xác định được bài viết.",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        txtHeaderPostTitle.setText(
                mainRequestItem.postTitle == null
                        ? "Yêu cầu đã nhận"
                        : mainRequestItem.postTitle
        );

        txtPostDateRange.setText(
                mainRequestItem.dateRangeText == null
                        ? ""
                        : mainRequestItem.dateRangeText
        );

        txtPostRating.setText(
                mainRequestItem.rating > 0
                        ? String.format(java.util.Locale.US, "%.1f",
                        mainRequestItem.rating)
                        : "—"
        );

        txtPostLocation.setText(
                mainRequestItem.locationText == null
                        ? ""
                        : mainRequestItem.locationText
        );

        if (mainRequestItem.postImageUrl != null
                && !mainRequestItem.postImageUrl.isEmpty()) {
            Glide.with(this)
                    .load(PostImageUtils.asGlideModel(
                            mainRequestItem.postImageUrl))
                    .into(imgMainPost);
        }

        allSubRequests.clear();
        visibleSubRequests.clear();
        adapter.notifyDataSetChanged();

        repository.resolveCurrentAccountId((accountId, error) -> {
            if (generation != loadGeneration || isFinishing() || isDestroyed()) return;
            if (error != null) {
                Toast.makeText(this,
                        "Không xác định được tài khoản: " + error.getMessage(),
                        Toast.LENGTH_LONG).show();
                return;
            }

            FirebaseFirestore db = FirebaseFirestore.getInstance();
            db.collection("exchangeRequests").whereEqualTo("recipientId", accountId).get().addOnSuccessListener(rows -> {
                if (generation != loadGeneration || isFinishing() || isDestroyed()) return;
                for (DocumentSnapshot doc : rows.getDocuments()) addReceivedRequestIfMatches(doc, accountId, mainRequestItem.postId);
                updateFilterCountsAndList("ALL");
            });

            // Truy vấn riêng theo host_accountId.
            db.collection("exchanges")
                    .whereEqualTo("host_accountId", accountId)
                    .get()
                    .addOnSuccessListener(snapshot -> {
                        if (generation != loadGeneration || isFinishing() || isDestroyed()) return;
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            addReceivedRequestIfMatches(
                                    doc, accountId, mainRequestItem.postId);
                        }

                        // Truy vấn riêng theo hostId.
                        db.collection("exchanges")
                                .whereEqualTo("hostId", accountId)
                                .get()
                                .addOnSuccessListener(snapshot2 -> {
                                    if (generation != loadGeneration || isFinishing() || isDestroyed()) return;
                                    for (DocumentSnapshot doc :
                                            snapshot2.getDocuments()) {
                                        addReceivedRequestIfMatches(
                                                doc, accountId,
                                                mainRequestItem.postId);
                                    }

                                    updateFilterCountsAndList("ALL");
                                })
                                .addOnFailureListener(e -> {
                                    updateFilterCountsAndList("ALL");
                                    Toast.makeText(this,
                                            "Không tải được yêu cầu theo hostId: "
                                                    + e.getMessage(),
                                            Toast.LENGTH_LONG).show();
                                });
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this,
                                    "Không tải được yêu cầu theo host_accountId: "
                                            + e.getMessage(),
                                    Toast.LENGTH_LONG).show()
                    );
        });
    }

    private void addReceivedRequestIfMatches(
            DocumentSnapshot doc,
            String accountId,
            String targetPostId) {

        String host = doc.getString("host_accountId");

        if (host == null || host.isEmpty()) {
            host = doc.getString("hostId");
        }

        if (host == null || host.isEmpty()) host = doc.getString("recipientId");

        String postId = doc.getString("postId");

        if (postId == null || postId.isEmpty()) {
            postId = doc.getString("targetPostId");
        }

        if (!accountId.equals(host)
                || postId == null
                || !targetPostId.equals(postId)) {
            return;
        }

        // Tránh thêm trùng khi cùng một document có cả hai trường host.
        for (RequestItem existing : allSubRequests) {
            if (doc.getId().equals(existing.exchangeId) && doc.getReference().getParent().getId().equals(existing.sourceCollection)) {
                return;
            }
        }

        RequestItem sub = new RequestItem();

        sub.exchangeId = doc.getId();
        sub.sourceCollection = doc.getReference().getParent().getId();
        sub.subRequestId = doc.getId();
        sub.postId = postId;
        sub.requesterId = doc.getString("requester_accountId");

        if (sub.requesterId == null || sub.requesterId.isEmpty()) {
            sub.requesterId = doc.getString("requesterId");
        }

        if (sub.requesterId == null || sub.requesterId.isEmpty()) {
            sub.requesterId = doc.getString("senderId");
        }

        sub.hostId = host;

        String status = doc.getString("status");
        sub.status = status == null ? "PENDING" : status.toUpperCase(java.util.Locale.ROOT);

        String type = doc.getString("exchangeType");
        sub.exchangeType =
                "NON_RECIPROCAL".equalsIgnoreCase(type)
                        || "MONEY".equalsIgnoreCase(type)
                        ? "MONEY"
                        : "RECIPROCAL";

        sub.requestDateText = "Yêu cầu " + sub.status;
        sub.walletManaged = Boolean.TRUE.equals(doc.getBoolean("walletManaged"));
        sub.paymentStatus = doc.getString("paymentStatus") == null ? "" : doc.getString("paymentStatus");
        sub.paymentId = doc.getString("paymentId") == null ? "" : doc.getString("paymentId");
        sub.isPaid = "PAID".equals(sub.paymentStatus) || "RELEASED".equals(sub.paymentStatus);

        String offeredPostId = doc.getString("offeredPostId");

        if (offeredPostId == null || offeredPostId.isEmpty()) {
            offeredPostId = doc.getString("proposedPostId");
        }

        sub.proposedPostId = offeredPostId;

        allSubRequests.add(sub);

        loadSenderInfo(sub);
        loadProposedPostInfo(sub);
    }

    private void loadProposedPostInfo(RequestItem item) {
        if (item.proposedPostId == null || item.proposedPostId.isEmpty()) return;
        FirebaseFirestore.getInstance().collection("posts").document(item.proposedPostId).get()
                .addOnSuccessListener(post -> {
                    if (post.exists()) {
                        item.proposedPostTitle = post.getString("title");
                        Object rating = post.get("rating");
                        if (rating instanceof Number) item.proposedPostRating = ((Number) rating).doubleValue();
                        String img = post.getString("imageUrl");
                        if (img != null && !img.isEmpty()) item.proposedPostImageUrl = img;
                    }
                    FirebaseFirestore.getInstance().collection("images")
                            .whereEqualTo("postId", item.proposedPostId).limit(1).get()
                            .addOnSuccessListener(images -> {
                                if (images != null && !images.isEmpty()) {
                                    String url = images.getDocuments().get(0).getString("imgUrl");
                                    if (url == null || url.isEmpty()) url = images.getDocuments().get(0).getString("imageUrl");
                                    if (url != null && !url.isEmpty()) item.proposedPostImageUrl = url;
                                }
                                adapter.notifyDataSetChanged();
                            }).addOnFailureListener(e -> adapter.notifyDataSetChanged());
                });
    }

    private void loadSenderInfo(RequestItem item) {
        if (item.requesterId == null || item.requesterId.isEmpty()) return;
        FirebaseFirestore.getInstance().collection("accounts").document(item.requesterId).get()
                .addOnSuccessListener(account -> {
                    if (account.exists()) {
                        item.senderName = account.getString("fullName");
                        item.senderAvatarUrl = account.getString("avatarUrl");
                    }
                    adapter.notifyDataSetChanged();
                });
    }

    private void setupEvents() {
        btnBack.setOnClickListener(v -> finish());

        chipFilterAll.setOnClickListener(v -> updateFilterCountsAndList("ALL"));
        chipFilterSwap.setOnClickListener(v -> updateFilterCountsAndList("SWAP"));
        chipFilterMoney.setOnClickListener(v -> updateFilterCountsAndList("MONEY"));
    }

    private void updateFilterCountsAndList(String filterType) {
        int swapCount = 0;
        int moneyCount = 0;

        for (RequestItem item : allSubRequests) {
            if ("MONEY".equalsIgnoreCase(item.exchangeType)) {
                moneyCount++;
            } else {
                swapCount++;
            }
        }

        chipFilterAll.setText("Tất cả (" + allSubRequests.size() + ")");
        chipFilterSwap.setText("Trao đổi chỗ ở (" + swapCount + ")");
        chipFilterMoney.setText("Thanh toán (" + moneyCount + ")");

        // Cập nhật giao diện Chip
        resetChipStyles();
        if ("SWAP".equals(filterType)) {
            chipFilterSwap.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_btn_primary_blue));
            chipFilterSwap.setTextColor(Color.WHITE);
        } else if ("MONEY".equals(filterType)) {
            chipFilterMoney.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_btn_primary_blue));
            chipFilterMoney.setTextColor(Color.WHITE);
        } else {
            chipFilterAll.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_btn_primary_blue));
            chipFilterAll.setTextColor(Color.WHITE);
        }

        // Filter danh sách
        visibleSubRequests.clear();
        for (RequestItem item : allSubRequests) {
            if ("SWAP".equals(filterType) && !"MONEY".equalsIgnoreCase(item.exchangeType)) {
                visibleSubRequests.add(item);
            } else if ("MONEY".equals(filterType) && "MONEY".equalsIgnoreCase(item.exchangeType)) {
                visibleSubRequests.add(item);
            } else if ("ALL".equals(filterType)) {
                visibleSubRequests.add(item);
            }
        }

        adapter.notifyDataSetChanged();
        if (!focusOpened && !isFinishing() && !isDestroyed()) {
            String focusedId = getIntent().getStringExtra(EXTRA_FOCUS_REQUEST_ID);
            String collection = getIntent().getStringExtra(EXTRA_FOCUS_COLLECTION);
            if (focusedId != null) for (RequestItem item : allSubRequests) {
                if (focusedId.equals(item.exchangeId) && (collection == null || collection.equals(item.sourceCollection))) {
                    focusOpened = true;
                    showDetailDialog(item);
                    break;
                }
            }
        }
    }

    private void resetChipStyles() {
        TextView[] chips = {chipFilterAll, chipFilterSwap, chipFilterMoney};
        for (TextView chip : chips) {
            chip.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_btn_light_gray));
            chip.setTextColor(Color.parseColor("#333333"));
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (mainRequestItem != null) loadIntentData();
    }

    private void showDetailDialog(RequestItem subRequest) {
        if (subRequest.walletManaged) {
            Intent next = new Intent(this, WalletActivity.class);
            next.putExtra(WalletActivity.EXTRA_BOOKING_ID, subRequest.exchangeId);
            startActivity(next);
            return;
        }
        if ("MONEY".equalsIgnoreCase(subRequest.exchangeType)) {
            showMoneyDialog(subRequest);
        } else {
            showSwapDialog(subRequest);
        }
    }

    private void showSwapDialog(RequestItem subRequest) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_request_swap_detail);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        ImageView btnClose = dialog.findViewById(R.id.btnCloseDialog);
        ShapeableImageView imgAvatar = dialog.findViewById(R.id.imgDialogAvatar);
        TextView txtSenderName = dialog.findViewById(R.id.txtDialogSenderName);
        TextView txtRequestDate = dialog.findViewById(R.id.txtDialogRequestDate);

        TextView txtProposedTitle = dialog.findViewById(R.id.txtProposedTitle);
        TextView txtProposedDates = dialog.findViewById(R.id.txtProposedDates);
        TextView txtProposedRating = dialog.findViewById(R.id.txtProposedRating);
        ImageView imgProposedPost = dialog.findViewById(R.id.imgProposedPost);

        TextView btnReject = dialog.findViewById(R.id.btnDialogReject);
        TextView btnSwitchMoney = dialog.findViewById(R.id.btnDialogSwitchMoney);
        TextView btnAccept = dialog.findViewById(R.id.btnDialogAccept);

        txtSenderName.setText(valueOrDefault(subRequest.senderName, "Người dùng"));
        txtRequestDate.setText(valueOrDefault(subRequest.requestDateText, "Thời gian chưa có"));

        txtProposedTitle.setText(valueOrDefault(subRequest.proposedPostTitle, "Chưa có bài viết đề xuất"));
        txtProposedDates.setText(valueOrDefault(subRequest.proposedPostDates, "Chưa cập nhật thời gian"));
        txtProposedRating.setText(String.format(java.util.Locale.US, "%.1f", subRequest.proposedPostRating > 0 ? subRequest.proposedPostRating : 0));

        if (subRequest.senderAvatarUrl != null && !subRequest.senderAvatarUrl.isEmpty()) {
            Glide.with(this).load(subRequest.senderAvatarUrl).into(imgAvatar);
        }
        if (subRequest.proposedPostImageUrl != null && !subRequest.proposedPostImageUrl.isEmpty()) {
            Glide.with(this).load(PostImageUtils.asGlideModel(subRequest.proposedPostImageUrl)).into(imgProposedPost);
        }

        btnClose.setOnClickListener(v -> dialog.dismiss());

        btnReject.setOnClickListener(v -> updateStatus(subRequest, "REJECTED", dialog));
        btnSwitchMoney.setOnClickListener(v -> {
            Map<String, Object> changes = new HashMap<>();
            changes.put("exchangeType", "NON_RECIPROCAL");
            changes.put("status", "PENDING");
            repository.updateExchange(this, subRequest.sourceCollection, subRequest.exchangeId, changes, error -> {
                if (error != null) Toast.makeText(this, "Cập nhật thất bại: " + error.getMessage(), Toast.LENGTH_LONG).show();
                else { Toast.makeText(this, "Đã chuyển sang thanh toán bằng tiền.", Toast.LENGTH_SHORT).show(); dialog.dismiss(); loadIntentData(); }
            });
        });
        btnAccept.setOnClickListener(v -> updateStatus(subRequest, "ACCEPTED", dialog));

        dialog.show();
    }

    private void showMoneyDialog(RequestItem subRequest) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_request_money_detail);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        ImageView btnClose = dialog.findViewById(R.id.btnCloseMoneyDialog);
        ShapeableImageView imgAvatar = dialog.findViewById(R.id.imgMoneyAvatar);
        TextView txtSenderName = dialog.findViewById(R.id.txtMoneySenderName);
        TextView txtRequestDate = dialog.findViewById(R.id.txtMoneyRequestDate);

        TextView txtTargetTitle = dialog.findViewById(R.id.txtMoneyTargetTitle);
        TextView txtTargetDates = dialog.findViewById(R.id.txtMoneyTargetDates);
        TextView txtTargetRating = dialog.findViewById(R.id.txtMoneyTargetRating);
        ImageView imgTargetPost = dialog.findViewById(R.id.imgMoneyTargetPost);

        TextView btnReject = dialog.findViewById(R.id.btnMoneyReject);
        TextView btnAccept = dialog.findViewById(R.id.btnMoneyAccept);

        txtSenderName.setText(valueOrDefault(subRequest.senderName, "Người dùng"));
        txtRequestDate.setText(valueOrDefault(subRequest.requestDateText, "Thời gian chưa có"));

        txtTargetTitle.setText(valueOrDefault(subRequest.proposedPostTitle, "Chưa có bài viết đề xuất"));
        txtTargetDates.setText(valueOrDefault(subRequest.proposedPostDates, "Chưa cập nhật thời gian"));
        txtTargetRating.setText(String.format(java.util.Locale.US, "%.1f", subRequest.proposedPostRating > 0 ? subRequest.proposedPostRating : 0));

        if (subRequest.senderAvatarUrl != null && !subRequest.senderAvatarUrl.isEmpty()) {
            Glide.with(this).load(subRequest.senderAvatarUrl).into(imgAvatar);
        }
        if (subRequest.proposedPostImageUrl != null && !subRequest.proposedPostImageUrl.isEmpty()) {
            Glide.with(this).load(PostImageUtils.asGlideModel(subRequest.proposedPostImageUrl)).into(imgTargetPost);
        }

        btnClose.setOnClickListener(v -> dialog.dismiss());
        btnAccept.setEnabled(subRequest.isPaid);
        if (!subRequest.isPaid) btnAccept.setText("Chờ khách thanh toán ví");
        if (!subRequest.isPaid) btnAccept.setText("Chờ khách thanh toán ví");

        btnReject.setOnClickListener(v -> updateStatus(subRequest, "REJECTED", dialog));
        btnAccept.setOnClickListener(v -> updateStatus(subRequest, "ACCEPTED", dialog));

        dialog.show();
    }

    private void updateStatus(RequestItem item, String status, Dialog dialog) {
        Map<String, Object> changes = new HashMap<>(); changes.put("status", status);
        repository.updateExchange(this, item.sourceCollection, item.exchangeId, changes, error -> {
            if (error != null) Toast.makeText(this, "Cập nhật thất bại: " + error.getMessage(), Toast.LENGTH_LONG).show();
            else { Toast.makeText(this, status.equals("ACCEPTED") ? "Đã chấp nhận yêu cầu." : "Đã từ chối yêu cầu.", Toast.LENGTH_SHORT).show(); dialog.dismiss(); loadIntentData(); }
        });
    }

    private String valueOrDefault(String val, String fallback) {
        return (val == null || val.trim().isEmpty()) ? fallback : val;
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putBoolean("noticeFocusOpened", focusOpened);
        super.onSaveInstanceState(out);
    }

}
