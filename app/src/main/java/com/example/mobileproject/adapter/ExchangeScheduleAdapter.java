package com.example.mobileproject.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.mobileproject.R;
import com.example.mobileproject.util.PostImageUtils;
import com.example.mobileproject.view.ExchangeScheduleItem;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ExchangeScheduleAdapter
        extends RecyclerView.Adapter<ExchangeScheduleAdapter.Holder> {

    public interface OnItemClickListener {
        void onClick(ExchangeScheduleItem item);
    }

    private final List<ExchangeScheduleItem> items = new ArrayList<>();
    private final OnItemClickListener listener;
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("dd/MM/yyyy", new Locale("vi", "VN"));

    public ExchangeScheduleAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<ExchangeScheduleItem> next) {
        items.clear();
        if (next != null) items.addAll(next);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_exchange_schedule, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        ExchangeScheduleItem item = items.get(position);
        h.title.setText(item.title);
        h.host.setText("Chủ nhà: " + item.hostName);
        h.address.setText(item.address.isEmpty() ? "Chưa có địa chỉ" : item.address);

        String date;
        if (item.startDate != null && item.endDate != null) {
            date = dateFormat.format(item.startDate) + " - " + dateFormat.format(item.endDate);
        } else if (item.startDate != null) {
            date = "Từ " + dateFormat.format(item.startDate);
        } else if (item.endDate != null) {
            date = "Đến " + dateFormat.format(item.endDate);
        } else {
            date = "Thời gian chưa cập nhật";
        }

        if (item.guestCount > 0) {
            date += "  •  " + item.guestCount + " khách";
        }
        h.date.setText(date);

        if ("ONGOING".equals(item.status)) {
            h.status.setText("Đang diễn ra");
            h.status.setBackgroundResource(
                    R.drawable.bg_schedule_status_ongoing);
        } else if ("COMPLETED".equals(item.status)) {
            h.status.setText("Đã xong");
            h.status.setBackgroundResource(
                    R.drawable.bg_schedule_status_done);
        } else {
            h.status.setText("Sắp tới");
            h.status.setBackgroundResource(
                    R.drawable.bg_schedule_status_upcoming);
        }

        PostImageUtils.loadInto(h.image, item.imageUrl, R.drawable.bg_blank_canvas);

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(item);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView status, title, host, address, date;

        Holder(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.imgExchange);
            status = itemView.findViewById(R.id.txtExchangeStatus);
            title = itemView.findViewById(R.id.txtExchangeTitle);
            host = itemView.findViewById(R.id.txtExchangeHost);
            address = itemView.findViewById(R.id.txtExchangeAddress);
            date = itemView.findViewById(R.id.txtExchangeDate);
        }
    }
}