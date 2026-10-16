package com.example.mobileproject.view.Search;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mobileproject.R;
import com.example.mobileproject.model.CalendarDay;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class SearchTimeActivity extends AppCompatActivity {

    // Top Bar & Tabs
    private TextView txtCancelTime;
    private ImageView btnNextTime;
    private LinearLayout tabCustom, tabFlexible;
    private TextView txtTabCustom, txtTabFlexible;
    private View indicatorCustom, indicatorFlexible;

    // Tab Containers
    private LinearLayout layoutCustomContent;
    private ScrollView layoutFlexibleContent;

    // Tab 1: Tự chọn - Chips
    private TextView chipExact, chip1Day, chip3Days, chip7Days;
    private String selectedTolerance = "Chính xác";

    // Tab 1: Calendar
    private ImageView btnPrevMonth, btnNextMonth;
    private TextView txtMonthYear;
    private RecyclerView recyclerCalendar;
    private CalendarAdapter calendarAdapter;
    private List<CalendarDay> calendarDaysList;
    private Calendar currentCalendar;
    private Calendar startDate;
    private Calendar endDate;
    private TextView txtStartDate, txtEndDate;

    // Tab 2: Linh hoạt - Duration
    private TextView chipFlex1Week, chipFlex2Weeks, chipFlex1Month;
    private String selectedFlexibleDuration = "1 Tuần";

    // Tab 2: Linh hoạt - Months
    private LinearLayout cardMonth1, cardMonth2, cardMonth3;
    private TextView txtMonthTitle1, txtYearTitle1;
    private TextView txtMonthTitle2, txtYearTitle2;
    private TextView txtMonthTitle3, txtYearTitle3;
    private String selectedFlexibleMonth = "Tháng 10 2026";

    private boolean isCustomTabActive = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search_time);

        initViews();
        setupTabs();
        setupCustomTabCalendar();
        setupCustomTabChips();
        setupFlexibleTab();
        setupActions();
    }

    private void initViews() {
        txtCancelTime = findViewById(R.id.txtCancelTime);
        btnNextTime = findViewById(R.id.btnNextTime);

        tabCustom = findViewById(R.id.tabCustom);
        tabFlexible = findViewById(R.id.tabFlexible);
        txtTabCustom = findViewById(R.id.txtTabCustom);
        txtTabFlexible = findViewById(R.id.txtTabFlexible);
        indicatorCustom = findViewById(R.id.indicatorCustom);
        indicatorFlexible = findViewById(R.id.indicatorFlexible);

        layoutCustomContent = findViewById(R.id.layoutCustomContent);
        layoutFlexibleContent = findViewById(R.id.layoutFlexibleContent);

        // Custom tab views
        chipExact = findViewById(R.id.chipExact);
        chip1Day = findViewById(R.id.chip1Day);
        chip3Days = findViewById(R.id.chip3Days);
        chip7Days = findViewById(R.id.chip7Days);

        btnPrevMonth = findViewById(R.id.btnPrevMonth);
        btnNextMonth = findViewById(R.id.btnNextMonth);
        txtMonthYear = findViewById(R.id.txtMonthYear);
        recyclerCalendar = findViewById(R.id.recyclerCalendar);
        txtStartDate = findViewById(R.id.txtStartDate);
        txtEndDate = findViewById(R.id.txtEndDate);

        // Flexible tab views
        chipFlex1Week = findViewById(R.id.chipFlex1Week);
        chipFlex2Weeks = findViewById(R.id.chipFlex2Weeks);
        chipFlex1Month = findViewById(R.id.chipFlex1Month);

        cardMonth1 = findViewById(R.id.cardMonth1);
        cardMonth2 = findViewById(R.id.cardMonth2);
        cardMonth3 = findViewById(R.id.cardMonth3);

        txtMonthTitle1 = findViewById(R.id.txtMonthTitle1);
        txtYearTitle1 = findViewById(R.id.txtYearTitle1);
        txtMonthTitle2 = findViewById(R.id.txtMonthTitle2);
        txtYearTitle2 = findViewById(R.id.txtYearTitle2);
        txtMonthTitle3 = findViewById(R.id.txtMonthTitle3);
        txtYearTitle3 = findViewById(R.id.txtYearTitle3);
    }

    private void setupTabs() {
        tabCustom.setOnClickListener(v -> selectTab(true));
        tabFlexible.setOnClickListener(v -> selectTab(false));
    }

    private void selectTab(boolean isCustom) {
        isCustomTabActive = isCustom;
        if (isCustom) {
            txtTabCustom.setTextColor(ContextCompat.getColor(this, android.R.color.black));
            txtTabCustom.setTypeface(null, android.graphics.Typeface.BOLD);
            indicatorCustom.setVisibility(View.VISIBLE);

            txtTabFlexible.setTextColor(android.graphics.Color.parseColor("#888888"));
            txtTabFlexible.setTypeface(null, android.graphics.Typeface.NORMAL);
            indicatorFlexible.setVisibility(View.INVISIBLE);

            layoutCustomContent.setVisibility(View.VISIBLE);
            layoutFlexibleContent.setVisibility(View.GONE);
        } else {
            txtTabFlexible.setTextColor(ContextCompat.getColor(this, android.R.color.black));
            txtTabFlexible.setTypeface(null, android.graphics.Typeface.BOLD);
            indicatorFlexible.setVisibility(View.VISIBLE);

            txtTabCustom.setTextColor(android.graphics.Color.parseColor("#888888"));
            txtTabCustom.setTypeface(null, android.graphics.Typeface.NORMAL);
            indicatorCustom.setVisibility(View.INVISIBLE);

            layoutCustomContent.setVisibility(View.GONE);
            layoutFlexibleContent.setVisibility(View.VISIBLE);
        }
    }

    private void setupCustomTabChips() {
        View.OnClickListener chipClickListener = v -> {
            resetCustomChips();
            TextView selected = (TextView) v;
            selected.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_chip_selected_dark));
            selected.setTextColor(android.graphics.Color.WHITE);
            selected.setTypeface(null, android.graphics.Typeface.BOLD);
            selectedTolerance = selected.getText().toString();
        };

        chipExact.setOnClickListener(chipClickListener);
        chip1Day.setOnClickListener(chipClickListener);
        chip3Days.setOnClickListener(chipClickListener);
        chip7Days.setOnClickListener(chipClickListener);
    }

    private void resetCustomChips() {
        TextView[] chips = {chipExact, chip1Day, chip3Days, chip7Days};
        for (TextView chip : chips) {
            chip.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_chip_unselected_white));
            chip.setTextColor(android.graphics.Color.parseColor("#111111"));
            chip.setTypeface(null, android.graphics.Typeface.NORMAL);
        }
    }

    private void setupCustomTabCalendar() {
        // Default set calendar to October 2026 as per screenshot
        currentCalendar = Calendar.getInstance();
        currentCalendar.set(Calendar.YEAR, 2026);
        currentCalendar.set(Calendar.MONTH, Calendar.OCTOBER);
        currentCalendar.set(Calendar.DAY_OF_MONTH, 1);

        // Default selected range: 23/10/2026 to 25/10/2026
        startDate = (Calendar) currentCalendar.clone();
        startDate.set(Calendar.DAY_OF_MONTH, 23);

        endDate = (Calendar) currentCalendar.clone();
        endDate.set(Calendar.DAY_OF_MONTH, 25);

        calendarDaysList = new ArrayList<>();
        calendarAdapter = new CalendarAdapter(calendarDaysList, calendarDay -> {
            handleDateSelection(calendarDay.getCalendar());
        });

        recyclerCalendar.setLayoutManager(new GridLayoutManager(this, 7));
        recyclerCalendar.setAdapter(calendarAdapter);

        btnPrevMonth.setOnClickListener(v -> {
            currentCalendar.add(Calendar.MONTH, -1);
            renderCalendar();
        });

        btnNextMonth.setOnClickListener(v -> {
            currentCalendar.add(Calendar.MONTH, 1);
            renderCalendar();
        });

        renderCalendar();
        updateSummaryText();
    }

    private void renderCalendar() {
        calendarDaysList.clear();

        // Title format e.g. "Tháng 10, 2026"
        int month = currentCalendar.get(Calendar.MONTH) + 1;
        int year = currentCalendar.get(Calendar.YEAR);
        txtMonthYear.setText(String.format(Locale.getDefault(), "Tháng %d, %d", month, year));

        Calendar tempCal = (Calendar) currentCalendar.clone();
        tempCal.set(Calendar.DAY_OF_MONTH, 1);

        // Day of week for 1st day (Monday = 2 in Java Calendar)
        int firstDayOfWeek = tempCal.get(Calendar.DAY_OF_WEEK);
        int daysBefore = (firstDayOfWeek == Calendar.SUNDAY) ? 6 : (firstDayOfWeek - Calendar.MONDAY);

        // Previous month days
        Calendar prevMonthCal = (Calendar) tempCal.clone();
        prevMonthCal.add(Calendar.MONTH, -1);
        int maxDaysPrevMonth = prevMonthCal.getActualMaximum(Calendar.DAY_OF_MONTH);

        for (int i = daysBefore - 1; i >= 0; i--) {
            Calendar dayCal = (Calendar) prevMonthCal.clone();
            int dayNum = maxDaysPrevMonth - i;
            dayCal.set(Calendar.DAY_OF_MONTH, dayNum);
            calendarDaysList.add(new CalendarDay(dayCal, dayNum, false));
        }

        // Current month days
        int daysInMonth = tempCal.getActualMaximum(Calendar.DAY_OF_MONTH);
        for (int i = 1; i <= daysInMonth; i++) {
            Calendar dayCal = (Calendar) tempCal.clone();
            dayCal.set(Calendar.DAY_OF_MONTH, i);
            calendarDaysList.add(new CalendarDay(dayCal, i, true));
        }

        // Next month days to pad to 35 or 42 cells
        int totalCells = (calendarDaysList.size() > 35) ? 42 : 35;
        int nextMonthDays = totalCells - calendarDaysList.size();
        Calendar nextMonthCal = (Calendar) tempCal.clone();
        nextMonthCal.add(Calendar.MONTH, 1);

        for (int i = 1; i <= nextMonthDays; i++) {
            Calendar dayCal = (Calendar) nextMonthCal.clone();
            dayCal.set(Calendar.DAY_OF_MONTH, i);
            calendarDaysList.add(new CalendarDay(dayCal, i, false));
        }

        calendarAdapter.setDateRange(startDate, endDate);
    }

    private void handleDateSelection(Calendar selectedCal) {
        if (selectedCal == null) return;

        if (startDate == null || (startDate != null && endDate != null)) {
            startDate = (Calendar) selectedCal.clone();
            endDate = null;
        } else if (startDate != null && endDate == null) {
            if (selectedCal.before(startDate)) {
                startDate = (Calendar) selectedCal.clone();
            } else if (selectedCal.equals(startDate)) {
                endDate = null;
            } else {
                endDate = (Calendar) selectedCal.clone();
            }
        }

        calendarAdapter.setDateRange(startDate, endDate);
        updateSummaryText();
    }

    private void updateSummaryText() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd / MM / yyyy", Locale.getDefault());
        if (startDate != null) {
            txtStartDate.setText("Ngày bắt đầu :    " + sdf.format(startDate.getTime()));
        } else {
            txtStartDate.setText("Ngày bắt đầu :    --");
        }

        if (endDate != null) {
            txtEndDate.setText("Ngày kết thúc :    " + sdf.format(endDate.getTime()));
        } else {
            txtEndDate.setText("Ngày kết thúc :    --");
        }
    }

    private void setupFlexibleTab() {
        // Duration selection
        View.OnClickListener flexDurationListener = v -> {
            resetFlexibleDurationChips();
            TextView selected = (TextView) v;
            selected.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_chip_selected_dark));
            selected.setTextColor(android.graphics.Color.WHITE);
            selected.setTypeface(null, android.graphics.Typeface.BOLD);
            selectedFlexibleDuration = selected.getText().toString();
        };

        chipFlex1Week.setOnClickListener(flexDurationListener);
        chipFlex2Weeks.setOnClickListener(flexDurationListener);
        chipFlex1Month.setOnClickListener(flexDurationListener);

        // Month selection
        View.OnClickListener flexMonthListener = v -> {
            resetFlexibleMonthCards();
            LinearLayout selectedCard = (LinearLayout) v;
            selectedCard.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_month_card_selected));

            TextView txtTitle = (TextView) selectedCard.getChildAt(0);
            TextView txtYear = (TextView) selectedCard.getChildAt(1);
            txtTitle.setTextColor(android.graphics.Color.WHITE);
            txtYear.setTextColor(android.graphics.Color.WHITE);

            selectedFlexibleMonth = txtTitle.getText().toString() + " " + txtYear.getText().toString();
        };

        cardMonth1.setOnClickListener(flexMonthListener);
        cardMonth2.setOnClickListener(flexMonthListener);
        cardMonth3.setOnClickListener(flexMonthListener);
    }

    private void resetFlexibleDurationChips() {
        TextView[] chips = {chipFlex1Week, chipFlex2Weeks, chipFlex1Month};
        for (TextView chip : chips) {
            chip.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_chip_unselected_white));
            chip.setTextColor(android.graphics.Color.parseColor("#111111"));
            chip.setTypeface(null, android.graphics.Typeface.NORMAL);
        }
    }

    private void resetFlexibleMonthCards() {
        LinearLayout[] cards = {cardMonth1, cardMonth2, cardMonth3};
        for (LinearLayout card : cards) {
            card.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_month_card_unselected));
            TextView txtTitle = (TextView) card.getChildAt(0);
            TextView txtYear = (TextView) card.getChildAt(1);
            txtTitle.setTextColor(android.graphics.Color.parseColor("#111111"));
            txtYear.setTextColor(android.graphics.Color.parseColor("#555555"));
        }
    }

    private void setupActions() {
        txtCancelTime.setOnClickListener(v -> finish());

        btnNextTime.setOnClickListener(v -> {
            Intent resultIntent = new Intent();
            if (isCustomTabActive) {
                if (startDate == null) {
                    Toast.makeText(this, "Vui lòng chọn ngày bắt đầu", Toast.LENGTH_SHORT).show();
                    return;
                }
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                resultIntent.putExtra("TIME_MODE", "CUSTOM");
                resultIntent.putExtra("START_DATE", sdf.format(startDate.getTime()));
                if (endDate != null) {
                    resultIntent.putExtra("END_DATE", sdf.format(endDate.getTime()));
                }
                resultIntent.putExtra("TOLERANCE", selectedTolerance);
            } else {
                resultIntent.putExtra("TIME_MODE", "FLEXIBLE");
                resultIntent.putExtra("FLEX_DURATION", selectedFlexibleDuration);
                resultIntent.putExtra("FLEX_MONTH", selectedFlexibleMonth);
            }
            setResult(RESULT_OK, resultIntent);
            finish();
        });
    }
}
