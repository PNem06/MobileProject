package com.example.mobileproject.viewmodel;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.mobileproject.model.AdminUserItem;
import com.example.mobileproject.repository.AdminUserRepository;

import java.util.ArrayList;
import java.util.List;

public final class AdminUsersViewModel extends AndroidViewModel {

    private final AdminUserRepository repository;

    public String query = "";
    public String filter = "ALL";
    public String selectedId;

    public AdminUsersViewModel(Application app) {
        super(app);
        repository = new AdminUserRepository(app);
    }

    public LiveData<AdminUserRepository.State> getState() {
        return repository.getState();
    }

    public LiveData<String> getMessage() {
        return repository.getMessage();
    }

    public AdminUserRepository.State snapshot() {
        return repository.getState().getValue();
    }

    public void consumeMessage() {
        repository.consumeMessage();
    }

    public void enablePostDetails() {
        repository.enablePostDetails();
    }

    public void start() {
        repository.start();
    }

    public void stop() {
        repository.stop();
    }

    public void setConnected(boolean value) {
        repository.setConnected(value);
    }

    public void refreshUi() {
        repository.refreshUi();
    }

    public boolean writable() {
        return snapshot().writable;
    }

    public String sourceLabel() {
        return snapshot().source;
    }

    public void changeStatus(AdminUserItem user) {
        repository.changeStatus(user);
    }

    public List<AdminUserItem> visibleUsers() {
        List<AdminUserItem> result = new ArrayList<>();

        for (AdminUserItem user : snapshot().users) {
            boolean matchesStatus =
                    "ALL".equals(filter) || filter.equals(user.status);

            if (user.matches(query) && matchesStatus) {
                result.add(user);
            }
        }

        return result;
    }

    public AdminUserItem find(String id) {
        if (id == null) {
            return null;
        }

        for (AdminUserItem user : snapshot().users) {
            if (user.documentId.equals(id)) {
                return user;
            }
        }

        return null;
    }

    public String summary() {
        long active = snapshot().users.stream()
                .filter(AdminUserItem::active)
                .count();

        long inactive = snapshot().users.stream()
                .filter(user -> "INACTIVE".equals(user.status))
                .count();

        return snapshot().users.size()
                + " tài khoản · "
                + active
                + " hoạt động · "
                + inactive
                + " đã khóa";
    }

    @Override
    protected void onCleared() {
        repository.close();
        super.onCleared();
    }
}