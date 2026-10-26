package com.example.mobileproject.notifications;

import static org.junit.Assert.*;
import android.app.Application;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(application=Application.class,sdk=35)
public class NotificationDeliveryStoreTest {
    private NotificationDeliveryStore store;
    @Before public void setUp() {
        RuntimeEnvironment.getApplication().getSharedPreferences("com.example.mobileproject.notification_delivery_v1",0).edit().clear().commit();
        store=new NotificationDeliveryStore(RuntimeEnvironment.getApplication());
    }
    @Test public void oneClaimPerNotification() { assertTrue(store.claim("a","n")); assertFalse(store.claim("a","n")); }
    @Test public void newStoreRetainsDeliveryRecord() {
        store.claim("a","n"); assertFalse(new NotificationDeliveryStore(RuntimeEnvironment.getApplication()).claim("a","n"));
    }
    @Test public void differentUsersAndDifferentIdsAreIndependent() {
        assertTrue(store.claim("a","n")); assertTrue(store.claim("b","n")); assertTrue(store.claim("a","other"));
    }
    @Test public void keysCannotCollideWhenIdsContainSeparators() {
        assertTrue(store.claim("a:b","c")); assertTrue(store.claim("a","b:c"));
    }
    @Test public void failedDisplayCanReleaseTheClaim() {
        store.claim("a","n"); store.release("a","n"); assertTrue(store.claim("a","n"));
    }
    @Test public void emptyIdentityIsNotDelivered() { assertFalse(store.claim("","n")); assertFalse(store.claim("a","")); }
}
