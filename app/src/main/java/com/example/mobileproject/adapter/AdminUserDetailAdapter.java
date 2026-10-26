package com.example.mobileproject.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.mobileproject.R;
import com.example.mobileproject.model.AdminUserItem;
import com.example.mobileproject.model.AdminUserPostItem;
import com.example.mobileproject.repository.AdminUserRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public final class AdminUserDetailAdapter
        extends RecyclerView.Adapter<AdminUserDetailAdapter.Holder> {

    private final Consumer<AdminUserItem> changeStatus;
    private final Set<String> expandedPosts;
    private final List<AdminUserPostItem> posts = new ArrayList<>();

    private AdminUserRepository.State state;
    private AdminUserItem user;

    public AdminUserDetailAdapter(
            Consumer<AdminUserItem> changeStatus,
            Set<String> expandedPosts
    ) {
        this.changeStatus = changeStatus;
        this.expandedPosts = expandedPosts;

        setStateRestorationPolicy(StateRestorationPolicy.PREVENT);
    }

    public void submit(
            AdminUserRepository.State value,
            String documentId
    ) {
        state = value;
        user = null;
        posts.clear();

        if (state.authorized) {
            for (AdminUserItem item : state.users) {
                if (item.documentId.equals(documentId)) {
                    user = item;
                    break;
                }
            }

            if (user != null) {
                for (AdminUserPostItem post : state.posts) {
                    if (post.belongsTo(user)) {
                        posts.add(post);
                    }
                }
            }
        }

        setStateRestorationPolicy(
                state.postDetailsLoaded || state.denied
                        ? StateRestorationPolicy.ALLOW
                        : StateRestorationPolicy.PREVENT
        );

        notifyDataSetChanged();
    }

    public AdminUserItem selectedUser() {
        return user;
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? 0 : 1;
    }

    @Override
    public Holder onCreateViewHolder(ViewGroup parent, int type) {
        return new Holder(
                LayoutInflater.from(parent.getContext()).inflate(
                        type == 0
                                ? R.layout.section_admin_user_profile
                                : R.layout.item_admin_user_post,
                        parent,
                        false
                )
        );
    }

    @Override
    public void onBindViewHolder(Holder holder, int position) {
        View view = holder.itemView;

        if (position == 0) {
            bindProfile(view);
            return;
        }

        AdminUserPostItem post = posts.get(position - 1);

        text(view, R.id.adminUserPostTitle, post.title);
        text(view, R.id.adminUserPostAddress, post.address);
        text(view, R.id.adminUserPostStatus, post.statusLabel());
        text(view, R.id.adminUserPostDescription, post.description);

        text(
                view,
                R.id.adminUserPostDetails,
                view.getContext().getString(
                        R.string.admin_user_post_details,
                        post.postId,
                        post.propertyType,
                        post.bedroomCount,
                        post.bathroomCount,
                        post.maxGuests,
                        post.additionalInformation
                )
        );

        boolean expanded = expandedPosts.contains(post.documentId);

        TextView description = view.findViewById(
                R.id.adminUserPostDescription
        );

        description.setMaxLines(
                expanded ? Integer.MAX_VALUE : 3
        );

        view.findViewById(R.id.adminUserPostDetails)
                .setVisibility(expanded ? View.VISIBLE : View.GONE);

        Button more = view.findViewById(R.id.adminUserPostMore);

        more.setText(
                expanded
                        ? R.string.admin_user_post_less
                        : R.string.admin_user_post_more
        );

        more.setOnClickListener(v -> {
            if (!expandedPosts.remove(post.documentId)) {
                expandedPosts.add(post.documentId);
            }

            int current = holder.getBindingAdapterPosition();

            if (current != RecyclerView.NO_POSITION) {
                notifyItemChanged(current);
            }
        });

        ImageView cover = view.findViewById(
                R.id.adminUserPostCover
        );

        Glide.with(view)
                .load(
                        post.coverImageUrl.isEmpty()
                                ? null
                                : post.coverImageUrl
                )
                .placeholder(R.drawable.ic_photo)
                .error(R.drawable.ic_photo)
                .centerCrop()
                .into(cover);
    }

    private void bindProfile(View view) {
        boolean visible = user != null
                && state != null
                && state.authorized;

        view.findViewById(R.id.adminUserProfileCard)
                .setVisibility(visible ? View.VISIBLE : View.GONE);

        text(
                view,
                R.id.adminUserProfileSource,
                state == null ? "" : state.source
        );

        TextView empty = view.findViewById(
                R.id.adminUserProfileEmpty
        );

        if (!visible) {
            String message = state == null
                    ? view.getContext().getString(
                    R.string.admin_users_verifying
            )
                    : state.denied
                      ? state.notice
                      : !state.authorized
                        ? view.getContext().getString(
                    R.string.admin_users_verifying
            )
                        : state.loaded
                          ? view.getContext().getString(
                    R.string.admin_user_profile_missing
            )
                          : view.getContext().getString(
                    R.string.admin_users_not_synced
            );

            empty.setText(message);
            empty.setVisibility(View.VISIBLE);

            text(
                    view,
                    R.id.adminUserProfilePostsTitle,
                    view.getContext().getString(
                            R.string.admin_user_profile_posts
                    )
            );

            return;
        }

        text(
                view,
                R.id.adminUserProfileName,
                user.fullName
        );

        text(
                view,
                R.id.adminUserProfileStatus,
                user.statusLabel()
        );

        view.findViewById(R.id.adminUserProfileStatus)
                .setActivated(user.active());

        String count = state.postDetailsLoaded
                ? String.valueOf(posts.size())
                : user.postCount < 0
                  ? view.getContext().getString(
                R.string.admin_user_count_unknown
        )
                  : String.valueOf(user.postCount);

        text(
                view,
                R.id.adminUserProfileInfo,
                view.getContext().getString(
                        R.string.admin_user_profile_info,
                        user.accountId,
                        provided(user.username),
                        provided(user.email),
                        provided(user.phone),
                        count
                )
        );

        text(
                view,
                R.id.adminUserProfilePostsTitle,
                view.getContext().getString(
                        R.string.admin_user_profile_posts_count,
                        count
                )
        );

        Button status = view.findViewById(
                R.id.adminUserProfileStatusButton
        );

        status.setText(
                state.busy
                        ? R.string.admin_user_saving
                        : user.active()
                          ? R.string.admin_user_lock
                          : R.string.admin_user_unlock
        );

        status.setEnabled(
                state.writable && user.canChangeStatus()
        );

        status.setActivated(user.active());
        status.setOnClickListener(v -> changeStatus.accept(user));

        ImageView avatar = view.findViewById(
                R.id.adminUserProfileAvatar
        );

        Glide.with(view)
                .load(
                        user.avatarUrl.isEmpty()
                                ? null
                                : user.avatarUrl
                )
                .placeholder(R.drawable.ic_profile)
                .error(R.drawable.ic_profile)
                .circleCrop()
                .into(avatar);

        empty.setVisibility(
                posts.isEmpty() ? View.VISIBLE : View.GONE
        );

        empty.setText(
                state.postDetailsLoaded
                        ? R.string.admin_user_profile_no_posts
                        : R.string.admin_user_profile_posts_loading
        );
    }

    private static String provided(String value) {
        return value.isEmpty() ? "Chưa cập nhật" : value;
    }

    private static void text(View root, int id, String value) {
        ((TextView) root.findViewById(id)).setText(value);
    }

    @Override
    public int getItemCount() {
        return posts.size() + 1;
    }

    static final class Holder extends RecyclerView.ViewHolder {
        Holder(View view) {
            super(view);
        }
    }
}
