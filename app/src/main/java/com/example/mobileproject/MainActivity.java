
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
import com.example.mobileproject.view.HomeUi;
import com.example.mobileproject.view.Login;
import com.example.mobileproject.viewmodel.HomeViewModel;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.firebase.auth.*;
import com.example.mobileproject.navigation.MainTab;
import com.example.mobileproject.navigation.MainNavigationFragment;
import com.example.mobileproject.view.HomeContentFragment;
import androidx.fragment.app.Fragment;
import android.content.Context;

public class MainActivity extends AppCompatActivity implements MainNavigationFragment.Host {
    public static final String EXTRA_HOME_PAGE = "com.example.mobileproject.EXTRA_HOME_PAGE";

    private HomeViewModel vm;
    private HomeUi ui;
    private ConnectivityManager connectivity;
    private boolean visible, networkRegistered;
    private String sessionUid;
    private final FirebaseAuth.AuthStateListener authListener = auth -> {
        String next = auth.getCurrentUser() == null ? "" : auth.getCurrentUser().getUid();
        if (!java.util.Objects.equals(sessionUid, next)) {
            sessionUid = next;
            navigation().resetSession();
        }
    };
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
        setContentView(R.layout.activity_main);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(true);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightNavigationBars(true);
        connectivity = getSystemService(ConnectivityManager.class);
        sessionUid = saved == null ? currentUid() : saved.getString("navigationUser", currentUid());
        registerLaunchers();
        if (navigation() == null) getSupportFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .add(R.id.mainNavigationHost, new MainNavigationFragment(), "main-navigation").commitNow();
        com.example.mobileproject.navigation.MainNavigationInsets.install(
                findViewById(R.id.mainNavigationHost), R.id.mainBottomMenu);
        if (saved == null) openPageFromIntent(getIntent());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (navigation().handleBack()) return;
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
                setEnabled(true);
            }
        });
    }

    private String currentUid() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user == null ? "" : user.getUid();
    }

    private MainNavigationFragment navigation() {
        return (MainNavigationFragment) getSupportFragmentManager().findFragmentByTag("main-navigation");
    }

    public static Intent tabIntent(Context context, MainTab tab) {
        return new Intent(context, MainActivity.class).putExtra(EXTRA_HOME_PAGE, tab.page)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }

    @Override public boolean canOpenMainTab(MainTab tab) {
        MainNavigationFragment nav = navigation();
        Fragment current = nav == null ? null : nav.currentTab();
        if (current instanceof HomeContentFragment && ((HomeContentFragment) current).busy()) {
            Toast.makeText(this, "Vui lòng chờ thao tác hoàn tất.", Toast.LENGTH_SHORT).show();
            return false;
        }
        return tab == MainTab.EXPLORE || requireLogin();
    }

    public void bindHomeUi(HomeViewModel model, HomeUi renderer) { vm = model; ui = renderer; }

    private void registerLaunchers() {
        pickAvatar = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri == null || vm == null || ui == null) return;
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {
            }
            vm.draftAvatar = uri.toString();
            ui.rebuild();
        });
        googleReauth =
                registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() != RESULT_OK || vm == null) return;
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
        FirebaseAuth.getInstance().addAuthStateListener(authListener);
    }

    @Override
    protected void onStop() {
        visible = false;
        if (networkRegistered) {
            connectivity.unregisterNetworkCallback(networkCallback);
            networkRegistered = false;
        }
        FirebaseAuth.getInstance().removeAuthStateListener(authListener);
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        out.putString("navigationUser", sessionUid);
        super.onSaveInstanceState(out);
    }

    private void connectionChanged() {
        runOnUiThread(() -> {
            if (visible && !isDestroyed()) {
                Fragment current = navigation().currentTab();
                if (current instanceof HomeContentFragment) ((HomeContentFragment) current).connectionChanged();
            }
        });
    }

    public void openPage(String page) {
        navigation().openPage(page, null);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        openPageFromIntent(intent);
    }

    private void openPageFromIntent(Intent intent) {
        if (intent == null || !intent.hasExtra(EXTRA_HOME_PAGE)) return;
        String page = intent.getStringExtra(EXTRA_HOME_PAGE);
        intent.removeExtra(EXTRA_HOME_PAGE);
        if (page != null && !page.isEmpty()) navigation().openPage(page, intent.getExtras());
    }

    public void chooseAvatar() {
        if (vm != null && !vm.busy) pickAvatar.launch(new String[] {"image/*"});
    }

    public boolean requireLogin() {
        if (FirebaseAuth.getInstance().getCurrentUser() != null) return true;
        startActivity(new Intent(this, Login.class));
        return false;
    }

    public void confirmAccountDeletion() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || vm == null || vm.busy) return;
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
