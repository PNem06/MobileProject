
package com.example.mobileproject;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import com.example.mobileproject.ui.HomeUi;
import com.example.mobileproject.view.Login;
import com.example.mobileproject.viewmodel.HomeViewModel;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.firebase.auth.*;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {
    private HomeViewModel vm;
    private HomeUi ui;
    private ConnectivityManager connectivity;
    private boolean visible, networkRegistered;
    private ActivityResultLauncher<String[]> pickAvatar;
    private ActivityResultLauncher<Intent> googleReauth;
    private final ConnectivityManager.NetworkCallback networkCallback =
            new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    connectionChanged();
                }

                @Override
                public void onLost(Network network) {
                    connectionChanged();
                }

                @Override
                public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
                    connectionChanged();
                }
            };

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.homeScreenContent), (view, insets) -> {
            Insets bars =
                    insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(true);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightNavigationBars(true);
        vm = new ViewModelProvider(this).get(HomeViewModel.class);
        ui = new HomeUi(this, vm, saved);
        vm.uiInitialized = true;
        connectivity = getSystemService(ConnectivityManager.class);
        registerLaunchers();
        vm.changed.observe(this, value -> ui.render());
        vm.event.observe(this, message -> {
            if (message == null) return;
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            vm.event.setValue(null);
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (vm.busy) {
                    vm.tell("Vui lòng chờ thao tác hoàn tất.");
                    return;
                }
                if ("home".equals(vm.page)) {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                } else
                    openPage(
                            Arrays.asList("edit", "settings", "about").contains(vm.page) ? "account" : "home");
            }
        });
    }

    private void registerLaunchers() {
        pickAvatar = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri == null) return;
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {
            }
            vm.draftAvatar = uri.toString();
            ui.rebuild();
        });
        googleReauth =
                registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() != RESULT_OK) return;
                    try {
                        GoogleSignInAccount account =
                                GoogleSignIn.getSignedInAccountFromIntent(result.getData()).getResult();
                        if (account == null || account.getIdToken() == null)
                            throw new IllegalStateException("Thiếu Google ID token.");
                        vm.deleteAccount(GoogleAuthProvider.getCredential(account.getIdToken(), null));
                    } catch (Exception e) {
                        vm.tell("Không xác minh được Google: " + e.getMessage());
                    }
                });
    }

    @Override
    protected void onStart() {
        super.onStart();
        visible = true;
        if (connectivity != null) {
            connectivity.registerDefaultNetworkCallback(networkCallback);
            networkRegistered = true;
        }
        vm.setActive(true);
    }

    @Override
    protected void onStop() {
        visible = false;
        if (networkRegistered) {
            connectivity.unregisterNetworkCallback(networkCallback);
            networkRegistered = false;
        }
        vm.setActive(false);
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        ui.saveState(out);
        super.onSaveInstanceState(out);
    }

    private void connectionChanged() {
        runOnUiThread(() -> {
            if (visible && !isDestroyed()) vm.onConnectionChanged();
        });
    }

    public void openPage(String page) {
        ui.openPage(page);
    }

    public void chooseAvatar() {
        if (!vm.busy) pickAvatar.launch(new String[] {"image/*"});
    }

    public boolean requireLogin() {
        if (vm.signedIn()) return true;
        startActivity(new Intent(this, Login.class));
        return false;
    }

    public void confirmAccountDeletion() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || vm.busy) return;
        if (!vm.online()) {
            vm.tell("Kết nối mạng để xóa tài khoản.");
            return;
        }
        boolean password = false;
        for (UserInfo provider : user.getProviderData())
            if ("password".equals(provider.getProviderId())) password = true;
        if (password) {
            EditText pass = new EditText(this);
            pass.setSingleLine(true);
            pass.setHint("Mật khẩu hiện tại");
            pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle("Xác minh tài khoản")
                    .setView(pass)
                    .setNegativeButton("Hủy", null)
                    .setPositiveButton("Xóa tài khoản", null)
                    .create();
            dialog.setOnShowListener(
                    d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                        String value = pass.getText().toString();
                        if (value.isEmpty()) {
                            pass.setError("Nhập mật khẩu hiện tại.");
                            return;
                        }
                        vm.deleteAccount(EmailAuthProvider.getCredential(user.getEmail(), value));
                        dialog.dismiss();
                    }));
            dialog.show();
        } else {
            GoogleSignInOptions options = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(getString(R.string.default_web_client_id))
                    .requestEmail()
                    .build();
            googleReauth.launch(GoogleSignIn.getClient(this, options).getSignInIntent());
        }
    }
}
