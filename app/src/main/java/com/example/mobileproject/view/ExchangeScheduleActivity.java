package com.example.mobileproject.view;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mobileproject.MainActivity;
import com.example.mobileproject.R;
import com.example.mobileproject.adapter.ExchangeScheduleAdapter;
import com.example.mobileproject.viewmodel.ExchangeScheduleViewModel;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class ExchangeScheduleActivity extends AppCompatActivity {

    private ExchangeScheduleViewModel vm;
    private ExchangeScheduleAdapter adapter;
    private TextView tabUpcoming;
    private TextView tabOngoing;
    private TextView tabCompleted;
    private TextView txtEmpty;
    private TextView txtMonth;
    private TextView txtReminder;
    private View reminderCard;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_exchange_schedule);

        View root = findViewById(R.id.scheduleRoot);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.ime()
            );
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        BottomNavigationView bottomNavigationView = findViewById(R.id.homeDrawerMenu);
        if (bottomNavigationView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(bottomNavigationView, (v, insets) -> {
                v.setPadding(0, 0, 0, 0);
                return insets;
            });
        }
        bindViews();
        setupList();
        setupBottomNavigation();

        vm = new ViewModelProvider(this).get(ExchangeScheduleViewModel.class);

        setupTabs();

        vm.getVisibleItems().observe(this, this::renderList);
        vm.getSelectedTab().observe(this, this::renderSelectedTab);
        vm.getError().observe(this, message -> {
            if (message != null && !message.isEmpty()) {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            }
        });
        vm.getShowingOfflineCache().observe(this, cached -> {
            if (Boolean.TRUE.equals(cached)) {
                Toast.makeText(
                        this,
                        "Đang hiển thị lịch đã lưu trên máy.",
                        Toast.LENGTH_SHORT
                ).show();
            }
        });

        SimpleDateFormat monthFormat =
                new SimpleDateFormat("'Tháng' M, yyyy", new Locale("vi", "VN"));
        txtMonth.setText(monthFormat.format(Calendar.getInstance().getTime()));
    }

    private void bindViews() {
        tabUpcoming = findViewById(R.id.tabUpcoming);
        tabOngoing = findViewById(R.id.tabOngoing);
        tabCompleted = findViewById(R.id.tabCompleted);
        txtEmpty = findViewById(R.id.txtScheduleEmpty);
        txtMonth = findViewById(R.id.txtScheduleMonth);
        txtReminder = findViewById(R.id.txtScheduleReminder);
        reminderCard = findViewById(R.id.scheduleReminderCard);
    }


    private void setupList() {
        RecyclerView list = findViewById(R.id.recyclerExchangeSchedule);
        boolean landscape =
                getResources().getConfiguration().orientation
                        == Configuration.ORIENTATION_LANDSCAPE;

        list.setLayoutManager(
                landscape
                        ? new GridLayoutManager(this, 2)
                        : new LinearLayoutManager(this)
        );
        list.setHasFixedSize(false);

        adapter = new ExchangeScheduleAdapter(item -> {
            if (item.postId == null || item.postId.isEmpty()) {
                return;
            }

            Intent intent = new Intent(this, Detail.class);
            intent.putExtra(Detail.EXTRA_POST_ID, item.postId);
            startActivity(intent);
        });

        list.setAdapter(adapter);
    }

    private void setupTabs() {
        tabUpcoming.setOnClickListener(
                v -> vm.selectTab(ExchangeScheduleViewModel.TAB_UPCOMING)
        );
        tabOngoing.setOnClickListener(
                v -> vm.selectTab(ExchangeScheduleViewModel.TAB_ONGOING)
        );
        tabCompleted.setOnClickListener(
                v -> vm.selectTab(ExchangeScheduleViewModel.TAB_COMPLETED)
        );
    }

    private void setupBottomNavigation() {
        BottomNavigationView navigation = findViewById(R.id.homeDrawerMenu);
        navigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.menu_calendar) return true;

            Intent intent;
            if (id == R.id.menu_request) {
                intent = new Intent(this, Request.class);
            } else if (id == R.id.menu_message) {
                intent = new Intent(this, Chat.class);
            } else if (id == R.id.menu_explore
                    || id == R.id.menu_settings) {
                intent = new Intent(this, MainActivity.class);
                intent.putExtra(MainActivity.EXTRA_HOME_PAGE,
                        id == R.id.menu_settings ? "account" : "home");
            } else {
                return false;
            }

            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            overridePendingTransition(0, 0);
            return true;
        });
        navigation.setSelectedItemId(R.id.menu_calendar);
    }

    private void renderSelectedTab(Integer tabValue) {
        int tab = tabValue == null
                ? ExchangeScheduleViewModel.TAB_UPCOMING
                : tabValue;

        TextView[] tabs = {
                tabUpcoming,
                tabOngoing,
                tabCompleted
        };

        for (int i = 0; i < tabs.length; i++) {
            tabs[i].setSelected(i == tab);
        }
    }

    private void renderList(List<ExchangeScheduleItem> list) {
        adapter.submitList(list);

        txtEmpty.setVisibility(
                list == null || list.isEmpty()
                        ? View.VISIBLE
                        : View.GONE
        );

        ExchangeScheduleItem next = vm.nextUpcomingWithinTwoDays();

        if (next != null) {
            reminderCard.setVisibility(View.VISIBLE);
            txtReminder.setText("Sắp đến lịch trao đổi trong 2 ngày");
        } else {
            reminderCard.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        vm.start();
    }

    @Override
    protected void onStop() {
        vm.stop();
        super.onStop();
    }
}
