package com.example.mobileproject.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.mobileproject.R;
import com.example.mobileproject.ui.GlyphView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Adapter/ViewHolder tạo view một lần rồi gắn dữ liệu khi cuộn. */
public final class PostCardAdapter extends RecyclerView.Adapter<PostCardAdapter.PostViewHolder> {
    public interface Binder {
        void bind(PostViewHolder holder, JSONObject post);
    }

    private final List<JSONObject> posts;
    private final Binder binder;
    private final int width, spacing, photoHeight;

    public PostCardAdapter(
            List<JSONObject> posts, int width, int spacing, int photoHeight, Binder binder) {
        this.posts = new ArrayList<>(posts);
        this.width = width;
        this.spacing = spacing;
        this.photoHeight = photoHeight;
        this.binder = binder;
    }

    @NonNull
    @Override
    public PostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View item =
                LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_home_post, parent, false);
        RecyclerView.LayoutParams params =
                new RecyclerView.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.rightMargin = spacing;
        item.setLayoutParams(params);
        PostViewHolder holder = new PostViewHolder(item);
        ViewGroup.LayoutParams photoParams = holder.photoFrame.getLayoutParams();
        photoParams.height = photoHeight;
        holder.photoFrame.setLayoutParams(photoParams);
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull PostViewHolder holder, int position) {
        binder.bind(holder, posts.get(position));
    }

    @Override
    public int getItemCount() {
        return posts.size();
    }

    @Override
    public void onViewRecycled(@NonNull PostViewHolder holder) {
        holder.photo.setTag(null);
        Glide.with(holder.photo).clear(holder.photo);
        Glide.with(holder.avatar).clear(holder.avatar);
        super.onViewRecycled(holder);
    }

    public static final class PostViewHolder extends RecyclerView.ViewHolder {
        public final FrameLayout photoFrame, avatarFrame, favoriteButton;
        public final ImageView photo, avatar;
        public final TextView imageState, title, price, rating, badge, initials;
        public final GlyphView heart;

        public PostViewHolder(@NonNull View item) {
            super(item);
            photoFrame = item.findViewById(R.id.postPhotoFrame);
            photo = item.findViewById(R.id.postPhoto);
            imageState = item.findViewById(R.id.postImageState);
            title = item.findViewById(R.id.postTitle);
            price = item.findViewById(R.id.postPrice);
            rating = item.findViewById(R.id.postRating);
            badge = item.findViewById(R.id.postBadge);
            avatarFrame = item.findViewById(R.id.postAvatarFrame);
            avatar = item.findViewById(R.id.postAvatar);
            initials = item.findViewById(R.id.postInitials);
            favoriteButton = item.findViewById(R.id.postFavoriteButton);
            heart = new GlyphView(item.getContext(), "heart", android.graphics.Color.WHITE);
            int size = Math.round(24 * item.getResources().getDisplayMetrics().density);
            favoriteButton.addView(
                    heart, new FrameLayout.LayoutParams(size, size, android.view.Gravity.CENTER));
        }
    }
}
