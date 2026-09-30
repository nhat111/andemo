package com.example.andemo.disposal;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.andemo.R;
import com.example.andemo.api.ApiClient;
import com.example.andemo.api.DisposalApi;
import com.example.andemo.model.ApiErrorDto;
import com.example.andemo.model.DisposalActionRequest;
import com.example.andemo.model.DisposalDetailDto;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 폐기상세: xem phiếu + các thao tác theo trạng thái:
 * 등록 → Sửa / Hủy phiếu (người đăng ký, 점장), Xác nhận (점장); 확정 → Hủy xác nhận (점장, ngày chưa 마감).
 * Nút nào hiện do server quyết định (actions), app không tự suy ra quyền.
 */
public class DisposalDetailActivity extends AppCompatActivity {

    public static final String EXTRA_DISPOSAL_NO = "disposalNo";

    private DisposalApi api;
    private String disposalNo;
    /** Dữ liệu đang hiển thị: version gửi kèm mọi thao tác ghi */
    private DisposalDetailDto current;

    private TextView tvNo, tvStatus, tvInfo, tvIssues, tvTotal;
    private ProgressBar progress;
    private Button btnEdit, btnCancel, btnConfirm, btnCancelConfirm;
    private DisposalLineAdapter lineAdapter;

    private interface Action {
        Call<DisposalDetailDto> call(DisposalActionRequest body);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_disposal_detail);
        setTitle("Chi tiết phiếu hủy");

        disposalNo = getIntent().getStringExtra(EXTRA_DISPOSAL_NO);
        api = ApiClient.getClient(this).create(DisposalApi.class);

        tvNo = findViewById(R.id.tvNo);
        tvStatus = findViewById(R.id.tvStatus);
        tvInfo = findViewById(R.id.tvInfo);
        tvIssues = findViewById(R.id.tvIssues);
        tvTotal = findViewById(R.id.tvTotal);
        progress = findViewById(R.id.progress);
        btnEdit = findViewById(R.id.btnEdit);
        btnCancel = findViewById(R.id.btnCancel);
        btnConfirm = findViewById(R.id.btnConfirm);
        btnCancelConfirm = findViewById(R.id.btnCancelConfirm);

        RecyclerView rv = findViewById(R.id.rvLines);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        lineAdapter = new DisposalLineAdapter();
        rv.setAdapter(lineAdapter);

