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
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest;
import com.google.android.libraries.places.api.net.PlacesClient;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
                                "Địa điểm gợi ý"
                        );

                        txtSuggestionTitle.setVisibility(
                                View.VISIBLE
                        );

                        recyclerLocationSuggestions.setVisibility(
                                View.VISIBLE
                        );

                        // Chỉ tìm khi nhập ít nhất 2 ký tự.
                        if (query.length() < 2) {
                            suggestionList.clear();
                            suggestionAdapter.notifyDataSetChanged();
                            return;
                        }

                        // Chờ người dùng ngừng gõ trước khi tìm kiếm.
                        pendingSearch = () ->
                                performSearchSuggestions(query);

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

    private void performSearchSuggestions(String query) {
        lastRequestedQuery = query;
        suggestionList.clear();
        suggestionAdapter.notifyDataSetChanged();

        // 1. Gợi ý vị trí trên bản đồ (posts trong Firestore chứa từ khóa)
        queryFirestoreLocationSuggestions(query);

        // 2. Gợi ý từ Google Maps (nếu có PlacesClient)
        if (placesClient != null) {
            requestPlacePredictions(query);
        }
    }

    private void queryFirestoreLocationSuggestions(String query) {
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        com.example.mobileproject.util.AccountVisibilityHelper.getLockedAccountIds(lockedIds -> {
        FirebaseFirestore.getInstance().collection("posts")
                .whereEqualTo("status", "APPROVED")
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (isFinishing() || isDestroyed()) return;
                    String currentQuery = edtSearchLocation.getText().toString().trim();
                    if (!query.equals(currentQuery)) return;

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        String ownerId = doc.getString("accountId");
                        if (ownerId == null || ownerId.trim().isEmpty()) ownerId = doc.getString("ownerId");
                        if (lockedIds.contains(ownerId)) continue;
                        String destination = doc.getString("destination");
                        String address = doc.getString("address");

                        if (destination != null && !destination.trim().isEmpty()) {
                            String normDest = destination.toLowerCase(Locale.ROOT);
                            if (normDest.contains(normalizedQuery)) {
                                addSuggestionUnique(destination.trim(), "Địa điểm trên bản đồ");
                            }
                        }
                        if (address != null && !address.trim().isEmpty()) {
                            String normAddr = address.toLowerCase(Locale.ROOT);
                            if (normAddr.contains(normalizedQuery)) {
                                addSuggestionUnique(address.trim(), "Địa chỉ trên bản đồ");
                            }
                        }
                    }
                    suggestionAdapter.notifyDataSetChanged();
                    updateSuggestionTitle();
                })
                .addOnFailureListener(e -> {
                    updateSuggestionTitle();
                });
        });
    }

    private void addSuggestionUnique(String locationName, String details) {
        for (RecentSearch item : suggestionList) {
            if (item.getLocationName().equalsIgnoreCase(locationName)) {
                return;
            }
        }
        suggestionList.add(new RecentSearch(locationName, details));
    }

    private void updateSuggestionTitle() {
        if (suggestionList.isEmpty()) {
            txtSuggestionTitle.setText("Không tìm thấy địa điểm phù hợp");
        } else {
            txtSuggestionTitle.setText("Địa điểm gợi ý từ bản đồ & Google Maps");
        }
    }

    private void requestPlacePredictions(String query) {
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
                    for (AutocompletePrediction prediction :
                            response.getAutocompletePredictions()) {
                        String address = prediction
                                .getFullText(null)
                                .toString();
                        if (!address.trim().isEmpty()) {
                            addSuggestionUnique(address.trim(), "Google Maps");
                        }
                    }
                    suggestionAdapter.notifyDataSetChanged();
                    updateSuggestionTitle();
                })
                .addOnFailureListener(exception -> {
                    android.util.Log.e(
                            "GooglePlaces",
                            "Lỗi tìm địa điểm: " + query,
                            exception
                    );
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

    private List<RecentSearch> loadRecentSearchesList() {
        List<RecentSearch> list = new ArrayList<>();
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

            for (int i = 0; i < array.length(); i++) {
                Object item = array.get(i);
                if (item instanceof org.json.JSONObject) {
                    org.json.JSONObject obj = (org.json.JSONObject) item;
                    String loc = obj.optString("locationName", "").trim();
                    long time = obj.optLong("timestamp", 0L);
                    if (!loc.isEmpty()) {
                        list.add(new RecentSearch(loc, "Tìm kiếm gần đây", time));
                    }
                } else if (item instanceof String) {
                    String location = ((String) item).trim();
                    if (!location.isEmpty()) {
                        list.add(new RecentSearch(location, "Tìm kiếm gần đây", System.currentTimeMillis() - i));
                    }
                }
            }

        } catch (JSONException e) {
            // Nếu lịch sử bị lỗi, bắt đầu với danh sách rỗng.
        }

        // Sắp xếp theo thời gian mới nhất (timestamp giảm dần)
        list.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
        return list;
    }

    private void loadRecentSearches() {
        recentSearchList.clear();
        List<RecentSearch> loaded = loadRecentSearchesList();
        for (int i = 0; i < loaded.size() && recentSearchList.size() < MAX_RECENT_LOCATIONS; i++) {
            recentSearchList.add(loaded.get(i));
        }

        if (recentAdapter != null) {
            recentAdapter.notifyDataSetChanged();
        }
    }

    private void saveRecentSearch(String location) {
        List<RecentSearch> list = loadRecentSearchesList();
        list.removeIf(item -> item.getLocationName().equalsIgnoreCase(location));
        list.add(0, new RecentSearch(location, "Tìm kiếm gần đây", System.currentTimeMillis()));

        if (list.size() > MAX_RECENT_LOCATIONS) {
            list = list.subList(0, MAX_RECENT_LOCATIONS);
        }

        JSONArray array = new JSONArray();
        try {
            for (RecentSearch item : list) {
                org.json.JSONObject obj = new org.json.JSONObject();
                obj.put("locationName", item.getLocationName());
                obj.put("timestamp", item.getTimestamp());
                array.put(obj);
            }
        } catch (JSONException e) {
            e.printStackTrace();
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
