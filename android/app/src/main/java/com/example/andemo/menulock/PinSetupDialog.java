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
import com.example.andemo.model.PinSetupRequest;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Popup xác thực & thiết lập mật khẩu:
 * (3) Nhập mã OTP → (4) Nhập mật khẩu mới → (5) Xác nhận mật khẩu → 확인 → lưu DB.
 * Dùng cho cả cài lần đầu và Quên mật khẩu / Reset PIN.
 * Kết quả trả về qua Fragment Result API với key {@link #REQUEST_KEY}.
 */
public class PinSetupDialog extends DialogFragment {

    public static final String TAG = "PinSetupDialog";
    public static final String REQUEST_KEY = "pin_setup_result";
    public static final String RESULT_SAVED = "saved";

    private static final String ARG_IS_RESET = "is_reset";

    private MenuLockService service;
    private EditText etOtp, etNewPin, etConfirmPin;
    private TextView tvError;
    private boolean submitting;

    public static PinSetupDialog newInstance(boolean isReset) {
        Bundle args = new Bundle();
        args.putBoolean(ARG_IS_RESET, isReset);
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
        tvError = view.findViewById(R.id.tvError);
        view.findViewById(R.id.btnSendOtp).setOnClickListener(v -> requestOtp());

        boolean isReset = requireArguments().getBoolean(ARG_IS_RESET, false);
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(isReset ? "Reset mật khẩu 4 số" : "Xác thực & Thiết lập mật khẩu")
                .setView(view)
                // Listener gán lại trong onShow để không tự đóng popup khi validate lỗi
                .setPositiveButton("확인 (Xác nhận)", null)
                .setNegativeButton("취소", (d, w) -> sendResult(false))
                .create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnShowListener(d ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> submit()));
        return dialog;
    }

    @Override
    public void onCancel(@NonNull DialogInterface dialog) {
        super.onCancel(dialog);
        sendResult(false);
    }

    private void requestOtp() {
        service.requestOtp().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (!isAdded()) return;
                if (response.isSuccessful()) {
                    Toast.makeText(requireContext(),
                            "Đã gửi OTP (demo: 123456)", Toast.LENGTH_LONG).show();
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

    private void submit() {
        if (submitting) return;
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
        setSubmitting(true);
        service.setupPin(new PinSetupRequest(otp, newPin, confirmPin)).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (!isAdded()) return;
                setSubmitting(false);
                if (response.isSuccessful()) {
                    sendResult(true);
                    dismiss();
                } else {
                    showError(ApiError.from(response.errorBody(), response.code()).getMessage());
                }
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
