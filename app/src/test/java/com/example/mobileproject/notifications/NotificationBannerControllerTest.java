package com.example.mobileproject.notifications;

import static org.junit.Assert.*;
import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import com.example.mobileproject.R;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
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
import org.robolectric.annotation.LooperMode;

@RunWith(RobolectricTestRunner.class)
@Config(application=Application.class,sdk=35)
@LooperMode(LooperMode.Mode.PAUSED)
public class NotificationBannerControllerTest {
    private ActivityController<Host> activity;
    private NotificationDeliveryStore store;
    private NotificationBannerController banners;
    private String opened;
    @Before public void start() {
        RuntimeEnvironment.getApplication().getSharedPreferences("com.example.mobileproject.notification_delivery_v1",0).edit().clear().commit();
        activity=Robolectric.buildActivity(Host.class).setup();
        store=new NotificationDeliveryStore(RuntimeEnvironment.getApplication());
        banners=new NotificationBannerController(store,(host,notice)->opened=notice.id);
        banners.switchUser("a"); banners.resumed(activity.get());
    }
    @After public void stop() { banners.paused(activity.get()); activity.pause().stop().destroy(); }
    private Notice notice(String id,boolean read) {
        Map<String,Object> data=new HashMap<>(); data.put("title","Notice "+id); data.put("isRead",read);
        return new Notice(id,data);
    }
    private View banner() { return activity.get().findViewById(android.R.id.content).findViewWithTag(NotificationBannerController.BANNER_TAG); }
    private void expire() { Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(3500)); }
    @Test public void repeatedSnapshotsDoNotShowTheSameNoticeAgain() {
        banners.snapshot("a",Collections.singletonList(notice("n",false)),true);
        View first=banner(); assertNotNull(first);
        banners.snapshot("a",Collections.singletonList(notice("n",false)),true); assertSame(first,banner());
        expire(); assertNull(banner());
        banners.snapshot("a",Collections.singletonList(notice("n",false)),true); assertNull(banner());
    }
    @Test public void cacheConfirmationDoesNotLoseOrDuplicateNotification() {
        banners.snapshot("a",Collections.singletonList(notice("n",false)),false); assertNull(banner());
        banners.snapshot("a",Collections.singletonList(notice("n",false)),true); assertNotNull(banner());
        expire(); banners.snapshot("a",Collections.singletonList(notice("n",false)),true); assertNull(banner());
    }
    @Test public void alreadyReadNoticeDoesNotDisplay() {
        banners.snapshot("a",Collections.singletonList(notice("n",true)),true); assertNull(banner());
        assertFalse(store.shown("a","n"));
    }
    @Test public void onlyOneBannerIsMountedAtATime() {
        banners.snapshot("a",Arrays.asList(notice("one",false),notice("two",false)),true);
        assertNotNull(banner()); assertTrue(store.shown("a","one")); assertFalse(store.shown("a","two"));
        expire(); assertNotNull(banner()); assertTrue(store.shown("a","two")); expire(); assertNull(banner());
    }
    @Test public void queuedNoticeReadOnAnotherClientIsRemoved() {
        banners.snapshot("a",Arrays.asList(notice("one",false),notice("two",false)),true);
        banners.snapshot("a",Arrays.asList(notice("one",false),notice("two",true)),true);
        expire(); assertNull(banner()); assertFalse(store.shown("a","two"));
    }
    @Test public void backgroundDeliveryWaitsForAnActualActivity() {
        banners.paused(activity.get());
        banners.snapshot("a",Collections.singletonList(notice("n",false)),true);
        assertFalse(store.shown("a","n"));
        banners.resumed(activity.get()); assertNotNull(banner());
    }
    @Test public void resumeAndRecreatedControllerDoNotRepeatShownNotice() {
        banners.snapshot("a",Collections.singletonList(notice("n",false)),true);
        banners.paused(activity.get()); banners.resumed(activity.get()); assertNull(banner());
        banners=new NotificationBannerController(new NotificationDeliveryStore(RuntimeEnvironment.getApplication()),(a,n)->{});
        banners.switchUser("a"); banners.resumed(activity.get());
        banners.snapshot("a",Collections.singletonList(notice("n",false)),true); assertNull(banner());
    }
    @Test public void changingUserDropsOldBannerAndOldCallbacks() {
        banners.snapshot("a",Collections.singletonList(notice("n",false)),true); assertNotNull(banner());
        banners.switchUser("b"); assertNull(banner());
        banners.snapshot("a",Collections.singletonList(notice("old",false)),true); assertNull(banner());
        banners.snapshot("b",Collections.singletonList(notice("n",false)),true); assertNotNull(banner());
    }
    @Test public void tappingBannerUsesSameNoticeWithoutMarkingReadMerelyByDisplay() {
        Notice n=notice("n",false); banners.snapshot("a",Collections.singletonList(n),true);
        assertFalse(n.read()); banner().performClick(); assertEquals("n",opened); assertNull(banner());
    }
    public static class Host extends Activity {
        @Override public void onCreate(Bundle saved) {
            setTheme(R.style.Theme_MobileProject); super.onCreate(saved); setContentView(new FrameLayout(this));
        }
    }
}
