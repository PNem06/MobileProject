
package com.example.mobileproject.view;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;

import com.example.mobileproject.R;
import com.example.mobileproject.service.AuthService;
import com.example.mobileproject.viewmodel.DashboardViewModel;

import com.google.firebase.auth.FirebaseAuth;

public class DashboardAdmin extends AppCompatActivity {
    private TextView tvTotalUser;
    private TextView tvTotalPost;
    private TextView tvTotalExchange;
    private TextView tvPendingReport;
    private DashboardViewModel viewModel;
    private boolean isAuthorized = false;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_dashboard_admin);
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.main),
                (v, insets) -> {Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                    return insets;
                }
        );
        initViews();
        checkAdminAccess();
    }
    private void initViews() {
        tvTotalUser = findViewById(R.id.tvTotalUser);
        tvTotalPost = findViewById(R.id.tvTotalPost);
        tvTotalExchange = findViewById(R.id.tvTotalExchange);
        tvPendingReport = findViewById(R.id.tvPendingReport);
        tvTotalUser.setText("--");
        tvTotalPost.setText("--");
        tvTotalExchange.setText("--");
        tvPendingReport.setText("--");
    }
    private void checkAdminAccess() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            showMessage("Bạn chưa đăng nhập.");
            returnToLogin();
            return;
        }
        AuthService authService = new AuthService();
        authService.loadProfile(new AuthService.Callback() {
            @Override
            public void onSuccess(String role) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (!"ADMIN".equals(role)) {
                    showMessage("Bạn không có quyền truy cập Dashboard Admin.");
                    returnToLogin();
                    return;
                }
                isAuthorized = true;
                setupViewModel();
                setupClickListeners();
                if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) {
                    viewModel.startListening();
                }
            }
            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                showMessage(message);
                returnToLogin();
            }
        });
    }
    private void setupViewModel() {
        viewModel = new ViewModelProvider(this).get(DashboardViewModel.class);
        viewModel.getTotalUsers().observe(this, total -> {
            if (total != null && isAuthorized) {
                tvTotalUser.setText(String.valueOf(total));
            }
        });
        viewModel.getTotalPosts().observe(this, total -> {
            if (total != null && isAuthorized) {
                tvTotalPost.setText(String.valueOf(total));
            }
        });
        viewModel.getTotalExchanges().observe(this, total -> {
            if (total != null && isAuthorized) {
                tvTotalExchange.setText(String.valueOf(total));
            }
        });
        viewModel.getPendingReports().observe(this, total -> {
            if (total != null && isAuthorized) {
                tvPendingReport.setText(String.valueOf(total));
            }
        });
    }
    private void setupClickListeners() {
        findViewById(R.id.navHome).setOnClickListener(v -> {

            // TODO: Chuyen sang Home khi hoan thien.

            showComingSoon("Trang chủ");
        });
        findViewById(R.id.navDashboard).setOnClickListener(v -> {
            // Dang o Dashboard nen khong can chuyen trang.

        });
        findViewById(R.id.navAccount).setOnClickListener(v -> {

            // TODO: Chuyen sang trang Tai khoan.

            showComingSoon("Tài khoản");
        });

        // QUAN LY TAI KHOAN USER
        findViewById(R.id.layoutManageUser).setOnClickListener(v -> {

            startActivity(new Intent(this, AdminUsersActivity.class));
        });

        // QUAN LY BAI DANG -> Mở trang kiểm duyệt bài đăng (AdminManagePostActivity)
        findViewById(R.id.layoutManagePost).setOnClickListener(v -> {
            Intent intent = new Intent(DashboardAdmin.this, AdminManagePostActivity.class);
            startActivity(intent);
        });

        // QUAN LY TRAO DOI
        findViewById(R.id.layoutManageExchange).setOnClickListener(v -> {
            showComingSoon("Quản lý các trao đổi");
        });

        // TIEP NHAN / XU LY REPORT
        findViewById(R.id.layoutReport).setOnClickListener(v -> {

            showComingSoon("Tiếp nhận / Xử lý Report");
        });
    }
    private void showComingSoon(String featureName) {
        showMessage(featureName + " đang được phát triển");
    }
    private void showMessage(String message) {
        Toast.makeText(
                DashboardAdmin.this,
                message,
                Toast.LENGTH_SHORT
        ).show();
    }
    private void returnToLogin() {
        isAuthorized = false;
        if (viewModel != null) {
            viewModel.clearData();
        }
        Intent intent = new Intent(DashboardAdmin.this, Login.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    @Override
    protected void onStart() {
        super.onStart();
        if (isAuthorized && viewModel != null) {
            viewModel.startListening();
        }
    }
    @Override
    protected void onStop() {
        if (viewModel != null) {
            viewModel.stopListening();
        }
        super.onStop();
    }
}
