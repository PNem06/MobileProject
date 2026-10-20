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
import com.example.mobileproject.model.RequestItem;

import java.util.List;

public class RequestAdapter extends RecyclerView.Adapter<RequestAdapter.ViewHolder> {

    public interface OnRequestListener {
        void onCardClick(RequestItem item);
        void onLeftActionClick(RequestItem item);
        void onRightActionClick(RequestItem item);
    }

    private final Context context;
    private final List<RequestItem> items;
    private final OnRequestListener listener;

    public RequestAdapter(Context context, List<RequestItem> items, OnRequestListener listener) {
        this.context = context;
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_request_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RequestItem item = items.get(position);

        holder.txtPostTitle.setText(valueOrDefault(item.postTitle, "Chi tiết chỗ ở"));
        holder.txtDates.setText(valueOrDefault(item.dateRangeText, "Thời gian trao đổi chưa xác định"));
        holder.txtPriceOrPoints.setText(valueOrDefault(item.priceOrPointsText, "Thỏa thuận"));
        holder.txtRating.setText("★ " + String.format(java.util.Locale.US, "%.1f", item.rating));

        // Tải ảnh Glide
        if (item.postImageUrl != null && !item.postImageUrl.trim().isEmpty()) {
            Glide.with(context)
                    .load(item.postImageUrl)
                    .placeholder(R.drawable.bg_blank_canvas)
                    .error(R.drawable.bg_blank_canvas)
                    .into(holder.imgPostThumbnail);
        } else {
            holder.imgPostThumbnail.setImageResource(R.drawable.bg_blank_canvas);
        }

        // Cấu hình trạng thái & nút bấm dựa trên Tab ("Đã Gửi" hay "Đã Nhận")
        String status = item.status == null ? "PENDING" : item.status.toUpperCase();

        if ("ACCEPTED".equals(status)) {
            holder.txtStatusBadge.setText("Đã chấp nhận");
            holder.txtStatusBadge.setBackgroundResource(R.drawable.bg_status_accepted);
            holder.txtStatusBadge.setTextColor(0xFFFFFFFF);

            if (item.isSentTab) {
                holder.btnActionLeft.setText("Liên hệ chủ");
                holder.btnActionRight.setText("Tiếp tục");
            } else {
                holder.btnActionLeft.setText("Report");
                holder.btnActionRight.setText("Tiếp tục");
            }
        } else if ("CANCELLED".equals(status) || "REJECTED".equals(status)) {
            holder.txtStatusBadge.setText("Đã hủy");
            holder.txtStatusBadge.setBackgroundResource(R.drawable.bg_status_cancelled);
            holder.txtStatusBadge.setTextColor(0xFFFFFFFF);

            holder.btnActionLeft.setText("Report");
            holder.btnActionRight.setText("Block");
        } else {
            // PENDING - Đang chờ
            holder.txtStatusBadge.setText("Đang chờ");
            holder.txtStatusBadge.setBackgroundResource(R.drawable.bg_status_waiting);
            holder.txtStatusBadge.setTextColor(0xFF222222);

            if (item.isSentTab) {
                holder.btnActionLeft.setText("Hủy");
                holder.btnActionRight.setText("Chỉnh sửa");
            } else {
                holder.btnActionLeft.setText("Từ chối");
                holder.btnActionRight.setText("Đồng ý");
            }
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onCardClick(item);
        });

        holder.btnActionLeft.setOnClickListener(v -> {
            if (listener != null) listener.onLeftActionClick(item);
        });

        holder.btnActionRight.setOnClickListener(v -> {
            if (listener != null) listener.onRightActionClick(item);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private String valueOrDefault(String val, String fallback) {
        return (val == null || val.trim().isEmpty()) ? fallback : val;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgPostThumbnail, btnHeart;
        TextView txtBadgeFavorite, txtStatusBadge, txtRating;
        TextView txtPostTitle, txtDates, txtPriceOrPoints;
        TextView btnActionLeft, btnActionRight;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgPostThumbnail = itemView.findViewById(R.id.imgPostThumbnail);
            btnHeart = itemView.findViewById(R.id.btnHeart);
            txtBadgeFavorite = itemView.findViewById(R.id.txtBadgeFavorite);
            txtStatusBadge = itemView.findViewById(R.id.txtStatusBadge);
            txtRating = itemView.findViewById(R.id.txtRating);
            txtPostTitle = itemView.findViewById(R.id.txtPostTitle);
            txtDates = itemView.findViewById(R.id.txtDates);
            txtPriceOrPoints = itemView.findViewById(R.id.txtPriceOrPoints);
            btnActionLeft = itemView.findViewById(R.id.btnActionLeft);
            btnActionRight = itemView.findViewById(R.id.btnActionRight);
        }
    }
}