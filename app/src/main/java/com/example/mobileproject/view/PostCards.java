package com.example.mobileproject.view;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
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
import com.example.mobileproject.R;
import com.example.mobileproject.adapter.PostCardAdapter;
import com.example.mobileproject.adapter.PostCardAdapter.PostViewHolder;
import com.example.mobileproject.util.PostImageUtils;
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
        LinearLayout section = template(R.id.homePostSection);
        section.setPadding(0, 0, 0, dp(mine ? 16 : 22));
        TextView txtSectionTitle = section.findViewById(R.id.postSectionTitle);
        TextView txtSectionCount = section.findViewById(R.id.postSectionCount);
        txtSectionTitle.setText(title);
        txtSectionTitle.setTextSize(mine ? 21 : 23);
        FrameLayout arrow = section.findViewById(R.id.postSectionArrow);
        if (mine) {
            txtSectionCount.setText(posts.size() + " bài đăng");
            txtSectionCount.setVisibility(View.VISIBLE);
        } else {
            arrow.setVisibility(View.VISIBLE);
            ImageView arrowGlyph = arrow.findViewById(R.id.postArrowGlyph);
            arrowGlyph.setImageTintList(android.content.res.ColorStateList.valueOf(INK));
            arrow.setContentDescription("Xem thêm chỗ ở");
            arrow.setOnClickListener(v -> {
                RecyclerView list = section.findViewById(R.id.postSectionTrack);
                list.smoothScrollBy(dp(172), 0);
            });
        }
        RecyclerView track = section.findViewById(R.id.postSectionTrack);
        LinearLayoutManager manager =
                new LinearLayoutManager(activity, RecyclerView.HORIZONTAL, false);
        track.setLayoutManager(manager);
        track.setHorizontalScrollBarEnabled(false);
        if (mine && createPost != null) {
            LinearLayout action = section.findViewById(R.id.postCreateAction);
            action.setVisibility(View.VISIBLE);
            if (posts.isEmpty()) {
                section.findViewById(R.id.postEmptyTitle).setVisibility(View.VISIBLE);
                section.findViewById(R.id.postEmptyDescription).setVisibility(View.VISIBLE);
            }
            TextView createButton = section.findViewById(R.id.postCreateButton);
            createButton.setText(posts.isEmpty() ? "Đăng chỗ ở đầu tiên" : "＋ Tạo bài đăng");
            createButton.setOnClickListener(v -> {
                if (!vm.busy) createPost.run();
            });
        }
        if (posts.isEmpty()) {
            if (!mine || createPost == null) {
                section.findViewById(R.id.postSectionEmpty).setVisibility(View.VISIBLE);
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
            track.setVisibility(View.VISIBLE);
            track.getLayoutParams().height = height;
        }
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
        boolean favorite = vm.isFavorite(id);
        holder.heart.setImageResource(favorite ? R.drawable.ic_favorite : R.drawable.ic_heart_outline);
        holder.heart.setColorFilter(favorite ? 0xFFD92D38 : Color.WHITE);
        String status = post.optString("status", "PENDING").trim().toUpperCase();
        String badge = "";
        if (mine) {
            if ("APPROVED".equalsIgnoreCase(status) || "PUBLIC".equalsIgnoreCase(status) || "ACTIVE".equalsIgnoreCase(status)) {
                badge = "Active";
                holder.badge.setBackground(round(0xFFE8F5E9, 12));
                holder.badge.setTextColor(0xFF2E7D32);
            } else if ("BOOKED".equalsIgnoreCase(status) || !post.optString("bookingKey").isEmpty()) {
                badge="Đã có trao đổi"; holder.badge.setBackground(round(0xFFEFEDF8,12)); holder.badge.setTextColor(0xFF433883);
            } else if ("REJECTED".equalsIgnoreCase(status) || "PRIVATE".equalsIgnoreCase(status)) {
                badge = "Bị từ chối";
                holder.badge.setBackground(round(0xFFFFEBEE, 12));
                holder.badge.setTextColor(0xFFD32F2F);
            } else {
                badge = "Chờ duyệt";
                holder.badge.setBackground(round(0xFFFFF3E0, 12));
                holder.badge.setTextColor(0xFFE65100);
            }
        } else {
            badge = "BOOKED".equals(status) || !post.optString("bookingKey").isEmpty() ? "Trao đổi của bạn" : vm.isFavorite(id)
                    ? "Được bạn\nyêu thích"
                    : PostCardData.isAvailable(post, System.currentTimeMillis())
                    ? "Còn trống"
                    : "";
            holder.badge.setBackground(round(Color.WHITE, 12));
            holder.badge.setTextColor(0xFF222222);
        }
        holder.badge.setText(badge);
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
        Object imageModel = PostImageUtils.asGlideModel(url);
        if (imageModel == null) {
            holder.imageState.setText("Không tải được ảnh");
            return;
        }
        Glide.with(activity)
                .load(imageModel)
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
                    new Intent(activity, Detail.class).putExtra(Detail.EXTRA_POST_ID, postId));
        } catch (ActivityNotFoundException error) {
            vm.tell("Chưa mở được trang chi tiết bài đăng.");
        }
    }
}
