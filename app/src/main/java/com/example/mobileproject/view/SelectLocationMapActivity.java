package com.example.mobileproject.view;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.mobileproject.R;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.List;
import java.util.Locale;

public class SelectLocationMapActivity extends AppCompatActivity implements OnMapReadyCallback {

    public static final String EXTRA_LATITUDE = "LATITUDE";
    public static final String EXTRA_LONGITUDE = "LONGITUDE";
    public static final String EXTRA_ADDRESS = "ADDRESS";

    private GoogleMap googleMap;
    private Marker selectedMarker;
    private LatLng selectedLatLng;
    private String selectedAddress = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_location_map);

        double initialLat = getIntent().getDoubleExtra(EXTRA_LATITUDE, 10.7769);
        double initialLng = getIntent().getDoubleExtra(EXTRA_LONGITUDE, 106.7009);
        if (initialLat == 0 && initialLng == 0) {
            initialLat = 10.7769;
            initialLng = 106.7009;
        }
        selectedLatLng = new LatLng(initialLat, initialLng);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.selectLocationMap);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        Button btnCurrentLocation = findViewById(R.id.btnCurrentLocation);
        if (btnCurrentLocation != null) {
            btnCurrentLocation.setOnClickListener(v -> moveToCurrentLocation());
        }

        Button btnConfirmLocation = findViewById(R.id.btnConfirmLocation);
        if (btnConfirmLocation != null) {
            btnConfirmLocation.setOnClickListener(v -> confirmLocation());
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        googleMap = map;
        googleMap.getUiSettings().setZoomControlsEnabled(true);

        updateMarker(selectedLatLng);
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(selectedLatLng, 15f));

        googleMap.setOnMapClickListener(this::updateMarker);

        googleMap.setOnMarkerDragListener(new GoogleMap.OnMarkerDragListener() {
            @Override public void onMarkerDragStart(@NonNull Marker marker) { }
            @Override public void onMarkerDrag(@NonNull Marker marker) { }
            @Override
            public void onMarkerDragEnd(@NonNull Marker marker) {
                updateMarker(marker.getPosition());
            }
        });
    }

    private void updateMarker(LatLng latLng) {
        selectedLatLng = latLng;
        if (selectedMarker != null) {
            selectedMarker.remove();
        }
        if (googleMap != null) {
            selectedMarker = googleMap.addMarker(new MarkerOptions()
                    .position(latLng)
                    .title("Vị trí đã chọn")
                    .draggable(true));
        }
        fetchAddress(latLng);
    }

    private void fetchAddress(LatLng latLng) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        if (Build.VERSION.SDK_INT >= 33) {
            geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1, results -> {
                if (results != null && !results.isEmpty()) {
                    selectedAddress = results.get(0).getAddressLine(0);
                }
            });
        } else {
            try {
                List<Address> addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1);
                if (addresses != null && !addresses.isEmpty()) {
                    selectedAddress = addresses.get(0).getAddressLine(0);
                }
            } catch (Exception ignored) { }
        }
    }

    private void moveToCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                LocationServices.getFusedLocationProviderClient(this).getLastLocation()
                        .addOnSuccessListener(loc -> {
                            if (loc != null) {
                                LatLng current = new LatLng(loc.getLatitude(), loc.getLongitude());
                                updateMarker(current);
                                if (googleMap != null) {
                                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(current, 15f));
                                }
                            } else {
                                Toast.makeText(this, "Không tìm thấy vị trí hiện tại.", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .addOnFailureListener(e -> Toast.makeText(this, "Lỗi vị trí: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            } catch (SecurityException ignored) { }
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1001);
        }
    }

    private void confirmLocation() {
        if (selectedLatLng == null) {
            Toast.makeText(this, "Chưa chọn vị trí.", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent resultIntent = new Intent();
        resultIntent.putExtra(EXTRA_LATITUDE, selectedLatLng.latitude);
        resultIntent.putExtra(EXTRA_LONGITUDE, selectedLatLng.longitude);
        resultIntent.putExtra(EXTRA_ADDRESS, selectedAddress == null ? "" : selectedAddress);
        setResult(RESULT_OK, resultIntent);
        finish();
    }
}
