package com.example.mobileproject.view;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mobileproject.R;
import com.example.mobileproject.adapter.AdminExchangeAdapter;
import com.example.mobileproject.data.ExchangeCacheEntity;
import com.example.mobileproject.repository.AdminManagementRepository;
import com.example.mobileproject.viewmodel.AdminExchangeViewModel;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AdminManageExchangeActivity extends AppCompatActivity {
    private AdminExchangeViewModel viewModel;
    private TextView summary, empty;
    private AdminExchangeAdapter adapter;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_admin_manage_exchange);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        summary = findViewById(R.id.tvExchangeSummary);
        empty = findViewById(R.id.tvExchangesEmpty);
        findViewById(R.id.btnBackExchange).setOnClickListener(v -> finish());

        RecyclerView recycler = findViewById(R.id.rvExchanges);
        recycler.setLayoutManager(new LinearLayoutManager(this));

        adapter = new AdminExchangeAdapter(new AdminExchangeAdapter.Listener() {
            @Override
            public void onDetail(ExchangeCacheEntity item) {
                showExchangeDetails(item);
            }

            @Override
            public void onAcceptCancellation(ExchangeCacheEntity item) {
                confirmDecision(item, true);
            }

            @Override
            public void onRejectCancellation(ExchangeCacheEntity item) {
                confirmDecision(item, false);
            }
        });

        recycler.setAdapter(adapter);

        viewModel = new ViewModelProvider(this).get(AdminExchangeViewModel.class);
        viewModel.getExchanges().observe(this, exchanges -> {
            adapter.setItems(exchanges);
            empty.setVisibility(exchanges == null || exchanges.isEmpty() ? View.VISIBLE : View.GONE);
            int pendingCancel = 0;
            int paid = 0;
            if (exchanges != null) {
                for (ExchangeCacheEntity item : exchanges) {
                    if ("PAID".equalsIgnoreCase(item.paymentStatus)) paid++;
                    if ("PAID".equalsIgnoreCase(item.paymentStatus) && "PENDING".equalsIgnoreCase(item.cancelRequestStatus)) pendingCancel++;
                }
            }

            String summaryText = "Tổng trao đổi: "
                    + (exchanges == null ? 0 : exchanges.size())
                    + "\nĐã thanh toán: " + paid
                    + "\nYêu cầu hủy chờ duyệt: " + pendingCancel;

            summary.setText(summaryText);

        });
    }

    @Override protected void onStart() {
        super.onStart();
        if (viewModel != null) viewModel.startListening();
    }
    @Override protected void onStop() {
        if (viewModel != null) viewModel.stopListening();
        super.onStop();
    }

    private void showExchangeDetails(ExchangeCacheEntity item) {
        boolean canDecide = "PAID".equalsIgnoreCase(item.paymentStatus)
                && "PENDING".equalsIgnoreCase(item.cancelRequestStatus);
        String details = "Mã trao đổi: " + item.exchangeId
                + "\nNgười yêu cầu: " + safe(item.requesterId)
                + "\nChủ bài đăng: " + safe(item.hostId)
                + "\nMã bài đăng: " + safe(item.postId)
                + "\nLoại trao đổi: " + safe(item.exchangeType)
                + "\nTrạng thái trao đổi: " + safe(item.status)
                + "\nBắt đầu: " + format(item.startDate)
                + "\nKết thúc: " + format(item.endDate)
                + "\nSố khách: " + item.guestCount
                + "\nThanh toán: " + safe(item.paymentStatus)
                + "\nSố tiền: " + String.format(Locale.getDefault(), "%,.0f", item.paymentAmount)
                + "\nTrạng thái yêu cầu hủy: " + safe(item.cancelRequestStatus)
                + "\nLý do hủy: " + safe(item.cancelReason)
                + "\nGhi chú: " + safe(item.note);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Chi tiết trao đổi")
                .setMessage(details)
                .setNeutralButton("Đóng", null);
        if (canDecide) {
            builder.setPositiveButton("Accept hủy", (dialog, which) -> confirmDecision(item, true));
            builder.setNegativeButton("Reject hủy", (dialog, which) -> confirmDecision(item, false));
        }
        builder.show();
    }

    private void confirmDecision(ExchangeCacheEntity item, boolean accept) {
        String action = accept ? "chấp nhận yêu cầu hủy" : "từ chối yêu cầu hủy";
        new AlertDialog.Builder(this)
                .setTitle("Xác nhận quyết định")
                .setMessage("Bạn muốn " + action + " cho trao đổi " + item.exchangeId + "? Người yêu cầu và chủ bài đăng sẽ nhận thông báo.")
                .setNegativeButton("Quay lại", null)
                .setPositiveButton("Xác nhận", (dialog, which) -> viewModel.decideCancellation(item, accept, new AdminManagementRepository.ActionCallback() {
                    @Override public void onSuccess(String message) { if (!isFinishing()) Toast.makeText(AdminManageExchangeActivity.this, message, Toast.LENGTH_LONG).show(); }
                    @Override public void onError(String message) { if (!isFinishing()) Toast.makeText(AdminManageExchangeActivity.this, message, Toast.LENGTH_LONG).show(); }
                }))
                .show();
    }

    private String safe(String value) { return value == null || value.trim().isEmpty() ? "Chưa cập nhật" : value; }
    private String format(long millis) {
        if (millis <= 0) return "Chưa cập nhật";
        return new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date(millis));
    }
}
