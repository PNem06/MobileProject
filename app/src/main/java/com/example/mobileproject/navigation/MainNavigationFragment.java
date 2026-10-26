package com.example.mobileproject.navigation;

import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.Lifecycle;
import com.example.mobileproject.R;
import com.example.mobileproject.view.AccountFragment;
import com.example.mobileproject.view.ExploreFragment;
import com.example.mobileproject.view.HomeContentFragment;
import com.example.mobileproject.view.MessagesFragment;
import com.example.mobileproject.view.RequestsFragment;
import com.example.mobileproject.view.ScheduleFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/** Owns the one bottom menu; tab transactions only change mainTabContent. */
public class MainNavigationFragment extends Fragment {
    public interface Host { boolean canOpenMainTab(MainTab tab); }
    private BottomNavigationView menu;
    private MainTab selected = MainTab.EXPLORE;
    private String pendingPage;
    private Bundle pendingArguments;
    private boolean resetPending;

    public MainNavigationFragment() { super(R.layout.fragment_main_navigation); }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);
        if (state != null) {
            MainTab restored = MainTab.fromPage(state.getString("selectedTab"));
            if (restored != null) selected = restored;
            pendingPage = state.getString("pendingPage");
            pendingArguments = state.getBundle("pendingArguments");
            resetPending = state.getBoolean("resetPending");
        }
        menu = view.findViewById(R.id.mainBottomMenu);
        // MainActivity handles system/IME insets once, outside all tab content.
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(menu, (v, insets) -> {
            v.setPadding(0, 0, 0, 0);
            return insets;
        });
        menu.setOnItemSelectedListener(item -> {
            MainTab tab = MainTab.fromMenuId(item.getItemId());
            return tab != null && selectTab(tab);
        });
        menu.setOnItemReselectedListener(item -> { /* Keep the current view and scroll. */ });
        if (!allowed(selected)) selected = MainTab.EXPLORE;
        selectTab(selected);
    }

    private boolean allowed(MainTab tab) { return ((Host) requireActivity()).canOpenMainTab(tab); }

    protected Fragment createTab(MainTab tab) {
        switch (tab) {
            case REQUESTS: return new RequestsFragment();
            case CALENDAR: return new ScheduleFragment();
            case MESSAGES: return new MessagesFragment();
            case ACCOUNT: return new AccountFragment();
            default: return new ExploreFragment();
        }
    }

    public MainTab selectedTab() { return selected; }
    public Fragment currentTab() { return getChildFragmentManager().findFragmentByTag(selected.tag()); }

    public boolean selectTab(MainTab tab) {
        if (!allowed(tab)) return false;
        FragmentManager manager = getChildFragmentManager();
        if (manager.isStateSaved() || menu == null) {
            pendingPage = tab.page;
            return true;
        }
        Fragment target = manager.findFragmentByTag(tab.tag());
        FragmentTransaction change = manager.beginTransaction().setReorderingAllowed(true);
        for (Fragment fragment : manager.getFragments()) {
            if (fragment != target) change.hide(fragment).setMaxLifecycle(fragment, Lifecycle.State.STARTED);
        }
        if (target == null) {
            target = createTab(tab);
            change.add(R.id.mainTabContent, target, tab.tag());
        } else change.show(target);
        change.setMaxLifecycle(target, Lifecycle.State.RESUMED).setPrimaryNavigationFragment(target);
        selected = tab;
        change.commitNow();
        menu.getMenu().findItem(tab.menuId).setChecked(true);
        return true;
    }

    public void openPage(String page, @Nullable Bundle arguments) {
        if (page == null || page.isEmpty()) return;
        if (getChildFragmentManager().isStateSaved() || menu == null) {
            pendingPage = page;
            pendingArguments = arguments == null ? null : new Bundle(arguments);
            return;
        }
        MainTab tab = MainTab.fromPage(page);
        if (tab == null) {
            if (!java.util.Arrays.asList("edit", "settings", "about", "notifications").contains(page)) return;
            if ("edit".equals(page) || "settings".equals(page)) tab = MainTab.ACCOUNT;
            else if ("notifications".equals(page)) tab = MainTab.EXPLORE;
            else tab = currentTab() instanceof HomeContentFragment ? selected : MainTab.EXPLORE;
        }
        if (!selectTab(tab)) return;
        Fragment target = currentTab();
        if (target instanceof HomeContentFragment) ((HomeContentFragment) target).openPage(page);
        else if (target instanceof RequestsFragment && arguments != null
                && (arguments.containsKey("selectReceivedTab") || arguments.containsKey("selectTab"))) {
            ((RequestsFragment) target).selectReceivedTab(arguments.getBoolean("selectReceivedTab")
                    || "received".equalsIgnoreCase(arguments.getString("selectTab")));
        }
    }

    /** Cached private views must never survive a change of signed-in user. */
    public void resetSession() {
        if (menu == null || getChildFragmentManager().isStateSaved()) { resetPending = true; return; }
        FragmentTransaction change = getChildFragmentManager().beginTransaction().setReorderingAllowed(true);
        for (Fragment fragment : getChildFragmentManager().getFragments()) change.remove(fragment);
        change.commitNow();
        resetPending = false;
        pendingPage = null;
        pendingArguments = null;
        selected = MainTab.EXPLORE;
        selectTab(selected);
    }

    public boolean handleBack() {
        Fragment current = currentTab();
        if (current instanceof HomeContentFragment && ((HomeContentFragment) current).handleBack()) return true;
        return selected != MainTab.EXPLORE && selectTab(MainTab.EXPLORE);
    }

    @Override public void onResume() {
        super.onResume();
        requireView().post(() -> {
            if (!isAdded() || getView() == null || getChildFragmentManager().isStateSaved()) return;
            if (resetPending) resetSession();
            if (pendingPage != null) {
                String page = pendingPage;
                Bundle arguments = pendingArguments;
                pendingPage = null;
                pendingArguments = null;
                openPage(page, arguments);
            }
        });
    }

    @Override public void onSaveInstanceState(@NonNull Bundle out) {
        out.putString("selectedTab", selected.page);
        out.putString("pendingPage", pendingPage);
        out.putBundle("pendingArguments", pendingArguments);
        out.putBoolean("resetPending", resetPending);
        super.onSaveInstanceState(out);
    }
    @Override public void onDestroyView() {
        if (menu != null) {
            menu.setOnItemSelectedListener(null);
            menu.setOnItemReselectedListener(null);
        }
        menu = null;
        super.onDestroyView();
    }
}
