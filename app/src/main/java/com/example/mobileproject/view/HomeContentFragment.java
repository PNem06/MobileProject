package com.example.mobileproject.view;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.lifecycle.ViewModelProvider;
import com.example.mobileproject.MainActivity;
import com.example.mobileproject.R;
import com.example.mobileproject.viewmodel.HomeViewModel;

/** Home and Account have their own state but share the team's existing UI renderer. */
public abstract class HomeContentFragment extends TabContentFragment {
    private HomeViewModel vm;
    private HomeUi ui;
    private Bundle viewState;
    protected HomeContentFragment() { super(R.layout.activity_home); }
    protected abstract String initialPage();

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle saved) {
        super.onViewCreated(view, saved);
        vm = new ViewModelProvider(this).get(HomeViewModel.class);
        if (!vm.uiInitialized) vm.page = initialPage();
        ui = new HomeUi((MainActivity) requireActivity(), vm, view, saved == null ? viewState : saved, initialPage());
        vm.uiInitialized = true;
        if (!isHidden()) ((MainActivity) requireActivity()).bindHomeUi(vm, ui);
        vm.changed.observe(getViewLifecycleOwner(), value -> { if (isResumed()) ui.render(); });
        vm.event.observe(getViewLifecycleOwner(), message -> {
            if (message == null || !isResumed()) return;
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            vm.event.setValue(null);
        });
        ui.render();
    }
    public boolean busy() { return vm != null && vm.busy; }
    public void connectionChanged() { if (isResumed() && vm != null) vm.onConnectionChanged(); }
    public void openPage(String page) { if (ui != null) ui.openPage(page); }

    @Override public void onResume() {
        super.onResume();
        ((MainActivity) requireActivity()).bindHomeUi(vm, ui);
        vm.setActive(true);
        ui.render();
        String message = vm.event.getValue();
        if (message != null) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            vm.event.setValue(null);
        }
    }
    @Override public void onPause() { vm.setActive(false); super.onPause(); }

    public boolean handleBack() {
        if (vm.busy) { vm.tell("Vui lòng chờ thao tác hoàn tất."); return true; }
        DrawerLayout drawer = findViewById(R.id.homeRoot);
        if (drawer.isDrawerOpen(GravityCompat.START)) { drawer.closeDrawer(GravityCompat.START); return true; }
        if (!initialPage().equals(vm.page)) {
            ui.openPage(java.util.Arrays.asList("edit", "settings").contains(vm.page) ? "account" : initialPage());
            return true;
        }
        return false;
    }
    @Override public void onSaveInstanceState(@NonNull Bundle out) {
        if (ui != null) ui.saveState(out);
        super.onSaveInstanceState(out);
    }
    @Override public void onDestroyView() {
        if (ui != null) { viewState = new Bundle(); ui.saveState(viewState); ui.dispose(); }
        ui = null;
        super.onDestroyView();
    }
}
