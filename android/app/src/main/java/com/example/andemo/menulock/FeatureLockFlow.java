package com.example.andemo.menulock;

import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.andemo.R;
import com.example.andemo.api.ApiClient;
import com.example.andemo.api.MenuLockService;
import com.example.andemo.model.ApiError;
import com.example.andemo.model.LockRequest;
import com.example.andemo.model.MenuLockStatusResponse;
import com.example.andemo.model.MenuStatusDto;
import com.example.andemo.model.UnlockRequest;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Luồng nút "Khóa / Mở khóa" trên title (khóa / mở tất cả menu nhạy cảm cùng lúc):
 * - Khóa: đã cài mật khẩu? N → popup thiết lập mật khẩu (OTP) → Y → popup "Bạn có muốn khóa chức năng?" → khóa
 * - Mở khóa: popup nhập mật khẩu 4 số → Mở khóa / Đóng / Đặt lại mật khẩu (OTP)
 * Gọi {@link #FeatureLockFlow} trong onCreate của Activity (đăng ký Fragment Result listener).
 */
public class FeatureLockFlow {

    public interface Listener {
        /** Trạng thái thay đổi → cập nhật chữ trên nút title / danh sách menu */
        void onStatusChanged(@Nullable MenuLockStatusResponse status);
    }

    // Việc cần làm tiếp sau khi lưu mật khẩu xong
    private enum AfterPinSetup { NONE, CONFIRM_LOCK, SHOW_UNLOCK }

    private final AppCompatActivity activity;
    private final Listener listener;
    private final MenuLockService service;

    private MenuLockStatusResponse status;
    private AfterPinSetup afterPinSetup = AfterPinSetup.NONE;

    public FeatureLockFlow(AppCompatActivity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
        this.service = ApiClient.getClient(activity).create(MenuLockService.class);

        activity.getSupportFragmentManager().setFragmentResultListener(PinSetupDialog.REQUEST_KEY, activity,
                (key, result) -> onPinSetupResult(result.getBoolean(PinSetupDialog.RESULT_SAVED)));
    }

    @Nullable
    public MenuLockStatusResponse getStatus() {
        return status;
    }

    /** Chữ hiển thị trên nút title */
    public String getButtonText() {
        return status != null && status.isAnyLocked() ? "Mở khóa" : "Khóa";
    }

    public void onTitleButtonClick() {
        if (status == null) {
            Toast.makeText(activity, "Đang tải trạng thái, vui lòng thử lại", Toast.LENGTH_SHORT).show();
            refresh(null);
            return;
        }
        if (status.isAnyLocked()) {
            showUnlockDialog();
        } else {
            startLock();
        }
    }

    public void refresh(@Nullable Runnable onLoaded) {
        service.getStatus().enqueue(new Callback<MenuLockStatusResponse>() {
            @Override
            public void onResponse(@NonNull Call<MenuLockStatusResponse> call,
                                   @NonNull Response<MenuLockStatusResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    status = response.body();
                    listener.onStatusChanged(status);
                    if (onLoaded != null) onLoaded.run();
                } else {
                    listener.onStatusChanged(null);
                }
            }

            @Override
            public void onFailure(@NonNull Call<MenuLockStatusResponse> call, @NonNull Throwable t) {
                listener.onStatusChanged(null);
            }
        });
    }

    // ===================== Khóa =====================

    /** Đã cài mật khẩu? N → thiết lập mật khẩu trước, Y → popup xác nhận khóa */
    private void startLock() {
        if (!status.isPinSet()) {
            openPinSetup(AfterPinSetup.CONFIRM_LOCK);
            return;
        }
        confirmLock();
    }

    private void confirmLock() {
        StringBuilder names = new StringBuilder();
        for (MenuStatusDto m : status.getMenus()) {
            if (names.length() > 0) names.append(", ");
            names.append(m.getMenuName());
        }
        new AlertDialog.Builder(activity)
                .setTitle("Bạn có muốn khóa chức năng không?")
                .setMessage("(" + names + ")")
                .setPositiveButton("Xác nhận", (d, w) -> doLock())
                .setNegativeButton("Đóng", null)
                .show();
    }

    private void doLock() {
        List<String> codes = new ArrayList<>();
        for (MenuStatusDto m : status.getMenus()) {
            if (!m.isLocked()) codes.add(m.getMenuCode());
        }
        service.lock(new LockRequest(codes)).enqueue(new Callback<List<MenuStatusDto>>() {
            @Override
            public void onResponse(@NonNull Call<List<MenuStatusDto>> call,
                                   @NonNull Response<List<MenuStatusDto>> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(activity, "Đã khóa chức năng", Toast.LENGTH_SHORT).show();
                    refresh(null);
                    return;
                }
                ApiError error = ApiError.from(response.errorBody(), response.code());
                if (ApiError.PIN_NOT_SET.equals(error.getCode())) {
                    openPinSetup(AfterPinSetup.CONFIRM_LOCK);
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

    /** Popup: Mật khẩu [••••] [Mở khóa] — Đóng / Đặt lại mật khẩu */
    private void showUnlockDialog() {
        View view = activity.getLayoutInflater().inflate(R.layout.dialog_unlock, null);
        EditText etPin = view.findViewById(R.id.etPin);
        Button btnUnlock = view.findViewById(R.id.btnUnlock);
        TextView tvError = view.findViewById(R.id.tvError);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Mở khóa chức năng")
                .setView(view)
                .setPositiveButton("Đặt lại mật khẩu", (d, w) -> openPinSetup(AfterPinSetup.SHOW_UNLOCK))
                .setNegativeButton("Đóng", null)
                .create();
        dialog.setCanceledOnTouchOutside(false);

        if (status.isOtpRequired()) {
            showOtpRequired(tvError, btnUnlock,
                    "Bạn đã nhập sai mật khẩu quá nhiều lần. Vui lòng đặt lại mật khẩu (xác thực OTP).");
        }

        btnUnlock.setOnClickListener(v -> {
            String pin = etPin.getText().toString();
            if (!pin.matches("\\d{4}")) {
                tvError.setText("Mật khẩu phải gồm 4 chữ số");
                tvError.setVisibility(View.VISIBLE);
                return;
            }
            btnUnlock.setEnabled(false);
            doUnlock(pin, dialog, etPin, btnUnlock, tvError);
        });
        dialog.show();
    }

    private void doUnlock(String pin, AlertDialog dialog, EditText etPin, Button btnUnlock, TextView tvError) {
        service.unlock(new UnlockRequest(null, pin)).enqueue(new Callback<List<MenuStatusDto>>() {
            @Override
            public void onResponse(@NonNull Call<List<MenuStatusDto>> call,
                                   @NonNull Response<List<MenuStatusDto>> response) {
                if (response.isSuccessful()) {
                    dialog.dismiss();
                    Toast.makeText(activity, "Đã mở khóa chức năng", Toast.LENGTH_SHORT).show();
                    refresh(null);
                    return;
                }
                btnUnlock.setEnabled(true);
                ApiError error = ApiError.from(response.errorBody(), response.code());
                if (ApiError.OTP_REQUIRED.equals(error.getCode())) {
                    // Sai quá N lần → bắt buộc đặt lại mật khẩu qua OTP
                    showOtpRequired(tvError, btnUnlock, error.getMessage());
                    refresh(null);
                } else if (ApiError.PIN_NOT_SET.equals(error.getCode())) {
                    dialog.dismiss();
                    openPinSetup(AfterPinSetup.NONE);
                } else {
                    // Sai dưới N lần → báo lỗi, cho nhập lại
                    etPin.setText("");
                    tvError.setText(error.getMessage());
                    tvError.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<MenuStatusDto>> call, @NonNull Throwable t) {
                btnUnlock.setEnabled(true);
                tvError.setText("Lỗi kết nối: " + t.getMessage());
                tvError.setVisibility(View.VISIBLE);
            }
        });
    }

    private void showOtpRequired(TextView tvError, Button btnUnlock, String message) {
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
        btnUnlock.setEnabled(false);
    }

    // ===================== Thiết lập / đặt lại mật khẩu =====================

    private void openPinSetup(AfterPinSetup next) {
        if (activity.getSupportFragmentManager().findFragmentByTag(PinSetupDialog.TAG) != null) return;
        afterPinSetup = next;
        PinSetupDialog.newInstance(status.isPinSet(), status.getStoreId(), status.getStoreName(),
                        status.getOwnerPhone())
                .show(activity.getSupportFragmentManager(), PinSetupDialog.TAG);
    }

    private void onPinSetupResult(boolean saved) {
        AfterPinSetup next = afterPinSetup;
        afterPinSetup = AfterPinSetup.NONE;
        if (!saved) return;
        Toast.makeText(activity, "Đã lưu mật khẩu", Toast.LENGTH_SHORT).show();
        refresh(() -> {
            // Lưu mật khẩu xong → quay lại bước đang làm dở
            if (next == AfterPinSetup.CONFIRM_LOCK) confirmLock();
            else if (next == AfterPinSetup.SHOW_UNLOCK && status.isAnyLocked()) showUnlockDialog();
        });
    }

    private void showError(String message) {
        new AlertDialog.Builder(activity)
                .setMessage(message)
                .setPositiveButton("Đóng", null)
                .show();
    }
}
