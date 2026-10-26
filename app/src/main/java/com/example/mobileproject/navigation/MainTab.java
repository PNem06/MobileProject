package com.example.mobileproject.navigation;

import com.example.mobileproject.R;

public enum MainTab {
    EXPLORE("home", R.id.menu_explore), REQUESTS("requests", R.id.menu_request),
    CALENDAR("calendar", R.id.menu_calendar), MESSAGES("messages", R.id.menu_message),
    ACCOUNT("account", R.id.menu_settings);

    public final String page;
    public final int menuId;
    MainTab(String page, int menuId) { this.page = page; this.menuId = menuId; }
    public String tag() { return "main-tab:" + page; }
    public static MainTab fromMenuId(int id) {
        for (MainTab tab : values()) if (tab.menuId == id) return tab;
        return null;
    }
    public static MainTab fromPage(String page) {
        for (MainTab tab : values()) if (tab.page.equals(page)) return tab;
        return null;
    }
}
