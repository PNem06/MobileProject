package com.example.mobileproject.view;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.util.Pair;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.example.mobileproject.R;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class New_Post extends AppCompatActivity {
    private FirebaseFirestore db;
    private FirebaseStorage storage;
    private EditText title, address, description, extra, bedrooms, bathrooms, guests, start, end;
    private Spinner propertyType;
    private ImageView preview;
    private TextView imageHint, coordinates;
    private Button publish, cancel;
    private Uri imageUri;
    private double latitude = 10.75, longitude = 106.68;
    private boolean publishing = false;
    private ActivityResultLauncher<String> imagePicker, locationPermission;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_new_post);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();

        bindViews();
        setupSpinner();
        setupLaunchers();
        setupClickListeners();
    }

    private void bindViews() {
        title = findViewById(R.id.edtTitle);
        address = findViewById(R.id.edtAddress);
        description = findViewById(R.id.edtDescription);
        extra = findViewById(R.id.edtAdditionalInfo);
        bedrooms = findViewById(R.id.edtBedroomCount);
        bathrooms = findViewById(R.id.edtBathroomCount);
        guests = findViewById(R.id.edtMaxGuests);
        start = findViewById(R.id.edtStartDate);
        end = findViewById(R.id.edtEndDate);
        propertyType = findViewById(R.id.spinnerPropertyType);
        preview = findViewById(R.id.imgPreview);
        imageHint = findViewById(R.id.txtImageHint);
        coordinates = findViewById(R.id.txtCoordinates);
        publish = findViewById(R.id.btnPublish);
        cancel = findViewById(R.id.btnCancel);
    }

    private void setupSpinner() {
        String[] types = {"Căn hộ/Chung cư", "Nhà nguyên căn", "Nhà khách/Nhà phụ", "Cabin/Bungalow", "Loại nhà khác"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, types);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        propertyType.setAdapter(adapter);
    }

    private void setupLaunchers() {
        imagePicker = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                imageUri = uri;
                preview.setVisibility(View.VISIBLE);
                Glide.with(this).load(uri).centerCrop().into(preview);
                imageHint.setText("Đã chọn ảnh");
            }
        });
        locationPermission = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted) {
                getLocation();
            } else {
                toast("Bạn chưa cấp quyền vị trí.");
            }
        });
    }

    private void setupClickListeners() {
        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }
        View btnChooseImage = findViewById(R.id.btnChooseImage);
        if (btnChooseImage != null) {
            btnChooseImage.setOnClickListener(v -> imagePicker.launch("image/*"));
        }
        View btnGetLocation = findViewById(R.id.btnGetLocation);
        if (btnGetLocation != null) {
            btnGetLocation.setOnClickListener(v -> requestLocation());
        }
        if (start != null) {
            start.setOnClickListener(v -> showDateRangePicker());
        }
        if (end != null) {
            end.setOnClickListener(v -> showDateRangePicker());
        }
        if (publish != null) {
            publish.setOnClickListener(v -> publishPost());
        }
        if (cancel != null) {
            cancel.setOnClickListener(v -> finish());
        }
    }

    private void showDateRangePicker() {
        MaterialDatePicker.Builder<Pair<Long, Long>> builder = MaterialDatePicker.Builder.dateRangePicker();
        builder.setTitleText("Chọn thời gian cho thuê");

        Date existingStart = parseDate(start.getText().toString().trim());
        Date existingEnd = parseDate(end.getText().toString().trim());
        if (existingStart != null && existingEnd != null) {
            builder.setSelection(new Pair<>(existingStart.getTime(), existingEnd.getTime()));
        }

        MaterialDatePicker<Pair<Long, Long>> picker = builder.build();
        picker.addOnPositiveButtonClickListener(selection -> {
            if (selection != null && selection.first != null && selection.second != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

                String startDateStr = sdf.format(new Date(selection.first));
                String endDateStr = sdf.format(new Date(selection.second));

                start.setText(startDateStr);
                end.setText(endDateStr);

                start.setError(null);
                end.setError(null);
            }
        });

        picker.show(getSupportFragmentManager(), "DATE_RANGE_PICKER");
    }

    private void requestLocation() {
        boolean fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (fine || coarse) {
            getLocation();
        } else {
            locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    @SuppressLint("MissingPermission")
    private void getLocation() {
        try {
            LocationServices.getFusedLocationProviderClient(this).getLastLocation()
                    .addOnSuccessListener(loc -> {
                        if (loc == null) {
                            toast("Không lấy được vị trí. Hãy nhập địa chỉ thủ công.");
                            return;
                        }
                        latitude = loc.getLatitude();
                        longitude = loc.getLongitude();
                        coordinates.setText(String.format(Locale.getDefault(), "Vị trí: %.5f, %.5f", latitude, longitude));
                        reverseGeocode(latitude, longitude);
                    }).addOnFailureListener(e -> toast("Lỗi vị trí: " + e.getMessage()));
        } catch (SecurityException e) {
            toast("Không có quyền vị trí.");
        }
    }

    private void reverseGeocode(double lat, double lng) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        if (Build.VERSION.SDK_INT >= 33) {
            geocoder.getFromLocation(lat, lng, 1, results -> {
                if (results != null && !results.isEmpty()) {
                    String line = results.get(0).getAddressLine(0);
                    runOnUiThread(() -> {
                        if (!TextUtils.isEmpty(line)) address.setText(line);
                    });
                }
            });
        } else {
            new Thread(() -> {
                try {
                    List<Address> results = geocoder.getFromLocation(lat, lng, 1);
                    if (results != null && !results.isEmpty()) {
                        String line = results.get(0).getAddressLine(0);
                        runOnUiThread(() -> {
                            if (!TextUtils.isEmpty(line)) address.setText(line);
                        });
                    }
                } catch (IOException ignored) {
                }
            }).start();
        }
    }

    private void publishPost() {
        if (publishing) return;
        String t = title.getText().toString().trim();
        String a = address.getText().toString().trim();
        String d = description.getText().toString().trim();
        if (t.isEmpty()) {
            title.setError("Nhập tên chỗ ở");
            return;
        }
        if (a.isEmpty()) {
            address.setError("Nhập địa chỉ");
            return;
        }
        if (d.isEmpty()) {
            description.setError("Nhập mô tả");
            return;
        }
        int bed = parsePositive(bedrooms, "Phòng ngủ", 1);
        int bath = parsePositive(bathrooms, "Phòng tắm", 1);
        int max = parsePositive(guests, "Số khách", 2);
        if (bed < 1 || bath < 1 || max < 1) return;

        Date sd = parseDate(start.getText().toString().trim());
        Date ed = parseDate(end.getText().toString().trim());
        if (sd != null && ed != null && ed.before(sd)) {
            end.setError("Ngày kết thúc phải sau ngày bắt đầu");
            return;
        }

        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            toast("Vui lòng đăng nhập trước khi đăng.");
            return;
        }

        publishing = true;
        publish.setEnabled(false);
        publish.setText("Đang đăng...");
        DocumentReference postRef = db.collection("posts").document();
        String postId = postRef.getId();
        Map<String, Object> post = new HashMap<>();
        post.put("postId", postId);
        post.put("accountId", uid);
        post.put("title", t);
        post.put("address", a);
        post.put("description", d);
        post.put("additionalInformation", extra.getText().toString().trim());
        post.put("bedroomCount", bed);
        post.put("bathroomCount", bath);
        post.put("maxGuests", max);
        post.put("propertyType", propertyType.getSelectedItem().toString());
        post.put("latitude", latitude);
        post.put("longitude", longitude);
        post.put("amenityIds", new ArrayList<String>());
        post.put("status", "PENDING");
        post.put("createdAt", FieldValue.serverTimestamp());
        if (sd != null) post.put("startDate", new Timestamp(sd));
        if (ed != null) post.put("endDate", new Timestamp(ed));

        if (imageUri == null) {
            savePost(postRef, post, null);
            return;
        }
        StorageReference ref = storage.getReference().child("post_images/" + postId + "/" + System.currentTimeMillis() + ".jpg");
        ref.putFile(imageUri).continueWithTask(task -> {
            if (!task.isSuccessful() && task.getException() != null) {
                throw task.getException();
            }
            return ref.getDownloadUrl();
        }).addOnSuccessListener(url -> {
            Map<String, Object> image = new HashMap<>();
            image.put("ImgId", db.collection("images").document().getId());
            image.put("imgUrl", url.toString());
            image.put("orderIndex", 0);
            image.put("postId", postId);
            savePost(postRef, post, image);
        }).addOnFailureListener(e -> finishPublish(false, "Tải ảnh thất bại: " + e.getMessage()));
    }

    private void savePost(DocumentReference postRef, Map<String, Object> post, Map<String, Object> image) {
        WriteBatch batch = db.batch();
        batch.set(postRef, post);
        if (image != null) batch.set(db.collection("images").document(), image);
        batch.commit().addOnSuccessListener(v -> {
            finishPublish(true, "Đăng bài thành công!");
            setResult(RESULT_OK);
            finish();
        }).addOnFailureListener(e -> finishPublish(false, "Đăng thất bại: " + e.getMessage()));
    }

    private int parsePositive(EditText field, String label, int fallback) {
        String s = field.getText().toString().trim();
        if (s.isEmpty()) return fallback;
        try {
            int n = Integer.parseInt(s);
            if (n < 1) {
                field.setError(label + " phải lớn hơn 0");
                return -1;
            }
            return n;
        } catch (NumberFormatException e) {
            field.setError("Nhập số hợp lệ");
            return -1;
        }
    }

    private Date parseDate(String s) {
        if (s.isEmpty()) return null;
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            f.setLenient(false);
            return f.parse(s);
        } catch (Exception e) {
            try {
                SimpleDateFormat f2 = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                f2.setLenient(false);
                return f2.parse(s);
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    private void clearForm() {
        title.setText("");
        address.setText("");
        description.setText("");
        extra.setText("");
        bedrooms.setText("1");
        bathrooms.setText("1");
        guests.setText("2");
        start.setText("");
        end.setText("");
        propertyType.setSelection(0);
        imageUri = null;
        preview.setImageDrawable(null);
        preview.setVisibility(View.GONE);
        imageHint.setText("Thêm ảnh của bạn");
        latitude = 10.75;
        longitude = 106.68;
        coordinates.setText("Chưa chọn vị trí");
    }

    private void finishPublish(boolean success, String message) {
        publishing = false;
        publish.setEnabled(true);
        publish.setText("Đăng");
        toast(message);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}
