package com.example.mobileproject.view.Search;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mobileproject.R;
import com.example.mobileproject.adapter.RecentSearchAdapter;
import com.example.mobileproject.model.RecentSearch;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest;
import com.google.android.libraries.places.api.net.PlacesClient;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

public class SearchLocationActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "search_location_prefs";
    private static final String KEY_RECENT_LOCATIONS = "recent_locations";
    private static final int MAX_RECENT_LOCATIONS = 5;
    private static final long SEARCH_DELAY_MS = 350;

    private TextView txtCancel;
    private TextView txtSuggestionTitle;
    private ImageView btnNext;
    private EditText edtSearchLocation;
    private RecyclerView recyclerRecentSearch;
    private RecyclerView recyclerLocationSuggestions;

    private RecentSearchAdapter recentAdapter;
    private RecentSearchAdapter suggestionAdapter;

    private final List<RecentSearch> recentSearchList = new ArrayList<>();
    private final List<RecentSearch> suggestionList = new ArrayList<>();

    private PlacesClient placesClient;

    private final Handler searchHandler =
            new Handler(Looper.getMainLooper());

    private Runnable pendingSearch;
    private String lastRequestedQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search_location);

        initViews();
        initPlaces();
        setupLists();
        loadRecentSearches();
        setupEvents();

        // Nếu màn hình nhận địa điểm ban đầu từ màn hình khác.
        String initialLocation =
                getIntent().getStringExtra("INITIAL_QUERY");

        if (initialLocation != null
                && !initialLocation.trim().isEmpty()) {

            edtSearchLocation.setText(initialLocation);
            edtSearchLocation.setSelection(
                    edtSearchLocation.length());
        }
    }

    private void initViews() {
        txtCancel = findViewById(R.id.txtCancel);
        btnNext = findViewById(R.id.btnNext);
        edtSearchLocation = findViewById(R.id.edtSearchLocation);

        recyclerRecentSearch =
                findViewById(R.id.recyclerRecentSearch);

        recyclerLocationSuggestions =
                findViewById(R.id.recyclerLocationSuggestions);

        txtSuggestionTitle =
                findViewById(R.id.txtSuggestionTitle);
    }

    private void initPlaces() {
        try {
            ApplicationInfo appInfo =
                    getPackageManager().getApplicationInfo(
                            getPackageName(),
                            PackageManager.GET_META_DATA
                    );

            String apiKey = appInfo.metaData.getString(
                    "com.google.android.geo.API_KEY"
            );

            if (apiKey == null
                    || apiKey.trim().isEmpty()
                    || apiKey.equals("DEFAULT_API_KEY")
                    || apiKey.equals("PASTE_YOUR_API_KEY_HERE")) {

                Toast.makeText(
                        this,
                        "Bạn chưa cấu hình Google Maps API key",
                        Toast.LENGTH_LONG
                ).show();

                return;
            }

            if (!Places.isInitialized()) {
                Places.initializeWithNewPlacesApiEnabled(
                        getApplicationContext(),
                        apiKey
                );
            }

            placesClient = Places.createClient(this);

        } catch (PackageManager.NameNotFoundException e) {
            Toast.makeText(
                    this,
                    "Không đọc được cấu hình Google Places",
                    Toast.LENGTH_SHORT
            ).show();
        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "Không khởi tạo được Google Places: "
                            + e.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void setupLists() {
        recentAdapter = new RecentSearchAdapter(
                recentSearchList,
                item -> selectLocation(item.getLocationName())
        );

        recyclerRecentSearch.setLayoutManager(
                new LinearLayoutManager(this)
        );
        recyclerRecentSearch.setAdapter(recentAdapter);

        suggestionAdapter = new RecentSearchAdapter(
                suggestionList,
                item -> selectLocation(item.getLocationName())
        );

        recyclerLocationSuggestions.setLayoutManager(
                new LinearLayoutManager(this)
        );
        recyclerLocationSuggestions.setAdapter(suggestionAdapter);
    }

    private void setupEvents() {
        txtCancel.setOnClickListener(v -> finish());

        btnNext.setOnClickListener(v -> submitLocation());

        edtSearchLocation.addTextChangedListener(
                new TextWatcher() {

                    @Override
                    public void beforeTextChanged(
                            CharSequence s,
                            int start,
                            int count,
                            int after
                    ) {
                    }

                    @Override
                    public void onTextChanged(
                            CharSequence s,
                            int start,
                            int before,
                            int count
                    ) {
                        String query = s.toString().trim();

                        // Hủy yêu cầu tìm kiếm đang chờ.
                        if (pendingSearch != null) {
                            searchHandler.removeCallbacks(
                                    pendingSearch
                            );
                        }

                        if (query.isEmpty()) {
                            lastRequestedQuery = "";

                            suggestionList.clear();
                            suggestionAdapter.notifyDataSetChanged();

                            txtSuggestionTitle.setVisibility(
                                    View.GONE
                            );

                            recyclerLocationSuggestions.setVisibility(
                                    View.GONE
                            );

                            recyclerRecentSearch.setVisibility(
                                    View.VISIBLE
                            );

                            return;
                        }

                        recyclerRecentSearch.setVisibility(
                                View.GONE
                        );

                        txtSuggestionTitle.setText(
                                "Địa điểm gợi ý từ Google Maps"
                        );

                        txtSuggestionTitle.setVisibility(
                                View.VISIBLE
                        );

                        recyclerLocationSuggestions.setVisibility(
                                View.VISIBLE
                        );

                        // Chỉ tìm khi nhập ít nhất 2 ký tự.
                        if (query.length() < 2
                                || placesClient == null) {

                            suggestionList.clear();
                            suggestionAdapter.notifyDataSetChanged();
                            return;
                        }

                        // Chờ người dùng ngừng gõ trước khi gọi API.
                        pendingSearch = () ->
                                requestPlacePredictions(query);

                        searchHandler.postDelayed(
                                pendingSearch,
                                SEARCH_DELAY_MS
                        );
                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                    }
                }
        );

        edtSearchLocation.setOnEditorActionListener(
                (v, actionId, event) -> {
                    if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                        submitLocation();
                        return true;
                    }
                    return false;
                }
        );
    }

    private void requestPlacePredictions(String query) {
        lastRequestedQuery = query;
        FindAutocompletePredictionsRequest request =
                FindAutocompletePredictionsRequest.builder()
                        .setQuery(query)
                        .setRegionCode("VN")
                        .build();
        placesClient.findAutocompletePredictions(request)
                .addOnSuccessListener(response -> {
                    String currentQuery =
                            edtSearchLocation.getText()
                                    .toString()
                                    .trim();
                    if (!query.equals(currentQuery)
                            || !query.equals(lastRequestedQuery)) {
                        return;
                    }
                    suggestionList.clear();
                    for (AutocompletePrediction prediction :
                            response.getAutocompletePredictions()) {
                        String address = prediction
                                .getFullText(null)
                                .toString();

                        if (!address.trim().isEmpty()) {
                            suggestionList.add(
                                    new RecentSearch(
                                            address,
                                            "Google Maps"
                                    )
                            );
                        }
                    }

                    suggestionAdapter.notifyDataSetChanged();

                    if (suggestionList.isEmpty()) {
                        txtSuggestionTitle.setText(
                                "Không tìm thấy địa điểm phù hợp"
                        );
                    } else {
                        txtSuggestionTitle.setText(
                                "Địa điểm gợi ý từ Google Maps"
                        );
                    }
                })

                .addOnFailureListener(exception -> {
                    String currentQuery = edtSearchLocation.getText()
                            .toString()
                            .trim();

                    if (!query.equals(currentQuery)) {
                        return;
                    }

                    android.util.Log.e(
                            "GooglePlaces",
                            "Lỗi tìm địa điểm: " + query,
                            exception
                    );

                    suggestionList.clear();
                    suggestionAdapter.notifyDataSetChanged();

                    txtSuggestionTitle.setText(
                            "Không tải được gợi ý. Hãy kiểm tra API key và kết nối mạng."
                    );

                    txtSuggestionTitle.setVisibility(View.VISIBLE);
                    recyclerLocationSuggestions.setVisibility(View.VISIBLE);

                    Toast.makeText(
                            SearchLocationActivity.this,
                            "Lỗi Google Places: " + exception.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    private void submitLocation() {
        String location = edtSearchLocation.getText()
                .toString()
                .trim();

        if (location.isEmpty()) {
            Toast.makeText(
                    this,
                    "Vui lòng nhập nơi bạn muốn đến",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        selectLocation(location);
    }

    private void selectLocation(String location) {
        String cleanedLocation =
                location == null ? "" : location.trim();

        if (cleanedLocation.isEmpty()) {
            return;
        }

        // Lưu địa điểm vừa chọn vào lịch sử.
        saveRecentSearch(cleanedLocation);

        // Chuyển sang màn hình chọn thời gian.
        Intent intent = new Intent(
                SearchLocationActivity.this,
                SearchTimeActivity.class
        );

        intent.putExtra(
                "SELECTED_LOCATION",
                cleanedLocation
        );

        startActivity(intent);
    }

    private void loadRecentSearches() {
        recentSearchList.clear();

        SharedPreferences prefs = getSharedPreferences(
                PREFS_NAME,
                MODE_PRIVATE
        );

        String saved = prefs.getString(
                KEY_RECENT_LOCATIONS,
                "[]"
        );

        try {
            JSONArray array = new JSONArray(saved);

            for (int i = 0;
                 i < array.length()
                         && recentSearchList.size()
                         < MAX_RECENT_LOCATIONS;
                 i++) {

                String location = array.optString(i, "").trim();

                if (!location.isEmpty()) {
                    recentSearchList.add(
                            new RecentSearch(
                                    location,
                                    "Tìm kiếm gần đây"
                            )
                    );
                }
            }

        } catch (JSONException e) {
            // Nếu lịch sử bị lỗi, bắt đầu với danh sách rỗng.
        }

        if (recentAdapter != null) {
            recentAdapter.notifyDataSetChanged();
        }
    }

    private void saveRecentSearch(String location) {
        List<String> updated = new ArrayList<>();

        // Địa điểm mới nhất luôn đứng đầu.
        updated.add(location);

        for (RecentSearch item : recentSearchList) {
            String oldLocation = item.getLocationName();

            // Không lưu trùng địa điểm.
            if (!oldLocation.equalsIgnoreCase(location)
                    && updated.size() < MAX_RECENT_LOCATIONS) {

                updated.add(oldLocation);
            }
        }

        JSONArray array = new JSONArray();

        for (String item : updated) {
            array.put(item);
        }

        getSharedPreferences(
                PREFS_NAME,
                MODE_PRIVATE
        ).edit()
                .putString(
                        KEY_RECENT_LOCATIONS,
                        array.toString()
                )
                .apply();

        loadRecentSearches();
    }

    @Override
    protected void onDestroy() {
        if (pendingSearch != null) {
            searchHandler.removeCallbacks(pendingSearch);
        }

        super.onDestroy();
    }
}
