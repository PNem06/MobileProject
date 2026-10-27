package com.example.mobileproject.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.mobileproject.R;
import com.example.mobileproject.model.BlockedUserItem;
import com.google.android.material.imageview.ShapeableImageView;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class BlockedUserAdapter extends RecyclerView.Adapter<BlockedUserAdapter.ViewHolder> {

    public interface OnUnblockListener {
        void onUnblock(BlockedUserItem item);
    }

    private final Context context;
    private final List<BlockedUserItem> items;
    private final OnUnblockListener listener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("vi", "VN"));

    public BlockedUserAdapter(Context context, List<BlockedUserItem> items, OnUnblockListener listener) {
        this.context = context;
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_blocked_user, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        BlockedUserItem item = items.get(position);

        holder.txtUserName.setText(item.fullName != null && !item.fullName.trim().isEmpty() ? item.fullName : item.blockedUserId);
        if (item.createdAt != null) {
            holder.txtBlockDate.setText("Ngày chặn: " + dateFormat.format(item.createdAt));
        } else {
            holder.txtBlockDate.setText("Ngày chặn: Chưa rõ");
        }

        if (item.avatarUrl != null && !item.avatarUrl.trim().isEmpty()) {
            Glide.with(context)
                    .load(item.avatarUrl)
                    .placeholder(R.drawable.ic_user)
                    .error(R.drawable.ic_user)
                    .into(holder.imgAvatar);
        } else {
            holder.imgAvatar.setImageResource(R.drawable.ic_user);
        }

        holder.btnUnblock.setOnClickListener(v -> {
            if (listener != null) {
                listener.onUnblock(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ShapeableImageView imgAvatar;
        TextView txtUserName, txtBlockDate, btnUnblock;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgAvatar = itemView.findViewById(R.id.imgBlockedUserAvatar);
            txtUserName = itemView.findViewById(R.id.txtBlockedUserName);
            txtBlockDate = itemView.findViewById(R.id.txtBlockedDate);
            btnUnblock = itemView.findViewById(R.id.btnUnblock);
        }
    }
}
