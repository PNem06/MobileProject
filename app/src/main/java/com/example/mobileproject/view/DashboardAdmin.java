package com.example.mobileproject.view;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;
import android.widget.FrameLayout;
import android.net.Uri;
import android.app.Activity;
import android.widget.ScrollView;
import android.widget.LinearLayout;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.example.mobileproject.viewmodel.HomeViewModel;

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
    private HomeViewModel homeViewModel;
    private AccountUi accountUi;
    private FrameLayout accountContainer;
    private TextView tvTotalUser;
    private TextView tvTotalPost;
    private TextView tvTotalExchange;
    private TextView tvPendingReport;
    private DashboardViewModel viewModel;
    private boolean isAuthorized = false;
    private LinearLayout accountContent;
    private String accountPage = "account";
    private final ActivityResultLauncher<String> avatarPicker =
            registerForActivityResult(
                    new ActivityResultContracts.GetContent(),
                    uri -> {
                        if (uri != null && homeViewModel != null) {
                            homeViewModel.draftAvatar = uri.toString();
                            showAccountPage("edit");
                        }
                    }
            );
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
        accountContainer = findViewById(R.id.accountContainer);
        initViews();
        checkAdminAccess();
    }
    private void setupAdminAccount() {
        homeViewModel = new HomeViewModel(getApplication());
        accountContainer = findViewById(R.id.accountContainer);
        accountContent = new LinearLayout(this);
        accountContent.setOrientation(LinearLayout.VERTICAL);
        accountContent.setBackgroundColor(android.graphics.Color.WHITE);
        ScrollView accountScroll = new ScrollView(this);
        accountScroll.setFillViewport(true);
        accountScroll.addView(accountContent, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        accountContainer.removeAllViews();
        accountContainer.addView(accountScroll, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        HomeViews.AccountHost host = new HomeViews.AccountHost() {
            @Override
            public void openAccountPage(String page) {
                showAccountPage(page);
            }

            @Override
            public boolean requireAccountLogin() {
                if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                    return true;
                }
                returnToLogin();
                return false;
            }

            @Override
            public void chooseAccountAvatar() {
                avatarPicker.launch("image/*");
            }

            @Override
            public void confirmAccountDeletion() {
                showMessage(
                        "Chức năng xóa tài khoản cần xác minh lại danh tính."
                );
            }
        };
        accountUi = new AccountUi(this, homeViewModel, accountContent, host);
        homeViewModel.changed.observe(this, changed -> {
            if (changed == null || !isAuthorized) {return;}
            if (FirebaseAuth.getInstance().getCurrentUser() == null) {
                returnToLogin();
                return;
            }
            if (accountContainer.getVisibility() == View.VISIBLE) {showAccountPage(accountPage);}
        });
        homeViewModel.event.observe(this, message -> {
            if (message != null && !message.isEmpty()) {showMessage(message);}
        });
        homeViewModel.setActive(true);
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
                setupAdminAccount();
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
        findViewById(R.id.navDashboard).setOnClickListener(v -> {
            accountContainer.setVisibility(View.GONE);
            findViewById(R.id.scrollDashboard).setVisibility(View.VISIBLE);

            updateBottomNavigation(false);});
        findViewById(R.id.navAccount).setOnClickListener(v -> {
            findViewById(R.id.scrollDashboard).setVisibility(View.GONE);
            accountContainer.setVisibility(View.VISIBLE);
            showAccountPage("account");
            updateBottomNavigation(true);
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
            startActivity(new Intent(DashboardAdmin.this, AdminManageExchangeActivity.class));
        });

        // TIEP NHAN / XU LY REPORT
        findViewById(R.id.layoutReport).setOnClickListener(v -> {
            startActivity(new Intent(DashboardAdmin.this, AdminManageReportActivity.class));
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
    private void updateBottomNavigation(boolean isAccountSelected) {
        updateNavigationItem(R.id.navDashboard, !isAccountSelected);
        updateNavigationItem(R.id.navAccount, isAccountSelected);
    }

    private void updateNavigationItem(int viewId, boolean isSelected) {
        View item = findViewById(viewId);
        if (item == null) {return;}
        if (item instanceof TextView) {
            ((TextView) item).setTextColor(
                    isSelected
                            ? android.graphics.Color.RED
                            : android.graphics.Color.BLACK
            );
        } else if (item instanceof LinearLayout) {
            LinearLayout layout = (LinearLayout) item;
            for (int i = 0; i < layout.getChildCount(); i++) {
                View child = layout.getChildAt(i);
                if (child instanceof TextView) {
                    ((TextView) child).setTextColor(
                            isSelected
                                    ? android.graphics.Color.RED
                                    : android.graphics.Color.BLACK
                    );
                }
                if (child instanceof android.widget.ImageView) {
                    ((android.widget.ImageView) child).setColorFilter(
                            isSelected
                                    ? android.graphics.Color.RED
                                    : android.graphics.Color.BLACK
                    );
                }
            }
        }
    }
    private void showAccountPage(String page) {
        if (accountUi == null || homeViewModel == null) {
            return;
        }
        accountPage = page;
        accountContent.removeAllViews();
        switch (page) {
            case "account":
                accountUi.account();
                break;
            case "edit":
                accountUi.edit();
                break;
            case "settings":
                accountUi.settings();
                break;
            case "about":
                accountUi.about();
                break;
            default:
                accountPage = "account";
                accountUi.account();
                break;
        }
    }
}