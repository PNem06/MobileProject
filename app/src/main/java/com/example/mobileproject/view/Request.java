package com.example.mobileproject.view;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.mobileproject.R;
import com.example.mobileproject.MainActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class Request extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_request);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        setupBottomNavigation();
    }

    private void setupBottomNavigation() {
        BottomNavigationView navigation = findViewById(R.id.requestBottomMenu);
        navigation.getMenu().findItem(R.id.menu_request).setChecked(true);
        navigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.menu_request) return true;

            Intent intent;
            if (id == R.id.menu_explore || id == R.id.menu_settings) {
                intent = new Intent(this, MainActivity.class);
                intent.putExtra(MainActivity.EXTRA_HOME_PAGE,
                        id == R.id.menu_settings ? "account" : "home");
            } else if (id == R.id.menu_calendar) {
                intent = new Intent(this, ExchangeScheduleActivity.class);
            } else if (id == R.id.menu_message) {
                intent = new Intent(this, Chat.class);
            } else {
                return false;
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            return true;
        });
    }
}
