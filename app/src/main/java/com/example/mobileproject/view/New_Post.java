package com.example.mobileproject.view;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Matrix;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.CheckBox;
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
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.util.Pair;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.example.mobileproject.R;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
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
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class New_Post extends AppCompatActivity {
    private FirebaseFirestore db;
    private FirebaseStorage storage;
    private EditText title, address, description, extra, bedrooms, bathrooms, guests, start, end, price;
    private Spinner propertyType;
    private LinearLayout imagePreviewList, amenitiesList;
    private View imagePlaceholder;
    private HorizontalScrollView imagePreviewScroll;
    private TextView imageHint, coordinates;
    private Button publish, cancel;
    private final List<Uri> imageUris = new ArrayList<>();
    private final Map<String, CheckBox> amenityChoices = new LinkedHashMap<>();
    private Uri pendingCameraUri;
    private double latitude = 10.75, longitude = 106.68;
    private boolean publishing = false;
    private ActivityResultLauncher<String> locationPermission;
    private ActivityResultLauncher<String> imagePicker;
    private ActivityResultLauncher<Uri> cameraCapture;
    private static final int MAX_IMAGES = 8;

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
        price = findViewById(R.id.edtPrice);
        propertyType = findViewById(R.id.spinnerPropertyType);
        imagePreviewList = findViewById(R.id.imagePreviewList);
        imagePreviewScroll = findViewById(R.id.imagePreviewScroll);
        imagePlaceholder = findViewById(R.id.imagePlaceholder);
        amenitiesList = findViewById(R.id.amenitiesList);
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
        imagePicker = registerForActivityResult(new ActivityResultContracts.GetMultipleContents(), uris -> {
            if (uris != null) {
                boolean overflow = false;
                for (Uri uri : uris) {
                    if (imageUris.contains(uri)) continue;
                    if (imageUris.size() >= MAX_IMAGES) {
                        overflow = true;
                        break;
                    }
                    imageUris.add(uri);
                }
                if (overflow) toast("Tối đa " + MAX_IMAGES + " ảnh.");
                refreshImages();
            }
        });
        cameraCapture = registerForActivityResult(new ActivityResultContracts.TakePicture(), saved -> {
            if (saved && pendingCameraUri != null && imageUris.size() < MAX_IMAGES) {
                imageUris.add(pendingCameraUri);
                refreshImages();
            } else if (imageUris.size() >= MAX_IMAGES) {
                toast("Tối đa " + MAX_IMAGES + " ảnh.");
            }
            pendingCameraUri = null;
        });
        locationPermission = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted) {
                getLocation();
            } else {
                toast("Bạn chưa cấp quyền vị trí.");
            }
        });
        loadAmenities();
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
        View btnTakePhoto = findViewById(R.id.btnTakePhoto);
        if (btnTakePhoto != null) btnTakePhoto.setOnClickListener(v -> takePhoto());
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

    private void takePhoto() {
        if (imageUris.size() >= MAX_IMAGES) {
            toast("Tối đa " + MAX_IMAGES + " ảnh.");
            return;
        }
        try {
            File directory = new File(getCacheDir(), "camera");
            if (!directory.exists() && !directory.mkdirs()) throw new IOException("Không tạo được thư mục ảnh.");
            File photo = File.createTempFile("new_post_", ".jpg", directory);
            pendingCameraUri = FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", photo);
            cameraCapture.launch(pendingCameraUri);
        } catch (IOException error) {
            toast("Không thể mở camera: " + error.getLocalizedMessage());
        }
    }

    private void refreshImages() {
        imagePreviewList.removeAllViews();
        boolean hasImages = !imageUris.isEmpty();
        imagePlaceholder.setVisibility(hasImages ? View.GONE : View.VISIBLE);
        imagePreviewScroll.setVisibility(hasImages ? View.VISIBLE : View.GONE);
        imageHint.setText(hasImages ? imageUris.size() + " ảnh đã chọn" : "Chọn ảnh hoặc chụp ảnh mới");
        for (int i = 0; i < imageUris.size(); i++) {
            final int index = i;
            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setPadding(dp(4), dp(4), dp(4), dp(4));
            tile.setBackground(roundedBackground(0xFFF2F2F7, 10));
            LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(dp(112), ViewGroup.LayoutParams.WRAP_CONTENT);
            tileParams.setMargins(0, 0, dp(8), 0);
            ImageView image = new ImageView(this);
            image.setLayoutParams(new LinearLayout.LayoutParams(dp(104), dp(78)));
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setContentDescription("Ảnh xem trước đã làm mờ " + (i + 1));
            Glide.with(this).load(imageUris.get(i)).override(28, 22).centerCrop().into(image);
            tile.addView(image);
            LinearLayout actions = new LinearLayout(this);
            actions.setGravity(Gravity.CENTER);
            TextView edit = actionText("Sửa", true);
            edit.setOnClickListener(v -> editImage(index));
            TextView remove = actionText("Xóa", false);
            remove.setOnClickListener(v -> {
                Glide.with(this).clear(image);
                imageUris.remove(index);
                refreshImages();
            });
            actions.addView(edit, new LinearLayout.LayoutParams(0, dp(34), 1));
            actions.addView(remove, new LinearLayout.LayoutParams(0, dp(34), 1));
            tile.addView(actions);
            imagePreviewList.addView(tile, tileParams);
        }
    }

    private TextView actionText(String label, boolean primary) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextSize(12);
        button.setGravity(Gravity.CENTER);
        button.setTextColor(primary ? 0xFF3533CD : 0xFF73737B);
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private GradientDrawable roundedBackground(int color, int radiusDp) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(radiusDp));
        return background;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private Bitmap loadBitmap(Uri uri) throws IOException {
        try (java.io.InputStream input = getContentResolver().openInputStream(uri)) {
            Bitmap original = android.graphics.BitmapFactory.decodeStream(input);
            if (original == null) throw new IOException("Không đọc được tệp ảnh.");
            int width = original.getWidth();
            int height = original.getHeight();
            float scale = Math.min(1f, 1800f / Math.max(width, height));
            if (scale < 1f) {
                int targetW = Math.round(width * scale);
                int targetH = Math.round(height * scale);
                Bitmap scaled = Bitmap.createScaledBitmap(original, targetW, targetH, true);
                if (scaled != original) original.recycle();
                return scaled;
            }
            return original;
        }
    }

    @SuppressLint("WrongThread")
    private void editImage(int index) {
        if (index < 0 || index >= imageUris.size()) return;
        final Bitmap source;
        try {
            source = loadBitmap(imageUris.get(index));
        } catch (IOException error) {
            toast("Không đọc được ảnh để chỉnh sửa.");
            return;
        }

        Bitmap[] working = {source};
        boolean[] squareCrop = {false};
        LinearLayout editor = new LinearLayout(this);
        editor.setPadding(dp(16), dp(12), dp(16), dp(8));
        editor.setOrientation(LinearLayout.VERTICAL);
        ImageView canvas = new ImageView(this);
        canvas.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(310)));
        canvas.setScaleType(ImageView.ScaleType.FIT_CENTER);
        canvas.setImageBitmap(source);
        editor.addView(canvas);
        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        TextView rotate = actionText("Xoay 90°", true);
        TextView crop = actionText("Cắt vuông", true);
        controls.addView(rotate, new LinearLayout.LayoutParams(0, dp(44), 1));
        controls.addView(crop, new LinearLayout.LayoutParams(0, dp(44), 1));
        editor.addView(controls);
        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        TextView cancelEdit = actionText("Hủy", false);
        TextView saveEdit = actionText("Lưu ảnh", true);
        footer.addView(cancelEdit, new LinearLayout.LayoutParams(dp(72), dp(44)));
        footer.addView(saveEdit, new LinearLayout.LayoutParams(dp(84), dp(44)));
        editor.addView(footer);
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                .setTitle("Chỉnh sửa ảnh")
                .setView(editor)
                .create();
        rotate.setOnClickListener(v -> {
            Bitmap rotated = rotateBitmap(working[0]);
            if (working[0] != source) working[0].recycle();
            working[0] = rotated;
            canvas.setImageBitmap(rotated);
        });
        crop.setOnClickListener(v -> {
            squareCrop[0] = !squareCrop[0];
            crop.setText(squareCrop[0] ? "Bỏ cắt vuông" : "Cắt vuông");
            canvas.setScaleType(squareCrop[0] ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
        });
        cancelEdit.setOnClickListener(v -> dialog.dismiss());
        saveEdit.setOnClickListener(v -> {
            Bitmap edited = squareCrop[0] ? centerSquareCrop(working[0]) : working[0];
            try {
                imageUris.set(index, saveEditedBitmap(edited));
                if (edited != working[0]) edited.recycle();
                refreshImages();
                dialog.dismiss();
            } catch (IOException error) {
                toast("Không thể lưu ảnh đã chỉnh sửa.");
                if (edited != working[0]) edited.recycle();
            }
        });
        dialog.setOnDismissListener(v -> {
            if (!source.isRecycled()) source.recycle();
            if (working[0] != source && !working[0].isRecycled()) working[0].recycle();
        });
        dialog.show();
    }

    private Bitmap rotateBitmap(Bitmap bitmap) {
        Matrix matrix = new Matrix();
        matrix.postRotate(90f);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    private Bitmap centerSquareCrop(Bitmap bitmap) {
        int side = Math.min(bitmap.getWidth(), bitmap.getHeight());
        int left = (bitmap.getWidth() - side) / 2;
        int top = (bitmap.getHeight() - side) / 2;
        return Bitmap.createBitmap(bitmap, left, top, side, side);
    }

    private Uri saveEditedBitmap(Bitmap bitmap) throws IOException {
        File directory = new File(getCacheDir(), "edited_photos");
        if (!directory.exists() && !directory.mkdirs()) throw new IOException("Không tạo được thư mục ảnh.");
        File file = File.createTempFile("edited_", ".jpg", directory);
        try (FileOutputStream output = new FileOutputStream(file)) {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)) throw new IOException("Không lưu được ảnh.");
        }
        return Uri.fromFile(file);
    }

    private void loadAmenities() {
        amenitiesList.removeAllViews();
        TextView loading = new TextView(this);
        loading.setText("Đang tải tiện nghi…");
        amenitiesList.addView(loading);
        db.collection("amenities").get().addOnSuccessListener(snapshot -> {
            amenitiesList.removeAllViews();
            amenityChoices.clear();
            if (snapshot.isEmpty()) {
                TextView empty = new TextView(this);
                empty.setText("Chưa có tiện nghi để chọn.");
                amenitiesList.addView(empty);
                return;
            }
            List<com.google.firebase.firestore.DocumentSnapshot> docs = new ArrayList<>(snapshot.getDocuments());
            Collections.sort(docs, (a, b) -> amenityName(a).compareToIgnoreCase(amenityName(b)));
            for (com.google.firebase.firestore.DocumentSnapshot document : docs) {
                CheckBox choice = new CheckBox(this);
                choice.setText(amenityName(document));
                choice.setTag(document.getId());
                choice.setTextColor(0xFF171719);
                amenitiesList.addView(choice);
                amenityChoices.put(document.getId(), choice);
            }
        }).addOnFailureListener(error -> {
            amenitiesList.removeAllViews();
            TextView failed = new TextView(this);
            failed.setText("Không tải được tiện nghi. Kiểm tra kết nối rồi thử lại.");
            amenitiesList.addView(failed);
        });
    }

    private String amenityName(com.google.firebase.firestore.DocumentSnapshot document) {
        String name = document.getString("name");
        return name == null || name.trim().isEmpty() ? document.getId() : name.trim();
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
        long tripPrice = parsePrice();
        if (bed < 1 || bath < 1 || max < 1 || tripPrice < 1) return;

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
        post.put("price", tripPrice);
        post.put("propertyType", propertyType.getSelectedItem().toString());
        post.put("latitude", latitude);
        post.put("longitude", longitude);
        post.put("amenityIds", selectedAmenityIds());
        post.put("status", "PENDING");
        post.put("createdAt", FieldValue.serverTimestamp());
        if (sd != null) post.put("startDate", new Timestamp(sd));
        if (ed != null) post.put("endDate", new Timestamp(ed));
        uploadImagesAndSave(postRef, post, postId);
    }

    private List<String> selectedAmenityIds() {
        List<String> selected = new ArrayList<>();
        for (Map.Entry<String, CheckBox> entry : amenityChoices.entrySet()) {
            if (entry.getValue().isChecked()) selected.add(entry.getKey());
        }
        return selected;
    }

    private void uploadImagesAndSave(DocumentReference postRef, Map<String, Object> post, String postId) {
        List<Task<Uri>> uploads = new ArrayList<>();
        long timestamp = System.currentTimeMillis();
        for (int i = 0; i < imageUris.size(); i++) {
            Uri imageUri = imageUris.get(i);
            StorageReference reference = storage.getReference()
                    .child("post_images/" + postId + "/" + timestamp + "_" + i + ".jpg");
            uploads.add(reference.putFile(imageUri).continueWithTask(task -> {
                if (!task.isSuccessful() && task.getException() != null) throw task.getException();
                return reference.getDownloadUrl();
            }));
        }
        Task<List<Uri>> allUploads = uploads.isEmpty()
                ? Tasks.<List<Uri>>forResult(Collections.emptyList())
                : Tasks.<Uri>whenAllSuccess(uploads);
        allUploads.addOnSuccessListener(downloadUrls -> {
            WriteBatch batch = db.batch();
            batch.set(postRef, post);
            for (int i = 0; i < downloadUrls.size(); i++) {
                Uri downloadUrl = downloadUrls.get(i);
                DocumentReference imageRef = db.collection("images").document();
                Map<String, Object> image = new HashMap<>();
                image.put("ImgId", imageRef.getId());
                image.put("imgUrl", downloadUrl.toString());
                image.put("orderIndex", i);
                image.put("postId", postId);
                batch.set(imageRef, image);
            }
            batch.commit().addOnSuccessListener(v -> {
                finishPublish(true, "Đăng bài thành công!");
                setResult(RESULT_OK);
                finish();
            }).addOnFailureListener(e -> finishPublish(false, "Đăng thất bại: " + e.getMessage()));
        }).addOnFailureListener(e -> finishPublish(false, "Tải ảnh thất bại: " + e.getMessage()));
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

    private long parsePrice() {
        String value = price.getText().toString().trim();
        if (value.isEmpty()) {
            price.setError("Nhập giá cho toàn bộ chuyến đi");
            return -1;
        }
        try {
            long amount = Long.parseLong(value);
            if (amount < 1) {
                price.setError("Giá phải lớn hơn 0");
                return -1;
            }
            return amount;
        } catch (NumberFormatException error) {
            price.setError("Nhập giá hợp lệ");
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
        price.setText("");
        start.setText("");
        end.setText("");
        propertyType.setSelection(0);
        imageUris.clear();
        refreshImages();
        for (CheckBox choice : amenityChoices.values()) choice.setChecked(false);
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
