package com.example.mobileproject.ui;

import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import com.example.mobileproject.MainActivity;
import com.example.mobileproject.R;
import com.example.mobileproject.view.Request;
import com.example.mobileproject.view.ExchangeScheduleActivity;
import com.example.mobileproject.view.Chat;
import com.example.mobileproject.viewmodel.HomeViewModel;
import com.google.firebase.auth.*;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

public class HomeUi extends HomeViews {
    private final FrameLayout header, filters;
    private final LinearLayout content, navigation;
    private final ScrollView scroll;
    private final ProgressBar progress;
    private final PostCards cards;
    private final AccountUi accountUi;
    private LinearLayout homeSections;
    private String builtPage = "", headerPage = "", lastUser = "";
    private int selectedFilter;

    public HomeUi(MainActivity activity, HomeViewModel vm, Bundle saved) {
        super(activity, vm);
        header = activity.findViewById(R.id.homeHeader);
        filters = activity.findViewById(R.id.homeFilters);
        content = activity.findViewById(R.id.homeContent);
        navigation = activity.findViewById(R.id.homeNavigation);
        scroll = activity.findViewById(R.id.homeScroll);
        progress = activity.findViewById(R.id.homeProgress);
        cards = new PostCards(activity, vm);
        cards.onFavoritesChanged = this::renderHomeSections;
        accountUi = new AccountUi(activity, vm, content, cards);
        restoreState(saved);
        scroll.setOnScrollChangeListener(
                (View.OnScrollChangeListener) (v, x, y, oldX, oldY) -> vm.scrollY = y);
    }

