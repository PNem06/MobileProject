package com.example.mobileproject.view;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.mobileproject.MainActivity;
import com.example.mobileproject.R;
import com.example.mobileproject.service.AuthService;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;

public class Login extends AppCompatActivity {
    private AuthService service;
    private GoogleSignInClient googleClient;
    private ActivityResultLauncher<Intent> googleLauncher;
    private EditText email, password;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        getWindow().getDecorView().setSystemUiVisibility(
                android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                        | android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
        setContentView(R.layout.activity_login);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        service = new AuthService();
        email = findViewById(R.id.edtEmail);
        password = findViewById(R.id.edtPassword);
        GoogleSignInOptions options = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id)).requestEmail().build();
        googleClient = GoogleSignIn.getClient(this, options);
        googleLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            try {
                if (result.getData() == null) { show("Đăng nhập Google bị hủy."); return; }
                GoogleSignInAccount account = GoogleSignIn.getSignedInAccountFromIntent(result.getData()).getResult(ApiException.class);
                if (account == null || account.getIdToken() == null) { show("Không nhận được Google ID Token."); return; }
                service.signInWithGoogle(account.getIdToken(), callback());
            } catch (ApiException e) { show("Google Sign-In lỗi code " + e.getStatusCode() + ": " + e.getLocalizedMessage()); }
            catch (Exception e) { show("Google Sign-In thất bại: " + e.getLocalizedMessage()); }
        });
        findViewById(R.id.btnLogin).setOnClickListener(v -> {
            String e = email.getText().toString().trim();
            String p = password.getText().toString();
            if (e.isEmpty() || p.isEmpty()) { show("Vui lòng nhập email và mật khẩu."); return; }
            service.signInWithPassword(e, p, callback());
        });

        findViewById(R.id.btnGoogle).setOnClickListener(v -> {
            int availability = com.google.android.gms.common.GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this);
            if (availability != com.google.android.gms.common.ConnectionResult.SUCCESS) {
                android.app.Dialog dialog = com.google.android.gms.common.GoogleApiAvailability.getInstance().getErrorDialog(this, availability, 9000);
                if (dialog != null) dialog.show();
                else show("Dịch vụ Google Play không khả dụng.");
                return;
            }
            try {
                googleClient.signOut().addOnCompleteListener(this, task -> {
                    try {
                        googleLauncher.launch(googleClient.getSignInIntent());
                    } catch (Exception e) {
                        show("Không thể khởi động Google Sign-In: " + e.getLocalizedMessage());
                    }
                });
            } catch (Exception e) {
                try {
                    googleLauncher.launch(googleClient.getSignInIntent());
                } catch (Exception ex) {
                    show("Lỗi Google Sign-In: " + ex.getLocalizedMessage());
                }
            }
        });

        findViewById(R.id.btnRegister).setOnClickListener(v -> startActivity(new Intent(this, Register.class)));
    }
    private AuthService.Callback callback() { return new AuthService.Callback() {
        @Override public void onSuccess(String role) {
            if (isFinishing() || isDestroyed()) return;
            Intent next;
            if ("ADMIN".equals(role)) next = new Intent(Login.this, DashboardAdmin.class);
            else {
                // TODO: Thay MainActivity bằng Home.class khi nhóm hoàn thiện Home.
                next = new Intent(Login.this, MainActivity.class);
            }
            startActivity(next);
            finish();
        }
        @Override public void onError(String message) { show(message); }
    }; }
    private void show(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
}