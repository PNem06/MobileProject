
package com.example.mobileproject.view.Search;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mobileproject.R;
import com.example.mobileproject.adapter.SearchPostAdapter;
import com.example.mobileproject.model.ListingPost;
import com.example.mobileproject.view.Detail;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

public class SearchResultsActivity extends AppCompatActivity {

    private final List<ListingPost> allApprovedPosts = new ArrayList<>();
    private final List<ListingPost> visiblePosts = new ArrayList<>();

    private SearchPostAdapter adapter;

    private TextView edtSearchLocation;
    private TextView chipLocation, chipTime;
    private TextView txtChipFilterLabel, txtChipSortLabel;
    private TextView txtResultCount, txtEmptyResults;
    private ProgressBar progressResults;

    private String selectedLocation = "";
    private String timeSummary = "";

    private String selectedPropertyType = "Tất cả";
    private final Set<String> selectedAmenities = new HashSet<>();

    private int sortMode = 0;
    private int minBedrooms = 0;
    private int minBathrooms = 0;
    private int minGuests = 0;

    private SharedPreferences filterPrefs;
    private Intent currentSearchIntent;

    private ActivityResultLauncher<Intent> locationLauncher;
    private ActivityResultLauncher<Intent> timeLauncher;

    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search_results);

        db = FirebaseFirestore.getInstance();

        filterPrefs = getSharedPreferences(
                "search_filter_preferences",
                MODE_PRIVATE
        );
        restoreFilterSettings();

        currentSearchIntent = getIntent();

        selectedLocation = valueOrEmpty(
                getIntent().getStringExtra("SELECTED_LOCATION")
        );

        if (selectedLocation.isEmpty()) {
            selectedLocation = valueOrEmpty(
                    getIntent().getStringExtra("LOCATION_TEXT")
            );
        }

        timeSummary = buildTimeSummary(getIntent());

        edtSearchLocation = findViewById(R.id.edtSearchLocation);
        chipLocation = findViewById(R.id.chipLocation);
        chipTime = findViewById(R.id.chipTime);
        txtChipFilterLabel = findViewById(R.id.txtChipFilterLabel);
        txtChipSortLabel = findViewById(R.id.txtChipSortLabel);

        txtResultCount = findViewById(R.id.txtResultCount);
        txtEmptyResults = findViewById(R.id.txtEmptyResults);
        progressResults = findViewById(R.id.progressResults);

        RecyclerView recyclerPosts = findViewById(R.id.recyclerPosts);
        ImageView btnBackResults = findViewById(R.id.btnBack);
        View btnMap = findViewById(R.id.btnMap);
        View chipFilters = findViewById(R.id.chipFilters);
        View chipSort = findViewById(R.id.chipSort);

        updateHeaderAndChips();
        updateFilterCount();

        registerLaunchers();

        View.OnClickListener openLocation = v -> {
            Intent intent = new Intent(
                    SearchResultsActivity.this,
                    SearchLocationActivity.class
            );
            copySearchExtras(currentSearchIntent, intent);
            locationLauncher.launch(intent);
        };

        edtSearchLocation.setOnClickListener(openLocation);
        chipLocation.setOnClickListener(openLocation);

        chipTime.setOnClickListener(v -> {
            Intent intent = new Intent(
                    SearchResultsActivity.this,
                    SearchTimeActivity.class
            );
            copySearchExtras(currentSearchIntent, intent);
            timeLauncher.launch(intent);
        });

        chipFilters.setOnClickListener(v -> showFilterDialog());
        chipSort.setOnClickListener(v -> showSortDialog());

        adapter = new SearchPostAdapter(this, visiblePosts, post -> {
            if (post.getPostId() == null
                    || post.getPostId().trim().isEmpty()) {
                Toast.makeText(
                        this,
                        "Không xác định được bài đăng này",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            Intent detailIntent = new Intent(
                    SearchResultsActivity.this,
                    Detail.class
            );

            detailIntent.putExtra(
                    Detail.EXTRA_POST_ID,
                    post.getPostId()
            );

            startActivity(detailIntent);
        });

        recyclerPosts.setLayoutManager(new LinearLayoutManager(this));
        recyclerPosts.setAdapter(adapter);

        btnBackResults.setOnClickListener(v -> finish());
        btnMap.setOnClickListener(v -> openMap());

        loadApprovedPosts();
    }

    private void registerLaunchers() {
        locationLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK
                            && result.getData() != null) {

                        currentSearchIntent = result.getData();

                        String newLoc = result.getData()
                                .getStringExtra("SELECTED_LOCATION");

                        if (newLoc == null || newLoc.isEmpty()) {
                            newLoc = result.getData()
                                    .getStringExtra("LOCATION_TEXT");
                        }

                        if (newLoc != null) {
                            selectedLocation = newLoc.trim();
                        }

                        updateHeaderAndChips();
                        applyAllFilters();
                    }
                }
        );

        timeLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK
                            && result.getData() != null) {

                        currentSearchIntent = result.getData();
                        timeSummary = buildTimeSummary(result.getData());

                        updateHeaderAndChips();
                        applyAllFilters();
                    }
                }
        );
    }

    private void updateHeaderAndChips() {
        String locDisplay = selectedLocation.isEmpty()
                ? "Tất cả địa điểm"
                : selectedLocation;

        edtSearchLocation.setText(locDisplay);

        chipLocation.setText(
                selectedLocation.isEmpty()
                        ? "Địa điểm"
                        : selectedLocation
        );

        chipTime.setText(
                timeSummary.isEmpty()
                        ? "Thời gian"
                        : timeSummary
        );
    }

    private void loadApprovedPosts() {
        progressResults.setVisibility(View.VISIBLE);
        txtEmptyResults.setVisibility(View.GONE);

        visiblePosts.clear();

        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }

        com.example.mobileproject.util.BlockHelper.getBlockedUserIds(blockedIds -> {
            if (isFinishing() || isDestroyed()) return;
            db.collection("posts")
                    .whereEqualTo("status", "APPROVED")
                    .get()
                    .addOnSuccessListener(snapshot -> {
                        allApprovedPosts.clear();

                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            ListingPost post =
                                    doc.toObject(ListingPost.class);

                            if (post == null) {
                                continue;
                            }

                            String ownerId = post.getAccountId();
                            if (ownerId == null || ownerId.isEmpty()) {
                                ownerId = doc.getString("ownerId");
                            }
                            if (blockedIds.contains(ownerId)) {
                                continue;
                            }

                            if (post.getPostId() == null
                                    || post.getPostId().trim().isEmpty()) {
                                post.setPostId(doc.getId());
                            }

                            Object priceObj = doc.get("price");

                            if (priceObj instanceof Number) {
                                post.setPricePerNight(
                                        ((Number) priceObj).doubleValue()
                                );
                            }

                            allApprovedPosts.add(post);
                            loadCoverImage(post);
                        }

                        applyAllFilters();
                        progressResults.setVisibility(View.GONE);
                    })
                    .addOnFailureListener(error -> {
                        progressResults.setVisibility(View.GONE);

                        txtResultCount.setText("Không tải được kết quả");

                        txtEmptyResults.setText(
                                "Không thể tải bài đăng. "
                                        + "Kiểm tra kết nối mạng rồi thử lại."
                        );

                        txtEmptyResults.setVisibility(View.VISIBLE);

                        Toast.makeText(
                                SearchResultsActivity.this,
                                "Lỗi tải bài đăng: "
                                        + error.getLocalizedMessage(),
                                Toast.LENGTH_LONG
                        ).show();
                    });
        });
    }

    private void loadCoverImage(ListingPost post) {
        if (post == null
                || post.getPostId() == null
                || post.getPostId().trim().isEmpty()) {
            return;
        }

        db.collection("images")
                .whereEqualTo("postId", post.getPostId())
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (isFinishing()
                            || isDestroyed()
                            || snapshot == null
                            || snapshot.isEmpty()) {
                        return;
                    }

                    DocumentSnapshot first = null;
                    long bestOrder = Long.MAX_VALUE;

                    for (DocumentSnapshot imageDoc
                            : snapshot.getDocuments()) {

                        Object orderValue = imageDoc.get("orderIndex");

                        long order = orderValue instanceof Number
                                ? ((Number) orderValue).longValue()
                                : Long.MAX_VALUE - 1;

                        if (first == null || order < bestOrder) {
                            first = imageDoc;
                            bestOrder = order;
                        }
                    }

                    if (first != null) {
                        String image = first.getString("imgUrl");

                        if (image == null || image.isEmpty()) {
                            image = first.getString("imageUrl");
                        }

                        if (image != null && !image.isEmpty()) {
                            post.setCoverImageUrl(image);

                            if (adapter != null) {
                                adapter.notifyDataSetChanged();
                            }
                        }
                    }
                });
    }

    private void applyAllFilters() {
        visiblePosts.clear();

        String locationQuery = normalize(selectedLocation);

        for (ListingPost post : allApprovedPosts) {
            String address = normalize(post.getAddress());
            String title = normalize(post.getTitle());

            // 1. Lọc theo địa điểm.
            if (!locationQuery.isEmpty()
                    && !matchesLocation(
                    locationQuery,
                    address,
                    title
            )) {
                continue;
            }

            // 2. Lọc theo loại chỗ ở.
            if (!"Tất cả".equalsIgnoreCase(selectedPropertyType)
                    && !selectedPropertyType.isEmpty()) {

                String type = normalize(post.getPropertyType());

                if (!type.equals(normalize(selectedPropertyType))) {
                    continue;
                }
            }

            // 3. Lọc theo tiện nghi.
            // selectedAmenities chứa ID document của collection amenities.
            if (!selectedAmenities.isEmpty()) {
                List<String> postAmenities = post.getAmenityIds();

                if (postAmenities == null || postAmenities.isEmpty()) {
                    continue;
                }

                Set<String> postAmenitySet =
                        new HashSet<>(postAmenities);

                // Tất cả tiện nghi đã chọn đều phải có trong bài đăng.
                if (!postAmenitySet.containsAll(selectedAmenities)) {
                    continue;
                }
            }

            // 4. Lọc số phòng ngủ tối thiểu.
            if (minBedrooms > 0
                    && post.getBedroomCount() < minBedrooms) {
                continue;
            }

            // 5. Lọc số phòng tắm tối thiểu.
            if (minBathrooms > 0
                    && post.getBathroomCount() < minBathrooms) {
                continue;
            }

            // 6. Lọc số khách tối đa của chỗ ở.
            if (minGuests > 0
                    && post.getMaxGuests() < minGuests) {
                continue;
            }

            visiblePosts.add(post);
        }

        // 7. Sắp xếp theo giá.
        if (sortMode == 1) {
            visiblePosts.sort(
                    (a, b) -> Double.compare(
                            a.getPricePerNight(),
                            b.getPricePerNight()
                    )
            );
        } else if (sortMode == 2) {
            visiblePosts.sort(
                    (a, b) -> Double.compare(
                            b.getPricePerNight(),
                            a.getPricePerNight()
                    )
            );
        }

        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }

        txtResultCount.setText(
                visiblePosts.size() + " chỗ ở phù hợp"
        );

        if (visiblePosts.isEmpty()) {
            txtEmptyResults.setText(
                    "Không tìm thấy bài đăng phù hợp với bộ lọc."
            );
            txtEmptyResults.setVisibility(View.VISIBLE);
        } else {
            txtEmptyResults.setVisibility(View.GONE);
        }
    }

    private void showFilterDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(R.layout.dialog_bottom_sheet_filter);

        Spinner spinnerType =
                dialog.findViewById(R.id.spinnerFilterPropertyType);

        LinearLayout amenitiesLayout =
                dialog.findViewById(R.id.filterAmenitiesList);

        EditText edtBedrooms =
                dialog.findViewById(R.id.edtFilterBedroomCount);

        EditText edtBathrooms =
                dialog.findViewById(R.id.edtFilterBathroomCount);

        EditText edtGuests =
                dialog.findViewById(R.id.edtFilterMaxGuests);

        TextView txtReset =
                dialog.findViewById(R.id.txtResetFilter);

        View btnApply =
                dialog.findViewById(R.id.btnApplyFilter);

        if (spinnerType == null
                || amenitiesLayout == null
                || edtBedrooms == null
                || edtBathrooms == null
                || edtGuests == null
                || txtReset == null
                || btnApply == null) {

            Toast.makeText(
                    this,
                    "Không tìm thấy thành phần giao diện bộ lọc. "
                            + "Hãy kiểm tra ID trong XML.",
                    Toast.LENGTH_LONG
            ).show();

            dialog.dismiss();
            return;
        }

        edtBedrooms.setText(
                minBedrooms > 0 ? String.valueOf(minBedrooms) : ""
        );

        edtBathrooms.setText(
                minBathrooms > 0 ? String.valueOf(minBathrooms) : ""
        );

        edtGuests.setText(
                minGuests > 0 ? String.valueOf(minGuests) : ""
        );

        // Chặn áp dụng khi dữ liệu Firebase chưa tải xong.
        btnApply.setEnabled(false);

        List<String> propertyTypes = new ArrayList<>();
        propertyTypes.add("Tất cả");

        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                propertyTypes
        );

        typeAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        spinnerType.setAdapter(typeAdapter);

        amenitiesLayout.removeAllViews();

        TextView loading = new TextView(this);
        loading.setText("Đang tải tiện nghi...");
        amenitiesLayout.addView(loading);

        boolean[] typesLoaded = {false};
        boolean[] amenitiesLoaded = {false};

        Runnable enableApplyWhenReady = () -> {
            if (typesLoaded[0] && amenitiesLoaded[0]) {
                btnApply.setEnabled(true);
            }
        };

        // Loại chỗ ở lấy từ các bài đăng APPROVED trong Firebase.
        db.collection("posts")
                .whereEqualTo("status", "APPROVED")
                .get()
                .addOnSuccessListener(snapshot -> {
                    TreeSet<String> uniqueTypes =
                            new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        String type = doc.getString("propertyType");

                        if (type != null && !type.trim().isEmpty()) {
                            uniqueTypes.add(type.trim());
                        }
                    }

                    propertyTypes.clear();
                    propertyTypes.add("Tất cả");
                    propertyTypes.addAll(uniqueTypes);

                    typeAdapter.notifyDataSetChanged();

                    int selectedIndex = 0;

                    for (int i = 0; i < propertyTypes.size(); i++) {
                        if (propertyTypes.get(i).equalsIgnoreCase(
                                selectedPropertyType)) {
                            selectedIndex = i;
                            break;
                        }
                    }

                    spinnerType.setSelection(selectedIndex);
                    typesLoaded[0] = true;
                    enableApplyWhenReady.run();
                })
                .addOnFailureListener(error -> {
                    typesLoaded[0] = true;
                    enableApplyWhenReady.run();

                    Toast.makeText(
                            this,
                            "Không tải được loại chỗ ở từ Firebase.",
                            Toast.LENGTH_LONG
                    ).show();
                });

        // Tiện nghi lấy từ collection amenities.
        db.collection("amenities")
                .get()
                .addOnSuccessListener(snapshot -> {
                    amenitiesLayout.removeAllViews();

                    List<DocumentSnapshot> amenityDocs =
                            new ArrayList<>(snapshot.getDocuments());

                    Collections.sort(
                            amenityDocs,
                            (a, b) -> amenityName(a).compareToIgnoreCase(
                                    amenityName(b)
                            )
                    );

                    if (amenityDocs.isEmpty()) {
                        TextView empty = new TextView(this);
                        empty.setText("Chưa có tiện nghi để chọn.");
                        amenitiesLayout.addView(empty);
                    } else {
                        for (DocumentSnapshot doc : amenityDocs) {
                            CheckBox checkBox = new CheckBox(this);

                            checkBox.setText(amenityName(doc));
                            checkBox.setTag(doc.getId());
                            checkBox.setTextColor(0xFF171719);

                            checkBox.setChecked(
                                    selectedAmenities.contains(doc.getId())
                            );

                            amenitiesLayout.addView(checkBox);
                        }
                    }

                    amenitiesLoaded[0] = true;
                    enableApplyWhenReady.run();
                })
                .addOnFailureListener(error -> {
                    amenitiesLayout.removeAllViews();

                    TextView failed = new TextView(this);
                    failed.setText(
                            "Không tải được tiện nghi. "
                                    + "Kiểm tra kết nối rồi thử lại."
                    );

                    amenitiesLayout.addView(failed);

                    amenitiesLoaded[0] = true;
                    enableApplyWhenReady.run();

                    Toast.makeText(
                            this,
                            "Không tải được danh sách tiện nghi.",
                            Toast.LENGTH_LONG
                    ).show();
                });

        txtReset.setOnClickListener(v -> {
            spinnerType.setSelection(0);

            edtBedrooms.setText("");
            edtBathrooms.setText("");
            edtGuests.setText("");

            for (int i = 0;
                 i < amenitiesLayout.getChildCount();
                 i++) {

                View child = amenitiesLayout.getChildAt(i);

                if (child instanceof CheckBox) {
                    ((CheckBox) child).setChecked(false);
                }
            }

            // Reset được áp dụng ngay, không cần bấm Áp dụng lần nữa.
            selectedPropertyType = "Tất cả";
            selectedAmenities.clear();

            minBedrooms = 0;
            minBathrooms = 0;
            minGuests = 0;

            saveFilterSettings();
            updateFilterCount();
            applyAllFilters();

            dialog.dismiss();
        });

        btnApply.setOnClickListener(v -> {
            int bedrooms = parseFilterNumber(
                    edtBedrooms.getText().toString()
            );

            int bathrooms = parseFilterNumber(
                    edtBathrooms.getText().toString()
            );

            int guests = parseFilterNumber(
                    edtGuests.getText().toString()
            );

            if (bedrooms < 0 || bathrooms < 0 || guests < 0) {
                Toast.makeText(
                        this,
                        "Số phòng ngủ, phòng tắm và số khách "
                                + "phải là số nguyên không âm.",
                        Toast.LENGTH_LONG
                ).show();
                return;
            }

            selectedPropertyType =
                    spinnerType.getSelectedItem() == null
                            ? "Tất cả"
                            : spinnerType.getSelectedItem().toString();

            minBedrooms = bedrooms;
            minBathrooms = bathrooms;
            minGuests = guests;

            selectedAmenities.clear();

            for (int i = 0;
                 i < amenitiesLayout.getChildCount();
                 i++) {

                View child = amenitiesLayout.getChildAt(i);

                if (child instanceof CheckBox) {
                    CheckBox checkBox = (CheckBox) child;

                    if (checkBox.isChecked()
                            && checkBox.getTag() != null) {
                        selectedAmenities.add(
                                checkBox.getTag().toString()
                        );
                    }
                }
            }

            saveFilterSettings();
            updateFilterCount();
            applyAllFilters();

            dialog.dismiss();
        });

        dialog.show();
    }

    private String amenityName(DocumentSnapshot document) {
        String name = document.getString("name");

        return name == null || name.trim().isEmpty()
                ? document.getId()
                : name.trim();
    }

    private int parseFilterNumber(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0;
        }

        try {
            int number = Integer.parseInt(value.trim());

            if (number < 0) {
                return -1;
            }

            return number;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void restoreFilterSettings() {
        selectedPropertyType = filterPrefs.getString(
                "propertyType",
                "Tất cả"
        );

        minBedrooms = filterPrefs.getInt("bedrooms", 0);
        minBathrooms = filterPrefs.getInt("bathrooms", 0);
        minGuests = filterPrefs.getInt("guests", 0);

        selectedAmenities.clear();

        Set<String> savedAmenities = filterPrefs.getStringSet(
                "amenities",
                Collections.emptySet()
        );

        if (savedAmenities != null) {
            selectedAmenities.addAll(savedAmenities);
        }
    }

    private void saveFilterSettings() {
        filterPrefs.edit()
                .putString("propertyType", selectedPropertyType)
                .putInt("bedrooms", minBedrooms)
                .putInt("bathrooms", minBathrooms)
                .putInt("guests", minGuests)
                .putStringSet(
                        "amenities",
                        new HashSet<>(selectedAmenities)
                )
                .apply();
    }

    private void updateFilterCount() {
        int count = 0;

        if (!"Tất cả".equalsIgnoreCase(selectedPropertyType)) {
            count++;
        }

        count += selectedAmenities.size();

        if (minBedrooms > 0) count++;
        if (minBathrooms > 0) count++;
        if (minGuests > 0) count++;

        if (txtChipFilterLabel != null) {
            txtChipFilterLabel.setText(
                    count == 0 ? "Filter" : "Filter (" + count + ")"
            );
        }
    }

    private void showSortDialog() {
        String[] options = {
                "Mặc định",
                "Mức giá tăng dần",
                "Mức giá giảm dần"
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Sắp xếp theo giá");

        builder.setSingleChoiceItems(
                options,
                sortMode,
                (dialog, which) -> {
                    sortMode = which;

                    if (sortMode == 1) {
                        txtChipSortLabel.setText("Giá: Thấp -> Cao");
                    } else if (sortMode == 2) {
                        txtChipSortLabel.setText("Giá: Cao -> Thấp");
                    } else {
                        txtChipSortLabel.setText("Sắp xếp");
                    }

                    applyAllFilters();
                    dialog.dismiss();
                }
        );

        builder.show();
    }

    private void openMap() {
        Intent mapIntent = new Intent(
                SearchResultsActivity.this,
                SearchMapActivity.class
        );

        copySearchExtras(currentSearchIntent, mapIntent);
        startActivity(mapIntent);
    }

    static void copySearchExtras(Intent source, Intent target) {
        if (source == null || target == null) {
            return;
        }

        String[] keys = {
                "SELECTED_LOCATION",
                "LOCATION_TEXT",
                "TIME_MODE",
                "START_DATE",
                "END_DATE",
                "TOLERANCE",
                "FLEX_DURATION",
                "FLEX_MONTH"
        };

        for (String key : keys) {
            String value = source.getStringExtra(key);

            if (value != null) {
                target.putExtra(key, value);
            }
        }
    }

    static String buildTimeSummary(Intent intent) {
        if (intent == null) {
            return "";
        }

        String mode = intent.getStringExtra("TIME_MODE");

        if ("CUSTOM".equals(mode)) {
            String start = intent.getStringExtra("START_DATE");
            String end = intent.getStringExtra("END_DATE");

            if (!TextUtils.isEmpty(start) && !TextUtils.isEmpty(end)) {
                return start + " đến " + end;
            }

            return TextUtils.isEmpty(start)
                    ? "Ngày linh hoạt"
                    : start;
        }

        if ("FLEXIBLE".equals(mode)) {
            String duration = intent.getStringExtra("FLEX_DURATION");
            String month = intent.getStringExtra("FLEX_MONTH");

            if (!TextUtils.isEmpty(duration)
                    && !TextUtils.isEmpty(month)) {
                return duration + " · " + month;
            }

            if (!TextUtils.isEmpty(duration)) {
                return duration;
            }

            return valueOrEmpty(month);
        }

        return "";
    }

    private static boolean matchesLocation(
            String query,
            String address,
            String title
    ) {
        if (address.contains(query)
                || title.contains(query)
                || (!address.isEmpty() && query.contains(address))) {
            return true;
        }

        String[] parts = query.split(",");

        for (String part : parts) {
            String token = part.trim();

            if (token.length() >= 3
                    && (address.contains(token)
                    || title.contains(token))) {
                return true;
            }
        }

        return false;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }

        String result = value.trim().toLowerCase(new Locale("vi", "VN"));

        // Giữ cách so sánh không phân biệt dấu tiếng Việt.
        result = Normalizer.normalize(result, Normalizer.Form.NFD);
        result = result.replaceAll("\\p{M}+", "");
        result = result.replace('đ', 'd');

        return result;
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
