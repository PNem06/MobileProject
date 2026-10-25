package com.example.mobileproject.view;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
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
import com.example.mobileproject.adapter.AdminReportAdapter;
import com.example.mobileproject.data.ReportCacheEntity;
import com.example.mobileproject.repository.AdminManagementRepository;
import com.example.mobileproject.viewmodel.AdminReportViewModel;

import java.util.ArrayList;
import java.util.List;

public class AdminManageReportActivity extends AppCompatActivity {
    private static final int TAB_USER = 0;
    private static final int TAB_POST = 1;
    private int selectedTab = TAB_USER;
    private AdminReportViewModel viewModel;
    private AdminReportAdapter adapter;
    private TextView summary, empty;
    private Button userTab, postTab;
    private List<ReportCacheEntity.AdminReportCacheEntity> allReports = new ArrayList<>();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_admin_manage_report);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        summary = findViewById(R.id.tvReportSummary);
        empty = findViewById(R.id.tvReportsEmpty);
        userTab = findViewById(R.id.btnReportUserTab);
        postTab = findViewById(R.id.btnReportPostTab);
        RecyclerView recycler = findViewById(R.id.rvReports);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AdminReportAdapter(new AdminReportAdapter.Listener() {
            @Override public void onResolve(ReportCacheEntity.AdminReportCacheEntity item) { confirmViolation(item); }
            @Override public void onReject(ReportCacheEntity.AdminReportCacheEntity item) { rejectReport(item); }
        });
        recycler.setAdapter(adapter);

        findViewById(R.id.btnBackReport).setOnClickListener(v -> finish());
        userTab.setOnClickListener(v -> selectTab(TAB_USER));
        postTab.setOnClickListener(v -> selectTab(TAB_POST));

        viewModel = new ViewModelProvider(this).get(AdminReportViewModel.class);
        viewModel.getReports().observe(this, reports -> {
            allReports = reports == null ? new ArrayList<>() : reports;
            renderCurrentTab();
        });
        selectTab(TAB_USER);
    }

    @Override protected void onStart() {
        super.onStart();
        if (viewModel != null) viewModel.startListening();
    }

    @Override protected void onStop() {
        if (viewModel != null) viewModel.stopListening();
        super.onStop();
    }


    private void selectTab(int tab) {
        selectedTab = tab;

        if (tab == TAB_USER) {
            userTab.setBackgroundResource(R.drawable.bg_black_blue);
            userTab.setBackgroundTintList(null);
            userTab.setTextColor(0xFFFFFFFF);

            postTab.setBackgroundTintList(ColorStateList.valueOf(0xFFE8E9F7));
            postTab.setTextColor(0xFF3432C7);

        } else {
            postTab.setBackgroundResource(R.drawable.bg_black_blue);
            postTab.setBackgroundTintList(null);
            postTab.setTextColor(0xFFFFFFFF);

            userTab.setBackgroundTintList(ColorStateList.valueOf(0xFFE8E9F7));
            userTab.setTextColor(0xFF3432C7);
        }
        renderCurrentTab();
    }

    private void renderCurrentTab() {
        if (adapter == null) return;
        List<ReportCacheEntity.AdminReportCacheEntity> filtered = new ArrayList<>();
        int pending = 0;
        for (ReportCacheEntity.AdminReportCacheEntity report : allReports) {
            String type = report.targetType == null ? "" : report.targetType.toUpperCase();
            boolean isUser = "ACCOUNT".equals(type) || "USER".equals(type);
            if ((selectedTab == TAB_USER && isUser) || (selectedTab == TAB_POST && "POST".equals(type))) {
                filtered.add(report);
                if ("PENDING".equalsIgnoreCase(report.status) || "REVIEWING".equalsIgnoreCase(report.status)) pending++;
            }
        }
        adapter.setItems(filtered);
        empty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);

        String tabName = selectedTab == TAB_USER ? "Report User" : "Report Post";
        String summaryText = tabName
                + "\nTổng số báo cáo: " + filtered.size()
                + "\nĐang chờ xử lý: " + pending;
        summary.setText(summaryText);
    }

    private void confirmViolation(ReportCacheEntity.AdminReportCacheEntity item) {
        boolean isUser = "ACCOUNT".equalsIgnoreCase(item.targetType) || "USER".equalsIgnoreCase(item.targetType);
        String action = isUser ? "khóa tài khoản (LOCKED)" : "ẩn bài đăng (HIDDEN)";
        new AlertDialog.Builder(this)
                .setTitle("Xác nhận vi phạm")
                .setMessage("Bạn xác nhận report này đúng vi phạm và muốn " + action + "? Hệ thống sẽ cập nhật trạng thái report và tạo thông báo cho người liên quan.")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xác nhận", (dialog, which) -> viewModel.confirmViolation(item, actionCallback()))
                .show();
    }

    private void rejectReport(ReportCacheEntity.AdminReportCacheEntity item) {
        new AlertDialog.Builder(this)
                .setTitle("Bỏ qua report")
                .setMessage("Xác nhận report này không đủ căn cứ vi phạm?")
                .setNegativeButton("Quay lại", null)
                .setPositiveButton("Từ chối report", (dialog, which) -> viewModel.reject(item, actionCallback()))
                .show();
    }

    private AdminManagementRepository.ActionCallback actionCallback() {
        return new AdminManagementRepository.ActionCallback() {
            @Override public void onSuccess(String message) { if (!isFinishing()) Toast.makeText(AdminManageReportActivity.this, message, Toast.LENGTH_LONG).show(); }
            @Override public void onError(String message) { if (!isFinishing()) Toast.makeText(AdminManageReportActivity.this, message, Toast.LENGTH_LONG).show(); }
        };
    }
}
