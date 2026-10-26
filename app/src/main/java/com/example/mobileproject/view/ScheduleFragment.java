package com.example.mobileproject.view;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mobileproject.R;
import com.example.mobileproject.adapter.ExchangeScheduleAdapter;
import com.example.mobileproject.viewmodel.ExchangeScheduleViewModel;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class ScheduleFragment extends TabContentFragment {
    public ScheduleFragment() { super(R.layout.activity_exchange_schedule); }

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
    public void onViewCreated(@androidx.annotation.NonNull View view, @androidx.annotation.Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindViews();
        findViewById(R.id.scheduleBack).setOnClickListener(v -> backToExplore());
        setupList();

        vm = new ViewModelProvider(this).get(ExchangeScheduleViewModel.class);

        if (savedInstanceState != null) vm.selectTab(savedInstanceState.getInt("scheduleTab", 0));
        setupTabs();

        vm.getVisibleItems().observe(getViewLifecycleOwner(), this::renderList);
        vm.getSelectedTab().observe(getViewLifecycleOwner(), this::renderSelectedTab);
        vm.getError().observe(getViewLifecycleOwner(), message -> {
            if (message != null && !message.isEmpty()) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            }
        });
        vm.getShowingOfflineCache().observe(getViewLifecycleOwner(), cached -> {
            if (Boolean.TRUE.equals(cached)) {
                Toast.makeText(
                        requireContext(),
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
                        ? new GridLayoutManager(requireContext(), 2)
                        : new LinearLayoutManager(requireContext())
        );
        list.setHasFixedSize(false);

        adapter = new ExchangeScheduleAdapter(item -> {
            if (item.postId == null || item.postId.isEmpty()) {
                return;
            }

            Intent intent = new Intent(requireContext(), Detail.class);
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
    public void onResume() {
        super.onResume();
        vm.start();
    }

    @Override
    public void onPause() {
        vm.stop();
        super.onPause();
    }
    @Override public void onSaveInstanceState(@androidx.annotation.NonNull Bundle out) {
        Integer tab = vm.getSelectedTab().getValue();
        out.putInt("scheduleTab", tab == null ? 0 : tab);
        super.onSaveInstanceState(out);
    }
    @Override public void onDestroyView() {
        ((RecyclerView) findViewById(R.id.recyclerExchangeSchedule)).setAdapter(null);
        adapter = null;
        super.onDestroyView();
    }

}
