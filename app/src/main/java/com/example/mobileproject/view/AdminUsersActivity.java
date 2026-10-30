package com.example.mobileproject.view;

import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mobileproject.R;
import com.example.mobileproject.adapter.AdminUserAdapter;
import com.example.mobileproject.model.AdminUserItem;
import com.example.mobileproject.repository.AdminUserRepository;
import com.example.mobileproject.viewmodel.AdminUsersViewModel;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class AdminUsersActivity extends AppCompatActivity {

    private static final String[] FILTERS = {
            "ALL",
            "ACTIVE",
            "INACTIVE"
    };

    private AdminUsersViewModel vm;
    private AdminUserAdapter adapter;

    private TextView summary;
    private TextView source;
    private TextView empty;

    private ConnectivityManager connectivity;
    private boolean registered;
    private boolean started;

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
        EdgeToEdge.enable(this);

        setContentView(R.layout.activity_admin_users);

        vm = new ViewModelProvider(this)
                .get(AdminUsersViewModel.class);

        if (saved != null) {
            vm.query = saved.getString(
                    "adminQuery",
                    vm.query
            );

            vm.filter = saved.getString(
                    "adminFilter",
                    vm.filter
            );
        }

        connectivity = getSystemService(
                ConnectivityManager.class
        );

        bindViews();
        observeData();
    }

    private void bindViews() {
        View root = findViewById(R.id.adminUsersRoot);
        applyInsets(root);

        summary = findViewById(R.id.adminUsersSummary);
        source = findViewById(R.id.adminUsersSource);
        empty = findViewById(R.id.adminUsersEmpty);

        findViewById(R.id.adminUsersBack)
                .setOnClickListener(v -> finish());

        findViewById(R.id.adminUsersReload)
                .setOnClickListener(v -> {
                    vm.stop();
                    vm.start();
                });

        EditText search = findViewById(
                R.id.adminUsersSearch
        );

        search.setText(vm.query);

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(
                    CharSequence s,
                    int start,
                    int count,
                    int after
            ) {
            }
            @Override
            public void onTextChanged(
                    CharSequence s,
                    int start,
                    int before,
                    int count
            ) {
                vm.query = s.toString();
                vm.refreshUi();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        Spinner filter = findViewById(
                R.id.adminUsersFilter
        );

        ArrayAdapter<CharSequence> filterAdapter =
                ArrayAdapter.createFromResource(
                        this,
                        R.array.admin_user_filter_labels,
                        R.layout.item_admin_user_filter
                );

        filterAdapter.setDropDownViewResource(
                R.layout.item_admin_user_filter
        );
        filter.setAdapter(filterAdapter);
        filter.setSelection(
                Math.max(
                        0,
                        Arrays.asList(FILTERS).indexOf(vm.filter)
                )
        );
        filter.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {

                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent,
                            View view,
                            int position,
                            long id
                    ) {
                        vm.filter = FILTERS[position];
                        vm.refreshUi();
                    }

                    @Override
                    public void onNothingSelected(
                            AdapterView<?> parent
                    ) {
                    }
                }
        );

        RecyclerView list = findViewById(
                android.R.id.list
        );

        adapter = new AdminUserAdapter(user -> {
            Intent intent = new Intent(
                    this,
                    AdminUserDetailActivity.class
            );

            intent.putExtra(
                    AdminUserDetailActivity.EXTRA_USER_DOCUMENT_ID,
                    user.documentId
            );

            startActivity(intent);
        });

        adapter.setStateRestorationPolicy(
                RecyclerView.Adapter.StateRestorationPolicy
                        .PREVENT_WHEN_EMPTY
        );

        list.setAdapter(adapter);
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
                                    | WindowInsetsCompat.Type.ime()
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

    private void observeData() {
        vm.getState().observe(this, state -> render());

        vm.getMessage().observe(this, message -> {
            if (message != null) {
                Toast.makeText(
                        this,
                        message,
                        Toast.LENGTH_LONG
                ).show();

                vm.consumeMessage();
            }
        });
    }

    private void render() {
        AdminUserRepository.State state = vm.snapshot();

        summary.setText(
                state.authorized
                        ? vm.summary()
                        : getString(R.string.admin_users_only)
        );

        source.setText(vm.sourceLabel());

        List<AdminUserItem> users = state.authorized
                ? vm.visibleUsers()
                : Collections.emptyList();

        adapter.submit(users);

        empty.setVisibility(
                users.isEmpty() ? View.VISIBLE : View.GONE
        );

        if (state.denied) {
            empty.setText(state.notice);
        } else if (!state.authorized) {
            empty.setText(
                    R.string.admin_users_verifying
            );
        } else if (!state.loaded) {
            empty.setText(
                    R.string.admin_users_not_synced
            );
        } else {
            empty.setText(
                    R.string.admin_users_no_match
            );
        }
    }

    private void updateConnection() {
        runOnUiThread(() -> {
            if (!started || isDestroyed()) {
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

        started = true;

        if (connectivity != null) {
            connectivity.registerDefaultNetworkCallback(
                    networkCallback
            );

            registered = true;
        }

        updateConnection();
        vm.start();
        render();
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

        vm.stop();
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        out.putString("adminQuery", vm.query);
        out.putString("adminFilter", vm.filter);

        super.onSaveInstanceState(out);
    }
}