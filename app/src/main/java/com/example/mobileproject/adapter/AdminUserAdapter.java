package com.example.mobileproject.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.mobileproject.R;
import com.example.mobileproject.model.AdminUserItem;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class AdminUserAdapter
        extends RecyclerView.Adapter<AdminUserAdapter.Holder> {

    private List<AdminUserItem> users = new ArrayList<>();
    private final Consumer<AdminUserItem> open;

    public AdminUserAdapter(Consumer<AdminUserItem> open) {
        this.open = open;
    }

    public void submit(List<AdminUserItem> value) {
        users = value;
        notifyDataSetChanged();
    }

    @Override
    public Holder onCreateViewHolder(ViewGroup parent, int type) {
        View card = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_admin_user, parent, false);

        return new Holder(card);
    }

    @Override
    public void onBindViewHolder(Holder holder, int position) {
        AdminUserItem user = users.get(position);

        holder.name.setText(user.fullName);
        holder.email.setText(
                user.email.isEmpty() ? user.username : user.email
        );
        holder.status.setText(user.statusLabel());
        holder.status.setActivated(user.active());

        String count = user.postCount < 0
                ? holder.itemView.getContext().getString(
                R.string.admin_user_count_unknown
        )
                : String.valueOf(user.postCount);

        holder.posts.setText(
                holder.itemView.getContext().getString(
                        R.string.admin_user_post_count,
                        count,
                        user.accountId
                )
        );

        Glide.with(holder.itemView)
                .load(user.avatarUrl.isEmpty() ? null : user.avatarUrl)
                .placeholder(R.drawable.ic_profile)
                .error(R.drawable.ic_profile)
                .circleCrop()
                .into(holder.avatar);

        holder.itemView.setContentDescription(
                holder.itemView.getContext().getString(
                        R.string.admin_user_open_description,
                        user.fullName
                )
        );

        holder.itemView.setOnClickListener(v -> open.accept(user));
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {

        final ImageView avatar;
        final TextView name;
        final TextView email;
        final TextView status;
        final TextView posts;

        Holder(View card) {
            super(card);

            avatar = card.findViewById(R.id.adminUserAvatar);
            name = card.findViewById(R.id.adminUserName);
            email = card.findViewById(R.id.adminUserEmail);
            status = card.findViewById(R.id.adminUserStatus);
            posts = card.findViewById(R.id.adminUserPostCount);
        }
    }
}