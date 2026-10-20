package com.example.mobileproject.adapter;

import android.graphics.Color;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mobileproject.R;
import com.example.mobileproject.model.CalendarDay;

import java.util.Calendar;
import java.util.List;

public class CalendarAdapter extends RecyclerView.Adapter<CalendarAdapter.ViewHolder> {

    public interface OnDateClickListener {
        void OnDateClick(CalendarDay calendarDay);
    }

    private final List<CalendarDay> daysList;
    private Calendar startDate;
    private Calendar endDate;
    private final OnDateClickListener listener;

    public CalendarAdapter(List<CalendarDay> daysList, OnDateClickListener listener) {
        this.daysList = daysList;
        this.listener = listener;
    }

    public void setDateRange(Calendar startDate, Calendar endDate) {
        this.startDate = startDate;
        this.endDate = endDate;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_calendar_day, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CalendarDay item = daysList.get(position);
        holder.txtDayNumber.setText(String.valueOf(item.getDayNumber()));

        if (!item.isCurrentMonth()) {
            holder.txtDayNumber.setTextColor(Color.parseColor("#D0D0D0"));
            holder.vSelectionBackground.setBackground(null);
            holder.txtDayNumber.setTypeface(null, Typeface.NORMAL);
            holder.itemView.setOnClickListener(null);
            return;
        }

        Calendar cellCal = item.getCalendar();

        if (startDate != null && endDate != null) {
            if (isSameDay(cellCal, startDate)) {
                holder.vSelectionBackground.setBackground(
                        ContextCompat.getDrawable(holder.itemView.getContext(), R.drawable.bg_cal_start));
                holder.txtDayNumber.setTextColor(Color.WHITE);
                holder.txtDayNumber.setTypeface(null, Typeface.BOLD);
            } else if (isSameDay(cellCal, endDate)) {
                holder.vSelectionBackground.setBackground(
                        ContextCompat.getDrawable(holder.itemView.getContext(), R.drawable.bg_cal_end));
                holder.txtDayNumber.setTextColor(Color.WHITE);
                holder.txtDayNumber.setTypeface(null, Typeface.BOLD);
            } else if (isBetween(cellCal, startDate, endDate)) {
                holder.vSelectionBackground.setBackground(
                        ContextCompat.getDrawable(holder.itemView.getContext(), R.drawable.bg_cal_middle));
                holder.txtDayNumber.setTextColor(Color.WHITE);
                holder.txtDayNumber.setTypeface(null, Typeface.NORMAL);
            } else {
                holder.vSelectionBackground.setBackground(null);
                holder.txtDayNumber.setTextColor(Color.parseColor("#333333"));
                holder.txtDayNumber.setTypeface(null, Typeface.NORMAL);
            }
        } else if (startDate != null) {
            if (isSameDay(cellCal, startDate)) {
                holder.vSelectionBackground.setBackground(
                        ContextCompat.getDrawable(holder.itemView.getContext(), R.drawable.bg_cal_single));
                holder.txtDayNumber.setTextColor(Color.WHITE);
                holder.txtDayNumber.setTypeface(null, Typeface.BOLD);
            } else {
                holder.vSelectionBackground.setBackground(null);
                holder.txtDayNumber.setTextColor(Color.parseColor("#333333"));
                holder.txtDayNumber.setTypeface(null, Typeface.NORMAL);
            }
        } else {
            holder.vSelectionBackground.setBackground(null);
            holder.txtDayNumber.setTextColor(Color.parseColor("#333333"));
            holder.txtDayNumber.setTypeface(null, Typeface.NORMAL);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.OnDateClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return daysList != null ? daysList.size() : 0;
    }

    private boolean isSameDay(Calendar cal1, Calendar cal2) {
        if (cal1 == null || cal2 == null) return false;
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH) &&
                cal1.get(Calendar.DAY_OF_MONTH) == cal2.get(Calendar.DAY_OF_MONTH);
    }

    private boolean isBetween(Calendar target, Calendar start, Calendar end) {
        if (target == null || start == null || end == null) return false;
        Calendar t = (Calendar) target.clone();
        Calendar s = (Calendar) start.clone();
        Calendar e = (Calendar) end.clone();
        resetTime(t);
        resetTime(s);
        resetTime(e);
        return t.after(s) && t.before(e);
    }

    private void resetTime(Calendar cal) {
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        View vSelectionBackground;
        TextView txtDayNumber;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            vSelectionBackground = itemView.findViewById(R.id.vSelectionBackground);
            txtDayNumber = itemView.findViewById(R.id.txtDayNumber);
        }
    }
}
