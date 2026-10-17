package com.example.mobileproject.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.mobileproject.repository.ExchangeScheduleRepository;
import com.example.mobileproject.ui.ExchangeScheduleItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class ExchangeScheduleViewModel extends AndroidViewModel {

    public static final int TAB_UPCOMING = 0;
    public static final int TAB_ONGOING = 1;
    public static final int TAB_COMPLETED = 2;

    private final ExchangeScheduleRepository repository;
    private final MutableLiveData<Integer> selectedTab =
            new MutableLiveData<>(TAB_UPCOMING);
    private final MediatorLiveData<List<ExchangeScheduleItem>> visibleItems =
            new MediatorLiveData<>();

    private List<ExchangeScheduleItem> allItems = Collections.emptyList();

    public ExchangeScheduleViewModel(@NonNull Application application) {
        super(application);
        repository = new ExchangeScheduleRepository(application);

        visibleItems.addSource(repository.getItems(), list -> {
            allItems = list == null ? Collections.emptyList() : list;
            applyFilter();
        });
        visibleItems.addSource(selectedTab, tab -> applyFilter());
    }

    public LiveData<List<ExchangeScheduleItem>> getVisibleItems() {
        return visibleItems;
    }

    public LiveData<Integer> getSelectedTab() {
        return selectedTab;
    }

    public LiveData<String> getError() {
        return repository.getError();
    }

    public LiveData<Boolean> getShowingOfflineCache() {
        return repository.getShowingOfflineCache();
    }

    public void selectTab(int tab) {
        selectedTab.setValue(tab);
    }

    public void start() {
        repository.start();
    }

    public void stop() {
        repository.stop();
    }

    private void applyFilter() {
        int tab = selectedTab.getValue() == null ? TAB_UPCOMING : selectedTab.getValue();
        long now = System.currentTimeMillis();
        List<ExchangeScheduleItem> result = new ArrayList<>();

        for (ExchangeScheduleItem item : allItems) {
            boolean match;
            if (tab == TAB_UPCOMING) {
                match = "ACCEPTED".equals(item.status)
                        && (item.startDate == null || item.startDate.getTime() > now);
            } else if (tab == TAB_ONGOING) {
                boolean dateOngoing = item.startDate != null && item.endDate != null
                        && item.startDate.getTime() <= now
                        && item.endDate.getTime() >= now;
                match = "ONGOING".equals(item.status)
                        || ("ACCEPTED".equals(item.status) && dateOngoing);
            } else {
                boolean dateFinished = item.endDate != null && item.endDate.getTime() < now;
                match = "COMPLETED".equals(item.status)
                        || ("ACCEPTED".equals(item.status) && dateFinished);
            }
            if (match) result.add(item);
        }

        result.sort((a, b) -> {
            long aTime = a.startDate == null ? 0 : a.startDate.getTime();
            long bTime = b.startDate == null ? 0 : b.startDate.getTime();
            return tab == TAB_COMPLETED
                    ? Long.compare(bTime, aTime)
                    : Long.compare(aTime, bTime);
        });
        visibleItems.setValue(result);
    }

    public ExchangeScheduleItem nextUpcomingWithinTwoDays() {
        long now = System.currentTimeMillis();
        long twoDays = 2L * 24 * 60 * 60 * 1000;
        ExchangeScheduleItem best = null;
        for (ExchangeScheduleItem item : allItems) {
            if (!"ACCEPTED".equals(item.status) || item.startDate == null) continue;
            long diff = item.startDate.getTime() - now;
            if (diff >= 0 && diff <= twoDays) {
                if (best == null || item.startDate.before(best.startDate)) best = item;
            }
        }
        return best;
    }

    @Override
    protected void onCleared() {
        repository.stop();
        super.onCleared();
    }
}
