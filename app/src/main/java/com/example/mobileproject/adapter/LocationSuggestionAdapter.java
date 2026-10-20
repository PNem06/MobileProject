
package com.example.mobileproject.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mobileproject.R;

import java.util.List;

public class LocationSuggestionAdapter
        extends RecyclerView.Adapter<LocationSuggestionAdapter.LocationViewHolder> {

    public interface OnLocationClickListener {
        void onLocationClick(String address);
    }

    private final List<String> locationList;
    private final OnLocationClickListener listener;

    public LocationSuggestionAdapter(
            List<String> locationList,
            OnLocationClickListener listener) {

        this.locationList = locationList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public LocationViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent, int viewType) {

        View view = LayoutInflater.from(parent.getContext())
                .inflate(
                        R.layout.item_location_suggestion,
                        parent,
                        false
                );

        return new LocationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull LocationViewHolder holder, int position) {

        String address = locationList.get(position);
        holder.txtLocationName.setText(address);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onLocationClick(address);
            }
        });
    }

    @Override
    public int getItemCount() {
        return locationList.size();
    }

    static class LocationViewHolder extends RecyclerView.ViewHolder {

        TextView txtLocationName;

        public LocationViewHolder(@NonNull View itemView) {
            super(itemView);
            txtLocationName = itemView.findViewById(
                    R.id.txtLocationName
            );
        }
    }
}
