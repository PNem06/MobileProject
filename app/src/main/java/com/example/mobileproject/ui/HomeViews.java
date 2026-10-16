package com.example.mobileproject.ui;

import android.content.ContextWrapper;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.example.mobileproject.MainActivity;
import com.example.mobileproject.R;
import com.example.mobileproject.viewmodel.HomeViewModel;

import java.util.function.Consumer;
import org.json.JSONArray;
import org.json.JSONObject;

public class HomeViews extends ContextWrapper {
    protected static final int INK = 0xFF171719, MUTED = 0xFF73737B, PURPLE = 0xFF3533CD;
    protected static final int LINE = 0xFFE9E9ED, RED = 0xFFD92D38;
    protected final MainActivity activity;
    protected final HomeViewModel vm;
    protected final Typeface serif, sans;

    public HomeViews(MainActivity activity, HomeViewModel vm) {
        super(activity);
        this.activity = activity;
        this.vm = vm;
        serif = getResources().getFont(R.font.sx_fraunces);
        sans = getResources().getFont(R.font.sx_noto_sans);
    }

    protected void go(String page) {
        activity.openPage(page);
    }

    protected boolean requireLogin() {
        return activity.requireLogin();
    }

    protected LinearLayout menuRow(
        String icon, String title, String subtitle, boolean danger, Runnable action) {
        LinearLayout wrapper = vertical();
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(17), 0, dp(17));
        row.addView(new GlyphView(this, icon, danger ? RED : INK), lp(23, 23));
        LinearLayout words = vertical();
        LinearLayout.LayoutParams w = new LinearLayout.LayoutParams(0, -2, 1);
        w.leftMargin = dp(13);
        w.rightMargin = dp(8);
        row.addView(words, w);
        TextView name = heading(title, 17);
        if (danger) name.setTextColor(RED);
        words.addView(name);
        if (!subtitle.isEmpty()) {
            gap(words, 5);
            words.addView(body(subtitle, 11, MUTED));
        }
        row.addView(new GlyphView(this, "chevron", danger ? RED : INK), lp(17, 17));
        row.setOnClickListener(v -> {
            if (!vm.busy) action.run();
        });
        wrapper.addView(row);
        if (!danger) divider(wrapper);
        return wrapper;
    }

    protected void empty(LinearLayout parent, String title, String description) {
        LinearLayout box = padded(25);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        gap(box, 35);
        box.addView(new GlyphView(this, "search", PURPLE), lp(38, 38));
        gap(box, 17);
        TextView name = heading(title, 23);
        name.setGravity(Gravity.CENTER);
        box.addView(name);
        gap(box, 10);
        TextView note = body(description, 13, MUTED);
        note.setGravity(Gravity.CENTER);
        box.addView(note);
        gap(box, 30);
        parent.addView(box);
    }

    protected TextView primary(String title, Runnable action) {
        TextView button = heading(title, 17);
        button.setTextColor(Color.WHITE);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(12), dp(12), dp(12), dp(12));
        button.setMinHeight(dp(48));
        button.setBackground(gradient(10));
        button.setOnClickListener(v -> {
            if (!vm.busy) action.run();
        });
        return button;
    }

    protected TextView heading(String text, int size) {
        TextView view = body(text, size, INK);
        view.setTypeface(serif);
        view.setLetterSpacing(-.015f);
        return view;
    }

    protected TextView body(String text, int size, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(sans);
        view.setIncludeFontPadding(false);
        view.setLineSpacing(dp(2), 1);
        return view;
    }

    protected TextView label(String text) {
        TextView label = body(text, 11, 0xFF85858E);
        label.setLetterSpacing(.07f);
        label.setPadding(0, dp(11), 0, dp(4));
        return label;
    }

    protected FrameLayout iconButton(String name, String description, Runnable action) {
        FrameLayout button = new FrameLayout(this);
        button.setContentDescription(description);
        GlyphView icon = new GlyphView(this, name, INK);
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(dp(23), dp(23), Gravity.CENTER);
        button.addView(icon, p);
        button.setOnClickListener(v -> {
            if (!vm.busy) action.run();
        });
        return button;
    }

    protected LinearLayout vertical() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        return v;
    }

    protected LinearLayout horizontal() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.HORIZONTAL);
        return v;
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
        parent.addView(new View(this), lp(1, height));
    }

    protected void divider(LinearLayout parent) {
        View line = new View(this);
        line.setBackgroundColor(LINE);
        parent.addView(line, lp(-1, 1));
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

    protected GradientDrawable border(int color, int stroke, int radius) {
        GradientDrawable d = round(color, radius);
        d.setStroke(dp(1), stroke);
        return d;
    }

    protected GradientDrawable chipBackground() {
        GradientDrawable d =
            new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[] {0xFF3834DC, 0xFF05040E});
        d.setCornerRadius(dp(30));
        return d;
    }

    protected GradientDrawable gradient(int radius) {
        GradientDrawable d =
            new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[] {0xFF0B0918, 0xFF3834D9});
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
