package com.example.mobileproject.navigation;

import android.view.View;
import android.view.ViewGroup;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/** One owner for system insets; the menu background extends to the bottom system bar. */
public final class MainNavigationInsets {
    private MainNavigationInsets() { }
    public static void install(View root, int menuId) {
        View menu = root.findViewById(menuId);
        if (menu != null) ViewCompat.setOnApplyWindowInsetsListener(menu,
                (view, insets) -> WindowInsetsCompat.CONSUMED);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            apply(view, view.findViewById(menuId), insets);
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(root);
    }
    public static void apply(View root, View menu, WindowInsetsCompat insets) {
        Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                | WindowInsetsCompat.Type.displayCutout());
        Insets keyboard = insets.getInsets(WindowInsetsCompat.Type.ime());
        boolean typing = insets.isVisible(WindowInsetsCompat.Type.ime()) && keyboard.bottom > bars.bottom;
        root.setPadding(bars.left, bars.top, bars.right, typing ? keyboard.bottom : 0);
        if (menu == null) return;
        menu.setVisibility(typing ? View.GONE : View.VISIBLE);
        // An explicit content height avoids Material's wrap_content/inset padding being counted twice.
        int contentHeight = Math.round(64 * root.getResources().getDisplayMetrics().density);
        menu.setMinimumHeight(contentHeight);
        menu.setPadding(0, 0, 0, bars.bottom);
        ViewGroup.LayoutParams params = menu.getLayoutParams();
        if (params.height != contentHeight + bars.bottom) {
            params.height = contentHeight + bars.bottom;
            menu.setLayoutParams(params);
        }
    }
}
