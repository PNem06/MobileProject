package com.example.mobileproject.ui;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.InputType;
import android.view.Gravity;
import android.widget.*;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.example.mobileproject.MainActivity;
import com.example.mobileproject.R;
import com.example.mobileproject.viewmodel.HomeViewModel;
import java.text.DateFormat;
import java.util.*;
import java.util.function.Consumer;
import org.json.JSONArray;
import org.json.JSONObject;

public class AccountUi extends HomeViews {
    private final LinearLayout content;
    private final PostCards cards;
    private final Runnable createPost;

    public AccountUi(MainActivity activity, HomeViewModel vm, LinearLayout content, PostCards cards,
                     Runnable createPost) {
        super(activity, vm);
        this.content = content;
        this.cards = cards;
        this.createPost = createPost;
    }

    void account() {
        if (!vm.signedIn()) {
            LinearLayout box = padded(24);
            gap(box, 25);
            box.addView(heading("Chào mừng đến StayExchange", 27));
            gap(box, 14);
            box.addView(body("Đăng nhập để quản lý hồ sơ và những bài đăng của bạn.", 14, MUTED));
            gap(box, 24);
            box.addView(primary("Đăng nhập", () -> requireLogin()));
            content.addView(box);
            return;
        }
        JSONObject profile = vm.profile();
        String name = profile.optString("fullName");
        if (name.isEmpty()) name = "Thành viên StayExchange";
        LinearLayout profileRow = horizontal();
        profileRow.setGravity(Gravity.CENTER_VERTICAL);
        profileRow.setPadding(dp(22), dp(8), dp(22), dp(22));
        profileRow.addView(avatar(profile.optString("avatarUrl"), name, 70), lp(70, 70));
        LinearLayout identity = vertical();
        LinearLayout.LayoutParams identityParams = new LinearLayout.LayoutParams(0, -2, 1);
        identityParams.leftMargin = dp(16);
        profileRow.addView(identity, identityParams);
        identity.addView(heading(name, 22));
        gap(identity, 7);
        identity.addView(body(profile.optString("email"), 11, MUTED));
        content.addView(profileRow);
        LinearLayout columns = wide() ? horizontal() : vertical();
        LinearLayout left = vertical(), right = padded(22);
        if (wide()) {
            columns.addView(left, new LinearLayout.LayoutParams(0, -2, 1.35f));
            columns.addView(right, new LinearLayout.LayoutParams(0, -2, 1));
        } else {
            columns.addView(left);
            columns.addView(right);
        }
        content.addView(columns);
        LinearLayout info = padded(22);
        info.setPadding(dp(22), 0, dp(22), dp(12));
        info.addView(menuRow("user", "Thông tin cá nhân", "Xem và chỉnh sửa thông tin của bạn", false, () -> {
            vm.draftName = vm.profile().optString("fullName");
            vm.draftPhone = vm.profile().optString("phone");
            vm.draftAvatar = null;
            go("edit");
        }));
        left.addView(info);
        List<JSONObject> mine = new ArrayList<>();
        JSONArray array = vm.rows("mine");
        for (int i = 0; i < array.length(); i++) {
            JSONObject p = array.optJSONObject(i);
            if (p != null) mine.add(p);
        }
        cards.section(left, "Bài đăng của tôi", mine, "mine", true, createPost);
        right.addView(label("TÙY CHỌN"));
        right.addView(
                menuRow("settings", "Cài đặt", "Thông báo, dữ liệu và tài khoản", false, () -> go("settings")));
        right.addView(menuRow("info", "Thông tin ứng dụng", "Về StayExchange", false, () -> go("about")));
        right.addView(menuRow("logout", "Đăng xuất", "", true, this::confirmLogout));
        gap(right, 16);
    }

    private FrameLayout avatar(String source, String name, int size) {
        FrameLayout avatar = new FrameLayout(this);
        avatar.setBackground(round(0xFFE9E6FC, size));
        String[] words = name.trim().split("\\s+");
        String initials = "SE";
        if (words.length > 0 && !words[0].isEmpty())
            initials = words.length == 1 ? words[0].substring(0, 1)
                    : words[Math.max(0, words.length - 2)].substring(0, 1)
                      + words[words.length - 1].substring(0, 1);
        TextView letters = heading(initials.toUpperCase(Locale.ROOT), size > 75 ? 31 : 26);
        letters.setGravity(Gravity.CENTER);
        letters.setTextColor(0xFF30268F);
        avatar.addView(letters, new FrameLayout.LayoutParams(-1, -1));
        if (source != null && !source.isEmpty()) {
            ImageView photo = new ImageView(this);
            avatar.addView(photo, new FrameLayout.LayoutParams(-1, -1));
            Glide.with(this).load(source).circleCrop().diskCacheStrategy(DiskCacheStrategy.ALL).into(photo);
        }
        return avatar;
    }

