package com.example.andemo.disposal;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
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
import com.example.andemo.model.ConfirmDisposalRequest;
import com.example.andemo.model.DisposalDetailDto;
import com.example.andemo.util.PreferenceManager;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Task 17 – Disposal Detail View + Confirm Button (+ Status Validation).
 * Bấm Xác nhận → server trừ tồn kho và ghi lịch sử (Task 18) trong cùng 1 transaction.
 */
public class DisposalDetailActivity extends AppCompatActivity {

    public static final String EXTRA_DISPOSAL_NO = "disposalNo";

    private DisposalApi api;
    private String disposalNo;
    private boolean isAdmin;
    /** Dữ liệu đang hiển thị: version gửi kèm khi xác nhận */
    private DisposalDetailDto current;

    private TextView tvNo, tvStatus, tvInfo, tvIssues;
    private ProgressBar progress;
    private Button btnConfirm;
    private DisposalLineAdapter lineAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_disposal_detail);
        setTitle("Chi tiết phiếu hủy");

        disposalNo = getIntent().getStringExtra(EXTRA_DISPOSAL_NO);
        isAdmin = "ADMIN".equals(new PreferenceManager(this).getRole());
        api = ApiClient.getClient(this).create(DisposalApi.class);

        tvNo = findViewById(R.id.tvNo);
        tvStatus = findViewById(R.id.tvStatus);
        tvInfo = findViewById(R.id.tvInfo);
        tvIssues = findViewById(R.id.tvIssues);
        progress = findViewById(R.id.progress);
        btnConfirm = findViewById(R.id.btnConfirm);

        RecyclerView rv = findViewById(R.id.rvLines);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        lineAdapter = new DisposalLineAdapter();
        rv.setAdapter(lineAdapter);

        btnConfirm.setOnClickListener(v -> askConfirm());
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
                .append("Kho: ").append(d.getWarehouseCode())
                .append("\nLý do: ").append(d.getReason())
                .append("\nNgười yêu cầu: ").append(d.getRequestedBy())
                .append(" · ").append(DisposalUi.formatTime(d.getRequestedAt()));
        if (d.getConfirmedBy() != null) {
            info.append("\nXác nhận: ").append(d.getConfirmedBy())
                    .append(" · ").append(DisposalUi.formatTime(d.getConfirmedAt()));
        }
        tvInfo.setText(info);

        boolean requested = "REQUESTED".equals(d.getStatus());
        if (d.getIssues() != null && !d.getIssues().isEmpty() && requested) {
            tvIssues.setText("Chưa xác nhận được:\n• " + TextUtils.join("\n• ", d.getIssues()));
            tvIssues.setVisibility(View.VISIBLE);
        } else {
            tvIssues.setVisibility(View.GONE);
        }
        lineAdapter.submit(d.getItems(), requested);

        // Task 17 – Disposal Status Validation phía app: chỉ hiện nút với phiếu chờ xác nhận + quyền ADMIN.
        // Server vẫn kiểm tra lại (app có thể hiển thị dữ liệu cũ).
        btnConfirm.setVisibility(requested && isAdmin ? View.VISIBLE : View.GONE);
        btnConfirm.setEnabled(d.isConfirmable());
    }

    private void askConfirm() {
        if (current == null) {
            return;
        }
        long total = 0;
        for (DisposalDetailDto.Line line : current.getItems()) {
            total += line.getQty();
        }
        new AlertDialog.Builder(this)
                .setTitle("Xác nhận hủy hàng")
                .setMessage("Xác nhận phiếu " + current.getDisposalNo() + "?\n\n"
                        + current.getItems().size() + " mặt hàng, tổng số lượng " + total
                        + " sẽ bị TRỪ khỏi tồn kho. Không hoàn tác được.")
                .setPositiveButton("Xác nhận", (dialog, which) -> confirm())
                .setNegativeButton("Không", null)
                .show();
    }

    private void confirm() {
        setLoading(true);
        api.confirm(disposalNo, new ConfirmDisposalRequest(current.getVersion()))
                .enqueue(new Callback<DisposalDetailDto>() {
                    @Override
                    public void onResponse(Call<DisposalDetailDto> call, Response<DisposalDetailDto> response) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        setLoading(false);
                        if (response.isSuccessful() && response.body() != null) {
                            Toast.makeText(DisposalDetailActivity.this, "Đã xác nhận, tồn kho đã được trừ",
                                    Toast.LENGTH_LONG).show();
                            render(response.body());
                            return;
                        }
                        showError(ApiErrorDto.from(response));
                    }

                    @Override
                    public void onFailure(Call<DisposalDetailDto> call, Throwable t) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        setLoading(false);
                        // Không biết server đã xử lý hay chưa (mất mạng giữa chừng): tải lại để xem trạng thái thật
                        Toast.makeText(DisposalDetailActivity.this,
                                "Mất kết nối. Đang tải lại để kiểm tra trạng thái…", Toast.LENGTH_LONG).show();
                        load();
                    }
                });
    }

    /** 409 (trạng thái / dữ liệu cũ), 422 (thiếu tồn), 403 (không có quyền) */
    private void showError(ApiErrorDto error) {
        String message = error.getMessage();
        if (!error.getDetails().isEmpty()) {
            message += "\n\n• " + TextUtils.join("\n• ", error.getDetails());
        }
        new AlertDialog.Builder(this)
                .setTitle("Không xác nhận được")
                .setMessage(message)
                .setPositiveButton("Tải lại", (dialog, which) -> load())
                .show();
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) {
            btnConfirm.setEnabled(false); // chống bấm 2 lần khi đang gửi
        }
    }
}
