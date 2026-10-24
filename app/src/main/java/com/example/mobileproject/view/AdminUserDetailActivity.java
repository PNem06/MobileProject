package com.example.mobileproject.view;

import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mobileproject.R;
import com.example.mobileproject.adapter.AdminUserDetailAdapter;
import com.example.mobileproject.model.AdminUserItem;
import com.example.mobileproject.viewmodel.AdminUsersViewModel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public final class AdminUserDetailActivity extends AppCompatActivity {

    public static final String EXTRA_USER_DOCUMENT_ID =
            "adminUserDocumentId";

    private final Set<String> expandedPosts = new HashSet<>();

    private AdminUsersViewModel vm;
    private AdminUserDetailAdapter adapter;
    private ConnectivityManager connectivity;
    private AlertDialog confirmDialog;

    private String documentId;
    private boolean started;
    private boolean registered;

    private final ConnectivityManager.NetworkCallback networkCallback =
            new ConnectivityManager.NetworkCallback() {

                @Override
                public void onAvailable(Network network) {
                    updateConnection();
                }

                @Override
                public void onLost(Network network) {
                    updateConnection();
                }

                @Override
                public void onCapabilitiesChanged(
                        Network network,
                        NetworkCapabilities caps
                ) {
                    updateConnection();
                }
            };

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);

        documentId = getIntent().getStringExtra(
                EXTRA_USER_DOCUMENT_ID
        );

        if (documentId == null
                || documentId.trim().isEmpty()
                || documentId.contains("/")) {
            Toast.makeText(
                    this,
                    R.string.admin_user_profile_missing,
                    Toast.LENGTH_LONG
            ).show();

            finish();
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_admin_user_detail);

        vm = new ViewModelProvider(this)
                .get(AdminUsersViewModel.class);

        vm.enablePostDetails();

        connectivity = getSystemService(
                ConnectivityManager.class
        );

        if (saved != null) {
            ArrayList<String> expanded =
                    saved.getStringArrayList("expandedPosts");

            if (expanded != null) {
                expandedPosts.addAll(expanded);
            }
        }

        applyInsets(
                findViewById(R.id.adminUserProfileRoot)
        );

        findViewById(R.id.adminUserProfileBack)
                .setOnClickListener(v -> finish());

        findViewById(R.id.adminUserProfileReload)
                .setOnClickListener(v -> {
                    vm.stop();
                    vm.start();
                });

        adapter = new AdminUserDetailAdapter(
                this::confirmStatus,
                expandedPosts
        );

        RecyclerView list = findViewById(
                R.id.adminUserProfileList
        );

        GridLayoutManager manager =
                (GridLayoutManager) list.getLayoutManager();

        manager.setSpanCount(
                getResources().getConfiguration().screenWidthDp >= 600
                        ? 2
                        : 1
        );

        manager.setSpanSizeLookup(
                new GridLayoutManager.SpanSizeLookup() {
                    @Override
                    public int getSpanSize(int position) {
                        return position == 0
                                ? manager.getSpanCount()
                                : 1;
                    }
                }
        );

        list.setAdapter(adapter);

        vm.getState().observe(this, state -> {
            adapter.submit(state, documentId);

            if (confirmDialog != null
                    && (!state.authorized
                    || adapter.selectedUser() == null
                    || !state.writable)) {
                confirmDialog.dismiss();
            }
        });

        vm.getMessage().observe(this, message -> {
            if (message == null) {
                return;
            }

            Toast.makeText(
                    this,
                    message,
                    Toast.LENGTH_LONG
            ).show();

            vm.consumeMessage();
        });
    }

    private void confirmStatus(AdminUserItem user) {
        if (user == null
                || !vm.writable()
                || !user.canChangeStatus()) {
            return;
        }

        confirmDialog = new AlertDialog.Builder(this)
                .setTitle(
                        user.active()
                                ? R.string.admin_user_lock_title
                                : R.string.admin_user_unlock_title
                )
                .setMessage(
                        getString(
                                user.active()
                                        ? R.string.admin_user_lock_message
                                        : R.string.admin_user_unlock_message,
                                user.fullName
                        )
                )
                .setNegativeButton(
                        R.string.admin_user_cancel,
                        null
                )
                .setPositiveButton(
                        R.string.admin_user_confirm,
                        (dialog, which) -> vm.changeStatus(user)
                )
                .show();
    }

    private void applyInsets(View root) {
        WindowCompat.getInsetsController(getWindow(), root)
                .setAppearanceLightStatusBars(true);

        WindowCompat.getInsetsController(getWindow(), root)
                .setAppearanceLightNavigationBars(true);

        int left = root.getPaddingLeft();
        int top = root.getPaddingTop();
        int right = root.getPaddingRight();
        int bottom = root.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(
                root,
                (view, insets) -> {
                    Insets bars = insets.getInsets(
                            WindowInsetsCompat.Type.systemBars()
                    );

                    view.setPadding(
                            left + bars.left,
                            top + bars.top,
                            right + bars.right,
                            bottom + bars.bottom
                    );

                    return insets;
                }
        );

        ViewCompat.requestApplyInsets(root);
    }

    private void updateConnection() {
        runOnUiThread(() -> {
            if (!started || isDestroyed() || vm == null) {
                return;
            }

            Network network = connectivity == null
                    ? null
                    : connectivity.getActiveNetwork();

            NetworkCapabilities caps = network == null
                    ? null
                    : connectivity.getNetworkCapabilities(network);

            vm.setConnected(
                    caps != null
                            && caps.hasCapability(
                            NetworkCapabilities.NET_CAPABILITY_VALIDATED
                    )
            );
        });
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (vm == null) {
            return;
        }

        started = true;

        if (connectivity != null) {
            connectivity.registerDefaultNetworkCallback(
                    networkCallback
            );
            registered = true;
        }

        updateConnection();
        vm.start();
    }

    @Override
    protected void onStop() {
        started = false;

        if (registered) {
            connectivity.unregisterNetworkCallback(
                    networkCallback
            );
            registered = false;
        }

        if (confirmDialog != null) {
            confirmDialog.dismiss();
        }

        if (vm != null) {
            vm.stop();
        }

        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        out.putStringArrayList(
                "expandedPosts",
                new ArrayList<>(expandedPosts)
        );

        super.onSaveInstanceState(out);
    }
}