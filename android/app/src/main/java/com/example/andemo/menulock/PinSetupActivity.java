package com.example.andemo.menulock;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.andemo.R;
import com.example.andemo.api.ApiClient;
import com.example.andemo.api.MenuLockService;
import com.example.andemo.model.ApiError;
import com.example.andemo.model.PinSetupRequest;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Màn hình xác thực & thiết lập mật khẩu:
 * (3) Nhập mã OTP → (4) Nhập mật khẩu mới → (5) Xác nhận mật khẩu → 확인 → lưu DB.
 * Dùng cho cả cài lần đầu và Quên mật khẩu / Reset PIN.
 */
public class PinSetupActivity extends AppCompatActivity {

    public static final String EXTRA_IS_RESET = "is_reset";

    private MenuLockService service;
    private EditText etOtp, etNewPin, etConfirmPin;
    private TextView tvError;
    private Button btnConfirm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pin_setup);

        service = ApiClient.getClient(this).create(MenuLockService.class);
        etOtp = findViewById(R.id.etOtp);
        etNewPin = findViewById(R.id.etNewPin);
        etConfirmPin = findViewById(R.id.etConfirmPin);
        tvError = findViewById(R.id.tvError);
        btnConfirm = findViewById(R.id.btnConfirm);

        if (getIntent().getBooleanExtra(EXTRA_IS_RESET, false)) {
            ((TextView) findViewById(R.id.tvTitle)).setText("Reset mật khẩu 4 số");
        }

        findViewById(R.id.btnSendOtp).setOnClickListener(v -> requestOtp());
        btnConfirm.setOnClickListener(v -> submit());
        findViewById(R.id.btnBack).setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });
    }

    private void requestOtp() {
        service.requestOtp().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(PinSetupActivity.this,
                            "Đã gửi OTP (demo: 123456)", Toast.LENGTH_LONG).show();
                } else {
                    showError(ApiError.from(response.errorBody(), response.code()).getMessage());
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                showError("Lỗi kết nối: " + t.getMessage());
            }
        });
    }

    private void submit() {
        String otp = etOtp.getText().toString().trim();
        String newPin = etNewPin.getText().toString();
        String confirmPin = etConfirmPin.getText().toString();

        // Validate sớm ở client, backend vẫn validate lại
        if (otp.length() != 6) {
            showError("Vui lòng nhập mã OTP 6 số");
            return;
        }
        if (!newPin.matches("\\d{4}")) {
            showError("Mật khẩu phải gồm đúng 4 chữ số");
            return;
        }
        if (!newPin.equals(confirmPin)) {
            showError("Mật khẩu xác nhận không khớp");
            return;
        }

        tvError.setVisibility(View.GONE);
        btnConfirm.setEnabled(false);
        service.setupPin(new PinSetupRequest(otp, newPin, confirmPin)).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                btnConfirm.setEnabled(true);
                if (response.isSuccessful()) {
                    setResult(RESULT_OK);
                    finish();
                } else {
                    showError(ApiError.from(response.errorBody(), response.code()).getMessage());
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                btnConfirm.setEnabled(true);
                showError("Lỗi kết nối: " + t.getMessage());
            }
        });
    }

    private void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
    }
}
