package com.example.andemo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.andemo.api.ApiClient;
import com.example.andemo.api.LocationService;
import com.example.andemo.model.LocationRequest;
import com.example.andemo.model.NearbyUserDto;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class NearbyActivity extends AppCompatActivity {

    private TextView tvMyLocation, tvNearbyList;
    private FusedLocationProviderClient fusedLocationClient;

    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                Boolean fine = result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
                Boolean coarse = result.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false);
                if (Boolean.TRUE.equals(fine) || Boolean.TRUE.equals(coarse)) {
                    updateLocationAndFindNearby();
                } else {
                    Toast.makeText(this, "Cần quyền Location để tìm bạn quanh đây", Toast.LENGTH_LONG).show();
                    tvMyLocation.setText("Chưa có quyền vị trí");
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nearby);

        tvMyLocation = findViewById(R.id.tvMyLocation);
        tvNearbyList = findViewById(R.id.tvNearbyList);
        Button btnRefresh = findViewById(R.id.btnRefreshNearby);
        Button btnBack = findViewById(R.id.btnBack);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        btnRefresh.setOnClickListener(v -> checkPermissionAndFind());
        btnBack.setOnClickListener(v -> finish());

        // Tự chạy lần đầu
        checkPermissionAndFind();
    }

    private void checkPermissionAndFind() {
        boolean fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;

        if (fine || coarse) {
            updateLocationAndFindNearby();
        } else {
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    private void updateLocationAndFindNearby() {
        tvMyLocation.setText("Đang lấy vị trí...");
        tvNearbyList.setText("Đang tìm...");

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        CancellationTokenSource cts = new CancellationTokenSource();
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.getToken())
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        double lat = location.getLatitude();
                        double lng = location.getLongitude();
                        tvMyLocation.setText(String.format("Vị trí của bạn: %.5f, %.5f", lat, lng));
                        sendLocationThenLoadNearby(lat, lng);
                    } else {
                        // Fallback: dùng last location
                        fusedLocationClient.getLastLocation().addOnSuccessListener(last -> {
                            if (last != null) {
                                double lat = last.getLatitude();
                                double lng = last.getLongitude();
                                tvMyLocation.setText(String.format("Vị trí (last): %.5f, %.5f", lat, lng));
                                sendLocationThenLoadNearby(lat, lng);
                            } else {
                                // Emulator chưa set GPS → dùng vị trí mẫu HCM
                                useMockLocation();
                            }
                        });
                    }
                })
                .addOnFailureListener(e -> useMockLocation());
    }

    /** Khi emulator chưa có GPS thật → dùng tọa độ mẫu gần admin */
    private void useMockLocation() {
        double lat = 10.7780;
        double lng = 106.7015;
        tvMyLocation.setText(String.format("Vị trí mock (HCM): %.5f, %.5f", lat, lng));
        Toast.makeText(this, "Emulator chưa có GPS → dùng vị trí mẫu", Toast.LENGTH_SHORT).show();
        sendLocationThenLoadNearby(lat, lng);
    }

    private void sendLocationThenLoadNearby(double lat, double lng) {
        LocationService service = ApiClient.getClient(this).create(LocationService.class);

        // 1. Cập nhật vị trí lên server
        service.updateLocation(new LocationRequest(lat, lng)).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                // 2. Lấy danh sách nearby (bán kính 10km)
                loadNearby(service);
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                tvNearbyList.setText("Lỗi cập nhật vị trí: " + t.getMessage());
            }
        });
    }

    private void loadNearby(LocationService service) {
        service.getNearby(10.0).enqueue(new Callback<List<NearbyUserDto>>() {
            @Override
            public void onResponse(@NonNull Call<List<NearbyUserDto>> call,
                                   @NonNull Response<List<NearbyUserDto>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<NearbyUserDto> list = response.body();
                    if (list.isEmpty()) {
                        tvNearbyList.setText("Không tìm thấy ai trong bán kính 10km.\n\nThử login user khác hoặc đợi Render redeploy.");
                        return;
                    }

                    StringBuilder sb = new StringBuilder();
                    sb.append("Tìm thấy ").append(list.size()).append(" người:\n\n");
                    for (NearbyUserDto u : list) {
                        sb.append("• ").append(u.getUsername())
                                .append(" (").append(u.getRole()).append(")\n")
                                .append("  Cách bạn: ").append(u.getDistanceKm()).append(" km\n\n");
                    }
                    tvNearbyList.setText(sb.toString());
                } else if (response.code() == 400) {
                    tvNearbyList.setText("Bạn chưa có vị trí trên server. Bấm lại nút cập nhật.");
                } else {
                    tvNearbyList.setText("Lỗi: " + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<NearbyUserDto>> call, @NonNull Throwable t) {
                tvNearbyList.setText("Lỗi kết nối: " + t.getMessage());
            }
        });
    }
}
