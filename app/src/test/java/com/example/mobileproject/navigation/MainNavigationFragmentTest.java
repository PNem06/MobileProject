package com.example.mobileproject.navigation;

import static org.junit.Assert.*;
import android.app.Application;
import android.os.Bundle;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;
import com.example.mobileproject.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

/** Exercises the production menu/FragmentManager with isolated content, without Firebase. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
@LooperMode(LooperMode.Mode.PAUSED)
public class MainNavigationFragmentTest {
    private ActivityController<TestHost> controller;
    private TestHost host;
    private TestNavigation nav;

    @Before public void start() {
        controller = Robolectric.buildActivity(TestHost.class).setup();
        host = controller.get();
        nav = host.navigation();
        idle();
    }
    @After public void stop() { controller.pause().stop().destroy(); }
    private void idle() { Shadows.shadowOf(Looper.getMainLooper()).idle(); }
    private BottomNavigationView menu() { return host.findViewById(R.id.mainBottomMenu); }
    private void choose(MainTab tab) { menu().setSelectedItemId(tab.menuId); idle(); }

    @Test public void allFiveTabsKeepOneMenuAndOneActivity() {
        View originalMenu = menu();
        for (MainTab tab : MainTab.values()) {
            choose(tab);
            assertSame(originalMenu, menu());
            assertSame(host, nav.currentTab().requireActivity());
            assertEquals(tab, nav.selectedTab());
            assertEquals(tab.menuId, menu().getSelectedItemId());
        }
        assertEquals(5, nav.getChildFragmentManager().getFragments().size());
        assertNull(Shadows.shadowOf(host).getNextStartedActivity());
    }

    @Test public void returningToTabKeepsItsFragmentViewAndInput() {
        choose(MainTab.MESSAGES);
        Fragment messages = nav.currentTab();
        EditText input = (EditText) messages.requireView();
        input.setText("Nội dung đang xem");
        choose(MainTab.CALENDAR);
        choose(MainTab.MESSAGES);
        assertSame(messages, nav.currentTab());
        assertSame(input, messages.requireView());
        assertEquals("Nội dung đang xem", input.getText().toString());
    }

    @Test public void reselectingTabDoesNotRecreateContent() {
        choose(MainTab.REQUESTS);
        Fragment original = nav.currentTab();
        View originalView = original.requireView();
        for (int i = 0; i < 12; i++) choose(MainTab.REQUESTS);
        assertSame(original, nav.currentTab());
        assertSame(originalView, original.requireView());
    }

    @Test public void onlySelectedTabIsResumed() {
        for (MainTab tab : MainTab.values()) choose(tab);
        for (Fragment fragment : nav.getChildFragmentManager().getFragments()) {
            assertEquals(fragment == nav.currentTab() ? Lifecycle.State.RESUMED : Lifecycle.State.STARTED,
                    fragment.getLifecycle().getCurrentState());
        }
    }

    @Test public void recreationRestoresSelectionAndViewState() {
        choose(MainTab.MESSAGES);
        ((EditText) nav.currentTab().requireView()).setText("Tìm cuộc trò chuyện");
        controller.recreate();
        host = controller.get();
        nav = host.navigation();
        idle();
        assertEquals(MainTab.MESSAGES, nav.selectedTab());
        assertEquals(R.id.menu_message, menu().getSelectedItemId());
        assertEquals("Tìm cuộc trò chuyện", ((EditText) nav.currentTab().requireView()).getText().toString());
    }

    @Test public void deniedPrivateTabKeepsCurrentSelection() {
        host.allowPrivate = false;
        choose(MainTab.REQUESTS);
        assertEquals(MainTab.EXPLORE, nav.selectedTab());
        assertEquals(R.id.menu_explore, menu().getSelectedItemId());
        assertEquals(1, nav.getChildFragmentManager().getFragments().size());
    }

    @Test public void backReturnsToExploreWithoutAddingActivity() {
        choose(MainTab.CALENDAR);
        assertTrue(nav.handleBack());
        assertEquals(MainTab.EXPLORE, nav.selectedTab());
        assertFalse(nav.handleBack());
        assertNull(Shadows.shadowOf(host).getNextStartedActivity());
    }

    @Test public void sessionChangeDropsCachedPrivateViewsButKeepsMenu() {
        choose(MainTab.MESSAGES);
        Fragment oldMessages = nav.currentTab();
        View originalMenu = menu();
        nav.resetSession();
        assertEquals(MainTab.EXPLORE, nav.selectedTab());
        assertSame(originalMenu, menu());
        assertFalse(oldMessages.isAdded());
        assertEquals(1, nav.getChildFragmentManager().getFragments().size());
    }

    @Test public void unknownDestinationIsIgnored() {
        choose(MainTab.CALENDAR);
        nav.openPage("unknown-destination", null);
        assertEquals(MainTab.CALENDAR, nav.selectedTab());
    }

    @Test public void destinationReceivedWhileStoppedIsAppliedOnResume() {
        Bundle saved = new Bundle();
        controller.pause().saveInstanceState(saved).stop();
        nav.openPage("messages", null);
        assertEquals(MainTab.EXPLORE, nav.selectedTab());
        controller.start().resume().visible();
        idle();
        assertEquals(MainTab.MESSAGES, nav.selectedTab());
    }

    public static class TestHost extends FragmentActivity implements MainNavigationFragment.Host {
        boolean allowPrivate = true;
        @Override public void onCreate(Bundle saved) {
            setTheme(R.style.Theme_MobileProject);
            super.onCreate(saved);
            FrameLayout content = new FrameLayout(this);
            content.setId(R.id.mainNavigationHost);
            setContentView(content);
            if (saved == null) getSupportFragmentManager().beginTransaction()
                    .add(R.id.mainNavigationHost, new TestNavigation(), "test-navigation").commitNow();
        }
        TestNavigation navigation() {
            return (TestNavigation) getSupportFragmentManager().findFragmentByTag("test-navigation");
        }
        @Override public boolean canOpenMainTab(MainTab tab) { return tab == MainTab.EXPLORE || allowPrivate; }
    }
    public static class TestNavigation extends MainNavigationFragment {
        @Override protected Fragment createTab(MainTab tab) { return new ProbeFragment(); }
    }
    public static class ProbeFragment extends Fragment {
        @Override public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle saved) {
            EditText field = new EditText(requireContext());
            field.setId(android.R.id.edit);
            return field;
        }
    }
}
