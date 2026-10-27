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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.mobileproject.MainActivity;
import com.example.mobileproject.R;
import com.example.mobileproject.adapter.RequestAdapter;
import com.example.mobileproject.model.RequestItem;
import com.example.mobileproject.repository.ExchangeRepository;
import com.example.mobileproject.util.PostImageUtils;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.FieldValue;
import java.util.HashMap;
import java.util.Map;

import java.util.ArrayList;
import java.util.List;

public class RequestsFragment extends TabContentFragment {
    public RequestsFragment() { super(R.layout.activity_request); }

    private ImageView btnBack, btnFilter;
    private LinearLayout tabDaGui, tabDaNhan;
    private TextView txtDaGui, txtDaNhan;
    private View indicatorDaGui, indicatorDaNhan;

    private LinearLayout layoutSummaryReceived;
    private TextView txtTotalReceivedCount, txtTotalRejectedCount;

    private RecyclerView recyclerRequests;
    private LinearLayout layoutEmpty;
    private TextView txtEmptyMessage;


    private RequestAdapter adapter;
    private final List<RequestItem> currentDisplayList = new ArrayList<>();
    private final List<RequestItem> sentList = new ArrayList<>();
    private final List<RequestItem> receivedList = new ArrayList<>();

    private boolean isSentTabActive = true;
    private final ExchangeRepository exchangeRepository = new ExchangeRepository();
    private String currentAccountId = "";
    private int loadGeneration;
    private int activeFilter;

