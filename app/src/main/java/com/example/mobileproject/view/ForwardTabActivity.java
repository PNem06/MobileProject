package com.example.mobileproject.view;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.example.mobileproject.MainActivity;
import com.example.mobileproject.navigation.MainTab;

/** Compatibility for older notifications/intents; never creates a second tab UI. */
public abstract class ForwardTabActivity extends AppCompatActivity {
    protected abstract MainTab destination();
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Intent next = MainActivity.tabIntent(this, destination());
        if (getIntent().getExtras() != null) next.putExtras(getIntent().getExtras());
        next.putExtra(MainActivity.EXTRA_HOME_PAGE, destination().page);
        startActivity(next);
        finish();
        overridePendingTransition(0, 0);
    }
}
