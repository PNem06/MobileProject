package com.example.mobileproject.ui;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.example.mobileproject.MainActivity;
import com.example.mobileproject.ui.adapter.PostCardAdapter;
import com.example.mobileproject.ui.adapter.PostCardAdapter.PostViewHolder;
import com.example.mobileproject.view.DETAIL;
import com.example.mobileproject.viewmodel.HomeViewModel;

import org.json.JSONObject;

import java.util.*;

public class PostCards extends HomeViews {
    final Map<String, Integer> trackOffsets = new HashMap<>();

    public PostCards(MainActivity activity, HomeViewModel vm) {
        super(activity, vm);
    }

    void section(
            LinearLayout parent, String title, List<JSONObject> posts, String key, boolean mine) {
        section(parent, title, posts, key, mine, null);
    }

    void section(
            LinearLayout parent,
            String title,
            List<JSONObject> posts,
            String key,
            boolean mine,
            Runnable createPost) {
        LinearLayout section = vertical();
        LinearLayout top = horizontal();
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(22), mine ? dp(3) : dp(1), dp(22), dp(13));
        TextView name = heading(title, mine ? 21 : 23);
        top.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        RecyclerView track = new RecyclerView(activity);
        LinearLayoutManager manager =
                new LinearLayoutManager(activity, RecyclerView.HORIZONTAL, false);
        track.setLayoutManager(manager);
        track.setNestedScrollingEnabled(false);
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
        if (mine && createPost != null) {
            LinearLayout action = padded(22);
            action.setPadding(dp(22), 0, dp(22), dp(18));
            if (posts.isEmpty()) {
                action.addView(heading("Bạn chưa có bài đăng", 19));
                gap(action, 8);
                action.addView(
                        body("Chia sẻ chỗ ở của bạn để bắt đầu kết nối trao đổi.", 13, MUTED));
                gap(action, 16);
            }
            action.addView(
                    primary(posts.isEmpty() ? "Đăng chỗ ở đầu tiên" : "＋ Tạo bài đăng", createPost),
                    lp(-1, -2));
            section.addView(action);
        }
        if (posts.isEmpty()) {
            if (!mine || createPost == null) {
                TextView empty = body("Bạn chưa có bài đăng nào được tải về.", 12, MUTED);
                empty.setPadding(dp(22), dp(10), dp(22), dp(25));
                section.addView(empty);
            }
        } else {
            int width = wide() ? 182 : (mine ? 157 : 154);
            int stride = dp(width + 15);
            track.setAdapter(
                    new PostCardAdapter(
                            posts,
                            dp(width),
                            dp(15),
                            dp(mine ? 106 : 155),
                            (holder, post) -> bindCard(holder, post, mine)));
            track.addOnScrollListener(
                    new RecyclerView.OnScrollListener() {
                        @Override
                        public void onScrolled(RecyclerView view, int dx, int dy) {
                            int first = manager.findFirstVisibleItemPosition();
                            View child = manager.findViewByPosition(first);
                            if (first >= 0 && child != null)
                                trackOffsets.put(
                                        key,
                                        Math.max(
                                                0,
                                                first * stride
                                                        - child.getLeft()
                                                        + view.getPaddingLeft()));
                        }
                    });
            int x = Math.max(0, trackOffsets.containsKey(key) ? trackOffsets.get(key) : 0);
            int position = Math.min(posts.size() - 1, x / stride);
            manager.scrollToPositionWithOffset(position, -(x % stride));
            float fontScale = getResources().getConfiguration().fontScale;
            int height = dp((mine ? 106 : 155) + (int) Math.ceil(95 * fontScale) + 24);
            section.addView(track, lp(-1, height));
        }

