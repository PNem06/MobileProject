package com.example.mobileproject.view;

import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.mobileproject.R;

public class Chat extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);

        setContentView(R.layout.activity_chat);

        View root = findViewById(R.id.chatRoot);

        ViewCompat.setOnApplyWindowInsetsListener(
                root,
                (view, insets) -> {

                    Insets bars =
                            insets.getInsets(
                                    WindowInsetsCompat.Type.systemBars()
                                            | WindowInsetsCompat.Type.ime()
                            );

                    view.setPadding(
                            bars.left,
                            bars.top,
                            bars.right,
                            bars.bottom
                    );

                    return insets;
                }
        );

        WindowCompat
                .getInsetsController(
                        getWindow(),
                        getWindow().getDecorView()
                )
                .setAppearanceLightStatusBars(true);

        WindowCompat
                .getInsetsController(
                        getWindow(),
                        getWindow().getDecorView()
                )
                .setAppearanceLightNavigationBars(true);

        View back = findViewById(R.id.btnChatBack);

        if (back != null) {
            back.setOnClickListener(v ->
                    getOnBackPressedDispatcher().onBackPressed()
            );
        }
    }
}