    private void restoreState(Bundle saved) {
        if (saved == null) return;
        selectedFilter = saved.getInt("filter", 0);
        lastUser = saved.getString("lastUser", "");
        ArrayList<String> liked = saved.getStringArrayList("hearts");
        if (liked != null) cards.hearts.addAll(liked);
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
        out.putString("lastUser", lastUser);
        out.putStringArrayList("hearts", new ArrayList<>(cards.hearts));
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
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String identity = user == null ? "guest" : user.getUid();
        if (!identity.equals(lastUser)) {
            cards.hearts.clear();
            lastUser = identity;
        }
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
        if (vm.page.equals(headerPage)) {
            View badge = header.findViewWithTag("unread");
            if (badge != null) badge.setVisibility(unread() > 0 ? View.VISIBLE : View.GONE);
            return;
        }
        headerPage = vm.page;
        header.removeAllViews();
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(13), dp(4), dp(13), dp(4));
        header.addView(row, new FrameLayout.LayoutParams(-1, -1));
        if ("home".equals(vm.page)) {
            row.addView(iconButton("menu", "Mở menu", this::showMenu), lp(42, 46));
            LinearLayout search = horizontal();
            search.setGravity(Gravity.CENTER_VERTICAL);
            search.addView(new GlyphView(this, "search", INK), lp(26, 26));
            EditText input = new EditText(this);
            input.setSingleLine(true);
            input.setTextSize(18);
            input.setTypeface(serif);
            input.setTextColor(INK);
            input.setHintTextColor(INK);
            input.setBackgroundColor(Color.TRANSPARENT);
            input.setHint("Bắt đầu tìm kiếm");
            input.setPadding(dp(10), 0, 0, 0);
            input.setText(vm.search);
            input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
            input.setOnEditorActionListener(
                    (v, action, event) -> {
                        hideKeyboard();
                        return true;
                    });
            search.addView(input, new LinearLayout.LayoutParams(0, -1, 1));
            row.addView(search, new LinearLayout.LayoutParams(0, dp(48), 1));
            change(
                    input,
                    value -> {
                        vm.search = value;
                        if (homeSections != null) renderHomeSections();
                    });
            FrameLayout bell = new FrameLayout(this);
            bell.addView(
                    iconButton(
                            "bell",
                            "Thông báo",
                            () -> {
                                if (requireLogin()) go("notifications");
                            }),
                    new FrameLayout.LayoutParams(-1, -1));
            View dot = new View(this);
            dot.setTag("unread");
            dot.setBackground(round(PURPLE, 8));
            FrameLayout.LayoutParams dotParams =
                    new FrameLayout.LayoutParams(dp(7), dp(7), Gravity.TOP | Gravity.END);
            dotParams.setMargins(0, dp(10), dp(7), 0);
            bell.addView(dot, dotParams);
            dot.setVisibility(unread() > 0 ? View.VISIBLE : View.GONE);
            row.addView(bell, lp(42, 46));
        } else {
            FrameLayout titleFrame = new FrameLayout(this);
            row.addView(titleFrame, new LinearLayout.LayoutParams(-1, -1));
            TextView title = body(pageTitle(), 24, INK);
            title.setGravity(Gravity.CENTER);
            FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(-1, -1);
            titleParams.setMargins(dp(42), 0, dp(42), 0);
            titleFrame.addView(title, titleParams);
            FrameLayout back =
                    iconButton(
                            "back",
                            "Quay lại",
                            () ->
                                    go(
                                            Arrays.asList("edit", "settings", "about")
                                                    .contains(vm.page)
                                                    ? "account"
                                                    : "home"));
            back.setBackground(round(0xFFE9E9E6, 40));
            FrameLayout.LayoutParams backParams =
                    new FrameLayout.LayoutParams(
                            dp(34), dp(34), Gravity.START | Gravity.CENTER_VERTICAL);
            titleFrame.addView(back, backParams);
        }
    }

    private String pageTitle() {
        switch (vm.page) {
            case "account":
                return "Tài khoản";
            case "edit":
                return "Chỉnh sửa thông tin";
            case "settings":
                return "Cài đặt";
            case "about":
                return "Thông tin ứng dụng";
            default:
                return "Thông báo";
        }
    }

    private void buildNavigation() {
        navigation.removeAllViews();

        String[] labels = {
                "Khám phá",
                "Request",
                "Lịch",
                "Tin nhắn",
                vm.signedIn() ? "Tài khoản" : "Đăng nhập"
        };

        String[] icons = {
                "search",
                "exchange",
                "calendar",
                "chat",
                "userCircle"
        };

        for (int i = 0; i < labels.length; i++) {

            final int index = i;

            boolean selected =
                    (index == 0 && "home".equals(vm.page))
                            || (index == 4
                            && !Arrays.asList(
                            "home",
                            "notifications"
                    ).contains(vm.page));

            LinearLayout tab = vertical();

            tab.setGravity(Gravity.CENTER);
            tab.setMinimumHeight(dp(49));

            // Icon
            tab.addView(
                    new GlyphView(
                            this,
                            icons[index],
                            selected ? PURPLE : INK
                    ),
                    lp(25, 25)
            );

            // Label
            TextView label =
                    body(
                            labels[index],
                            10,
                            selected ? PURPLE : INK
                    );

            label.setGravity(Gravity.CENTER);

            LinearLayout.LayoutParams labelParams =
                    lp(-1, -2);

            labelParams.topMargin = dp(5);

            tab.addView(label, labelParams);

            // Chấm báo tab đang được chọn
            View dot = new View(this);

            dot.setBackground(
                    round(
                            selected
                                    ? PURPLE
                                    : Color.TRANSPARENT,
                            4
                    )
            );

            LinearLayout.LayoutParams dotParams =
                    lp(4, 4);

            dotParams.topMargin = dp(5);

            tab.addView(dot, dotParams);

            navigation.addView(
                    tab,
                    new LinearLayout.LayoutParams(
                            0,
                            -2,
                            1
                    )
            );

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
        filters.removeAllViews();
        boolean show = "home".equals(vm.page) && vm.online();
        filters.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) return;
        LinearLayout chips = horizontal();
        chips.setPadding(dp(16), dp(4), dp(16), dp(16));
        String[] labels = {"Tất cả", "Còn trống", "Được\nyêu thích"};
        for (int i = 0; i < labels.length; i++) {
            final int value = i;
            TextView chip = heading(labels[i], 15);
            chip.setGravity(Gravity.CENTER);
            chip.setMinHeight(dp(49));
            chip.setPadding(dp(8), dp(10), dp(8), dp(10));
            chip.setSelected(i == selectedFilter);
            chip.setTextColor(i == selectedFilter ? Color.WHITE : INK);
            chip.setBackground(i == selectedFilter ? chipBackground() : round(Color.WHITE, 30));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
            params.setMargins(dp(3), 0, dp(3), 0);
            chips.addView(chip, params);
            chip.setOnClickListener(
                    v -> {
                        selectedFilter = value;
                        cards.trackOffsets.remove("feed0");
                        cards.trackOffsets.remove("feed1");
                        builtPage = "";
                        render();
                    });
        }
        filters.addView(chips);
    }

    private void home() {
        homeSections = vertical();
        content.addView(homeSections);
        renderHomeSections();
    }

    private void renderHomeSections() {
        if (homeSections == null) return;
        homeSections.removeAllViews();
        List<JSONObject> posts = new ArrayList<>();
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
            if (!PostCardData.matchesFilter(p, selectedFilter, cards.hearts, now)) continue;
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
            LinearLayout columns = horizontal(), left = vertical(), right = vertical();
            columns.addView(left, new LinearLayout.LayoutParams(0, -2, 1));
            columns.addView(right, new LinearLayout.LayoutParams(0, -2, 1));
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

    private void showMenu() {
        if (vm.busy) return;
        Dialog drawer = new Dialog(this);
        drawer.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout menu = padded(24);
        menu.setBackgroundColor(Color.WHITE);
        gap(menu, 28);
        menu.addView(heading("StayExchange", 29));
        gap(menu, 22);
        String[] labels = {"Khám phá", "Tài khoản", "Thông báo", "Cài đặt", "Thông tin ứng dụng"};
        String[] icons = {"search", "user", "bell", "settings", "info"};
        String[] pages = {"home", "account", "notifications", "settings", "about"};
        for (int i = 0; i < labels.length; i++) {
            final String page = pages[i];
            menu.addView(
                    menuRow(
                            icons[i],
                            labels[i],
                            "",
                            false,
                            () -> {
                                drawer.dismiss();
                                if (Arrays.asList("home", "about").contains(page) || requireLogin())
                                    go(page);
                            }));
        }
        menu.addView(
                menuRow(
                        "logout",
                        vm.signedIn() ? "Đăng xuất" : "Đăng nhập",
                        "",
                        true,
                        () -> {
                            drawer.dismiss();
                            if (vm.signedIn()) accountUi.confirmLogout();
                            else requireLogin();
                        }));
        drawer.setContentView(menu);
        Window window = drawer.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setLayout(
                    Math.min(dp(320), getResources().getDisplayMetrics().widthPixels - dp(35)), -1);
            window.setGravity(Gravity.START);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(.3f);
        }
        drawer.show();
    }
}
