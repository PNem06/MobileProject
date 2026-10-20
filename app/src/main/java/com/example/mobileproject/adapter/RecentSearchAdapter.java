package com.example.mobileproject.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mobileproject.R;
import com.example.mobileproject.model.RecentSearch;

import java.util.List;

public class RecentSearchAdapter extends RecyclerView.Adapter<RecentSearchAdapter.ViewHolder> {

    public interface OnRecentSearchClickListener {
        void onRecentSearchClick(RecentSearch item);
    }

    private final List<RecentSearch> recentSearches;
    private final OnRecentSearchClickListener listener;

    public RecentSearchAdapter(List<RecentSearch> recentSearches, OnRecentSearchClickListener listener) {
        this.recentSearches = recentSearches;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recent_search, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RecentSearch item = recentSearches.get(position);
        holder.txtLocationName.setText(item.getLocationName());
        holder.txtSearchDetail.setText(item.getDetails());

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onRecentSearchClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return recentSearches != null ? recentSearches.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtLocationName;
        TextView txtSearchDetail;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            txtLocationName = itemView.findViewById(R.id.txtLocationName);
            txtSearchDetail = itemView.findViewById(R.id.txtSearchDetail);
        }
    }
}
