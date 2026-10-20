package com.example.mobileproject.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.mobileproject.R;
import com.example.mobileproject.model.ListingPost;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class SearchPostAdapter extends RecyclerView.Adapter<SearchPostAdapter.PostViewHolder> {

    private final Context context;
    private final List<ListingPost> postList;
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(ListingPost post);
    }

    public SearchPostAdapter(Context context, List<ListingPost> postList, OnItemClickListener listener) {
        this.context = context;
        this.postList = postList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public PostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_search_post, parent, false);
        return new PostViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PostViewHolder holder, int position) {
        ListingPost post = postList.get(position);

        holder.txtPostTitle.setText(post.getTitle() != null ? post.getTitle() : "Chỗ ở chưa có tên");

        String bedBathText = post.getBedroomCount() + " phòng ngủ | " + post.getBathroomCount() + " phòng tắm";
        holder.txtBedBathSummary.setText(bedBathText);

        if (post.getAmenityIds() != null && !post.getAmenityIds().isEmpty()) {
            holder.txtAmenitiesSummary.setText("Tiện nghi: " + String.join(", ", post.getAmenityIds()));
        } else {
            holder.txtAmenitiesSummary.setText("Không có tiện nghi");
        }

        NumberFormat formatter = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
        holder.txtCurrentPrice.setText(formatter.format(post.getPricePerNight()));

        if (post.getCoverImageUrl() != null && !post.getCoverImageUrl().isEmpty()) {
            Glide.with(context)
                    .load(post.getCoverImageUrl())
                    .placeholder(R.drawable.logo_stageexchange)
                    .into(holder.imgPostCover);
        } else {
            holder.imgPostCover.setImageResource(R.drawable.logo_stageexchange);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(post);
        });
    }

    @Override
    public int getItemCount() {
        return postList != null ? postList.size() : 0;
    }

    public static class PostViewHolder extends RecyclerView.ViewHolder {
        ImageView imgPostCover;
        TextView txtPostTitle, txtBedBathSummary, txtAmenitiesSummary, txtCurrentPrice;

        public PostViewHolder(@NonNull View itemView) {
            super(itemView);
            imgPostCover = itemView.findViewById(R.id.imgPostCover);
            txtPostTitle = itemView.findViewById(R.id.txtPostTitle);
            txtBedBathSummary = itemView.findViewById(R.id.txtBedBathSummary);
            txtAmenitiesSummary = itemView.findViewById(R.id.txtAmenitiesSummary);
            txtCurrentPrice = itemView.findViewById(R.id.txtCurrentPrice);
        }
    }
}