package com.example.andemo;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.andemo.api.ApiClient;
import com.example.andemo.api.AuthService;
import com.example.andemo.api.ItemService;
import com.example.andemo.model.ItemDto;
import com.example.andemo.model.RefreshTokenRequest;
import com.example.andemo.command.CommandChannel;
import com.example.andemo.disposal.DisposalListActivity;
import com.example.andemo.migration.NexacroGatewayDemoActivity;
import com.example.andemo.migration.ProductSearchActivity;
import com.example.andemo.requester.FindPdaActivity;
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
        findViewById(R.id.btnProductSearch).setOnClickListener(v ->
                startActivity(new Intent(this, ProductSearchActivity.class))
        );
        findViewById(R.id.btnNexacroGateway).setOnClickListener(v ->
                startActivity(new Intent(this, NexacroGatewayDemoActivity.class))
        );
        findViewById(R.id.btnDisposal).setOnClickListener(v ->
                startActivity(new Intent(this, DisposalListActivity.class))
        );

        // Requester: chỉ quản lý (ADMIN) mới được gửi lệnh tìm PDA; server cũng kiểm tra lại
        Button btnFindPda = findViewById(R.id.btnFindPda);
        if ("ADMIN".equals(role)) {
            btnFindPda.setVisibility(View.VISIBLE);
            btnFindPda.setOnClickListener(v ->
                    startActivity(new Intent(this, FindPdaActivity.class))
            );
        }

        btnLogout.setOnClickListener(v -> {
            CommandChannel.stop(this);
            revokeRefreshToken();
            pref.clear();
            Toast.makeText(this, "Đã logout", Toast.LENGTH_SHORT).show();
            goToLogin();
        });

        loadItems();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Start khi activity đang hiển thị: Android 12+ cho phép start foreground service lúc này.
        // Service đang chạy rồi thì chỉ bắt kịp lệnh 1 lần (poll, hoặc kết nối lại WebSocket).
        if (pref.isLoggedIn()) {
            CommandChannel.start(this);
        }
    }

    /** Thu hồi refresh token trên server. Gửi 1 lần, lỗi thì bỏ qua (token tự hết hạn sau 30 ngày). */
    private void revokeRefreshToken() {
        String refreshToken = pref.getRefreshToken();
        if (refreshToken == null) {
            return;
        }
        AuthService service = ApiClient.getClient(this).create(AuthService.class);
        service.logout(new RefreshTokenRequest(refreshToken)).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
            }
        });
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
