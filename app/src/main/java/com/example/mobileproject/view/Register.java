
package com.example.mobileproject.view;

import android.app.AlertDialog;
import android.net.Uri;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.mobileproject.R;
import com.example.mobileproject.service.AuthService;

public class Register extends AppCompatActivity {

    // Luu anh nguoi dung da chon
    private Uri avatarUri;

    private ImageView regAvatar;
    private TextView regChooseAvatar;

    // Mo thu vien anh cua dien thoai
    private final ActivityResultLauncher<String> pickAvatar =
            registerForActivityResult(
                    new ActivityResultContracts.GetContent(),
                    uri -> {
                        if (uri != null) {
                            avatarUri = uri;

                            // Hien thi anh vua chon
                            regAvatar.setImageURI(uri);

                            regChooseAvatar.setText("Thay đổi ảnh đại diện");
                        }
                    }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_register);

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.main),
                (v, insets) -> {
                    Insets systemBars = insets.getInsets(
                            WindowInsetsCompat.Type.systemBars()
                    );

                    v.setPadding(
                            systemBars.left,
                            systemBars.top,
                            systemBars.right,
                            systemBars.bottom
                    );

                    return insets;
                }
        );

        // Anh dai dien
        regAvatar = findViewById(R.id.regAvatar);
        regChooseAvatar = findViewById(R.id.regChooseAvatar);

        View.OnClickListener chooseAvatarClick = v -> {
            pickAvatar.launch("image/*");
        };

        regAvatar.setOnClickListener(chooseAvatarClick);
        regChooseAvatar.setOnClickListener(chooseAvatarClick);

        // Cac o nhap thong tin
        EditText name = findViewById(R.id.regFullName);
        EditText username = findViewById(R.id.regUsername);
        EditText email = findViewById(R.id.regEmail);
        EditText phone = findViewById(R.id.regPhone);
        EditText pass = findViewById(R.id.regPassword);
        EditText confirm = findViewById(R.id.regConfirm);

        // Nut Dang ky
        findViewById(R.id.regSubmit).setOnClickListener(v -> {

            if (!pass.getText().toString().equals(
                    confirm.getText().toString())) {

                confirm.setError("Mật khẩu xác nhận không khớp.");
                return;
            }

            v.setEnabled(false);

            // Tam thoi giu nguyen luong dang ky hien tai.
            // Buoc tiep theo se bo sung avatarUri vao AuthService.
            new AuthService().registerWithPassword(
                    username.getText().toString(),
                    pass.getText().toString(),
                    email.getText().toString(),
                    name.getText().toString(),
                    phone.getText().toString(),

                    new AuthService.Callback() {

                        @Override
                        public void onSuccess(String role) {

                            if (isFinishing() || isDestroyed()) {
                                return;
                            }

                            new AlertDialog.Builder(Register.this)
                                    .setTitle("Thành công")
                                    .setMessage(
                                            "Đăng ký thành công! Vui lòng đăng nhập."
                                    )
                                    .setCancelable(false)
                                    .setPositiveButton(
                                            "Về trang đăng nhập",
                                            (d, w) -> finish()
                                    )
                                    .show();
                        }

                        @Override
                        public void onError(String message) {

                            if (isFinishing() || isDestroyed()) {
                                return;
                            }

                            v.setEnabled(true);

                            Toast.makeText(
                                    Register.this,
                                    message,
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                    }
            );
        });

        // Nut Quay lai
        findViewById(R.id.regBack).setOnClickListener(
                v -> finish()
        );
    }
}
