package com.example.mobileproject.view;

import static org.junit.Assert.*;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.mobileproject.R;
import com.example.mobileproject.viewmodel.HomeViewModel;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class AccountWalletEntryTest {
    private ActivityController<AdminHost> controller;
    private ProfileModel model;

    @Before public void start() {
        Application app = RuntimeEnvironment.getApplication();
        for (FirebaseApp existing : FirebaseApp.getApps(app)) existing.delete();
        FirebaseApp.initializeApp(app, new FirebaseOptions.Builder()
                .setApplicationId("1:1234567890:android:0000000000000000")
                .setApiKey("AIzaSy000000000000000000000000000000000")
                .setProjectId("demo-shared-wallet").build());
        controller = Robolectric.buildActivity(AdminHost.class).setup();
        model = new ProfileModel(app);
    }
    @After public void stop() {
        model.dispose();
        controller.pause().stop().destroy();
        for (FirebaseApp app : FirebaseApp.getApps(RuntimeEnvironment.getApplication())) app.delete();
    }

    @Test public void adminAccountOpensWalletFromNonMainActivityWithoutCrashing() {
        AccountUi ui = new AccountUi(controller.get(), model, controller.get().content, null);
        assertNull(ui.activity); // The original click dereferenced this null MainActivity field.
        ui.account();
        assertEquals("Ví quản trị", ((TextView) controller.get().findViewById(R.id.walletEntryTitle)).getText().toString());
        controller.get().findViewById(R.id.btnWallet).performClick();
        Intent opened = Shadows.shadowOf(controller.get()).getNextStartedActivity();
        assertNotNull(opened);
        assertEquals(WalletActivity.class.getName(), opened.getComponent().getClassName());
        assertNull(Shadows.shadowOf(controller.get()).getNextStartedActivity());
    }

    @Test public void userAccountKeepsPersonalWalletLabelAndDestination() {
        model.role = "USER";
        new AccountUi(controller.get(), model, controller.get().content, null).account();
        assertEquals("Ví của tôi", ((TextView) controller.get().findViewById(R.id.walletEntryTitle)).getText().toString());
        controller.get().findViewById(R.id.btnWallet).performClick();
        assertEquals(WalletActivity.class.getName(), Shadows.shadowOf(controller.get())
                .getNextStartedActivity().getComponent().getClassName());
    }

    public static class AdminHost extends AppCompatActivity {
        LinearLayout content;
        @Override public void onCreate(Bundle state) {
            setTheme(R.style.Theme_MobileProject);
            super.onCreate(state);
            content = new LinearLayout(this);
            content.setOrientation(LinearLayout.VERTICAL);
            setContentView(content);
        }
    }
    private static class ProfileModel extends HomeViewModel {
        String role = "ADMIN";
        ProfileModel(Application app) { super(app); }
        @Override public boolean signedIn() { return true; }
        @Override public JSONObject profile() {
            JSONObject value = new JSONObject();
            try { value.put("fullName", "Admin thử nghiệm"); value.put("role", role); }
            catch (Exception e) { throw new AssertionError(e); }
            return value;
        }
        @Override public JSONArray rows(String key) { return new JSONArray(); }
        void dispose() { super.onCleared(); }
    }
}
