package com.example.mobileproject.view.Search;

import android.content.Intent;
import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mobileproject.R;
import com.example.mobileproject.model.RecentSearch;

import java.util.ArrayList;
import java.util.List;

public class SearchLocationActivity extends AppCompatActivity {

    private TextView txtCancel;
    private ImageView btnNext;
    private EditText edtSearchLocation;
    private RecyclerView recyclerRecentSearch;
    private RecentSearchAdapter adapter;
    private List<RecentSearch> recentSearchList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search_location);

        initViews();
        setupRecentSearchList();
        setupEvents();
    }

    private void initViews() {
        txtCancel = findViewById(R.id.txtCancel);
        btnNext = findViewById(R.id.btnNext);
        edtSearchLocation = findViewById(R.id.edtSearchLocation);
        recyclerRecentSearch = findViewById(R.id.recyclerRecentSearch);
    }

    private void setupRecentSearchList() {
        recentSearchList = new ArrayList<>();
        // Sample recent searches matching the design
        recentSearchList.add(new RecentSearch("Bà Rịa, Vũng Tàu", "1 Trưởng thành"));
        recentSearchList.add(new RecentSearch("Quận 3, Thành phố Hồ Chí Minh", "28-29 Th10, 1 Trưởng thành, 1 Trẻ em"));
        recentSearchList.add(new RecentSearch("Bình Chánh, Thành phố Hồ Chí Minh", "Th11, 2 Trưởng thành"));
        recentSearchList.add(new RecentSearch("Quy Nhơn", "1 Trưởng thành, 1 Trẻ em"));

        adapter = new RecentSearchAdapter(recentSearchList, item -> {
            edtSearchLocation.setText(item.getLocationName());
            edtSearchLocation.setSelection(item.getLocationName().length());
            performSearch(item.getLocationName());
        });

        recyclerRecentSearch.setLayoutManager(new LinearLayoutManager(this));
        recyclerRecentSearch.setAdapter(adapter);
    }

    private void setupEvents() {
        txtCancel.setOnClickListener(v -> finish());

        btnNext.setOnClickListener(v -> {
            String query = edtSearchLocation.getText().toString().trim();
            if (!query.isEmpty()) {
                performSearch(query);
            } else {
                Toast.makeText(this, "Vui lòng nhập nơi bạn muốn đến", Toast.LENGTH_SHORT).show();
            }
        });

        edtSearchLocation.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                String query = edtSearchLocation.getText().toString().trim();
                if (!query.isEmpty()) {
                    performSearch(query);
                }
                return true;
            }
            return false;
        });
    }

    private void performSearch(String location) {
        Intent resultIntent = new Intent();
        resultIntent.putExtra("SELECTED_LOCATION", location);
        setResult(RESULT_OK, resultIntent);
        finish();
    }
}
