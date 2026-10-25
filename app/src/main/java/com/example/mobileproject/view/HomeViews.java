package com.example.mobileproject.view;

import android.content.ContextWrapper;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import android.app.Activity;
import android.graphics.Color;
import com.example.mobileproject.MainActivity;
import com.example.mobileproject.R;
import com.example.mobileproject.viewmodel.HomeViewModel;

import java.util.function.Consumer;
import org.json.JSONArray;
import org.json.JSONObject;

public class HomeViews extends ContextWrapper {
    protected final Activity hostActivity;
    protected final MainActivity activity;
    protected final HomeViewModel vm;
    protected static final int INK = Color.rgb(31, 41, 55);
    protected static final int MUTED = Color.rgb(107, 114, 128);
    protected static final int PURPLE = Color.rgb(91, 76, 219);
    protected static final int RED = Color.rgb(185, 28, 28);
    public interface AccountHost {
        void openAccountPage(String page);

        boolean requireAccountLogin();

        void chooseAccountAvatar();

        void confirmAccountDeletion();
    }

    protected final AccountHost accountHost;

    public HomeViews(MainActivity activity, HomeViewModel vm) {
        super(activity);

        this.hostActivity = activity;
        this.activity = activity;
        this.vm = vm;
        this.accountHost = null;
    }

    public HomeViews(
            Activity activity,
            HomeViewModel vm,
            AccountHost accountHost
    ) {
        super(activity);

        this.hostActivity = activity;
        this.activity = activity instanceof MainActivity
                ? (MainActivity) activity : null;
        this.vm = vm;
        this.accountHost = accountHost;
    }

    protected void go(String page) {
        if (accountHost != null) {
            accountHost.openAccountPage(page);
        } else if (activity != null) {
            activity.openPage(page);
        }
    }

    protected boolean requireLogin() {
        if (accountHost != null) {
            return accountHost.requireAccountLogin();
        }
        if (activity != null) {
            return activity.requireLogin();
        }
        return false;
    }
    protected LinearLayout menuRow(
        String icon, String title, String subtitle, boolean danger, Runnable action) {
        LinearLayout wrapper = template(R.id.homeMenuRow);
        ImageView leading = wrapper.findViewById(R.id.menuLeadingGlyph);
        ImageView trailing = wrapper.findViewById(R.id.menuTrailingGlyph);
        int tint = danger ? RED : INK;
        leading.setImageResource(iconResource(icon));
        leading.setImageTintList(ColorStateList.valueOf(tint));
        trailing.setImageTintList(ColorStateList.valueOf(tint));
        TextView name = wrapper.findViewById(R.id.menuTitle);
        name.setText(title);
        TextView detail = wrapper.findViewById(R.id.menuSubtitle);
        detail.setText(subtitle);
        detail.setVisibility(subtitle.isEmpty() ? View.GONE : View.VISIBLE);
        if (danger) name.setTextColor(RED);
        wrapper.findViewById(R.id.menuRowContent).setOnClickListener(v -> {
            if (!vm.busy) action.run();
        });
        if (!danger) divider(wrapper);
        return wrapper;
    }

    protected void empty(LinearLayout parent, String title, String description) {
        LinearLayout box = template(R.id.homeEmptyState);
        ImageView glyph = box.findViewById(R.id.emptyGlyph);
        glyph.setImageTintList(ColorStateList.valueOf(PURPLE));
        ((TextView) box.findViewById(R.id.emptyTitle)).setText(title);
        ((TextView) box.findViewById(R.id.emptyDescription)).setText(description);
        parent.addView(box);
    }

    protected TextView primary(String title, Runnable action) {
        TextView button = template(R.id.homePrimary);
        button.setText(title);
        button.setOnClickListener(v -> {
            if (!vm.busy) action.run();
        });
        return button;
    }

    protected TextView heading(String text, int size) {
        TextView view = template(R.id.homeHeading);
        view.setText(text);
        view.setTextSize(size);
        return view;
    }

    protected TextView body(String text, int size, int color) {
        TextView view = template(R.id.homeBody);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    protected TextView label(String text) {
        TextView label = template(R.id.homeLabel);
        label.setText(text);
        return label;
    }

    protected FrameLayout iconButton(String name, String description, Runnable action) {
        FrameLayout button = template(R.id.homeIconButton);
        button.setContentDescription(description);
        ImageView icon = button.findViewById(R.id.iconGlyph);
        icon.setImageResource(iconResource(name));
        icon.setImageTintList(ColorStateList.valueOf(INK));
        button.setOnClickListener(v -> {
            if (!vm.busy) action.run();
        });
        return button;
    }

    protected LinearLayout vertical() {
        LinearLayout v = template(R.id.homeViewsContainer);
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        return v;
    }

    @SuppressWarnings("unchecked")
    protected <T extends View> T inflate(int layout, ViewGroup parent) {
        return (T) android.view.LayoutInflater.from(this).inflate(layout, parent, false);
    }

    @SuppressWarnings("unchecked")
    protected <T extends View> T template(int id) {
        LinearLayout bank = inflate(R.layout.home_views, null);
        View component = bank.findViewById(id);
        if (component == null) throw new IllegalArgumentException("Missing home view template: " + id);
        ((ViewGroup) component.getParent()).removeView(component);
        return (T) component;
    }

    protected int iconResource(String name) {
        switch (name) {
            case "user": return R.drawable.ic_profile;
            case "info": return R.drawable.ic_information;
            case "trash": return R.drawable.ic_delete;
            case "bell": return R.drawable.ic_notification;
            case "chevron": return R.drawable.ic_chevron_right;
            case "next": return R.drawable.ic_arrow_next;
            case "search": return R.drawable.ic_search;
            case "exchange": return R.drawable.ic_exchange;
            case "calendar": return R.drawable.ic_calendar;
            case "chat": return R.drawable.ic_message;
            case "userCircle": return R.drawable.ic_account;
            default: throw new IllegalArgumentException("Unknown icon: " + name);
        }
    }

    protected LinearLayout horizontal() {
        return template(R.id.homeHorizontal);
    }

    protected LinearLayout padded(int padding) {
        LinearLayout box = vertical();
        box.setPadding(dp(padding), 0, dp(padding), 0);
        return box;
    }

    protected LinearLayout.LayoutParams lp(int width, int height) {
        return new LinearLayout.LayoutParams(width < 0 ? width : dp(width), height < 0 ? height : dp(height));
    }

    protected void gap(LinearLayout parent, int height) {
        parent.addView(template(R.id.homeSpacer), lp(1, height));
    }

    protected void divider(LinearLayout parent) {
        parent.addView(template(R.id.homeDivider));
    }

    protected int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    protected boolean wide() {
        Configuration c = getResources().getConfiguration();
        return c.orientation == Configuration.ORIENTATION_LANDSCAPE || c.screenWidthDp >= 600;
    }

    protected GradientDrawable round(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    protected void enable(View view, boolean enabled) {
        view.setEnabled(enabled);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) enable(group.getChildAt(i), enabled);
        }
    }

    protected void change(EditText field, Consumer<String> callback) {
        field.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                callback.accept(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    protected int unread() {
        int count = 0;
        JSONArray a = vm.rows("notices");
        for (int i = 0; i < a.length(); i++) {
            JSONObject n = a.optJSONObject(i);
            if (n != null && !n.optBoolean("read")) count++;
        }
        return count;
    }
}
