package com.example.mobileproject.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.mobileproject.R;
import com.example.mobileproject.model.RequestItem;
import com.google.android.material.imageview.ShapeableImageView;

import java.util.List;

public class ReceivedSubRequestAdapter extends RecyclerView.Adapter<ReceivedSubRequestAdapter.ViewHolder> {

    public interface OnSubRequestListener {
        void onViewDetailClick(RequestItem subRequest);
    }

    private final Context context;
    private final List<RequestItem> subRequests;
    private final OnSubRequestListener listener;

    public ReceivedSubRequestAdapter(Context context, List<RequestItem> subRequests, OnSubRequestListener listener) {
        this.context = context;
        this.subRequests = subRequests;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_received_sub_request, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RequestItem item = subRequests.get(position);

        holder.txtSenderName.setText(valueOrDefault(item.senderName, "Người dùng"));
        String method = "MONEY".equalsIgnoreCase(item.exchangeType) ? "Thanh toán bằng tiền" : "Trao đổi chỗ ở";
        holder.txtExchangeMethod.setText(method);
        holder.txtRequestDates.setText(valueOrDefault(item.requestDateText, "10/02/2026 - 12/02/2026"));

        // Avatar
        if (item.senderAvatarUrl != null && !item.senderAvatarUrl.trim().isEmpty()) {
            Glide.with(context)
                    .load(item.senderAvatarUrl)
                    .placeholder(R.drawable.ic_user)
                    .error(R.drawable.ic_user)
                    .into(holder.imgSenderAvatar);
        } else {
            holder.imgSenderAvatar.setImageResource(R.drawable.ic_user);
        }

        holder.btnViewDetail.setOnClickListener(v -> {
            if (listener != null) listener.onViewDetailClick(item);
        });

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onViewDetailClick(item);
        });
    }

    @Override
    public int getItemCount() {
        return subRequests != null ? subRequests.size() : 0;
    }

    private String valueOrDefault(String val, String fallback) {
        return (val == null || val.trim().isEmpty()) ? fallback : val;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ShapeableImageView imgSenderAvatar;
        TextView txtSenderName, txtExchangeMethod, txtRequestDates;
        LinearLayout btnViewDetail;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgSenderAvatar = itemView.findViewById(R.id.imgSenderAvatar);
            txtSenderName = itemView.findViewById(R.id.txtSenderName);
            txtExchangeMethod = itemView.findViewById(R.id.txtExchangeMethod);
            txtRequestDates = itemView.findViewById(R.id.txtRequestDates);
            btnViewDetail = itemView.findViewById(R.id.btnViewDetail);
        }
    }
}