        btnEdit.setOnClickListener(v -> {
            Intent intent = new Intent(this, DisposalEditActivity.class);
            intent.putExtra(DisposalEditActivity.EXTRA_DISPOSAL_NO, disposalNo);
            startActivity(intent);
        });
        btnCancel.setOnClickListener(v -> askReason("Hủy phiếu (취소)",
                "Phiếu sẽ chuyển sang 취소, không dùng được nữa. Tồn kho không thay đổi.",
                body -> api.cancel(disposalNo, body), "Đã hủy phiếu"));
        btnConfirm.setOnClickListener(v -> askConfirm());
        btnCancelConfirm.setOnClickListener(v -> askReason("Hủy xác nhận (확정취소)",
                "Tồn kho được cộng lại, 수불 ghi thêm dòng đảo. Phiếu quay về 등록.",
                body -> api.cancelConfirm(disposalNo, body), "Đã hủy xác nhận, tồn kho đã được cộng lại"));
    }

    /** Quay lại từ màn sửa: tải lại */
    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        setLoading(true);
        api.detail(disposalNo).enqueue(new Callback<DisposalDetailDto>() {
            @Override
            public void onResponse(Call<DisposalDetailDto> call, Response<DisposalDetailDto> response) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                setLoading(false);
                if (!response.isSuccessful() || response.body() == null) {
                    Toast.makeText(DisposalDetailActivity.this, ApiErrorDto.from(response).getMessage(),
                            Toast.LENGTH_LONG).show();
                    return;
                }
                render(response.body());
            }

            @Override
            public void onFailure(Call<DisposalDetailDto> call, Throwable t) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                setLoading(false);
                Toast.makeText(DisposalDetailActivity.this, "Không kết nối được server", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void render(DisposalDetailDto d) {
        current = d;
        tvNo.setText(d.getDisposalNo());
        tvStatus.setText(DisposalUi.statusLabel(d.getStatus()));
        tvStatus.setTextColor(DisposalUi.statusColor(d.getStatus()));

        StringBuilder info = new StringBuilder()
                .append("영업일자: ").append(d.getBusinessDate()).append(d.isClosed() ? " (đã 마감)" : "")
                .append(" · Cửa hàng ").append(d.getStoreCode())
                .append("\nĐăng ký: ").append(d.getRegisteredBy()).append(" · ").append(DisposalUi.formatTime(d.getRegisteredAt()));
        if (d.getUpdatedBy() != null) {
            info.append("\nSửa: ").append(d.getUpdatedBy()).append(" · ").append(DisposalUi.formatTime(d.getUpdatedAt()));
        }
        if (d.getConfirmedBy() != null) {
            info.append("\nXác nhận: ").append(d.getConfirmedBy()).append(" · ").append(DisposalUi.formatTime(d.getConfirmedAt()));
        }
        if (d.getConfirmCancelledBy() != null) {
            info.append("\nHủy xác nhận: ").append(d.getConfirmCancelledBy()).append(" · ")
                    .append(DisposalUi.formatTime(d.getConfirmCancelledAt())).append(" · ").append(d.getConfirmCancelReason());
        }
        if (d.getCancelledBy() != null) {
            info.append("\nHủy phiếu: ").append(d.getCancelledBy()).append(" · ")
                    .append(DisposalUi.formatTime(d.getCancelledAt())).append(" · ").append(d.getCancelReason());
        }
        if (d.getRemark() != null) {
            info.append("\nGhi chú: ").append(d.getRemark());
        }
        tvInfo.setText(info);
        tvTotal.setText(d.getItems().size() + " mặt hàng · SL " + d.getTotalQty()
                + " · giá vốn " + DisposalUi.won(d.getTotalCostAmount())
                + " · giá bán " + DisposalUi.won(d.getTotalSaleAmount()));

        boolean registered = "REGISTERED".equals(d.getStatus());
        if (registered && d.getIssues() != null && !d.getIssues().isEmpty()) {
            tvIssues.setText("Chưa xác nhận được:\n• " + TextUtils.join("\n• ", d.getIssues()));
            tvIssues.setVisibility(View.VISIBLE);
        } else {
            tvIssues.setVisibility(View.GONE);
        }
        lineAdapter.submit(d.getItems(), registered);

        DisposalDetailDto.Actions a = d.getActions();
        btnEdit.setVisibility(a.isEdit() ? View.VISIBLE : View.GONE);
        btnCancel.setVisibility(a.isCancel() ? View.VISIBLE : View.GONE);
        btnConfirm.setVisibility(a.isConfirm() ? View.VISIBLE : View.GONE);
        // Thiếu tồn: vẫn hiện nút để thấy chức năng, nhưng tắt (server cũng chặn lại)
        btnConfirm.setEnabled(a.isConfirm() && (d.getIssues() == null || d.getIssues().isEmpty()));
        btnCancelConfirm.setVisibility(a.isCancelConfirm() ? View.VISIBLE : View.GONE);
        setButtonsEnabled(true);
    }

    /** R10: phiếu có giá vốn từ mức này phải xác nhận thêm 1 lần (bản Nexacro: MSG_CONFIRM_BIG) */
    private static final long BIG_AMOUNT = 100_000;

    private void askConfirm() {
        new AlertDialog.Builder(this)
                .setTitle("Xác nhận hủy hàng (확정)")
                .setMessage("Xác nhận phiếu " + current.getDisposalNo() + "?\n\n"
                        + current.getItems().size() + " mặt hàng, SL " + current.getTotalQty()
                        + ", giá vốn " + DisposalUi.won(current.getTotalCostAmount())
                        + " sẽ bị TRỪ khỏi tồn kho.\nHủy xác nhận được tới khi chốt sổ (마감).")
                .setPositiveButton("Xác nhận", (dialog, which) -> {
                    if (current.getTotalCostAmount() >= BIG_AMOUNT) {
                        askConfirmBig();
                    } else {
                        doConfirm();
                    }
                })
                .setNegativeButton("Không", null)
                .show();
    }

    /** R10: hỏi lần 2 với phiếu giá trị lớn */
    private void askConfirmBig() {
        new AlertDialog.Builder(this)
                .setTitle("Phiếu hủy giá trị lớn (고액 폐기)")
                .setMessage("Giá vốn " + DisposalUi.won(current.getTotalCostAmount())
                        + ". Bạn chắc chắn muốn xác nhận?")
                .setPositiveButton("Chắc chắn", (dialog, which) -> doConfirm())
                .setNegativeButton("Không", null)
                .show();
    }

    private void doConfirm() {
        run(api.confirm(disposalNo, new DisposalActionRequest(current.getVersion(), null)),
                "Đã xác nhận, tồn kho đã được trừ");
    }

    /** Hủy phiếu / hủy xác nhận: bắt buộc nhập lý do (취소사유) */
    private void askReason(String title, String message, Action action, String successMessage) {
        EditText input = new EditText(this);
        input.setHint("Lý do (bắt buộc)");
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setView(input)
                .setPositiveButton("Đồng ý", null) // gắn listener sau để không tự đóng khi thiếu lý do
                .setNegativeButton("Không", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String reason = input.getText().toString().trim();
            if (reason.isEmpty()) {
                input.setError("Nhập lý do");
                return;
            }
            dialog.dismiss();
            run(action.call(new DisposalActionRequest(current.getVersion(), reason)), successMessage);
        }));
        dialog.show();
    }

    private void run(Call<DisposalDetailDto> call, String successMessage) {
        setLoading(true);
        call.enqueue(new Callback<DisposalDetailDto>() {
            @Override
            public void onResponse(Call<DisposalDetailDto> c, Response<DisposalDetailDto> response) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                setLoading(false);
                if (response.isSuccessful() && response.body() != null) {
                    Toast.makeText(DisposalDetailActivity.this, successMessage, Toast.LENGTH_LONG).show();
                    render(response.body());
                    return;
                }
                showError(ApiErrorDto.from(response));
            }

            @Override
            public void onFailure(Call<DisposalDetailDto> c, Throwable t) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                setLoading(false);
                // Không biết server đã xử lý chưa (mất mạng giữa chừng): tải lại trạng thái thật, không gửi lại mù
                Toast.makeText(DisposalDetailActivity.this,
                        "Mất kết nối. Đang tải lại để kiểm tra trạng thái…", Toast.LENGTH_LONG).show();
                load();
            }
        });
    }

    /** 409 (trạng thái / dữ liệu cũ / 마감), 422 (thiếu tồn), 403, 400 */
    private void showError(ApiErrorDto error) {
        String message = error.getMessage();
        if (!error.getDetails().isEmpty()) {
            message += "\n\n• " + TextUtils.join("\n• ", error.getDetails());
        }
        new AlertDialog.Builder(this)
                .setTitle("Không thực hiện được")
                .setMessage(message)
                .setPositiveButton("Tải lại", (dialog, which) -> load())
                .show();
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) {
            setButtonsEnabled(false); // chống bấm 2 lần khi đang gửi
        }
    }

    private void setButtonsEnabled(boolean enabled) {
        btnEdit.setEnabled(enabled);
        btnCancel.setEnabled(enabled);
        btnCancelConfirm.setEnabled(enabled);
        if (!enabled) {
            btnConfirm.setEnabled(false);
        }
    }
}