    void edit() {
        if (vm.draftName == null) vm.draftName = vm.profile().optString("fullName");
        if (vm.draftPhone == null) vm.draftPhone = vm.profile().optString("phone");
        LinearLayout form = padded(25);
        form.setPadding(dp(25), dp(6), dp(25), dp(24));
        content.addView(form);
        LinearLayout portrait = vertical();
        portrait.setGravity(Gravity.CENTER_HORIZONTAL);
        portrait.addView(avatar(vm.draftAvatar == null ? vm.profile().optString("avatarUrl") : vm.draftAvatar,
                        vm.draftName, 87),
                lp(87, 87));
        gap(portrait, 10);
        TextView choose = body("Thay đổi ảnh đại diện", 12, PURPLE);
        choose.setPaintFlags(choose.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        choose.setPadding(0, dp(5), 0, dp(5));
        choose.setOnClickListener(v -> {
            if (!vm.busy) activity.chooseAvatar();
        });
        portrait.addView(choose);
        form.addView(portrait);
        gap(form, 26);
        field(form, "Họ và tên", vm.draftName, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS,
                false, value -> vm.draftName = value);
        field(form, "Email", vm.profile().optString("email"), InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                true, null);
        field(form, "Số điện thoại", vm.draftPhone, InputType.TYPE_CLASS_PHONE, false,
                value -> vm.draftPhone = value);
        LinearLayout buttons = horizontal();
        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, dp(48), 1);
        a.rightMargin = dp(10);
        TextView cancel = primary("Hủy", () -> {
            vm.draftAvatar = null;
            go("account");
        });
        cancel.setBackground(round(0xFFF1F1F3, 10));
        cancel.setTextColor(INK);
        buttons.addView(cancel, a);
        buttons.addView(
                primary("Lưu thay đổi", vm::saveProfile), new LinearLayout.LayoutParams(0, dp(48), 1));
        gap(form, 8);
        form.addView(buttons);
        enable(content, !vm.busy);
    }

    private void field(LinearLayout form, String title, String value, int type, boolean readOnly,
                       Consumer<String> callback) {
        form.addView(heading(title, 16));
        gap(form, 8);
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextSize(14);
        input.setTypeface(sans);
        input.setTextColor(readOnly ? MUTED : INK);
        input.setInputType(type);
        input.setText(value);
        input.setPadding(dp(13), dp(10), dp(13), dp(10));
        input.setBackground(border(readOnly ? 0xFFF5F5F7 : Color.WHITE, 0xFFCDCDD3, 9));
        if (readOnly) {
            input.setKeyListener(null);
            input.setFocusable(false);
        } else if (callback != null)
            change(input, callback);
        form.addView(input, lp(-1, 48));
        gap(form, 19);
    }

    void settings() {
        LinearLayout box = padded(24);
        content.addView(box);
        box.addView(label("TÀI KHOẢN"));
        box.addView(
                menuRow("user", "Thông tin cá nhân", "Cập nhật họ tên, ảnh và số điện thoại", false, () -> {
                    vm.draftName = vm.profile().optString("fullName");
                    vm.draftPhone = vm.profile().optString("phone");
                    vm.draftAvatar = null;
                    go("edit");
                }));
        gap(box, 18);
        box.addView(label("DỮ LIỆU VÀ QUYỀN RIÊNG TƯ"));
        box.addView(
                menuRow("info", "Chính sách dữ liệu", "Tìm hiểu dữ liệu ứng dụng sử dụng", false, this::privacy));
        gap(box, 22);
        box.addView(label("QUẢN LÝ TÀI KHOẢN"));
        box.addView(menuRow("trash", "Xóa tài khoản", "Thao tác này không thể hoàn tác", true,
                ()
                        -> new AlertDialog.Builder(this)
                        .setTitle("Xóa tài khoản?")
                        .setMessage("Bạn sẽ mất quyền đăng nhập bằng tài khoản này. Hãy"
                                + " xác minh lại danh tính để tiếp tục.")
                        .setNegativeButton("Hủy", null)
                        .setPositiveButton("Tiếp tục", (d, w) -> activity.confirmAccountDeletion())
                        .show()));
    }

