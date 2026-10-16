package com.example.mobileproject.ui;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.example.mobileproject.MainActivity;
import com.example.mobileproject.R;
import com.example.mobileproject.viewmodel.HomeViewModel;

import org.json.JSONObject;

import java.util.*;

public class PostCards extends HomeViews {
    Runnable onFavoritesChanged = () -> {};
    final Set<String> hearts = new HashSet<>();
    final Map<String, Integer> trackOffsets = new HashMap<>();

    public PostCards(MainActivity activity, HomeViewModel vm) {
        super(activity, vm);
    }

    void section(
            LinearLayout parent, String title, List<JSONObject> posts, String key, boolean mine) {
        LinearLayout section = vertical();
        LinearLayout top = horizontal();
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(22), mine ? dp(3) : dp(1), dp(22), dp(13));
        TextView name = heading(title, mine ? 21 : 23);
        top.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        HorizontalScrollView track = new HorizontalScrollView(this);
        track.setHorizontalScrollBarEnabled(false);
        track.setClipToPadding(false);
        track.setPadding(dp(22), 0, dp(22), dp(12));
        if (mine) {
            TextView count = body(posts.size() + " bài đăng", 11, MUTED);
            top.addView(count);
        } else {
            FrameLayout arrow =
                    iconButton("next", "Xem thêm chỗ ở", () -> track.smoothScrollBy(dp(172), 0));
            arrow.setBackground(round(0xFFE8E8E4, 30));
            LinearLayout.LayoutParams a = lp(28, 28);
            a.leftMargin = dp(12);
            top.addView(arrow, a);
        }
        section.addView(top);
        if (posts.isEmpty()) {
            TextView empty = body("Bạn chưa có bài đăng nào được tải về.", 12, MUTED);
            empty.setPadding(dp(22), dp(10), dp(22), dp(25));
            section.addView(empty);
        } else {
            LinearLayout cards = horizontal();
            track.addView(cards);
            int width = wide() ? 182 : (mine ? 157 : 154);
            for (JSONObject post : posts) {
                LinearLayout.LayoutParams p = lp(width, -2);
                p.rightMargin = dp(15);
                cards.addView(postCard(post, mine), p);
            }
            track.setOnScrollChangeListener(
                    (View.OnScrollChangeListener) (v, x, y, ox, oy) -> trackOffsets.put(key, x));
            int x = trackOffsets.containsKey(key) ? trackOffsets.get(key) : 0;
            track.post(() -> track.scrollTo(x, 0));
            section.addView(track);
        }
        section.setPadding(0, 0, 0, dp(mine ? 16 : 22));
        parent.addView(section);
    }

    private LinearLayout postCard(JSONObject post, boolean mine) {
        LinearLayout card = vertical();
        FrameLayout photo = new FrameLayout(this);
        photo.setBackground(round(0xFFEDEDED, 13));
        photo.setClipToOutline(true);
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        photo.addView(image, new FrameLayout.LayoutParams(-1, -1));
        TextView illustration = body("Ảnh minh họa", 8, Color.WHITE);
        illustration.setPadding(dp(5), dp(3), dp(5), dp(3));
        illustration.setBackground(round(0x99000000, 5));
        FrameLayout.LayoutParams caption =
                new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        caption.setMargins(dp(6), 0, 0, dp(6));
        photo.addView(illustration, caption);
        String url = vm.image(post.optString("id"));
        int fallback = roomImage(post.optString("id"));
        Glide.with(this)
                .load(url.isEmpty() ? fallback : url)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .centerCrop()
                .placeholder(fallback)
                .error(fallback)
                .listener(
                        new RequestListener<Drawable>() {
                            @Override
                            public boolean onLoadFailed(
                                    GlideException e,
                                    Object model,
                                    Target<Drawable> target,
                                    boolean first) {
                                illustration.setVisibility(View.VISIBLE);
                                return false;
                            }

                            @Override
                            public boolean onResourceReady(
                                    Drawable d,
                                    Object model,
                                    Target<Drawable> target,
                                    DataSource source,
                                    boolean first) {
                                illustration.setVisibility(
                                        url.isEmpty() ? View.VISIBLE : View.GONE);
                                return false;
                            }
                        })
                .into(image);
        FrameLayout owner = ownerAvatar(vm.owner(post));
        FrameLayout.LayoutParams ownerParams =
                new FrameLayout.LayoutParams(dp(32), dp(32), Gravity.BOTTOM | Gravity.END);
        ownerParams.setMargins(0, 0, dp(7), dp(7));
        photo.addView(owner, ownerParams);
        if (!mine) {
            String badgeText =
                    hearts.contains(post.optString("id"))
                            ? "Được bạn\nyêu thích"
                            : PostCardData.isAvailable(post, System.currentTimeMillis())
                              ? "Còn trống"
                              : "";
            if (!badgeText.isEmpty()) {
                TextView badge = heading(badgeText, 10);
                badge.setPadding(dp(8), dp(5), dp(8), dp(5));
                badge.setBackground(round(Color.WHITE, 12));
                FrameLayout.LayoutParams b =
                        new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
                b.setMargins(dp(7), dp(8), dp(40), 0);
                photo.addView(badge, b);
            }
            GlyphView heart = new GlyphView(this, "heart", Color.WHITE);
            heart.solid = hearts.contains(post.optString("id"));
            FrameLayout button = new FrameLayout(this);
            button.setBackground(round(0x33000000, 30));
            FrameLayout.LayoutParams g =
                    new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER);
            button.addView(heart, g);
            FrameLayout.LayoutParams h =
                    new FrameLayout.LayoutParams(dp(31), dp(31), Gravity.TOP | Gravity.END);
            h.setMargins(0, dp(7), dp(6), 0);
            photo.addView(button, h);
            button.setContentDescription("Yêu thích " + postTitle(post));
            button.setOnClickListener(
                    v -> {
                        String id = post.optString("id");
                        if (!hearts.add(id)) hearts.remove(id);
                        heart.solid = hearts.contains(id);
                        heart.invalidate();
                        onFavoritesChanged.run();
                    });
        }
        card.addView(photo, lp(-1, mine ? 106 : 155));
        TextView title = heading(postTitle(post), mine ? 15 : 16);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setLineSpacing(dp(1), 1);
        LinearLayout.LayoutParams t = lp(-1, -2);
        t.topMargin = dp(7);
        card.addView(title, t);
        if (!mine) title.setMinHeight(dp(43));
        LinearLayout details = horizontal();
        details.setGravity(Gravity.TOP);
        TextView price = heading(PostCardData.priceLabel(post), 11);
        price.setMaxLines(2);
        details.addView(price, new LinearLayout.LayoutParams(0, -2, 1));
        String rating = PostCardData.ratingLabel(post);
        if (!rating.isEmpty()) {
            TextView score = body(rating, 10, INK);
            LinearLayout.LayoutParams scoreParams = lp(-2, -2);
            scoreParams.leftMargin = dp(4);
            details.addView(score, scoreParams);
        }
        LinearLayout.LayoutParams detailsParams = lp(-1, -2);
        detailsParams.topMargin = dp(5);
        card.addView(details, detailsParams);
        card.setOnClickListener(v -> postDetails(post));
        return card;
    }

    private int roomImage(String id) {
        int[] photos = {
                R.drawable.sx_room_1,
                R.drawable.sx_room_2,
                R.drawable.sx_room_3,
                R.drawable.sx_room_4,
                R.drawable.sx_room_5,
                R.drawable.sx_room_6
        };
        return photos[Math.floorMod(id.hashCode(), photos.length)];
    }

    String postTitle(JSONObject post) {
        return PostCardData.title(post);
    }

    private FrameLayout ownerAvatar(JSONObject owner) {
        FrameLayout ring = new FrameLayout(this);
        ring.setBackground(round(Color.WHITE, 32));
        ring.setPadding(dp(2), dp(2), dp(2), dp(2));
        String name = owner.optString("fullName").trim();
        ring.setContentDescription(name.isEmpty() ? "Chủ bài đăng" : "Chủ bài đăng: " + name);
        FrameLayout circle = new FrameLayout(this);
        circle.setBackground(round(0xFFE9E6FC, 28));
        circle.setClipToOutline(true);
        ring.addView(circle, new FrameLayout.LayoutParams(-1, -1));
        TextView letters = body(PostCardData.initials(name), 10, PURPLE);
        letters.setGravity(Gravity.CENTER);
        circle.addView(letters, new FrameLayout.LayoutParams(-1, -1));
        String url = owner.optString("avatarUrl").trim();
        if (!url.isEmpty()) {
            ImageView avatar = new ImageView(this);
            circle.addView(avatar, new FrameLayout.LayoutParams(-1, -1));
            Glide.with(activity).load(url).circleCrop().into(avatar);
        }
        return ring;
    }

    private void postDetails(JSONObject post) {
        LinearLayout box = padded(22);
        box.addView(heading(postTitle(post), 23));
        gap(box, 10);
        String ownerName = vm.owner(post).optString("fullName").trim();
        box.addView(
                body("Chủ nhà: " + (ownerName.isEmpty() ? "Đang cập nhật" : ownerName), 12, MUTED));
        gap(box, 10);
        box.addView(heading(PostCardData.priceLabel(post), 15));
        gap(box, 12);
        box.addView(body(post.optString("addressText"), 13, MUTED));
        gap(box, 16);
        box.addView(body(post.optString("description"), 14, INK));
        gap(box, 16);
        box.addView(
                body(
                        post.optInt("maxGuests")
                                + " khách · "
                                + post.optInt("bedroomCount")
                                + " phòng ngủ · "
                                + post.optInt("bathroomCount")
                                + " phòng tắm",
                        12,
                        MUTED));
        new AlertDialog.Builder(this).setView(box).setPositiveButton("Đóng", null).show();
    }
}
