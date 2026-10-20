package com.example.mobileproject.view;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.example.mobileproject.MainActivity;
import com.example.mobileproject.R;
import com.example.mobileproject.viewmodel.HomeViewModel;
import com.google.android.material.navigation.NavigationView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

public class HomeUi extends HomeViews {
    private final FrameLayout header, filters;
    private final LinearLayout content;
    private final ScrollView scroll;
    private final ProgressBar progress;
    private final androidx.appcompat.widget.Toolbar toolbarHome;
    private final LinearLayout homeHeaderContent;
    private final FrameLayout subpageHeader;
    private final EditText searchInput;
    private final View unreadDot;
    private final TextView subpageTitle;
    private final PostCards cards;
    private final AccountUi accountUi;
    private final DrawerLayout drawerLayout;
    private final NavigationView drawerMenu;
    private LinearLayout homeSections;
    private String builtPage = "";
    private int selectedFilter;

    public HomeUi(MainActivity activity, HomeViewModel vm, Bundle saved) {
        super(activity, vm);
        header = activity.findViewById(R.id.homeHeader);
        filters = activity.findViewById(R.id.homeFilters);
        content = activity.findViewById(R.id.homeContent);
        scroll = activity.findViewById(R.id.homeScroll);
        progress = activity.findViewById(R.id.homeProgress);
        homeHeaderContent = activity.findViewById(R.id.headerHomeContent);
        subpageHeader = activity.findViewById(R.id.headerSubpageContent);
        searchInput = activity.findViewById(R.id.edtSearch);
        unreadDot = activity.findViewById(R.id.viewUnreadDot);
        subpageTitle = activity.findViewById(R.id.txtHeaderTitle);
        toolbarHome = activity.findViewById(R.id.toolbarHome);
        drawerLayout = activity.findViewById(R.id.homeRoot);
        drawerMenu = activity.findViewById(R.id.homeDrawerMenu);
        cards = new PostCards(activity, vm);
        accountUi = new AccountUi(activity, vm, content, cards, this::openNewPost);
        setupToolbar();
        setupHeaderControls();
        restoreState(saved);
        scroll.setOnScrollChangeListener(
                (View.OnScrollChangeListener) (v, x, y, oldX, oldY) -> vm.scrollY = y);
    }

    private void setupToolbar() {
        activity.setSupportActionBar(toolbarHome);
        ActionBarDrawerToggle drawerToggle = new ActionBarDrawerToggle(
                activity, drawerLayout, toolbarHome, R.string.open, R.string.close);
        drawerLayout.addDrawerListener(drawerToggle);
        drawerToggle.syncState();
        drawerMenu.setNavigationItemSelectedListener(item -> {
            drawerLayout.closeDrawer(GravityCompat.START);
            return handleDrawerItem(item);
        });
    }

