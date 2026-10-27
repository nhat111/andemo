package com.example.andemo.menulock;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.example.andemo.R;
import com.example.andemo.api.ApiClient;
import com.example.andemo.api.MenuLockService;
import com.example.andemo.model.ApiError;
import com.example.andemo.model.OtpVerifyRequest;
import com.example.andemo.model.PinSetupRequest;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Popup xác thực & thiết lập mật khẩu:
 * (2) Yêu cầu mã xác thực → popup xác nhận gửi OTP tới SĐT chủ cửa hàng
 * (3) Nhập OTP → Xác nhận → (4) Nhập mật khẩu mới → (5) Xác nhận mật khẩu → Xác nhận → lưu DB.
 * Dùng cho cả cài lần đầu và Đặt lại mật khẩu.
 * Kết quả trả về qua Fragment Result API với key {@link #REQUEST_KEY}.
 */
public class PinSetupDialog extends DialogFragment {

    public static final String TAG = "PinSetupDialog";
    public static final String REQUEST_KEY = "pin_setup_result";
    public static final String RESULT_SAVED = "saved";

    private static final String ARG_IS_RESET = "is_reset";
    private static final String ARG_STORE_ID = "store_id";
    private static final String ARG_STORE_NAME = "store_name";
    private static final String ARG_OWNER_PHONE = "owner_phone";
    private static final String STATE_OTP_VERIFIED = "otp_verified";

    private MenuLockService service;
    private EditText etOtp, etNewPin, etConfirmPin;
    private Button btnVerifyOtp;
    private TextView tvOtpVerified, tvError;
    private boolean otpVerified;
    private boolean submitting;

    public static PinSetupDialog newInstance(boolean isReset, String storeId, String storeName, String ownerPhone) {
        Bundle args = new Bundle();
        args.putBoolean(ARG_IS_RESET, isReset);
        args.putString(ARG_STORE_ID, storeId);
        args.putString(ARG_STORE_NAME, storeName);
        args.putString(ARG_OWNER_PHONE, ownerPhone);
        PinSetupDialog dialog = new PinSetupDialog();
        dialog.setArguments(args);
        return dialog;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        service = ApiClient.getClient(requireContext()).create(MenuLockService.class);

        View view = getLayoutInflater().inflate(R.layout.dialog_pin_setup, null);
        etOtp = view.findViewById(R.id.etOtp);
        etNewPin = view.findViewById(R.id.etNewPin);
        etConfirmPin = view.findViewById(R.id.etConfirmPin);
        btnVerifyOtp = view.findViewById(R.id.btnVerifyOtp);
        tvOtpVerified = view.findViewById(R.id.tvOtpVerified);
        tvError = view.findViewById(R.id.tvError);
        view.findViewById(R.id.btnRequestOtp).setOnClickListener(v -> confirmRequestOtp());
        btnVerifyOtp.setOnClickListener(v -> verifyOtp());

        if (savedInstanceState != null && savedInstanceState.getBoolean(STATE_OTP_VERIFIED)) {
            onOtpVerified();
        }

        boolean isReset = requireArguments().getBoolean(ARG_IS_RESET, false);
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(isReset ? "Đặt lại mật khẩu" : "Thiết lập mật khẩu")
                .setView(view)
                // Listener gán lại trong onShow để không tự đóng popup khi validate lỗi
                .setPositiveButton("Xác nhận", null)
                .setNegativeButton("Đóng", (d, w) -> sendResult(false))
                .create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnShowListener(d ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> submit()));
        return dialog;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_OTP_VERIFIED, otpVerified);
    }

    @Override
    public void onCancel(@NonNull DialogInterface dialog) {
        super.onCancel(dialog);
        sendResult(false);
    }

    // ===================== (2) Yêu cầu mã xác thực =====================

    private void confirmRequestOtp() {
        Bundle args = requireArguments();
        String phone = args.getString(ARG_OWNER_PHONE);
        String message = "Mã cửa hàng: " + args.getString(ARG_STORE_ID)
                + "\nTên cửa hàng: " + args.getString(ARG_STORE_NAME)
                + "\nSố điện thoại: " + (phone != null ? phone : "—")
                + "\n\nBạn có muốn yêu cầu xác thực OTP không?";
        new AlertDialog.Builder(requireContext())
                .setTitle("Yêu cầu mã xác thực")
                .setMessage(message)
                .setPositiveButton("Xác nhận", (d, w) -> requestOtp())
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void requestOtp() {
        service.requestOtp().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (!isAdded()) return;
                if (response.isSuccessful()) {
                    Toast.makeText(requireContext(),
                            "Đã gửi mã OTP (demo: 123456)", Toast.LENGTH_LONG).show();
                } else {
                    showError(ApiError.from(response.errorBody(), response.code()).getMessage());
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                showError("Lỗi kết nối: " + t.getMessage());
            }
        });
    }

    // ===================== (3) Xác nhận OTP =====================

    private void verifyOtp() {
        String otp = etOtp.getText().toString().trim();
        if (otp.length() != 6) {
            showError("Vui lòng nhập mã OTP 6 số");
            return;
        }
        tvError.setVisibility(View.GONE);
        btnVerifyOtp.setEnabled(false);
        service.verifyOtp(new OtpVerifyRequest(otp)).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (!isAdded()) return;
                if (response.isSuccessful()) {
                    onOtpVerified();
                } else {
                    btnVerifyOtp.setEnabled(true);
                    ApiError error = ApiError.from(response.errorBody(), response.code());
                    if (isOtpCancelled(error)) etOtp.setText("");
                    showError(error.getMessage());
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                btnVerifyOtp.setEnabled(true);
                showError("Lỗi kết nối: " + t.getMessage());
            }
        });
    }

    /** OTP đúng → khóa ô OTP, mở ô nhập mật khẩu (4)(5) */
    private void onOtpVerified() {
        otpVerified = true;
        etOtp.setEnabled(false);
        btnVerifyOtp.setEnabled(false);
        tvOtpVerified.setVisibility(View.VISIBLE);
        etNewPin.setEnabled(true);
        etConfirmPin.setEnabled(true);
        etNewPin.requestFocus();
    }

    /** Hết hạn / nhập sai quá số lần → mã bị hủy, phải yêu cầu mã mới */
    private boolean isOtpCancelled(ApiError error) {
        return ApiError.OTP_EXPIRED.equals(error.getCode())
                || ApiError.OTP_ATTEMPTS_EXCEEDED.equals(error.getCode());
    }

    private void resetOtp() {
        otpVerified = false;
        etOtp.setText("");
        etOtp.setEnabled(true);
        btnVerifyOtp.setEnabled(true);
        tvOtpVerified.setVisibility(View.GONE);
        etNewPin.setEnabled(false);
        etConfirmPin.setEnabled(false);
    }

    // ===================== (4)(5) Lưu mật khẩu =====================

    private void submit() {
        if (submitting) return;
        String otp = etOtp.getText().toString().trim();
        String newPin = etNewPin.getText().toString();
        String confirmPin = etConfirmPin.getText().toString();

        // Validate sớm ở client, backend vẫn validate lại
        if (!otpVerified) {
            showError("Vui lòng xác thực mã OTP trước");
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
        setSubmitting(true);
        service.setupPin(new PinSetupRequest(otp, newPin, confirmPin)).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (!isAdded()) return;
                setSubmitting(false);
                if (response.isSuccessful()) {
                    sendResult(true);
                    dismiss();
                    return;
                }
                ApiError error = ApiError.from(response.errorBody(), response.code());
                // Mã hết hạn / bị hủy giữa chừng → quay lại bước (2)(3)
                if (isOtpCancelled(error)) resetOtp();
                showError(error.getMessage());
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                setSubmitting(false);
                showError("Lỗi kết nối: " + t.getMessage());
            }
        });
    }

    private void setSubmitting(boolean value) {
        submitting = value;
        AlertDialog dialog = (AlertDialog) getDialog();
        if (dialog == null) return;
        Button btnConfirm = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (btnConfirm != null) btnConfirm.setEnabled(!value);
    }

    private void sendResult(boolean saved) {
        Bundle result = new Bundle();
        result.putBoolean(RESULT_SAVED, saved);
        getParentFragmentManager().setFragmentResult(REQUEST_KEY, result);
    }

    private void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
    }
}
