package com.example.mobileproject.adapter;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.mobileproject.R;
import com.example.mobileproject.model.RequestItem;
import com.example.mobileproject.util.PostImageUtils;

import java.util.List;
import java.util.Locale;

public class RequestAdapter extends RecyclerView.Adapter<RequestAdapter.ViewHolder> {

    public interface OnRequestListener {
        void onItemClick(RequestItem item);
        void onLeftButtonClick(RequestItem item);
        void onRightButtonClick(RequestItem item);
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

        holder.txtPostTitle.setText(valueOrDefault(item.postTitle, "Căn hộ chỗ ở"));
        holder.txtDates.setText(valueOrDefault(item.dateRangeText, "Thời gian chưa cập nhật"));
        holder.txtRating.setText(String.format(Locale.US, "%.1f", item.rating));

        // Tải ảnh bài đăng
        if (item.postImageUrl != null && !item.postImageUrl.trim().isEmpty()) {
            Object imageModel = PostImageUtils.asGlideModel(item.postImageUrl);
            if (imageModel != null) {
                Glide.with(context)
                        .load(imageModel)
                        .placeholder(R.drawable.bg_blank_canvas)
                        .error(R.drawable.bg_blank_canvas)
                        .into(holder.imgPostThumbnail);
            } else {
                holder.imgPostThumbnail.setImageResource(R.drawable.bg_blank_canvas);
            }
        } else {
            holder.imgPostThumbnail.setImageResource(R.drawable.bg_blank_canvas);
        }

        String status = item.status == null ? "PENDING" : item.status.toUpperCase();

        if (item.isSentTab) {
            // TAB "ĐÃ GỬI"
            holder.layoutReceivedSubStats.setVisibility(View.GONE);

            if ("ACCEPTED".equals(status)) {
                holder.txtStatusBadge.setText("Đã chấp nhận");
                holder.txtStatusBadge.setBackground(ContextCompat.getDrawable(context, R.drawable.bg_badge_accepted));
                holder.txtStatusBadge.setTextColor(Color.parseColor("#1565C0"));

                holder.btnActionLeft.setVisibility(View.VISIBLE);
                holder.btnActionLeft.setText("Liên hệ");
                holder.btnActionLeft.setTextColor(Color.parseColor("#D32F2F"));

                holder.btnActionRight.setVisibility(View.VISIBLE);
                holder.btnActionRight.setText("Tiếp tục");
                holder.btnActionRight.setBackground(ContextCompat.getDrawable(context, R.drawable.bg_btn_primary_blue));

            } else if ("CANCELLED".equals(status) || "REJECTED".equals(status)) {
                holder.txtStatusBadge.setText("CANCELLED".equals(status) ? "Đã hủy" : "Đã từ chối");
                holder.txtStatusBadge.setBackground(ContextCompat.getDrawable(context, R.drawable.bg_badge_pending));
                holder.txtStatusBadge.setTextColor(Color.parseColor("#D32F2F"));

                holder.btnActionLeft.setVisibility(View.VISIBLE);
                holder.btnActionLeft.setText("Liên hệ");
                holder.btnActionLeft.setTextColor(Color.parseColor("#D32F2F"));

                holder.btnActionRight.setVisibility(View.GONE);

            } else {
                // PENDING - Đang chờ
                holder.txtStatusBadge.setText("Đang chờ");
                holder.txtStatusBadge.setBackground(ContextCompat.getDrawable(context, R.drawable.bg_badge_pending));
                holder.txtStatusBadge.setTextColor(Color.parseColor("#E65100"));

                holder.btnActionLeft.setVisibility(View.VISIBLE);
                holder.btnActionLeft.setText("Hủy");
                holder.btnActionLeft.setTextColor(Color.parseColor("#D32F2F"));

                holder.btnActionRight.setVisibility(View.VISIBLE);
                if (item.hasMoneyAlternativePending) {
                    holder.btnActionRight.setText("Thanh toán");
                } else {
                    holder.btnActionRight.setText("Chỉnh sửa");
                }
                holder.btnActionRight.setBackground(ContextCompat.getDrawable(context, R.drawable.bg_btn_primary_blue));
            }

        } else {
            // TAB "ĐÃ NHẬN"
            holder.layoutReceivedSubStats.setVisibility(View.VISIBLE);
            holder.txtSubRequestsCount.setText(item.subRequestsCount + " yêu cầu");
            holder.txtRejectedCount.setText(item.rejectedSubRequestsCount + " đã từ chối");

            if ("ACCEPTED".equals(status)) {
                holder.txtStatusBadge.setText("Đã chấp nhận");
                holder.txtStatusBadge.setBackground(ContextCompat.getDrawable(context, R.drawable.bg_badge_accepted));
                holder.txtStatusBadge.setTextColor(Color.parseColor("#1565C0"));

                holder.btnActionLeft.setVisibility(View.VISIBLE);
                holder.btnActionLeft.setText("Xem");
                holder.btnActionLeft.setTextColor(Color.parseColor("#D32F2F"));

                holder.btnActionRight.setVisibility(View.VISIBLE);
                holder.btnActionRight.setText("Tiếp tục");
                holder.btnActionRight.setBackground(ContextCompat.getDrawable(context, R.drawable.bg_btn_primary_blue));

            } else {
                // PENDING - Đang chờ
                holder.txtStatusBadge.setText("Đang chờ");
                holder.txtStatusBadge.setBackground(ContextCompat.getDrawable(context, R.drawable.bg_badge_pending));
                holder.txtStatusBadge.setTextColor(Color.parseColor("#E65100"));

                holder.btnActionLeft.setVisibility(View.VISIBLE);
                holder.btnActionLeft.setText("Xem");
                holder.btnActionLeft.setTextColor(Color.parseColor("#D32F2F"));

                holder.btnActionRight.setVisibility(View.VISIBLE);
                holder.btnActionRight.setText("Chấp nhận");
                holder.btnActionRight.setBackground(ContextCompat.getDrawable(context, R.drawable.bg_btn_primary_blue));
            }
        }