    @Override
    public void onViewCreated(@androidx.annotation.NonNull View view, @androidx.annotation.Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews();
        setupRecyclerView();
        setupTabs();

        isSentTabActive = savedInstanceState == null || savedInstanceState.getBoolean("sentTab", true);
        activeFilter = savedInstanceState == null ? 0 : savedInstanceState.getInt("requestFilter");

        selectTab(isSentTabActive);
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


        btnFilter = findViewById(R.id.btnFilter);
        if (btnFilter != null) {
            btnFilter.setOnClickListener(v -> showFilterDialog());
        }

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> backToExplore());
        }
    }

    private void showFilterDialog() {
        String[] filterOptions = {"Tất cả", "Đang chờ", "Đã chấp nhận", "Đã hủy / Từ chối"};
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Lọc trạng thái yêu cầu")
                .setItems(filterOptions, (dialog, which) -> filterCurrentTabList(which))
                .show();
    }

    private void filterCurrentTabList(int filterIndex) {
        activeFilter = filterIndex;
        List<RequestItem> sourceList = isSentTabActive ? sentList : receivedList;
        currentDisplayList.clear();

        for (RequestItem item : sourceList) {
            String status = item.status == null ? "PENDING" : item.status.toUpperCase();
            if (filterIndex == 0) {
                currentDisplayList.add(item);
            } else if (filterIndex == 1 && "PENDING".equals(status)) {
                currentDisplayList.add(item);
            } else if (filterIndex == 2 && "ACCEPTED".equals(status)) {
                currentDisplayList.add(item);
            } else if (filterIndex == 3 && ("CANCELLED".equals(status) || "REJECTED".equals(status))) {
                currentDisplayList.add(item);
            }
        }

        if (currentDisplayList.isEmpty()) {
            recyclerRequests.setVisibility(View.GONE);
            layoutEmpty.setVisibility(View.VISIBLE);
            txtEmptyMessage.setText("Không tìm thấy yêu cầu phù hợp.");
        } else {
            recyclerRequests.setVisibility(View.VISIBLE);
            layoutEmpty.setVisibility(View.GONE);
        }

        adapter.notifyDataSetChanged();
    }

    private void setupRecyclerView() {
        recyclerRequests.setLayoutManager(new LinearLayoutManager(requireContext()));

        adapter = new RequestAdapter(requireContext(), currentDisplayList, new RequestAdapter.OnRequestListener() {
            @Override
            public void onItemClick(RequestItem item) {
                if (item.walletManaged && item.isSentTab) { openWalletBooking(item); return; }
                if (!item.isSentTab) {
                    openReceivedDetailActivity(item);
                } else {
                    showSentDetailDialog(item);
                }
            }

            @Override
            public void onLeftButtonClick(RequestItem item) {
                if (item.walletManaged && item.isSentTab) { openWalletBooking(item); return; }
                if (item.isSentTab) {
                    // Nút "Hủy" hoặc "Liên hệ"
                    if ("PENDING".equalsIgnoreCase(item.status)) {
                        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                                .setTitle("Hủy yêu cầu")
                                .setMessage("Bạn có chắc chắn muốn hủy yêu cầu trao đổi này không?")
                                .setPositiveButton("Hủy yêu cầu", (dialog, which) -> updateExchangeStatus(item, "CANCELLED", "Đã hủy yêu cầu trao đổi."))
                                .setNegativeButton("Đóng", null)
                                .show();
                    } else if ("ACCEPTED".equalsIgnoreCase(item.status)) {
                        Toast.makeText(requireContext(), "Đang kết nối đến chủ nhà...", Toast.LENGTH_SHORT).show();
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
                        if (item.needsWalletPayment()) {
                            Intent payment = new Intent(requireContext(), RentalActivity.class);
                            payment.putExtra("targetPostId", item.postId);
                            payment.putExtra(WalletActivity.EXTRA_LEGACY_ID, item.exchangeId);
                            startActivity(payment);
                        } else {
                            Intent intent = new Intent(requireContext(), ExchangeActivity.class);
                            intent.putExtra("targetPostId", item.postId);
                            intent.putExtra("existingExchangeId", item.exchangeId);
                            startActivity(intent);
                        }
                    } else if ("ACCEPTED".equalsIgnoreCase(item.status)) {
                        Toast.makeText(requireContext(), "Tiếp tục quy trình trao đổi", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    // Tab Đã nhận
                    if ("PENDING".equalsIgnoreCase(item.status)) {
                        updateExchangeStatus(item, "ACCEPTED", "Đã chấp nhận yêu cầu.");
                    } else {
                        Toast.makeText(requireContext(), "Tiếp tục quy trình trao đổi", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });

        recyclerRequests.setAdapter(adapter);
    }

    private void openReceivedDetailActivity(RequestItem item) {
        Intent intent = new Intent(requireContext(), ReceivedRequestsDetailActivity.class);
        intent.putExtra(ReceivedRequestsDetailActivity.EXTRA_REQUEST_ITEM, item);
        startActivity(intent);
    }

    private void showSentDetailDialog(RequestItem item) {
        Dialog dialog = new Dialog(requireContext());
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

        View btnReject = dialog.findViewById(R.id.btnDialogReject);
        View btnSwitchMoney = dialog.findViewById(R.id.btnDialogSwitchMoney);
        View btnAccept = dialog.findViewById(R.id.btnDialogAccept);

        if (btnReject != null) btnReject.setVisibility(View.GONE);
        if (btnSwitchMoney != null) btnSwitchMoney.setVisibility(View.GONE);
        if (btnAccept != null) btnAccept.setVisibility(View.GONE);

        txtSenderName.setText("Yêu cầu gửi tới: " + valueOrDefault(item.postTitle, "Chủ nhà"));
        String exchangeMethodLabel = "MONEY".equalsIgnoreCase(item.exchangeType) || "NON_RECIPROCAL".equalsIgnoreCase(item.exchangeType)
                ? "Phương thức: Thanh toán bằng tiền"
                : "Phương thức: Trao đổi chỗ ở";
        txtRequestDate.setText(exchangeMethodLabel + "\nTrạng thái: " + item.status);

        if (item.proposedPostTitle != null && !item.proposedPostTitle.isEmpty()) {
            txtProposedTitle.setText(item.proposedPostTitle);
        } else {
            txtProposedTitle.setText(valueOrDefault(item.postTitle, "Chưa chọn bài viết đề xuất"));
        }
        txtProposedDates.setText(valueOrDefault(item.dateRangeText, "Chưa cập nhật thời gian"));
        txtProposedRating.setText(String.format(java.util.Locale.US, "%.1f", item.proposedPostRating > 0 ? item.proposedPostRating : 5.0));

        if (item.postImageUrl != null && !item.postImageUrl.isEmpty()) {
            Glide.with(this).load(PostImageUtils.asGlideModel(item.postImageUrl)).into(imgAvatar);
        }
        if (item.proposedPostImageUrl != null && !item.proposedPostImageUrl.isEmpty()) {
            Glide.with(this).load(PostImageUtils.asGlideModel(item.proposedPostImageUrl)).into(imgProposedPost);
        }

        if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private String valueOrDefault(String val, String fallback) {
        return (val == null || val.trim().isEmpty()) ? fallback : val;
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

            layoutSummaryReceived.setVisibility(View.VISIBLE);

            int totalSent = sentList.size();
            int totalCancelled = 0;
            for (RequestItem item : sentList) {
                String s = item.status == null ? "" : item.status.toUpperCase();
                if ("CANCELLED".equals(s) || "REJECTED".equals(s)) {
                    totalCancelled++;
                }
            }
            txtTotalReceivedCount.setText("Tổng số đã gửi " + totalSent);
            txtTotalRejectedCount.setText("Đã hủy " + totalCancelled);

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
            txtTotalReceivedCount.setText("Tổng số đã nhận " + totalRequests);
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
        if (activeFilter != 0) filterCurrentTabList(activeFilter);
    }



    private void loadFirebaseData() {
        final int generation = ++loadGeneration;
        sentList.clear(); receivedList.clear();
        exchangeRepository.resolveCurrentAccountId((accountId, accountError) -> {
            if (!currentLoad(generation)) return;
            if (accountError != null || accountId == null) {
                showError(accountError != null ? accountError : new Exception("Không xác định được tài khoản."));
                selectTab(isSentTabActive); return;
            }
            currentAccountId = accountId;
            FirebaseFirestore db = FirebaseFirestore.getInstance();

            // TAB "ĐÃ GỬI"
            db.collection("exchanges").whereEqualTo("requester_accountId", accountId).get()
                    .addOnSuccessListener(q -> {
                        if (!currentLoad(generation)) return;
                        for (DocumentSnapshot doc : q.getDocuments()) {
                            RequestItem item = mapExchange(doc, true);
                            sentList.add(item);
                            loadPostInfo(item, null);
                        }
                        selectTab(isSentTabActive);
                    }).addOnFailureListener(e -> { if (currentLoad(generation)) showError(e); });

            // TAB "ĐÃ NHẬN"
            List<DocumentSnapshot> receivedDocs = new ArrayList<>();
            db.collection("exchanges")
                    .whereEqualTo("host_accountId", accountId)
                    .get().addOnSuccessListener(q -> {
                        if (!currentLoad(generation)) return;
                for (DocumentSnapshot doc : q.getDocuments()) {
                    String h = stringField(doc, "host_accountId", "hostId");
                    if (h.isEmpty()) h = stringField(doc, "recipientId", "");
                    if (accountId.equals(h)) {
                        receivedDocs.add(doc);
                    }
                }
                db.collection("exchangeRequests")
                        .whereEqualTo("recipientId", accountId)
                        .get().addOnSuccessListener(q2 -> {
                    if (!currentLoad(generation)) return;
                    for (DocumentSnapshot doc2 : q2.getDocuments()) {
                        String h2 = stringField(doc2, "recipientId", "host_accountId");
                        if (accountId.equals(h2)) {
                            receivedDocs.add(doc2);
                        }
                    }
                    loadReceivedGrouped(receivedDocs);
                }).addOnFailureListener(e -> { if (currentLoad(generation)) loadReceivedGrouped(receivedDocs); });
            }).addOnFailureListener(e -> { if (currentLoad(generation)) showError(e); });
        });
    }

    private RequestItem mapExchange(DocumentSnapshot doc, boolean sent) {
        RequestItem item = new RequestItem();
        item.exchangeId = doc.getId();
        item.sourceCollection = doc.getReference().getParent().getId();
        item.subRequestId = doc.getId();
        item.requesterId = stringField(doc, "requester_accountId", "requesterId");
        if (item.requesterId.isEmpty()) item.requesterId = stringField(doc, "senderId", "");
        item.hostId = stringField(doc, "host_accountId", "hostId");
        if (item.hostId.isEmpty()) item.hostId = stringField(doc, "recipientId", "");
        item.postId = doc.getString("postId");
        if (item.postId == null || item.postId.isEmpty()) item.postId = doc.getString("targetPostId");
        item.status = safeString(doc.getString("status"), "PENDING").toUpperCase();
        item.exchangeType = safeString(doc.getString("exchangeType"), "RECIPROCAL");
        item.isSentTab = sent;
        item.startDateText = dateText(doc.get("startDate"));
        item.endDateText = dateText(doc.get("endDate"));
        item.dateRangeText = item.startDateText.isEmpty() ? "Thời gian chưa cập nhật" : item.startDateText + (item.endDateText.isEmpty() ? "" : " - " + item.endDateText);
        item.walletManaged = Boolean.TRUE.equals(doc.getBoolean("walletManaged"));
        item.paymentStatus = safeString(doc.getString("paymentStatus"), "");
        item.paymentId = safeString(doc.getString("paymentId"), "");
        item.isPaid = "PAID".equals(item.paymentStatus) || "RELEASED".equals(item.paymentStatus);
        item.hasMoneyAlternativePending = item.needsWalletPayment();
        return item;
    }

    private void loadReceivedGrouped(List<DocumentSnapshot> documents) {
        if (!screenActive()) return;
        Map<String, RequestItem> groups = new HashMap<>();
        Map<String, Integer> rejected = new HashMap<>();
        for (DocumentSnapshot doc : documents) {
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
            if ("ACCEPTED".equals(sub.status) || "ONGOING".equals(sub.status)
                    || "COMPLETED".equals(sub.status)) group.status = "ACCEPTED";
            else if ("PENDING".equals(sub.status) && !"ACCEPTED".equals(group.status)) group.status = "PENDING";
        }
        receivedList.clear(); receivedList.addAll(groups.values());
        for (RequestItem group : receivedList) loadPostInfo(group, () -> selectTab(isSentTabActive));
        if (receivedList.isEmpty()) selectTab(isSentTabActive);
    }

    private void loadPostInfo(RequestItem item, Runnable done) {
        final int generation = loadGeneration;
        if (item.postId == null || item.postId.isEmpty()) { if (currentLoad(generation) && done != null) done.run(); return; }
        FirebaseFirestore.getInstance().collection("posts").document(item.postId).get()
                .addOnSuccessListener(post -> {
                    if (!currentLoad(generation)) return;
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
                                if (currentLoad(generation) && done != null) done.run();
                                if (adapter != null) adapter.notifyDataSetChanged();
                            });
                        } else {
                            if (currentLoad(generation) && done != null) done.run();
                            if (adapter != null) adapter.notifyDataSetChanged();
                        }
                    });
                }).addOnFailureListener(e -> { if (currentLoad(generation) && done != null) done.run(); });
    }

    private void loadPostImageFromCollection(String collection, RequestItem item, Runnable done) {
        final int generation = loadGeneration;
        FirebaseFirestore.getInstance().collection(collection)
                .whereEqualTo("postId", item.postId)
                .limit(1)
                .get()
                .addOnSuccessListener(images -> {
                    if (!currentLoad(generation)) return;
                    if (!images.isEmpty()) {
                        DocumentSnapshot imageDoc = images.getDocuments().get(0);
                        String value = imageDoc.getString("imgUrl");
                        if (value == null || value.isEmpty()) value = imageDoc.getString("imageUrl");
                        if (value != null && !value.isEmpty()) {
                            item.postImageUrl = value;
                            if (currentLoad(generation) && done != null) done.run();
                            if (adapter != null) adapter.notifyDataSetChanged();
                            return;
                        }
                    }
                    if (currentLoad(generation) && done != null) done.run();
                })
                .addOnFailureListener(e -> {
                    if (currentLoad(generation) && done != null) done.run();
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
        exchangeRepository.updateExchange(requireContext(), item.sourceCollection, item.exchangeId, changes, error -> {
            if (!screenActive()) return;
            if (error != null) showError(error);
            else { Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show(); loadFirebaseData(); }
        });
    }
    private void showError(Exception error) {
        if (!screenActive()) return;
        Toast.makeText(requireContext(), "Lỗi Firebase: " + error.getMessage(), Toast.LENGTH_LONG).show();
    }
    private void openWalletBooking(RequestItem item) {
        Intent intent = new Intent(requireContext(), WalletActivity.class);
        intent.putExtra(WalletActivity.EXTRA_BOOKING_ID, item.exchangeId);
        startActivity(intent);
    }
    private boolean currentLoad(int generation) { return generation == loadGeneration && screenActive(); }
    public void selectReceivedTab(boolean received) { selectTab(!received); }
    @Override public void onResume() { super.onResume(); if (adapter != null) loadFirebaseData(); }
    @Override public void onPause() { loadGeneration++; super.onPause(); }
    @Override public void onSaveInstanceState(@androidx.annotation.NonNull Bundle out) {
        out.putBoolean("sentTab", isSentTabActive);
        out.putInt("requestFilter", activeFilter);
        super.onSaveInstanceState(out);
    }
    @Override public void onDestroyView() {
        loadGeneration++;
        recyclerRequests.setAdapter(null);
        adapter = null;
        super.onDestroyView();
    }
}
