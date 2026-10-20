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
                GoogleSignInAccount account = GoogleSignIn.getSignedInAccountFromIntent(result.getData()).getResult(ApiException.class);
                if (account.getIdToken() == null) { show("Không nhận được Google ID Token."); return; }
                service.signInWithGoogle(account.getIdToken(), callback());
            } catch (ApiException e) { show("Google Sign-In lỗi: " + e.getStatusCode()); }
        });
        findViewById(R.id.btnLogin).setOnClickListener(v -> {
            String e = email.getText().toString().trim();
            String p = password.getText().toString();
            if (e.isEmpty() || p.isEmpty()) { show("Vui lòng nhập email và mật khẩu."); return; }
            service.signInWithPassword(e, p, callback());
        });

        findViewById(R.id.btnGoogle).setOnClickListener(v -> {
            googleClient.signOut().addOnCompleteListener(this, task -> {
                if (task.isSuccessful()) {
                    googleLauncher.launch(googleClient.getSignInIntent());
                } else {
                    show("Không thể đặt lại phiên đăng nhập Google. Vui lòng thử lại.");
                }
            });
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