        section.setPadding(0, 0, 0, dp(mine ? 16 : 22));
        parent.addView(section);
    }

    private void bindCard(PostViewHolder holder, JSONObject post, boolean mine) {
        String id = post.optString("id");
        holder.photoFrame.setBackground(round(0xFFEDEDED, 13));
        holder.photoFrame.setClipToOutline(true);
        holder.title.setText(postTitle(post));
        holder.title.setTextSize(mine ? 15 : 16);
        holder.price.setText(PostCardData.priceLabel(post));
        String rating = PostCardData.ratingLabel(post);
        holder.rating.setText(rating);
        holder.rating.setVisibility(rating.isEmpty() ? View.GONE : View.VISIBLE);
        holder.photo.setContentDescription("Ảnh " + postTitle(post));
        bindPhoto(holder, vm.image(id));
        bindOwner(holder, vm.owner(post));
        holder.favoriteButton.setVisibility(mine ? View.GONE : View.VISIBLE);
        holder.favoriteButton.setBackground(round(0x55000000, 30));
        holder.favoriteButton.setContentDescription("Yêu thích " + postTitle(post));
        holder.favoriteButton.setEnabled(!vm.favoritePending(id));
        holder.favoriteButton.setAlpha(vm.favoritePending(id) ? .55f : 1f);
        holder.favoriteButton.setOnClickListener(
                v -> {
                    if (vm.busy || !requireLogin()) return;
                    vm.toggleFavorite(id);
                });
        holder.heart.solid = vm.isFavorite(id);
        holder.heart.invalidate();
        String badge =
                mine
                        ? ""
                        : vm.isFavorite(id)
                          ? "Được bạn\nyêu thích"
                          : PostCardData.isAvailable(post, System.currentTimeMillis())
                            ? "Còn trống"
                            : "";
        holder.badge.setText(badge);
        holder.badge.setBackground(round(Color.WHITE, 12));
        holder.badge.setVisibility(badge.isEmpty() ? View.GONE : View.VISIBLE);
        holder.itemView.setOnClickListener(v -> postDetails(post));
    }

    private void bindPhoto(PostViewHolder holder, String url) {
        Glide.with(activity).clear(holder.photo);
        holder.photo.setImageDrawable(null);
        holder.photo.setTag(url);
        holder.imageState.setVisibility(View.VISIBLE);
        holder.imageState.setText(url.isEmpty() ? "Chưa có ảnh" : "Đang tải ảnh…");
        if (url.isEmpty()) return;
        Glide.with(activity)
                .load(url)
                .centerCrop()
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .listener(
                        new RequestListener<Drawable>() {
                            @Override
                            public boolean onLoadFailed(
                                    GlideException error,
                                    Object model,
                                    Target<Drawable> target,
                                    boolean first) {
                                if (url.equals(holder.photo.getTag())) {
                                    holder.imageState.setText("Không tải được ảnh");
                                    holder.imageState.setVisibility(View.VISIBLE);
                                }
                                return false;
                            }

                            @Override
                            public boolean onResourceReady(
                                    Drawable image,
                                    Object model,
                                    Target<Drawable> target,
                                    DataSource source,
                                    boolean first) {
                                if (url.equals(holder.photo.getTag()))
                                    holder.imageState.setVisibility(View.GONE);
                                return false;
                            }
                        })
                .into(holder.photo);
    }

    private void bindOwner(PostViewHolder holder, JSONObject owner) {
        Glide.with(activity).clear(holder.avatar);
        holder.avatar.setImageDrawable(null);
        holder.avatarFrame.setBackground(round(Color.WHITE, 32));
        holder.initials.setBackground(round(0xFFE9E6FC, 28));
        String name = owner.optString("fullName").trim();
        holder.initials.setText(PostCardData.initials(name));
        holder.avatarFrame.setContentDescription(
                name.isEmpty() ? "Chủ bài đăng" : "Chủ bài đăng: " + name);
        String url = owner.optString("avatarUrl").trim();
        if (!url.isEmpty()) Glide.with(activity).load(url).circleCrop().into(holder.avatar);
    }

    String postTitle(JSONObject post) {
        return PostCardData.title(post);
    }

    private void postDetails(JSONObject post) {
        if (vm.busy) return;
        String postId = post.optString("id");
        if (postId.isEmpty()) {
            vm.tell("Không xác định được bài đăng.");
            return;
        }
        if (!vm.online()) {
            vm.tell("Kết nối mạng để xem chi tiết bài đăng.");
            return;
        }
        try {
            activity.startActivity(
                    new Intent(activity, DETAIL.class).putExtra(DETAIL.EXTRA_POST_ID, postId));
        } catch (ActivityNotFoundException error) {
            vm.tell("Chưa mở được trang chi tiết bài đăng.");
        }
    }
}
