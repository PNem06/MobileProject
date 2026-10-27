package com.example.mobileproject.navigation;

import static org.junit.Assert.*;
import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.example.mobileproject.R;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = {30,35})
@LooperMode(LooperMode.Mode.PAUSED)
public class MainNavigationInsetsTest {
    private ActivityController<Host> activity;
    private LinearLayout root;
    private View menu, content;
    @Before public void start() {
        activity = Robolectric.buildActivity(Host.class).setup();
        root = (LinearLayout) activity.get().findViewById(R.id.mainNavigationRoot);
        menu = root.findViewById(R.id.mainBottomMenu); content = root.findViewById(R.id.mainTabContent);
        MainNavigationInsets.install(root,R.id.mainBottomMenu);
    }
    @After public void stop() { activity.pause().stop().destroy(); }
    private WindowInsetsCompat insets(int left, int top, int right, int bottom, int ime) {
        return new WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.systemBars(),Insets.of(left,top,right,bottom))
                .setInsets(WindowInsetsCompat.Type.ime(),Insets.of(0,0,0,ime))
                .setVisible(WindowInsetsCompat.Type.ime(),ime>0).build();
    }
    private void apply(WindowInsetsCompat value) {
        ViewCompat.dispatchApplyWindowInsets(root,value);
        root.measure(View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1920,View.MeasureSpec.EXACTLY));
        root.layout(0,0,1080,1920);
    }
    @Test public void menuTouchesBottomAndOnlyItsBackgroundUsesTheSystemInset() {
        apply(insets(0,72,0,90,0));
        assertEquals(0,root.getPaddingBottom());
        assertEquals(root.getHeight(),menu.getBottom());
        assertEquals(90,menu.getPaddingBottom());
        assertEquals(menu.getTop(),content.getBottom());
    }
    @Test public void repeatedInsetDispatchDoesNotAccumulateBottomSpacing() {
        for(int i=0;i<12;i++) apply(insets(0,72,0,90,0));
        int expected=Math.round(64*root.getResources().getDisplayMetrics().density)+90;
        assertEquals(expected,menu.getHeight()); assertEquals(90,menu.getPaddingBottom());
        assertEquals(1920,menu.getBottom());
    }
    @Test public void gestureNavigationUsesOnlyTheCurrentSmallerInset() {
        apply(insets(0,72,0,90,0)); apply(insets(0,72,0,24,0));
        assertEquals(24,menu.getPaddingBottom());
        assertEquals(Math.round(64*root.getResources().getDisplayMetrics().density)+24,menu.getHeight());
    }
    @Test public void keyboardHidesTheMenuInsteadOfLiftingItIntoTheContent() {
        apply(insets(0,72,0,90,700));
        assertEquals(View.GONE,menu.getVisibility()); assertEquals(700,root.getPaddingBottom());
        assertEquals(1220,content.getBottom());
        apply(insets(0,72,0,90,0));
        assertEquals(View.VISIBLE,menu.getVisibility()); assertEquals(1920,menu.getBottom());
    }
    @Test public void landscapeSideBarsAreAppliedOnceToTheRoot() {
        apply(insets(30,48,90,0,0));
        assertEquals(30,root.getPaddingLeft()); assertEquals(90,root.getPaddingRight());
        assertEquals(0,menu.getPaddingLeft()); assertEquals(0,menu.getPaddingRight());
        assertEquals(30,menu.getLeft()); assertEquals(990,menu.getRight());
    }
    @Test public void aConsumedInsetDoesNotReachNestedTabOrMaterialListeners() {
        WindowInsetsCompat result=ViewCompat.dispatchApplyWindowInsets(root,insets(0,72,0,90,0));
        assertTrue(result.isConsumed());
    }
    public static class Host extends Activity {
        @Override public void onCreate(Bundle state) {
            setTheme(R.style.Theme_MobileProject); super.onCreate(state);
            setContentView(R.layout.fragment_main_navigation);
        }
    }
}
