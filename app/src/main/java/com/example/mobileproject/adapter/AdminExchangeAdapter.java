package com.example.mobileproject.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mobileproject.R;
import com.example.mobileproject.data.ExchangeCacheEntity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AdminExchangeAdapter extends RecyclerView.Adapter<AdminExchangeAdapter.ExchangeHolder> {
    public interface Listener {
        void onDetail(ExchangeCacheEntity item);
        void onAcceptCancellation(ExchangeCacheEntity item);
        void onRejectCancellation(ExchangeCacheEntity item);
    }
    private final Listener listener;
    private final List<ExchangeCacheEntity> items = new ArrayList<>();
    public AdminExchangeAdapter(Listener listener) {
        this.listener = listener;
    }
    public void setItems(List<ExchangeCacheEntity> data) {
        items.clear();
        if (data != null) {
            items.addAll(data);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ExchangeHolder onCreateViewHolder(
            @NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_exchange, parent, false);
        return new ExchangeHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull ExchangeHolder holder, int position) {
        ExchangeCacheEntity item = items.get(position);
        holder.id.setText("Trao đổi " + String.format(Locale.getDefault(), "%02d", position + 1));
        holder.status.setText(safe(item.status, "Chưa rõ trạng thái"));
        holder.users.setText(
                "Người yêu cầu: " + safe(item.requesterUsername, safe(item.requesterId, "N/A"))
                        + "\nChủ bài đăng: " + safe(item.hostUsername, safe(item.hostId, "N/A")));
        holder.dates.setText(
                "Thời gian: " + format(item.startDate)
                        + " – " + format(item.endDate));
        holder.payment.setText(
                "Thanh toán: "
                        + safe(item.paymentStatus, "CHƯA CÓ DỮ LIỆU")
                        + (item.paymentAmount > 0
                        ? " • Số tiền: "
                          + String.format(
                        Locale.getDefault(),
                        "%,.0f",
                        item.paymentAmount) : ""));

        boolean pendingCancel = "PENDING".equalsIgnoreCase(item.cancelRequestStatus)
                && "PAID".equalsIgnoreCase(item.paymentStatus);

        holder.cancellationActions.setVisibility(View.VISIBLE);

        holder.acceptCancellation.setEnabled(pendingCancel);
        holder.rejectCancellation.setEnabled(pendingCancel);

        holder.acceptCancellation.setAlpha(pendingCancel ? 1.0f : 0.4f);
        holder.rejectCancellation.setAlpha(pendingCancel ? 1.0f : 0.4f);

        if (pendingCancel) {
            holder.cancel.setText("ĐANG CHỜ DUYỆT HỦY • " + safe(item.cancelReason, "Không có lý do"));
            holder.cancel.setTextColor(0xFF9C2739);
        } else {
            holder.cancel.setText("Yêu cầu hủy: " + safe(item.cancelRequestStatus, "Không có yêu cầu"));
            holder.cancel.setTextColor(0xFF686B75);
        }

        holder.detail.setText(pendingCancel
                ? "Xem chi tiết / duyệt hủy"
                : "Xem chi tiết");
        holder.detail.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDetail(item);
            }});
        holder.acceptCancellation.setOnClickListener(v -> {
            if (listener != null && pendingCancel) {
                listener.onAcceptCancellation(item);
            }});

        holder.rejectCancellation.setOnClickListener(v -> {
            if (listener != null && pendingCancel) {
                listener.onRejectCancellation(item);
            }});
    }
    private String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }
    private String format(long millis) {
        if (millis <= 0) {return "Chưa cập nhật";}
        return new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date(millis));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }
    static class ExchangeHolder extends RecyclerView.ViewHolder {
        TextView id, status, users, dates, payment, cancel;
        Button detail, acceptCancellation, rejectCancellation;
        LinearLayout cancellationActions;
        ExchangeHolder(@NonNull View view) {
            super(view);
            id = view.findViewById(R.id.tvExchangeId);
            status = view.findViewById(R.id.tvExchangeStatus);
            users = view.findViewById(R.id.tvExchangeUsers);
            dates = view.findViewById(R.id.tvExchangeDates);
            payment = view.findViewById(R.id.tvExchangePayment);
            cancel = view.findViewById(R.id.tvExchangeCancel);
            detail = view.findViewById(R.id.btnExchangeDetail);
            cancellationActions = view.findViewById(R.id.layoutCancellationActions);
            acceptCancellation = view.findViewById(R.id.btnAcceptCancellation);
            rejectCancellation = view.findViewById(R.id.btnRejectCancellation);
        }
    }
}