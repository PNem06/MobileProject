package com.example.mobileproject.view;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.mobileproject.R;

import java.util.ArrayList;
import java.util.List;

public class DETAILAdapter extends RecyclerView.Adapter<DETAILAdapter.ImageViewHolder> {
    private final List<String> imageUrls;
    private final List<Integer> imageResIds;

    public DETAILAdapter(List<String> imageUrls, boolean isUrlList) {
        this.imageUrls = imageUrls != null ? imageUrls : new ArrayList<>();
        this.imageResIds = null;
    }

    public DETAILAdapter(List<Integer> images) {
        this.imageResIds = images != null ? images : new ArrayList<>();
        this.imageUrls = null;
    }

    @NonNull
    @Override
    public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.detail_item_images, parent, false);
        return new ImageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
        if (imageUrls != null && position < imageUrls.size()) {
            String url = imageUrls.get(position);
            if (url != null && !url.trim().isEmpty()) {
                Glide.with(holder.imageView.getContext())
                        .load(url)
                        .centerCrop()
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .into(holder.imageView);
            } else {
                holder.imageView.setImageResource(android.R.drawable.ic_menu_gallery);
            }
        } else if (imageResIds != null && position < imageResIds.size()) {
            holder.imageView.setImageResource(imageResIds.get(position));
        }
    }

    @Override
    public int getItemCount() {
        if (imageUrls != null) return imageUrls.size();
        if (imageResIds != null) return imageResIds.size();
        return 0;
    }

    static class ImageViewHolder extends RecyclerView.ViewHolder {
        final ImageView imageView;

        ImageViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.imgRoomPage);
        }
    }
}
