package com.example.andemo.menulock;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.andemo.R;
import com.example.andemo.api.ApiClient;
import com.example.andemo.api.MenuLockService;
import com.example.andemo.model.ApiError;
import com.example.andemo.model.MenuAccessResponse;
import com.example.andemo.model.MenuLockStatusResponse;
import com.example.andemo.model.MenuStatusDto;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Danh sách menu có thể khóa (lấy động từ COMMON_CODE) + trạng thái khóa.
 * - Bấm tên menu → kiểm tra khóa → vào màn hình / popup chặn
 * - Nút Khóa / Mở khóa trên title → khóa / mở tất cả (xem {@link FeatureLockFlow})
 */
public class MenuLockActivity extends AppCompatActivity {

    private MenuLockService service;
    private FeatureLockFlow featureLock;
    private TextView tvStatus;
    private ProgressBar progressBar;
    private LinearLayout menuContainer;
    private Button btnFeatureLock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu_lock);

        service = ApiClient.getClient(this).create(MenuLockService.class);
        tvStatus = findViewById(R.id.tvStatus);
        progressBar = findViewById(R.id.progressBar);
        menuContainer = findViewById(R.id.menuContainer);
        btnFeatureLock = findViewById(R.id.btnFeatureLock);

        featureLock = new FeatureLockFlow(this, this::render);
        btnFeatureLock.setOnClickListener(v -> featureLock.onTitleButtonClick());
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        progressBar.setVisibility(View.VISIBLE);
        featureLock.refresh(null);
    }

    // ===================== Render =====================

    private void render(MenuLockStatusResponse status) {
        progressBar.setVisibility(View.GONE);
        btnFeatureLock.setText(featureLock.getButtonText());
        if (status == null) {
            tvStatus.setText("Không tải được trạng thái khóa");
            return;
        }

        String pinText = status.isPinSet() ? "Đã cài mật khẩu 4 số" : "Chưa cài mật khẩu 4 số";
        if (status.isOtpRequired()) pinText += " – ĐANG BỊ KHÓA (cần xác thực OTP)";
        tvStatus.setText("Cửa hàng: " + status.getStoreName() + " (" + status.getStoreId() + ")\n" + pinText);

        menuContainer.removeAllViews();
        for (MenuStatusDto menu : status.getMenus()) {
            menuContainer.addView(buildRow(menu));
        }
    }

    private View buildRow(MenuStatusDto menu) {
        TextView tvName = new TextView(this);
        String type = "MASK".equals(menu.getLockType()) ? "ẩn giá vốn" : "chặn truy cập";
        tvName.setText(menu.getMenuName() + "\n"
                + (menu.isLocked() ? "🔒 Đang khóa" : "🔓 Không khóa") + " · " + type);
        tvName.setTextSize(16);
        tvName.setTextColor(menu.isLocked() ? Color.parseColor("#C62828") : Color.parseColor("#2E7D32"));
        tvName.setPadding(0, 12, 0, 12);
        tvName.setOnClickListener(v -> openMenu(menu));
        return tvName;
    }

    // ===================== Bấm vào menu =====================

    private void openMenu(MenuStatusDto menu) {
        service.checkAccess(menu.getMenuCode()).enqueue(new Callback<MenuAccessResponse>() {
            @Override
            public void onResponse(@NonNull Call<MenuAccessResponse> call,
                                   @NonNull Response<MenuAccessResponse> response) {
                MenuAccessResponse access = response.body();
                if (!response.isSuccessful() || access == null) {
                    showError(ApiError.from(response.errorBody(), response.code()).getMessage());
                    return;
                }
                if (!access.isAllowed()) {
                    // Menu BLOCK bị khóa → popup + chặn không cho vào màn hình
                    new AlertDialog.Builder(MenuLockActivity.this)
                            .setTitle("🔒 " + menu.getMenuName())
                            .setMessage(access.getMessage())
                            .setPositiveButton("Đóng", null)
                            .show();
                    return;
                }
                Intent intent = new Intent(MenuLockActivity.this, MenuScreenActivity.class);
                intent.putExtra(MenuScreenActivity.EXTRA_MENU_CODE, menu.getMenuCode());
                intent.putExtra(MenuScreenActivity.EXTRA_MENU_NAME, menu.getMenuName());
                startActivity(intent);
            }

            @Override
            public void onFailure(@NonNull Call<MenuAccessResponse> call, @NonNull Throwable t) {
                showError("Lỗi kết nối: " + t.getMessage());
            }
        });
    }

    private void showError(String message) {
        new AlertDialog.Builder(this)
                .setMessage(message)
                .setPositiveButton("Đóng", null)
                .show();
    }
}
