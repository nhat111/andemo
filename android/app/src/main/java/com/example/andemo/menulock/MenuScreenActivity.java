package com.example.andemo.menulock;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.andemo.R;
import com.example.andemo.api.ApiClient;
import com.example.andemo.api.MenuLockService;
import com.example.andemo.model.ApiError;
import com.example.andemo.model.MenuDataDto;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Màn hình nghiệp vụ của 1 menu (dữ liệu mock từ backend).
 * Backend trả 423 nếu menu bị khóa; với menu Giá vốn bị khóa thì cột Giá vốn / Tỷ suất LN để trống.
 */
public class MenuScreenActivity extends AppCompatActivity {

    public static final String EXTRA_MENU_CODE = "menu_code";
    public static final String EXTRA_MENU_NAME = "menu_name";

    private TextView tvNotice, tvContent;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu_screen);

        String menuCode = getIntent().getStringExtra(EXTRA_MENU_CODE);
        String menuName = getIntent().getStringExtra(EXTRA_MENU_NAME);

        ((TextView) findViewById(R.id.tvTitle)).setText(menuName != null ? menuName : menuCode);
        tvNotice = findViewById(R.id.tvNotice);
        tvContent = findViewById(R.id.tvContent);
        progressBar = findViewById(R.id.progressBar);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        load(menuCode);
    }

    private void load(String menuCode) {
        progressBar.setVisibility(View.VISIBLE);
        MenuLockService service = ApiClient.getClient(this).create(MenuLockService.class);
        service.getMenuData(menuCode).enqueue(new Callback<MenuDataDto>() {
            @Override
            public void onResponse(@NonNull Call<MenuDataDto> call, @NonNull Response<MenuDataDto> response) {
                progressBar.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null) {
                    bind(response.body());
                    return;
                }
                ApiError error = ApiError.from(response.errorBody(), response.code());
                if (ApiError.MENU_LOCKED.equals(error.getCode())) {
                    // Bị khóa trong lúc đang mở (hoặc bỏ qua bước check) → chặn và quay lại
                    new AlertDialog.Builder(MenuScreenActivity.this)
                            .setMessage(error.getMessage())
                            .setCancelable(false)
                            .setPositiveButton("Đóng", (d, w) -> finish())
                            .show();
                } else {
                    tvContent.setText(error.getMessage());
                }
            }

            @Override
            public void onFailure(@NonNull Call<MenuDataDto> call, @NonNull Throwable t) {
                progressBar.setVisibility(View.GONE);
                tvContent.setText("Lỗi kết nối: " + t.getMessage());
            }
        });
    }

    private void bind(MenuDataDto data) {
        if (data.isMasked()) {
            tvNotice.setText("🔒 Giá vốn & Tỷ suất lợi nhuận đang bị ẩn (menu Giá vốn đã khóa)");
            tvNotice.setVisibility(View.VISIBLE);
        }

        List<List<String>> rows = data.getRows();
        if (rows == null || rows.isEmpty()) {
            tvContent.setText("Không có dữ liệu");
            return;
        }

        List<String> columns = data.getColumns();
        StringBuilder sb = new StringBuilder();
        for (List<String> row : rows) {
            for (int i = 0; i < row.size(); i++) {
                String label = columns != null && i < columns.size() ? columns.get(i) : "";
                String value = row.get(i) == null || row.get(i).isEmpty() ? "—" : row.get(i);
                sb.append(label).append(": ").append(value).append("\n");
            }
            sb.append("\n");
        }
        tvContent.setText(sb.toString());
    }
}