    private void privacy() {
        new AlertDialog.Builder(this)
                .setTitle("Thông tin dữ liệu của bản đồ án")
                .setMessage("Ứng dụng dùng hồ sơ để hiển thị tài khoản và bài đăng. Home và hồ sơ cần"
                        + " kết nối mạng để tải dữ liệu. Mật khẩu do Firebase Authentication"
                        + " quản lý.")
                .setPositiveButton("Đóng", null)
                .show();
    }

    void notifications() {
        LinearLayout top = horizontal();
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(22), dp(3), dp(22), dp(17));
        top.addView(body("Cập nhật dành cho bạn", 12, MUTED), new LinearLayout.LayoutParams(0, -2, 1));
        TextView count = body(unread() + " chưa đọc", 11, PURPLE);
        count.setPadding(dp(9), dp(5), dp(9), dp(5));
        count.setBackground(round(0xFFF0EDFF, 20));
        top.addView(count);
        content.addView(top);
        JSONArray items = vm.rows("notices");
        if (items.length() == 0) {
            empty(content, "Bạn đã xem hết rồi", "Những thông báo mới sẽ xuất hiện tại đây.");
            return;
        }
        for (int i = 0; i < items.length(); i++) {
            JSONObject notice = items.optJSONObject(i);
            if (notice == null) continue;
            LinearLayout row = horizontal();
            row.setGravity(Gravity.TOP);
            row.setPadding(dp(21), dp(18), dp(21), dp(18));
            row.setBackgroundColor(notice.optBoolean("read") ? Color.WHITE : 0xFFF8F7FE);
            FrameLayout icon = iconButton("bell", "Thông báo", () -> {});
            icon.setBackground(round(0xFFEDEBF9, 38));
            row.addView(icon, lp(38, 38));
            LinearLayout text = vertical();
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
            params.leftMargin = dp(12);
            row.addView(text, params);
            text.addView(heading(notice.optString("title"), 17));
            gap(text, 7);
            text.addView(body(notice.optString("body"), 12, MUTED));
            if (notice.optLong("createdAt") > 0) {
                gap(text, 8);
                text.addView(
                        body(DateFormat.getDateTimeInstance().format(new Date(notice.optLong("createdAt"))), 10,
                                MUTED));
            }
            row.setOnClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setTitle(notice.optString("title"))
                        .setMessage(notice.optString("body"))
                        .setPositiveButton("Đóng", null)
                        .show();
                if (!notice.optBoolean("read")) vm.markRead(notice);
            });
            content.addView(row);
            divider(content);
        }
    }

    void about() {
        LinearLayout box = padded(26);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        gap(box, 20);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo_stageexchange);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        box.addView(logo, lp(83, 83));
        gap(box, 18);
        box.addView(heading("StayExchange", 29));
        gap(box, 8);
        box.addView(body("Một mái nhà mới. Một hành trình mới.", 12, MUTED));
        gap(box, 27);
        TextView intro = heading("Trao đổi một mái nhà, mở ra một trải nghiệm. StayExchange kết nối những"
                        + " người muốn khám phá điểm đến và trải nghiệm cuộc sống địa phương.",
                18);
        intro.setLineSpacing(dp(5), 1);
        box.addView(intro, lp(-1, -2));
        gap(box, 25);
        box.addView(menuRow("info", "Thông tin dữ liệu", "Tìm hiểu về ứng dụng", false, this::privacy));
        gap(box, 30);
        TextView version = body("Phiên bản 1.0\nĐồ án môn Lập trình ứng dụng Mobile", 11, MUTED);
        version.setGravity(Gravity.CENTER);
        version.setLineSpacing(dp(5), 1);
        box.addView(version);
        content.addView(box);
    }

    void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle("Đăng xuất?")
                .setMessage("Bạn có thể đăng nhập lại để tiếp tục sử dụng tài khoản.")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Đăng xuất", (d, w) -> vm.logout())
                .show();
    }
}
