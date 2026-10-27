package com.example.andemo.menulock;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.andemo.R;
import com.example.andemo.api.ApiClient;
import com.example.andemo.api.MenuLockService;
import com.example.andemo.model.ApiError;
import com.example.andemo.model.LockRequest;
import com.example.andemo.model.MenuAccessResponse;
import com.example.andemo.model.MenuLockStatusResponse;
import com.example.andemo.model.MenuStatusDto;
import com.example.andemo.model.UnlockRequest;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Danh sách menu có thể khóa (lấy động từ COMMON_CODE).
 * - Bấm tên menu → kiểm tra khóa → vào màn hình / popup chặn
 * - Nút 잠금 → (chưa có PIN thì popup thiết lập PIN qua OTP) → popup xác nhận → khóa
 * - Nút 해제 → nhập PIN 4 số → mở khóa (sai quá N lần → bắt OTP)
 */
public class MenuLockActivity extends AppCompatActivity {

    private MenuLockService service;
    private TextView tvStatus;
    private ProgressBar progressBar;
    private LinearLayout menuContainer;

    private MenuLockStatusResponse status;

    // Menu đang chờ khóa trong lúc user đi thiết lập PIN
    private List<String> pendingLockCodes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu_lock);

        service = ApiClient.getClient(this).create(MenuLockService.class);
        tvStatus = findViewById(R.id.tvStatus);
        progressBar = findViewById(R.id.progressBar);
        menuContainer = findViewById(R.id.menuContainer);

        getSupportFragmentManager().setFragmentResultListener(PinSetupDialog.REQUEST_KEY, this,
                (key, result) -> onPinSetupResult(result.getBoolean(PinSetupDialog.RESULT_SAVED)));

        findViewById(R.id.btnLockAll).setOnClickListener(v -> {
            if (status == null) return;
            List<String> codes = new ArrayList<>();
            for (MenuStatusDto m : status.getMenus()) {
                if (!m.isLocked()) codes.add(m.getMenuCode());
            }
            if (codes.isEmpty()) {
                Toast.makeText(this, "Tất cả menu đã được khóa", Toast.LENGTH_SHORT).show();
                return;
            }
            startLock(codes);
        });
        findViewById(R.id.btnResetPin).setOnClickListener(v -> openPinSetup(null));
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        loadStatus(null);
    }

    // ===================== Load + render =====================

    private void loadStatus(Runnable onLoaded) {
        progressBar.setVisibility(View.VISIBLE);
        service.getStatus().enqueue(new Callback<MenuLockStatusResponse>() {
            @Override
            public void onResponse(@NonNull Call<MenuLockStatusResponse> call,
                                   @NonNull Response<MenuLockStatusResponse> response) {
                progressBar.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null) {
                    status = response.body();
                    render();
                    if (onLoaded != null) onLoaded.run();
                } else {
                    tvStatus.setText("Lỗi: " + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<MenuLockStatusResponse> call, @NonNull Throwable t) {
                progressBar.setVisibility(View.GONE);
                tvStatus.setText("Lỗi kết nối: " + t.getMessage());
            }
        });
    }

    private void render() {
        String pinText = status.isPinSet() ? "Đã cài mật khẩu 4 số" : "Chưa cài mật khẩu 4 số";
        if (status.isOtpRequired()) pinText += " – ĐANG BỊ KHÓA (cần xác thực OTP)";
        tvStatus.setText("Cửa hàng: " + status.getStoreId() + "\n" + pinText);

        menuContainer.removeAllViews();
        for (MenuStatusDto menu : status.getMenus()) {
            menuContainer.addView(buildRow(menu));
        }
    }

    private View buildRow(MenuStatusDto menu) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 12, 0, 12);

        TextView tvName = new TextView(this);
        String type = "MASK".equals(menu.getLockType()) ? "ẩn giá vốn" : "chặn truy cập";
        tvName.setText(menu.getMenuName() + "\n"
                + (menu.isLocked() ? "🔒 Đang khóa" : "🔓 Không khóa") + " · " + type);
        tvName.setTextSize(16);
        tvName.setTextColor(menu.isLocked() ? Color.parseColor("#C62828") : Color.parseColor("#2E7D32"));
        tvName.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        tvName.setOnClickListener(v -> openMenu(menu));

        Button btnToggle = new Button(this);
        btnToggle.setText(menu.isLocked() ? "해제 (Mở khóa)" : "잠금 (Khóa)");
        btnToggle.setOnClickListener(v -> {
            if (menu.isLocked()) {
                startUnlock(menu);
            } else {
                List<String> codes = new ArrayList<>();
                codes.add(menu.getMenuCode());
                startLock(codes);
            }
        });

        row.addView(tvName);
        row.addView(btnToggle);
        return row;
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
                            .setPositiveButton("확인", null)
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

    // ===================== Khóa =====================

    /** 비번 설정 ? → N: thiết lập PIN trước, Y: popup xác nhận */
    private void startLock(List<String> codes) {
        if (status != null && !status.isPinSet()) {
            Toast.makeText(this, "Vui lòng thiết lập mật khẩu 4 số trước", Toast.LENGTH_SHORT).show();
            openPinSetup(codes);
            return;
        }
        confirmLock(codes);
    }

    private void confirmLock(List<String> codes) {
        StringBuilder names = new StringBuilder();
        for (MenuStatusDto m : status.getMenus()) {
            if (codes.contains(m.getMenuCode())) {
                if (names.length() > 0) names.append(", ");
                names.append(m.getMenuName());
            }
        }
        new AlertDialog.Builder(this)
                .setTitle("기능을 잠금 하시겠습니까?")
                .setMessage("Khóa tính năng: " + names)
                .setPositiveButton("확인", (d, w) -> doLock(codes))
                .setNegativeButton("취소", null)
                .show();
    }

    private void doLock(List<String> codes) {
        service.lock(new LockRequest(codes)).enqueue(new Callback<List<MenuStatusDto>>() {
            @Override
            public void onResponse(@NonNull Call<List<MenuStatusDto>> call,
                                   @NonNull Response<List<MenuStatusDto>> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(MenuLockActivity.this, "Đã khóa", Toast.LENGTH_SHORT).show();
                    loadStatus(null);
                    return;
                }
                ApiError error = ApiError.from(response.errorBody(), response.code());
                if (ApiError.PIN_NOT_SET.equals(error.getCode())) {
                    openPinSetup(codes);
                } else {
                    showError(error.getMessage());
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<MenuStatusDto>> call, @NonNull Throwable t) {
                showError("Lỗi kết nối: " + t.getMessage());
            }
        });
    }

    // ===================== Mở khóa =====================

    private void startUnlock(MenuStatusDto menu) {
        if (status != null && status.isOtpRequired()) {
            askOtpReset("Bạn đã nhập sai mật khẩu quá nhiều lần. Vui lòng xác thực OTP và cài lại mật khẩu.");
            return;
        }
        showPinDialog(menu, null);
    }

    /** Popup nhập mật khẩu 4 số (4자리 비밀번호 입력) */
    private void showPinDialog(MenuStatusDto menu, String errorMessage) {
        EditText etPin = new EditText(this);
        etPin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        etPin.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
        etPin.setHint("••••");
        etPin.setGravity(Gravity.CENTER);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(48, 16, 48, 0);
        if (errorMessage != null) {
            TextView tvError = new TextView(this);
            tvError.setText(errorMessage);
            tvError.setTextColor(Color.parseColor("#F44336"));
            box.addView(tvError);
        }
        box.addView(etPin);

        new AlertDialog.Builder(this)
                .setTitle("4자리 비밀번호 입력")
                .setMessage("Nhập mật khẩu 4 số để mở khóa: " + menu.getMenuName())
                .setView(box)
                .setPositiveButton("확인", (d, w) -> {
                    String pin = etPin.getText().toString();
                    if (pin.length() != 4) {
                        showPinDialog(menu, "Mật khẩu phải gồm 4 chữ số");
                        return;
                    }
                    doUnlock(menu, pin);
                })
                .setNeutralButton("Quên mật khẩu / Reset PIN", (d, w) -> openPinSetup(null))
                .setNegativeButton("취소", null)
                .show();
    }

    private void doUnlock(MenuStatusDto menu, String pin) {
        service.unlock(new UnlockRequest(menu.getMenuCode(), pin)).enqueue(new Callback<List<MenuStatusDto>>() {
            @Override
            public void onResponse(@NonNull Call<List<MenuStatusDto>> call,
                                   @NonNull Response<List<MenuStatusDto>> response) {
                if (response.isSuccessful()) {
                    // Đúng PIN → nút đổi sang 잠금 & mở chặn truy cập / hiển thị giá vốn
                    Toast.makeText(MenuLockActivity.this, "Đã mở khóa", Toast.LENGTH_SHORT).show();
                    loadStatus(null);
                    return;
                }
                ApiError error = ApiError.from(response.errorBody(), response.code());
                if (ApiError.PIN_WRONG.equals(error.getCode())) {
                    // Sai dưới N lần → báo lỗi, cho nhập lại
                    showPinDialog(menu, error.getMessage());
                } else if (ApiError.OTP_REQUIRED.equals(error.getCode())) {
                    // Sai quá N lần → bắt buộc OTP
                    loadStatus(null);
                    askOtpReset(error.getMessage());
                } else if (ApiError.PIN_NOT_SET.equals(error.getCode())) {
                    openPinSetup(null);
                } else {
                    showError(error.getMessage());
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<MenuStatusDto>> call, @NonNull Throwable t) {
                showError("Lỗi kết nối: " + t.getMessage());
            }
        });
    }

    // ===================== helper =====================

    private void askOtpReset(String message) {
        new AlertDialog.Builder(this)
                .setTitle("Khóa tạm thời")
                .setMessage(message)
                .setPositiveButton("Xác thực OTP", (d, w) -> openPinSetup(null))
                .setNegativeButton("취소", null)
                .show();
    }

    private void openPinSetup(List<String> codesToLockAfter) {
        if (getSupportFragmentManager().findFragmentByTag(PinSetupDialog.TAG) != null) return;
        pendingLockCodes = codesToLockAfter;
        PinSetupDialog.newInstance(status != null && status.isPinSet())
                .show(getSupportFragmentManager(), PinSetupDialog.TAG);
    }

    private void onPinSetupResult(boolean saved) {
        List<String> codes = pendingLockCodes;
        pendingLockCodes = null;
        if (!saved) return;
        Toast.makeText(this, "Đã lưu mật khẩu", Toast.LENGTH_SHORT).show();
        loadStatus(() -> {
            // Lưu PIN xong → chuyển tiếp sang popup xác nhận khóa
            if (codes != null) confirmLock(codes);
        });
    }

    private void showError(String message) {
        new AlertDialog.Builder(this)
                .setMessage(message)
                .setPositiveButton("확인", null)
                .show();
    }
}
