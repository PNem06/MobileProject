package com.example.mobileproject.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RatingBar;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mobileproject.R;
import com.example.mobileproject.model.ReviewItem;
import com.bumptech.glide.Glide;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class ReviewAdapter extends RecyclerView.Adapter<ReviewAdapter.Holder> {
    private final List<ReviewItem> items = new ArrayList<>();
    private final Map<String, Profile> profileCache = new HashMap<>();
    public void submit(List<ReviewItem> reviews) {
        items.clear();
        if (reviews != null) items.addAll(reviews);
        notifyDataSetChanged();
    }
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.review_card, parent, false));
    }
    @Override public void onBindViewHolder(@NonNull Holder h, int position) {
        ReviewItem item = items.get(position);
        h.name.setText(item.reviewerName);
        h.date.setText(item.date);
        h.rating.setRating(item.rating);
        h.comment.setText(item.comment.isEmpty() ? "Không có nhận xét." : item.comment);
        h.profile.setImageResource(R.drawable.ic_user);
        h.profile.setTag(item.reviewerAccountId);
        Profile cached = profileCache.get(item.reviewerAccountId);
        if (cached != null) showProfile(h, item.reviewerAccountId, cached);
        else if (!item.reviewerAccountId.isEmpty()) {
            FirebaseFirestore.getInstance().collection("accounts").document(item.reviewerAccountId)
                    .get().addOnSuccessListener(doc -> {
                        if (!doc.exists()) return;
                        String name = doc.getString("fullName");
                        if (name == null || name.trim().isEmpty()) name = doc.getString("username");
                        String avatar = doc.getString("avatarUrl");
                        Profile profile = new Profile(name, avatar);
                        profileCache.put(item.reviewerAccountId, profile);
                        showProfile(h, item.reviewerAccountId, profile);
                    });
        }
    }
    @Override public int getItemCount() { return items.size(); }
    static class Holder extends RecyclerView.ViewHolder {
        TextView name, date, comment; RatingBar rating; ImageView profile;
        Holder(@NonNull View v) { super(v); name=v.findViewById(R.id.txtReviewerName); date=v.findViewById(R.id.txtReviewDate); comment=v.findViewById(R.id.txtReviewComment); rating=v.findViewById(R.id.ratingReview); profile=v.findViewById(R.id.imgReviewerProfile); }
    }
    private void showProfile(Holder holder, String accountId, Profile profile) {
        if (holder.profile == null || !accountId.equals(holder.profile.getTag())) return;
        if (profile.name != null && !profile.name.trim().isEmpty()) holder.name.setText(profile.name);
        Glide.with(holder.profile).load(profile.avatarUrl).circleCrop()
                .placeholder(R.drawable.ic_user).error(R.drawable.ic_user).into(holder.profile);
    }
    private static class Profile {
        final String name, avatarUrl;
        Profile(String name, String avatarUrl) { this.name=name; this.avatarUrl=avatarUrl; }
    }
}
