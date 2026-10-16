package com.example.mobileproject.model;

import java.util.Calendar;

public class CalendarDay {
    private Calendar calendar;
    private int dayNumber;
    private boolean isCurrentMonth;

    public CalendarDay(Calendar calendar, int dayNumber, boolean isCurrentMonth) {
        this.calendar = calendar;
        this.dayNumber = dayNumber;
        this.isCurrentMonth = isCurrentMonth;
    }

    public Calendar getCalendar() {
        return calendar;
    }

    public void setCalendar(Calendar calendar) {
        this.calendar = calendar;
    }

    public int getDayNumber() {
        return dayNumber;
    }

    public void setDayNumber(int dayNumber) {
        this.dayNumber = dayNumber;
    }

    public boolean isCurrentMonth() {
        return isCurrentMonth;
    }

    public void setCurrentMonth(boolean currentMonth) {
        isCurrentMonth = currentMonth;
    }
}
