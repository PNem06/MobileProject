
package com.example.mobileproject.view;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.mobileproject.R;

public class RentalActivity extends AppCompatActivity {

    private EditText edtFullName;
    private EditText edtBankName;
    private EditText edtAccountNumber;

    private Button btnPay;
    private Button btnPaymentComplete;
    private TextView txtPaymentStatus;

    private String targetPostId;
    private boolean paymentVerified = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rental);

        targetPostId = getIntent().getStringExtra("targetPostId");

        edtFullName = findViewById(R.id.edtFullName);
        edtBankName = findViewById(R.id.edtBankName);
        edtAccountNumber = findViewById(R.id.edtAccountNumber);

        btnPay = findViewById(R.id.btnPay);
        btnPaymentComplete = findViewById(R.id.btnPaymentComplete);
        txtPaymentStatus = findViewById(R.id.txtPaymentStatus);

        btnPaymentComplete.setEnabled(false);
        btnPaymentComplete.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        Color.GRAY
                )
        );

        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(
                    CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(
                    CharSequence s, int start, int before, int count) {
                validateForm();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };

        edtFullName.addTextChangedListener(watcher);
        edtBankName.addTextChangedListener(watcher);
        edtAccountNumber.addTextChangedListener(watcher);

        btnPay.setOnClickListener(v -> startPayment());

        btnPaymentComplete.setOnClickListener(v -> {
            if (!paymentVerified) return;

            // TODO: Save the confirmed booking to Firestore.
            Toast.makeText(this,
                    "Thanh toán đã được xác nhận.",
                    Toast.LENGTH_LONG).show();
        });

        findViewById(R.id.btnRentalCancel)
                .setOnClickListener(v -> finish());
    }

    private void validateForm() {
        boolean valid =
                !edtFullName.getText().toString().trim().isEmpty()
                        && !edtBankName.getText().toString().trim().isEmpty()
                        && !edtAccountNumber.getText().toString().trim().isEmpty();

        btnPay.setEnabled(valid);
    }

    private void startPayment() {
        if (targetPostId == null || targetPostId.trim().isEmpty()) {
            Toast.makeText(this,
                    "Không xác định được phòng cần thuê.",
                    Toast.LENGTH_SHORT).show();
            return;
        }


        Toast.makeText(this,
                "Cần tích hợp cổng thanh toán để tiếp tục.",
                Toast.LENGTH_LONG).show();
    }

    // Call ONLY after your trusted backend confirms successful payment.
    private void onPaymentVerified() {
        paymentVerified = true;

        txtPaymentStatus.setText("Trạng thái: Thanh toán thành công");
        txtPaymentStatus.setTextColor(Color.rgb(0, 128, 0));

        btnPaymentComplete.setEnabled(true);
        btnPaymentComplete.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        Color.rgb(46, 125, 50)
                )
        );
    }
}
