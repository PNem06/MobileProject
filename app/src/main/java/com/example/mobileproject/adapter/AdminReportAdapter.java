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
import com.example.mobileproject.data.ReportCacheEntity;

import java.util.ArrayList;
import java.util.List;

public class AdminReportAdapter extends RecyclerView.Adapter<AdminReportAdapter.ReportHolder> {
    public interface Listener {
        void onResolve(ReportCacheEntity.AdminReportCacheEntity item);
        void onReject(ReportCacheEntity.AdminReportCacheEntity item);
    }
    private final Listener listener;
    private final List<ReportCacheEntity.AdminReportCacheEntity> items = new ArrayList<>();
    public AdminReportAdapter(Listener listener) { this.listener = listener; }
    public void setItems(List<ReportCacheEntity.AdminReportCacheEntity> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public ReportHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_report, parent, false);
        return new ReportHolder(view);
    }

    @Override public void onBindViewHolder(@NonNull ReportHolder holder, int position) {
        ReportCacheEntity.AdminReportCacheEntity item = items.get(position);
        String type = item.targetType == null ? "" : item.targetType.toUpperCase();
        String typeLabel = ("ACCOUNT".equals(type) || "USER".equals(type)) ? "Tài khoản" : "Bài đăng";
        holder.target.setText(typeLabel + ": " + (item.targetName == null || item.targetName.trim().isEmpty() ? item.targetId : item.targetName));
        holder.id.setText("Report ID: " + item.reportId + "  •  Đối tượng: " + item.targetId);
        holder.reason.setText("Lý do: " + safe(item.reason, "Chưa cung cấp"));
        holder.description.setText("Mô tả: " + safe(item.description, "Không có mô tả"));
        holder.reporter.setText("Người báo cáo: " + safe(item.reporterId, "Không rõ"));
        holder.status.setText(safe(item.status, "PENDING"));
        boolean canAct = "PENDING".equalsIgnoreCase(item.status) || "REVIEWING".equalsIgnoreCase(item.status);
        holder.actions.setVisibility(canAct ? View.VISIBLE : View.GONE);
        holder.resolve.setText(("ACCOUNT".equals(type) || "USER".equals(type)) ? "Xác nhận & khóa" : "Xác nhận & ẩn bài");
        holder.resolve.setOnClickListener(v -> listener.onResolve(item));
        holder.reject.setOnClickListener(v -> listener.onReject(item));
    }
    private String safe(String value, String fallback) { return value == null || value.trim().isEmpty() ? fallback : value; }
    @Override public int getItemCount() { return items.size(); }
    static class ReportHolder extends RecyclerView.ViewHolder {
        TextView target, id, reason, description, reporter, status;
        LinearLayout actions;
        Button resolve, reject;
        ReportHolder(@NonNull View itemView) {
            super(itemView);
            target = itemView.findViewById(R.id.tvReportTarget);
            id = itemView.findViewById(R.id.tvReportId);
            reason = itemView.findViewById(R.id.tvReportReason);
            description = itemView.findViewById(R.id.tvReportDescription);
            reporter = itemView.findViewById(R.id.tvReportReporter);
            status = itemView.findViewById(R.id.tvReportStatus);
            actions = itemView.findViewById(R.id.layoutReportActions);
            resolve = itemView.findViewById(R.id.btnResolveReport);
            reject = itemView.findViewById(R.id.btnRejectReport);
        }
    }
}
