package com.example.mobileproject.view;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mobileproject.MainActivity;
import com.example.mobileproject.R;
import com.example.mobileproject.adapter.RequestAdapter;
import com.example.mobileproject.model.RequestItem;
import com.example.mobileproject.repository.ExchangeRepository;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.FieldValue;
import java.util.HashMap;
import java.util.Map;

import java.util.ArrayList;
import java.util.List;

public class Request extends AppCompatActivity {

    private ImageView btnBack;
    private LinearLayout tabDaGui, tabDaNhan;
    private TextView txtDaGui, txtDaNhan;
    private View indicatorDaGui, indicatorDaNhan;

    private LinearLayout layoutSummaryReceived;
    private TextView txtTotalReceivedCount, txtTotalRejectedCount;

    private RecyclerView recyclerRequests;
    private LinearLayout layoutEmpty;
    private TextView txtEmptyMessage;

    private LinearLayout navExplore, navRequest, navMessage, navAccount;

    private RequestAdapter adapter;
    private final List<RequestItem> currentDisplayList = new ArrayList<>();
    private final List<RequestItem> sentList = new ArrayList<>();
    private final List<RequestItem> receivedList = new ArrayList<>();

    private boolean isSentTabActive = true;
    private final ExchangeRepository exchangeRepository = new ExchangeRepository();
    private String currentAccountId = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_request);

        initViews();
        setupRecyclerView();
        setupBottomNav();
        setupTabs();
        selectTab(true);
    }

    @Override protected void onResume() { super.onResume(); if (adapter != null) loadFirebaseData(); }

    private void openWalletBooking(RequestItem item) {
        Intent next = new Intent(this, WalletActivity.class);
        next.putExtra(WalletActivity.EXTRA_BOOKING_ID, item.exchangeId);
        startActivity(next);
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);

        tabDaGui = findViewById(R.id.tabDaGui);
        tabDaNhan = findViewById(R.id.tabDaNhan);
        txtDaGui = findViewById(R.id.txtDaGui);
        txtDaNhan = findViewById(R.id.txtDaNhan);
        indicatorDaGui = findViewById(R.id.indicatorDaGui);
        indicatorDaNhan = findViewById(R.id.indicatorDaNhan);

        layoutSummaryReceived = findViewById(R.id.layoutSummaryReceived);
        txtTotalReceivedCount = findViewById(R.id.txtTotalReceivedCount);
        txtTotalRejectedCount = findViewById(R.id.txtTotalRejectedCount);

        recyclerRequests = findViewById(R.id.recyclerRequests);
        layoutEmpty = findViewById(R.id.layoutEmpty);
        txtEmptyMessage = findViewById(R.id.txtEmptyMessage);

        navExplore = findViewById(R.id.navExplore);
        navRequest = findViewById(R.id.navRequest);
        navMessage = findViewById(R.id.navMessage);
        navAccount = findViewById(R.id.navAccount);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }
    }

    private void setupRecyclerView() {
        recyclerRequests.setLayoutManager(new LinearLayoutManager(this));

        adapter = new RequestAdapter(this, currentDisplayList, new RequestAdapter.OnRequestListener() {
            @Override
            public void onItemClick(RequestItem item) {
                if (item.walletManaged && item.isSentTab) { openWalletBooking(item); return; }
                if (!item.isSentTab) {
                    openReceivedDetailActivity(item);
                } else {
                    Toast.makeText(Request.this, "Chi tiết yêu cầu: " + item.postTitle, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onLeftButtonClick(RequestItem item) {
                if (item.walletManaged && item.isSentTab) { openWalletBooking(item); return; }
                if (item.isSentTab) {
                    // Nút "Hủy" hoặc "Liên hệ"
                    if ("ACCEPTED".equalsIgnoreCase(item.status) || "CANCELLED".equalsIgnoreCase(item.status)) {
                        Toast.makeText(Request.this, "Đang kết nối đến chủ nhà...", Toast.LENGTH_SHORT).show();
                    } else {
                        updateExchangeStatus(item, "CANCELLED", "Đã hủy yêu cầu.");
                    }
                } else {
                    // Tab Đã nhận -> Nút "Xem"
                    openReceivedDetailActivity(item);
                }
            }

            @Override
            public void onRightButtonClick(RequestItem item) {
                if (!item.isSentTab) { openReceivedDetailActivity(item); return; }
                if (item.walletManaged) { openWalletBooking(item); return; }
                if (item.isSentTab) {
                    if ("PENDING".equalsIgnoreCase(item.status)) {
                        if (item.hasMoneyAlternativePending) {
                            Intent payment = new Intent(Request.this, RentalActivity.class);
                            payment.putExtra("targetPostId", item.postId);
                            payment.putExtra(WalletActivity.EXTRA_LEGACY_ID, item.exchangeId);
                            startActivity(payment);
                        } else {
                            Toast.makeText(Request.this, "Chức năng chỉnh sửa cần nối với màn hình sửa yêu cầu hiện có.", Toast.LENGTH_SHORT).show();
                        }
                    } else if ("ACCEPTED".equalsIgnoreCase(item.status)) {
                        Toast.makeText(Request.this, "Tiếp tục quy trình trao đổi", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    // Tab Đã nhận
                    if ("PENDING".equalsIgnoreCase(item.status)) {
                        updateExchangeStatus(item, "ACCEPTED", "Đã chấp nhận yêu cầu.");
                    } else {
                        Toast.makeText(Request.this, "Tiếp tục quy trình trao đổi", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });

        recyclerRequests.setAdapter(adapter);
    }

    private void openReceivedDetailActivity(RequestItem item) {
        Intent intent = new Intent(Request.this, ReceivedRequestsDetailActivity.class);
        intent.putExtra(ReceivedRequestsDetailActivity.EXTRA_REQUEST_ITEM, item);
        startActivity(intent);
    }

    private void setupTabs() {
        tabDaGui.setOnClickListener(v -> selectTab(true));
        tabDaNhan.setOnClickListener(v -> selectTab(false));
    }

    private void selectTab(boolean isSent) {
        isSentTabActive = isSent;

        if (isSent) {
            txtDaGui.setTextColor(Color.parseColor("#0B1354"));
            txtDaGui.setTypeface(null, android.graphics.Typeface.BOLD);
            indicatorDaGui.setVisibility(View.VISIBLE);

            txtDaNhan.setTextColor(Color.parseColor("#888888"));
            txtDaNhan.setTypeface(null, android.graphics.Typeface.NORMAL);
            indicatorDaNhan.setVisibility(View.INVISIBLE);

            layoutSummaryReceived.setVisibility(View.GONE);

            currentDisplayList.clear();
            currentDisplayList.addAll(sentList);

        } else {
            txtDaNhan.setTextColor(Color.parseColor("#0B1354"));
            txtDaNhan.setTypeface(null, android.graphics.Typeface.BOLD);
            indicatorDaNhan.setVisibility(View.VISIBLE);

            txtDaGui.setTextColor(Color.parseColor("#888888"));
            txtDaGui.setTypeface(null, android.graphics.Typeface.NORMAL);
            indicatorDaGui.setVisibility(View.INVISIBLE);

            layoutSummaryReceived.setVisibility(View.VISIBLE);

            // Tính tổng số yêu cầu & tổng số đã từ chối
            int totalRequests = 0;
            int totalRejected = 0;
            for (RequestItem item : receivedList) {
                totalRequests += item.subRequestsCount;
                totalRejected += item.rejectedSubRequestsCount;
            }
            txtTotalReceivedCount.setText("Tổng số yêu cầu " + totalRequests);
            txtTotalRejectedCount.setText("Đã từ chối " + totalRejected);

            currentDisplayList.clear();
            currentDisplayList.addAll(receivedList);
        }

        if (currentDisplayList.isEmpty()) {
            recyclerRequests.setVisibility(View.GONE);
            layoutEmpty.setVisibility(View.VISIBLE);
            txtEmptyMessage.setText(isSent ? "Bạn chưa gửi yêu cầu nào." : "Bạn chưa nhận được yêu cầu nào.");
        } else {
            recyclerRequests.setVisibility(View.VISIBLE);
            layoutEmpty.setVisibility(View.GONE);
        }

        adapter.notifyDataSetChanged();
    }

    private void setupBottomNav() {
        if (navExplore != null) {
            navExplore.setOnClickListener(v -> {
                Intent intent = new Intent(Request.this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
            });
        }

        if (navMessage != null) {
            navMessage.setOnClickListener(v -> {
                Intent intent = new Intent(Request.this, Chat.class);
                startActivity(intent);
            });
        }

        if (navAccount != null) {
            navAccount.setOnClickListener(v -> {
                Intent intent = new Intent(Request.this, MainActivity.class);
                intent.putExtra(MainActivity.EXTRA_HOME_PAGE, "account");
                startActivity(intent);
            });
        }
    }

    private void loadFirebaseData() {
        sentList.clear(); receivedList.clear(); currentDisplayList.clear();
        exchangeRepository.resolveCurrentAccountId((accountId, accountError) -> {
            if (accountError != null || accountId == null) {
                showError(accountError != null ? accountError : new Exception("Không xác định được tài khoản."));
                selectTab(isSentTabActive); return;
            }
            currentAccountId = accountId;
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            db.collection("exchanges").whereEqualTo("requester_accountId", accountId).get()
                    .addOnSuccessListener(q -> {
                        for (DocumentSnapshot doc : q.getDocuments()) {
                            RequestItem item = mapExchange(doc, true);
                            sentList.add(item);
                            loadPostInfo(item, null);
                        }
                        selectTab(isSentTabActive);
                    }).addOnFailureListener(this::showError);
            db.collection("exchanges").whereEqualTo("host_accountId", accountId).get()
                    .addOnSuccessListener(q -> loadReceivedGrouped(q))
                    .addOnFailureListener(this::showError);
        });
    }

    private RequestItem mapExchange(DocumentSnapshot doc, boolean sent) {
        RequestItem item = new RequestItem();
        item.exchangeId = doc.getId();
        item.subRequestId = doc.getId();
        item.requesterId = stringField(doc, "requester_accountId", "requesterId");
        item.hostId = stringField(doc, "host_accountId", "hostId");
        item.postId = doc.getString("postId");
        item.status = safeString(doc.getString("status"), "PENDING");
        item.exchangeType = safeString(doc.getString("exchangeType"), "RECIPROCAL");
        item.isSentTab = sent;
        item.startDateText = dateText(doc.get("startDate"));
        item.endDateText = dateText(doc.get("endDate"));
        item.dateRangeText = item.startDateText.isEmpty() ? "Thời gian chưa cập nhật" : item.startDateText + (item.endDateText.isEmpty() ? "" : " - " + item.endDateText);
        item.walletManaged = Boolean.TRUE.equals(doc.getBoolean("walletManaged"));
        item.paymentStatus = safeString(doc.getString("paymentStatus"), "");
        item.paymentId = safeString(doc.getString("paymentId"), "");
        item.isPaid = "PAID".equals(item.paymentStatus) || "RELEASED".equals(item.paymentStatus);
        item.hasMoneyAlternativePending = !item.walletManaged && !item.isPaid && "NON_RECIPROCAL".equals(item.exchangeType) && "PENDING".equals(item.status);
        return item;
    }

    private void loadReceivedGrouped(QuerySnapshot snapshot) {
        Map<String, RequestItem> groups = new HashMap<>();
        Map<String, Integer> rejected = new HashMap<>();
        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            RequestItem sub = mapExchange(doc, false);
            String postId = sub.postId;
            if (postId == null || postId.isEmpty()) continue;
            RequestItem group = groups.get(postId);
            if (group == null) {
                group = new RequestItem(); group.isSentTab = false; group.postId = postId;
                group.hostId = currentAccountId; group.status = sub.status;
                group.subRequestsList.clear(); groups.put(postId, group);
            }
            group.subRequestsList.add(sub);
            group.subRequestsCount++;
            if ("REJECTED".equals(sub.status)) {
                group.rejectedSubRequestsCount++;
                rejected.put(postId, group.rejectedSubRequestsCount);
            }
            if ("PENDING".equals(sub.status)) group.status = "PENDING";
        }
        receivedList.clear(); receivedList.addAll(groups.values());
        for (RequestItem group : receivedList) loadPostInfo(group, () -> selectTab(isSentTabActive));
        if (receivedList.isEmpty()) selectTab(isSentTabActive);
    }

    private void loadPostInfo(RequestItem item, Runnable done) {
        if (item.postId == null || item.postId.isEmpty()) { if (done != null) done.run(); return; }
        FirebaseFirestore.getInstance().collection("posts").document(item.postId).get()
                .addOnSuccessListener(post -> {
                    if (post.exists()) {
                        item.postTitle = safeString(post.getString("title"), "Bài viết");
                        item.locationText = safeString(post.getString("address"), safeString(post.getString("location"), ""));
                        Object rating = post.get("rating"); if (rating instanceof Number) item.rating = ((Number) rating).doubleValue();
                        Object image = post.get("imageBase64");
                        if (!(image instanceof String)) image = post.get("imageUrl");
                        if (image instanceof String) item.postImageUrl = (String) image;
                    }
                    loadPostImageFromCollection("images", item, () -> {
                        if (item.postImageUrl == null || item.postImageUrl.isEmpty()) {
                            loadPostImageFromCollection("postImages", item, () -> {
                                if (done != null) done.run();
                                if (adapter != null) adapter.notifyDataSetChanged();
                            });
                        } else {
                            if (done != null) done.run();
                            if (adapter != null) adapter.notifyDataSetChanged();
                        }
                    });
                }).addOnFailureListener(e -> { if (done != null) done.run(); });
    }

    private void loadPostImageFromCollection(String collection, RequestItem item, Runnable done) {
        FirebaseFirestore.getInstance().collection(collection)
                .whereEqualTo("postId", item.postId)
                .limit(1)
                .get()
                .addOnSuccessListener(images -> {
                    if (!images.isEmpty()) {
                        DocumentSnapshot imageDoc = images.getDocuments().get(0);
                        String value = imageDoc.getString("imgUrl");
                        if (value == null || value.isEmpty()) value = imageDoc.getString("imageUrl");
                        if (value != null && !value.isEmpty()) {
                            item.postImageUrl = value;
                            if (done != null) done.run();
                            if (adapter != null) adapter.notifyDataSetChanged();
                            return;
                        }
                    }
                    if (done != null) done.run();
                })
                .addOnFailureListener(e -> {
                    if (done != null) done.run();
                });
    }

    private String stringField(DocumentSnapshot doc, String first, String second) {
        String value = doc.getString(first); return value == null ? safeString(doc.getString(second), "") : value;
    }
    private String safeString(String value, String fallback) { return value == null || value.trim().isEmpty() ? fallback : value; }
    private String dateText(Object value) {
        if (value instanceof com.google.firebase.Timestamp) return new java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(((com.google.firebase.Timestamp) value).toDate());
        if (value instanceof java.util.Date) return new java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format((java.util.Date) value);
        return "";
    }
    private void updateExchangeStatus(RequestItem item, String status, String message) {
        Map<String, Object> changes = new HashMap<>(); changes.put("status", status);
        exchangeRepository.updateExchange(item.exchangeId, changes, error -> {
            if (error != null) showError(error);
            else { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); loadFirebaseData(); }
        });
    }
    private void showError(Exception error) {
        Toast.makeText(this, "Lỗi Firebase: " + error.getMessage(), Toast.LENGTH_LONG).show();
    }
}
