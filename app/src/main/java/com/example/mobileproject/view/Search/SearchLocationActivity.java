package com.example.mobileproject.view.Search;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mobileproject.R;
import com.example.mobileproject.adapter.RecentSearchAdapter;
import com.example.mobileproject.model.RecentSearch;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SearchLocationActivity extends AppCompatActivity {

    private TextView txtCancel, txtSuggestionTitle;
    private ImageView btnNext;
    private EditText edtSearchLocation;
    private RecyclerView recyclerRecentSearch, recyclerLocationSuggestions;
    private RecentSearchAdapter recentAdapter, suggestionAdapter;
    private final List<RecentSearch> recentSearchList = new ArrayList<>();
    private final List<RecentSearch> allLocationList = new ArrayList<>();
    private final List<RecentSearch> suggestionList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search_location);

        initViews();
        setupLocationData();
        setupEvents();

        String initialLocation = getIntent().getStringExtra("INITIAL_QUERY");
        if (initialLocation != null && !initialLocation.trim().isEmpty()) {
            edtSearchLocation.setText(initialLocation);
            edtSearchLocation.setSelection(edtSearchLocation.length());
        }
    }

    private void initViews() {
        txtCancel = findViewById(R.id.txtCancel);
        btnNext = findViewById(R.id.btnNext);
        edtSearchLocation = findViewById(R.id.edtSearchLocation);
        recyclerRecentSearch = findViewById(R.id.recyclerRecentSearch);
        recyclerLocationSuggestions = findViewById(R.id.recyclerLocationSuggestions);
        txtSuggestionTitle = findViewById(R.id.txtSuggestionTitle);
    }

    private void setupLocationData() {
        recentSearchList.add(new RecentSearch("Bà Rịa, Vũng Tàu", "Tìm kiếm gần đây"));
        recentSearchList.add(new RecentSearch("Quận 3, Thành phố Hồ Chí Minh", "Tìm kiếm gần đây"));
        recentSearchList.add(new RecentSearch("Bình Chánh, Thành phố Hồ Chí Minh", "Tìm kiếm gần đây"));
        recentSearchList.add(new RecentSearch("Quy Nhơn", "Tìm kiếm gần đây"));

        allLocationList.addAll(recentSearchList);
        allLocationList.add(new RecentSearch("Quận 1, Thành phố Hồ Chí Minh", "Thành phố Hồ Chí Minh"));
        allLocationList.add(new RecentSearch("Thủ Đức, Thành phố Hồ Chí Minh", "Thành phố Hồ Chí Minh"));
        allLocationList.add(new RecentSearch("Đà Lạt, Lâm Đồng", "Lâm Đồng"));
        allLocationList.add(new RecentSearch("Đà Nẵng", "Việt Nam"));
        allLocationList.add(new RecentSearch("Hà Nội", "Việt Nam"));

        recentAdapter = new RecentSearchAdapter(recentSearchList, item -> selectLocation(item.getLocationName()));
        recyclerRecentSearch.setLayoutManager(new LinearLayoutManager(this));
        recyclerRecentSearch.setAdapter(recentAdapter);

        suggestionAdapter = new RecentSearchAdapter(suggestionList, item -> selectLocation(item.getLocationName()));
        recyclerLocationSuggestions.setLayoutManager(new LinearLayoutManager(this));
        recyclerLocationSuggestions.setAdapter(suggestionAdapter);
    }

    private void setupEvents() {
        txtCancel.setOnClickListener(v -> finish());
        btnNext.setOnClickListener(v -> submitLocation());

        edtSearchLocation.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim().toLowerCase(new Locale("vi", "VN"));
                if (query.isEmpty()) {
                    txtSuggestionTitle.setVisibility(View.GONE);
                    recyclerLocationSuggestions.setVisibility(View.GONE);
                    recyclerRecentSearch.setVisibility(View.VISIBLE);
                    return;
                }
                suggestionList.clear();
                for (RecentSearch item : allLocationList) {
                    if (item.getLocationName().toLowerCase(new Locale("vi", "VN")).contains(query)) {
                        boolean exists = false;
                        for (RecentSearch current : suggestionList) {
                            if (current.getLocationName().equals(item.getLocationName())) exists = true;
                        }
                        if (!exists) suggestionList.add(item);
                    }
                }
                suggestionAdapter.notifyDataSetChanged();
                txtSuggestionTitle.setText("Địa điểm phù hợp");
                txtSuggestionTitle.setVisibility(View.VISIBLE);
                recyclerLocationSuggestions.setVisibility(View.VISIBLE);
                recyclerRecentSearch.setVisibility(View.GONE);
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        edtSearchLocation.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                submitLocation();
                return true;
            }
            return false;
        });
    }

    private void submitLocation() {
        String location = edtSearchLocation.getText().toString().trim();
        if (location.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập nơi bạn muốn đến", Toast.LENGTH_SHORT).show();
            return;
        }
        selectLocation(location);
    }

    private void selectLocation(String location) {
        Intent intent = new Intent(SearchLocationActivity.this, SearchTimeActivity.class);
        intent.putExtra("SELECTED_LOCATION", location);
        startActivity(intent);
    }
}