        if (item.walletManaged && item.isSentTab) {
            holder.btnActionLeft.setVisibility(("PENDING".equals(status) || "ACCEPTED".equals(status) || "ONGOING".equals(status)) ? View.VISIBLE : View.GONE);
            holder.btnActionLeft.setText("Chi tiết hủy");
            holder.btnActionRight.setVisibility(View.VISIBLE);
            holder.btnActionRight.setText("Xem thanh toán");
            if ("COMPLETED".equals(status)) holder.txtStatusBadge.setText("Đã hoàn tất");
        } else if (!item.isSentTab) {
            holder.btnActionRight.setText("Xem yêu cầu");
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(item);
        });

        holder.btnActionLeft.setOnClickListener(v -> {
            if (listener != null) listener.onLeftButtonClick(item);
        });

        holder.btnActionRight.setOnClickListener(v -> {
            if (listener != null) listener.onRightButtonClick(item);
        });
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    private String valueOrDefault(String val, String fallback) {
        return (val == null || val.trim().isEmpty()) ? fallback : val;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgPostThumbnail, btnHeart;
        TextView txtStatusBadge, txtRating;
        TextView txtPostTitle, txtDates;
        LinearLayout layoutReceivedSubStats;
        TextView txtSubRequestsCount, txtRejectedCount;
        TextView btnActionLeft, btnActionRight;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgPostThumbnail = itemView.findViewById(R.id.imgPostThumbnail);
            btnHeart = itemView.findViewById(R.id.btnHeart);
            txtStatusBadge = itemView.findViewById(R.id.txtStatusBadge);
            txtRating = itemView.findViewById(R.id.txtRating);
            txtPostTitle = itemView.findViewById(R.id.txtPostTitle);
            txtDates = itemView.findViewById(R.id.txtDates);
            layoutReceivedSubStats = itemView.findViewById(R.id.layoutReceivedSubStats);
            txtSubRequestsCount = itemView.findViewById(R.id.txtSubRequestsCount);
            txtRejectedCount = itemView.findViewById(R.id.txtRejectedCount);
            btnActionLeft = itemView.findViewById(R.id.btnActionLeft);
            btnActionRight = itemView.findViewById(R.id.btnActionRight);
        }
    }
}
