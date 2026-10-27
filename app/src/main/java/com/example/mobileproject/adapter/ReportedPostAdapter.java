package com.example.mobileproject.adapter;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.mobileproject.R;
import com.example.mobileproject.model.ReportedPostItem;
import com.example.mobileproject.util.PostImageUtils;

import java.util.List;

public class ReportedPostAdapter extends RecyclerView.Adapter<ReportedPostAdapter.ViewHolder> {

    public interface OnReportClickListener {
        void onReportClick(ReportedPostItem item);
    }

    private final Context context;
    private final List<ReportedPostItem> items;
    private final OnReportClickListener listener;

    public ReportedPostAdapter(Context context, List<ReportedPostItem> items, OnReportClickListener listener) {
        this.context = context;
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_reported_post, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ReportedPostItem item = items.get(position);

        holder.txtTitle.setText(item.postTitle != null && !item.postTitle.trim().isEmpty() ? item.postTitle : "Bài đăng");
        holder.txtAddress.setText(item.postAddress != null && !item.postAddress.trim().isEmpty() ? item.postAddress : "Chưa cập nhật địa chỉ");
        holder.txtReason.setText("Lý do báo cáo: " + (item.reason != null ? item.reason : "Không có lý do"));

        String status = item.status != null ? item.status.toUpperCase() : "PENDING";
        if ("RESOLVED".equals(status) || "REJECTED".equals(status) || status.contains("ĐÃ XỬ LÝ")) {
            holder.txtStatus.setText("Đã xử lý");
            holder.txtStatus.setTextColor(Color.parseColor("#1565C0"));
            holder.txtStatus.setBackground(ContextCompat.getDrawable(context, R.drawable.bg_badge_accepted));
        } else {
            holder.txtStatus.setText("Đang chờ");
            holder.txtStatus.setTextColor(Color.parseColor("#E65100"));
            holder.txtStatus.setBackground(ContextCompat.getDrawable(context, R.drawable.bg_badge_pending));
        }

        if (item.postImageUrl != null && !item.postImageUrl.trim().isEmpty()) {
            Object imgModel = PostImageUtils.asGlideModel(item.postImageUrl);
            if (imgModel != null) {
                Glide.with(context)
                        .load(imgModel)
                        .placeholder(R.drawable.bg_blank_canvas)
                        .error(R.drawable.bg_blank_canvas)
                        .into(holder.imgPhoto);
            } else {
                holder.imgPhoto.setImageResource(R.drawable.bg_blank_canvas);
            }
        } else {
            holder.imgPhoto.setImageResource(R.drawable.bg_blank_canvas);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onReportClick(item);
        });
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgPhoto;
        TextView txtTitle, txtAddress, txtReason, txtStatus;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgPhoto = itemView.findViewById(R.id.imgPostPhoto);
            txtTitle = itemView.findViewById(R.id.txtPostTitle);
            txtAddress = itemView.findViewById(R.id.txtPostAddress);
            txtReason = itemView.findViewById(R.id.txtReportReason);
            txtStatus = itemView.findViewById(R.id.txtReportStatus);
        }
    }
}
