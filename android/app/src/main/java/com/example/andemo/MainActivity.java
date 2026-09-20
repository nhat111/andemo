package com.example.andemo;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.andemo.api.ApiClient;
import com.example.andemo.api.ItemService;
import com.example.andemo.model.ItemDto;
import com.example.andemo.util.PreferenceManager;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private PreferenceManager pref;
    private TextView tvItems;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        pref = new PreferenceManager(this);

        if (!pref.isLoggedIn()) {
            goToLogin();
            return;
        }

        TextView tvWelcome = findViewById(R.id.tvWelcome);
        Button btnLoadItems = findViewById(R.id.btnLoadItems);
        Button btnQrScan = findViewById(R.id.btnQrScan);
        Button btnNearby = findViewById(R.id.btnNearby);
        Button btnLogout = findViewById(R.id.btnLogout);
        tvItems = findViewById(R.id.tvItems);

        String role = pref.getRole();
        String username = pref.getUsername();

        tvWelcome.setText("Xin chào " + username + " (" + role + ")");

        btnLoadItems.setOnClickListener(v -> loadItems());
        btnQrScan.setOnClickListener(v ->
                startActivity(new Intent(this, QrScanActivity.class))
        );
        btnNearby.setOnClickListener(v ->
                startActivity(new Intent(this, NearbyActivity.class))
        );

        btnLogout.setOnClickListener(v -> {
            pref.clear();
            Toast.makeText(this, "Đã logout", Toast.LENGTH_SHORT).show();
            goToLogin();
        });

        loadItems();
    }

    private void loadItems() {
        tvItems.setText("Đang tải...");

        ItemService service = ApiClient.getClient(this).create(ItemService.class);
        Call<List<ItemDto>> call = service.getItems();

        call.enqueue(new Callback<List<ItemDto>>() {
            @Override
            public void onResponse(Call<List<ItemDto>> call, Response<List<ItemDto>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<ItemDto> items = response.body();
                    StringBuilder sb = new StringBuilder();

                    String role = pref.getRole();
                    sb.append("Role hiện tại: ").append(role).append("\n");
                    sb.append("Số item nhận được: ").append(items.size()).append("\n\n");

                    for (ItemDto item : items) {
                        sb.append("• ").append(item.getName()).append("\n");
                        sb.append("  ").append(item.getDescription()).append("\n");
                        if (item.isAdminOnly()) {
                            sb.append("  [ADMIN ONLY]\n");
                        }
                        sb.append("\n");
                    }

                    if ("ADMIN".equals(role)) {
                        sb.append("———\nBạn là ADMIN nên thấy cả item ẩn.");
                    } else {
                        sb.append("———\nBạn là USER nên chỉ thấy item công khai.");
                    }

                    tvItems.setText(sb.toString());
                } else {
                    tvItems.setText("Lỗi: " + response.code() + " - Có thể token hết hạn hoặc server lỗi");
                }
            }

            @Override
            public void onFailure(Call<List<ItemDto>> call, Throwable t) {
                tvItems.setText("Lỗi kết nối: " + t.getMessage());
            }
        });
    }

    private void goToLogin() {
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }
}
