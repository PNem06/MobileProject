package com.example.mobileproject.view;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.util.Base64;
import android.view.View;
import android.widget.*;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.example.mobileproject.MainActivity;
import com.example.mobileproject.R;
import com.example.mobileproject.viewmodel.HomeViewModel;
import java.text.DateFormat;
import java.util.*;
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

    public AccountUi(Activity activity, HomeViewModel vm, LinearLayout content, AccountHost accountHost) {
        this(activity, vm, content, null, null, accountHost);
    }

    public AccountUi(Activity activity, HomeViewModel vm, LinearLayout content, PostCards cards,
                     Runnable createPost, AccountHost accountHost) {
        super(activity, vm, accountHost);
        this.content = content;
        this.cards = cards;
        this.createPost = createPost;
    }

    void account() {
        if (!vm.signedIn()) {
            LinearLayout page = template(R.id.accountSignedOutTemplate);
            page.findViewById(R.id.signedOutLogin).setOnClickListener(v -> requireLogin());
            content.addView(page);
            return;
        }
        JSONObject profile = vm.profile();
        String name = profile.optString("fullName");
        if (name.isEmpty()) name = "Thành viên StayExchange";
        View root = inflate(R.layout.account_ui, null);
        content.addView(root);
        TextView txtAccountName = root.findViewById(R.id.txtAccountName);
        TextView txtAccountEmail = root.findViewById(R.id.txtAccountEmail);
        TextView txtAvatarInitials = root.findViewById(R.id.txtAvatarInitials);
        txtAccountName.setText(name);
        txtAccountEmail.setText(profile.optString("email"));
        txtAvatarInitials.setText(PostCardData.initials(name));
        FrameLayout avatarFrame = root.findViewById(R.id.avatarFrame);
        avatarFrame.setClipToOutline(true);
        ImageView avatarPhoto = root.findViewById(R.id.imgAvatar);
        String avatarUrl = profile.optString("avatarUrl");
        if (!avatarUrl.isEmpty()) Glide.with(this).load(avatarImageModel(avatarUrl)).circleCrop()
                .diskCacheStrategy(DiskCacheStrategy.ALL).into(avatarPhoto);
        root.findViewById(R.id.btnWallet).setOnClickListener(v -> activity.startActivity(new Intent(activity, WalletActivity.class)));
        root.findViewById(R.id.btnPersonalInfo).setOnClickListener(v -> {
            if (vm.busy) return;
            vm.draftName = vm.profile().optString("fullName");
            vm.draftPhone = vm.profile().optString("phone");
            vm.draftAvatar = null;
            go("edit");
        });
        root.findViewById(R.id.btnAppInfo).setOnClickListener(v -> {
            if (!vm.busy) go("about");
        });
        root.findViewById(R.id.btnSettings).setOnClickListener(v -> {
            if (!vm.busy) go("settings");
        });
        root.findViewById(R.id.btnLogout).setOnClickListener(v -> {
            if (!vm.busy) confirmLogout();
        });
        List<JSONObject> mine = new ArrayList<>();
        JSONArray array = vm.rows("mine");
        for (int i = 0; i < array.length(); i++) {
            JSONObject p = array.optJSONObject(i);
            if (p != null) mine.add(p);
        }
        LinearLayout myPosts = root.findViewById(R.id.myPostsContainer);
        if (cards != null && myPosts != null) {
            cards.section(myPosts, "Bài đăng của tôi", mine, "mine", true, createPost);
        } else if (myPosts != null) {
            myPosts.setVisibility(View.GONE);
        }
    }

    void edit() {
        if (vm.draftName == null) vm.draftName = vm.profile().optString("fullName");
        if (vm.draftPhone == null) vm.draftPhone = vm.profile().optString("phone");
        LinearLayout form = template(R.id.accountEditTemplate);
        content.addView(form);
        String source = vm.draftAvatar == null ? vm.profile().optString("avatarUrl") : vm.draftAvatar;
        TextView txtAvatarLetters = form.findViewById(R.id.editAvatarLetters);
        txtAvatarLetters.setText(PostCardData.initials(vm.draftName));
        ImageView photo = form.findViewById(R.id.editAvatarPhoto);
        if (!source.isEmpty()) Glide.with(this).load(avatarImageModel(source)).circleCrop()
                .diskCacheStrategy(DiskCacheStrategy.ALL).into(photo);
        form.findViewById(R.id.editChooseAvatar).setOnClickListener(v -> {
            if (!vm.busy) {
                if (accountHost != null) {
                    accountHost.chooseAccountAvatar();
                } else if (activity != null) {
                    activity.chooseAvatar();
                }
            }
        });
        EditText txtFullName = form.findViewById(R.id.editName);
        EditText txtEmail = form.findViewById(R.id.editEmail);
        EditText txtPhone = form.findViewById(R.id.editPhone);
        txtFullName.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        txtFullName.setText(vm.draftName);
        txtEmail.setText(vm.profile().optString("email"));
        txtPhone.setText(vm.draftPhone);
        change(txtFullName, value -> vm.draftName = value);
        change(txtPhone, value -> vm.draftPhone = value);
        form.findViewById(R.id.editCancel).setOnClickListener(v -> {
            vm.draftAvatar = null;
            go("account");
        });
        form.findViewById(R.id.editSave).setOnClickListener(v -> {
            if (!vm.busy) vm.saveProfile();
        });
        enable(content, !vm.busy);
    }

    void settings() {
        LinearLayout box = template(R.id.accountSettingsTemplate);
        content.addView(box);
        LinearLayout personal = box.findViewById(R.id.settingsPersonal);
        personal.addView(menuRow("user", "Thông tin cá nhân", "Cập nhật họ tên, ảnh và số điện thoại", false, () -> {
            vm.draftName = vm.profile().optString("fullName");
            vm.draftPhone = vm.profile().optString("phone");
            vm.draftAvatar = null;
            go("edit");
        }));
        ((LinearLayout) box.findViewById(R.id.settingsPrivacy)).addView(
                menuRow("info", "Chính sách dữ liệu", "Tìm hiểu dữ liệu ứng dụng sử dụng", false, this::privacy));
        ((LinearLayout) box.findViewById(R.id.settingsDelete)).addView(menuRow("trash", "Xóa tài khoản", "Thao tác này không thể hoàn tác", true,
                ()
                        -> new AlertDialog.Builder(this)
                        .setTitle("Xóa tài khoản?")
                        .setMessage("Bạn sẽ mất quyền đăng nhập bằng tài khoản này. Hãy"
                                + " xác minh lại danh tính để tiếp tục.")
                        .setNegativeButton("Hủy", null)
                        .setPositiveButton("Tiếp tục", (d, w) -> {
                            if (accountHost != null) {
                                accountHost.confirmAccountDeletion();
                            } else if (activity != null) {
                                activity.confirmAccountDeletion();
                            }
                        })
                        .show()));
    }

    private Object avatarImageModel(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        String image = value.trim();
        if (image.startsWith("http://") || image.startsWith("https://")) return image;
        if (image.startsWith("content://") || image.startsWith("file://")) return Uri.parse(image);
        if (image.startsWith("data:image/")) {
            int comma = image.indexOf(',');
            if (comma < 0) return null;
            image = image.substring(comma + 1);
        }
        try {
            return Base64.decode(image, Base64.DEFAULT);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
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
        LinearLayout page = template(R.id.accountNotificationsTemplate);
        TextView txtUnreadCount = page.findViewById(R.id.notificationUnreadCount);
        txtUnreadCount.setText(unread() + " chưa đọc");
        LinearLayout rows = page.findViewById(R.id.notificationRows);
        content.addView(page);
        JSONArray items = vm.rows("notices");
        if (items.length() == 0) {
            empty(rows, "Bạn đã xem hết rồi", "Những thông báo mới sẽ xuất hiện tại đây.");
            return;
        }
        for (int i = 0; i < items.length(); i++) {
            JSONObject notice = items.optJSONObject(i);
            if (notice == null) continue;
            com.example.mobileproject.notifications.Notice message = com.example.mobileproject.notifications.Notice.fromJson(notice);
            LinearLayout row = template(R.id.notificationRowTemplate);
            row.setBackgroundColor(message.read() ? Color.WHITE : 0xFFF8F7FE);
            ImageView notificationGlyph = row.findViewById(R.id.notificationGlyph);
            notificationGlyph.setImageTintList(android.content.res.ColorStateList.valueOf(INK));
            TextView txtNotificationTitle = row.findViewById(R.id.notificationTitle);
            TextView txtNotificationBody = row.findViewById(R.id.notificationBody);
            TextView txtNotificationDate = row.findViewById(R.id.notificationDate);
            txtNotificationTitle.setText(message.title());
            txtNotificationBody.setText(message.body());
            if (notice.optLong("createdAt") > 0) {
                txtNotificationDate.setText(DateFormat.getDateTimeInstance().format(new Date(notice.optLong("createdAt"))));
                txtNotificationDate.setVisibility(View.VISIBLE);
            }
            row.setOnClickListener(v -> {
                Activity target = hostActivity != null ? hostActivity : activity;
                if (target != null) com.example.mobileproject.notifications.NotificationNavigator.open(
                        target, message);
            });

            rows.addView(row);
            divider(rows);
        }
    }

    void about() {
        LinearLayout box = template(R.id.accountAboutTemplate);
        ((LinearLayout) box.findViewById(R.id.aboutPrivacyRow)).addView(
                menuRow("info", "Thông tin dữ liệu", "Tìm hiểu về ứng dụng", false, this::privacy));
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