    private boolean handleDrawerItem(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_account) {
            if (requireLogin()) go("account");
        } else if (id == R.id.menu_new_post) {
            openNewPost();
        } else if (id == R.id.menu_notifications) {
            if (requireLogin()) go("notifications");
        } else if (id == R.id.menu_settings) {
            if (requireLogin()) go("settings");
        } else if (id == R.id.menu_about) {
            go("about");
        } else if (id == R.id.menu_logout) {
            if (vm.signedIn()) accountUi.confirmLogout();
            else requireLogin();
        } else {
            return false;
        }
        return true;
    }

    private void setupHeaderControls() {
        activity.findViewById(R.id.btnNotification).setOnClickListener(v -> {
            if (requireLogin()) go("notifications");
        });
        activity.findViewById(R.id.btnHeaderBack).setOnClickListener(v ->
                go(Arrays.asList("edit", "settings", "about").contains(vm.page) ? "account" : "home"));
        change(searchInput, value -> {
            vm.search = value;
            if (homeSections != null) renderHomeSections();
        });
        searchInput.setOnEditorActionListener((v, action, event) -> {
            hideKeyboard();
            return true;
        });
        searchInput.setOnClickListener(v -> {
            Intent intent = new Intent(
                    activity,
                    com.example.mobileproject.view.Search.SearchLocationActivity.class
            );
            activity.startActivity(intent);
        });
    }

    private void restoreState(Bundle saved) {
        if (saved == null) return;
        selectedFilter = saved.getInt("filter", 0);
        Bundle offsets = saved.getBundle("tracks");
        if (offsets != null)
            for (String key : offsets.keySet()) cards.trackOffsets.put(key, offsets.getInt(key));
        if (!vm.uiInitialized) {
            vm.page = saved.getString("page", "home");
            vm.search = saved.getString("search", "");
            vm.draftName = saved.getString("name");
            vm.draftPhone = saved.getString("phone");
            vm.draftAvatar = saved.getString("avatar");
            vm.scrollY = saved.getInt("scroll", 0);
        }
    }

    public void saveState(Bundle out) {
        out.putString("page", vm.page);
        out.putString("search", vm.search);
        out.putString("name", vm.draftName);
        out.putString("phone", vm.draftPhone);
        out.putString("avatar", vm.draftAvatar);
        out.putInt("scroll", vm.scrollY);
        out.putInt("filter", selectedFilter);
        Bundle offsets = new Bundle();
        for (Map.Entry<String, Integer> item : cards.trackOffsets.entrySet())
            offsets.putInt(item.getKey(), item.getValue());
        out.putBundle("tracks", offsets);
    }

    public void openPage(String page) {
        if (vm.busy) {
            vm.tell("Vui lòng chờ thao tác hoàn tất.");
            return;
        }
        hideKeyboard();
        vm.page = page;
        vm.scrollY = 0;
        rebuild();
    }

    public void rebuild() {
        builtPage = "";
        render();
    }

    private void hideKeyboard() {
        InputMethodManager keyboard = getSystemService(InputMethodManager.class);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(header.getWindowToken(), 0);
        header.clearFocus();
    }

    public void render() {
        if (!vm.signedIn() && !Arrays.asList("home", "about").contains(vm.page)) vm.page = "home";
        progress.setVisibility(vm.busy ? View.VISIBLE : View.GONE);
        buildHeader();
        buildNavigation();
        buildHomeFilters();
        if (!vm.online() && !Arrays.asList("settings", "about").contains(vm.page)) {
            builtPage = "";
            homeSections = null;
            content.removeAllViews();
            empty(
                    content,
                    "Chưa có kết nối mạng",
                    "Kết nối Wi-Fi hoặc dữ liệu di động để tiếp tục.");
            return;
        }
        if ("edit".equals(vm.page) && "edit".equals(builtPage)) {
            enable(content, !vm.busy);
            return;
        }
        int y = vm.scrollY;
        if ("home".equals(vm.page) && "home".equals(builtPage) && homeSections != null) {
            renderHomeSections();
            scroll.post(() -> scroll.scrollTo(0, y));
            return;
        }
        content.removeAllViews();
        builtPage = vm.page;
        switch (vm.page) {
            case "account":
                accountUi.account();
                break;
            case "edit":
                accountUi.edit();
                break;
            case "settings":
                accountUi.settings();
                break;
            case "about":
                accountUi.about();
                break;
            case "notifications":
                accountUi.notifications();
                break;
            default:
                home();
                break;
        }
        scroll.post(() -> scroll.scrollTo(0, y));
    }

    private void buildHeader() {
        boolean home = "home".equals(vm.page);
        MenuItem logoutItem = drawerMenu.getMenu().findItem(R.id.menu_logout);
        if (logoutItem != null) logoutItem.setTitle(vm.signedIn() ? "Đăng xuất" : "Đăng nhập");
        homeHeaderContent.setVisibility(home ? View.VISIBLE : View.GONE);
        subpageHeader.setVisibility(home ? View.GONE : View.VISIBLE);
        subpageTitle.setText(pageTitle());
        if (!vm.search.equals(searchInput.getText().toString())) searchInput.setText(vm.search);
        unreadDot.setVisibility(unread() > 0 ? View.VISIBLE : View.GONE);
    }

    private String pageTitle() {
        switch (vm.page) {
            case "account":
                return "";
            case "edit":
                return "";
            case "settings":
                return "";
            case "about":
                return "";
            default:
                return "";
        }
    }

    private void buildNavigation() {
        String[] labels = {
                "Khám phá",
                "Request",
                "Lịch",
                "Tin nhắn",
                vm.signedIn() ? "Tài khoản" : "Đăng nhập"
        };

        int[] tabIds = {R.id.tabExplore, R.id.tabRequest, R.id.tabSchedule, R.id.tabChat, R.id.tabAccount};
        int[] dotIds = {R.id.dotExplore, R.id.dotRequest, R.id.dotSchedule, R.id.dotChat, R.id.dotAccount};
        int[] iconIds = {R.drawable.ic_search, R.drawable.ic_exchange, R.drawable.ic_trip,
                R.drawable.ic_message, R.drawable.ic_profile};

        for (int i = 0; i < labels.length; i++) {

            final int index = i;

            boolean selected =
                    (index == 0 && "home".equals(vm.page))
                            || (index == 4
                            && !Arrays.asList(
                            "home",
                            "notifications"
                    ).contains(vm.page));

            LinearLayout tab = activity.findViewById(tabIds[index]);
            ImageView icon = (ImageView) tab.getChildAt(0);
            TextView label = (TextView) tab.getChildAt(1);
            View dot = activity.findViewById(dotIds[index]);
            icon.setImageResource(iconIds[index]);
            icon.setColorFilter(selected ? PURPLE : INK);
            label.setText(labels[index]);
            label.setTextColor(selected ? PURPLE : INK);
            dot.setBackgroundResource(selected ? R.drawable.bg_schedule_tab_selected
                    : R.drawable.bg_schedule_tab_unselected);

            tab.setContentDescription(labels[index]);

            tab.setOnClickListener(v -> {

                if (vm.busy) {
                    return;
                }

                switch (index) {

                    // KHÁM PHÁ
                    case 0:
                        go("home");
                        break;

                    // REQUEST
                    case 1:

                        if (!requireLogin()) {
                            return;
                        }

                        startActivity(
                                new Intent(
                                        activity,
                                        Request.class
                                )
                        );

                        break;

                    // LỊCH TRAO ĐỔI
                    case 2:

                        if (!requireLogin()) {
                            return;
                        }

                        startActivity(
                                new Intent(
                                        activity,
                                        ExchangeScheduleActivity.class
                                )
                        );

                        break;


                    // TIN NHẮN
                    case 3:

                        if (!requireLogin()) {
                            return;
                        }

                        startActivity(
                                new Intent(
                                        activity,
                                        Chat.class
                                )
                        );

                        break;

                    // TÀI KHOẢN / ĐĂNG NHẬP
                    case 4:

                        if (!requireLogin()) {
                            return;
                        }

                        go("account");

                        break;
                }
            });
        }
    }

    private void buildHomeFilters() {
        boolean show = "home".equals(vm.page) && vm.online();
        filters.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) return;
        String[] labels = {"Tất cả", "Còn trống", "Yêu thích"};
        int[] ids = {R.id.btnFilterAll, R.id.btnFilterAvailable, R.id.btnFilterFavorites};
        for (int i = 0; i < labels.length; i++) {
            final int value = i;
            TextView chip = activity.findViewById(ids[i]);
            chip.setText(labels[i]);
            chip.setSelected(i == selectedFilter);
            chip.setTextColor(i == selectedFilter ? Color.WHITE : INK);
            chip.setBackgroundResource(i == selectedFilter ? R.drawable.bg_chip_selected_dark
                    : R.drawable.bg_chip_unselected);
            chip.setOnClickListener(
                    v -> {
                        if (value == 2 && !requireLogin()) return;
                        selectedFilter = value;
                        cards.trackOffsets.remove("feed0");
                        cards.trackOffsets.remove("feed1");
                        builtPage = "";
                        render();
                    });
        }
    }

    private void home() {
        homeSections = content;
        renderHomeSections();
    }

    private void renderHomeSections() {
        if (homeSections == null) return;
        homeSections.removeAllViews();
        if (selectedFilter == 2 && !vm.signedIn()) {
            empty(
                    homeSections,
                    "Đăng nhập để xem yêu thích",
                    "Các chỗ ở đã lưu gắn với tài khoản của bạn.");
            return;
        }
        if (selectedFilter == 2 && !vm.favoritesLoaded()) {
            empty(
                    homeSections,
                    "Chưa tải xong yêu thích",
                    "Danh sách sẽ cập nhật khi đồng bộ thành công. Nếu có lỗi, hãy kiểm tra kết nối"
                            + " và mở lại ứng dụng.");
            return;
        }
        List<JSONObject> posts = new ArrayList<>();
        Set<String> favoriteIds = vm.favoriteIds();
        String query = vm.search.trim().toLowerCase(Locale.ROOT);
        JSONArray source = vm.rows("feed");
        long now = System.currentTimeMillis();
        for (int i = 0; i < source.length(); i++) {
            JSONObject p = source.optJSONObject(i);
            if (p == null) continue;
            String searchable =
                    (cards.postTitle(p)
                            + " "
                            + p.optString("title")
                            + " "
                            + p.optString("addressText"))
                            .toLowerCase(Locale.ROOT);
            if (!searchable.contains(query)) continue;
            if (!PostCardData.matchesFilter(p, selectedFilter, favoriteIds, now)) continue;
            posts.add(p);
        }
        if (posts.isEmpty()) {
            String title = "Chưa tìm thấy chỗ ở";
            String note =
                    query.isEmpty()
                            ? "Các bài đăng đã duyệt sẽ xuất hiện tại đây."
                            : "Thử tìm theo tên chỗ ở hoặc địa điểm khác.";
            if (selectedFilter == 1) {
                title = "Chưa có chỗ ở còn trống";
                note = "Chỉ hiển thị bài đăng có khoảng thời gian cho phép bao gồm hôm nay.";
            } else if (selectedFilter == 2) {
                title = "Chưa có chỗ ở yêu thích";
                note = "Chạm vào trái tim trên ảnh để chọn chỗ ở bạn thích.";
            }
            empty(homeSections, title, note);
            return;
        }
        int split = Math.min(3, posts.size());
        String firstTitle = query.isEmpty() ? "Nơi lưu trú được ưa chuộng" : "Kết quả tìm kiếm";
        if (wide() && posts.size() > split) {
            LinearLayout columns = template(R.id.homeColumns);
            LinearLayout left = columns.findViewById(R.id.homeColumnLeft);
            LinearLayout right = columns.findViewById(R.id.homeColumnRight);
            homeSections.addView(columns);
            cards.section(left, firstTitle, posts.subList(0, split), "feed0", false);
            cards.section(
                    right,
                    "Gợi ý cho chuyến đi tiếp của bạn",
                    posts.subList(split, posts.size()),
                    "feed1",
                    false);
        } else {
            cards.section(homeSections, firstTitle, posts.subList(0, split), "feed0", false);
            if (posts.size() > split)
                cards.section(
                        homeSections,
                        "Gợi ý cho chuyến đi tiếp của bạn",
                        posts.subList(split, posts.size()),
                        "feed1",
                        false);
        }
    }

    private void openNewPost() {
        if (vm.busy || !requireLogin()) return;
        if (!vm.online()) {
            vm.tell("Kết nối mạng để tạo bài đăng mới.");
            return;
        }
        hideKeyboard();
        try {
            activity.startActivity(new Intent(activity, New_Post.class));
        } catch (ActivityNotFoundException error) {
            vm.tell("Chưa mở được màn hình tạo bài đăng.");
        }
    }

}
