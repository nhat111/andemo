package com.example.andemo.disposal;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
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
import com.example.andemo.model.DisposalDetailDto;
import com.example.andemo.model.DisposalReasonDto;
import com.example.andemo.model.DisposalSaveRequest;
import com.example.andemo.model.InventoryItemDto;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 폐기등록 / 폐기수정: nhân viên quét từng món cần hủy.
 * - Quét trùng món đã có: +1 số lượng (không tạo dòng mới, server cũng không nhận dòng trùng)
 * - Scanner cứng của PDA ở chế độ keyboard wedge: gõ barcode vào ô + Enter → tự thêm
 */
public class DisposalEditActivity extends AppCompatActivity {

    /** Có = sửa phiếu này; không có = đăng ký mới */
    public static final String EXTRA_DISPOSAL_NO = "disposalNo";

    private DisposalApi api;
    private String disposalNo;
    private Long version;

    private final List<EditLine> lines = new ArrayList<>();
    private final List<DisposalReasonDto> reasons = new ArrayList<>();
    private EditLineAdapter adapter;
    private ArrayAdapter<DisposalReasonDto> reasonAdapter;

    private EditText edtBarcode, edtRemark;
    private Spinner spReason;
    private TextView tvSummary;
    private ProgressBar progress;
    private Button btnSave, btnAdd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_disposal_edit);

        disposalNo = getIntent().getStringExtra(EXTRA_DISPOSAL_NO);
        setTitle(disposalNo == null ? "Đăng ký hủy hàng (폐기등록)" : "Sửa " + disposalNo);
        api = ApiClient.getClient(this).create(DisposalApi.class);

        edtBarcode = findViewById(R.id.edtBarcode);
        edtRemark = findViewById(R.id.edtRemark);
        spReason = findViewById(R.id.spReason);
        tvSummary = findViewById(R.id.tvSummary);
        progress = findViewById(R.id.progress);
        btnSave = findViewById(R.id.btnSave);
        btnAdd = findViewById(R.id.btnAdd);

        reasonAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, reasons);
        reasonAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spReason.setAdapter(reasonAdapter);

        RecyclerView rv = findViewById(R.id.rvLines);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        adapter = new EditLineAdapter(lines, new EditLineAdapter.Listener() {
            @Override
            public void onClick(int position) {
                editLine(position);
            }

            @Override
            public void onLongClick(int position) {
                removeLine(position);
            }
        });
        rv.setAdapter(adapter);

        btnAdd.setOnClickListener(v -> addScanned());
        // Enter từ bàn phím ảo hoặc từ scanner cứng
        edtBarcode.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_DONE || enter) {
                addScanned();
                return true;
            }
            return false;
        });
        btnSave.setOnClickListener(v -> save());

        updateSummary();
        loadReasons();
    }

    // ---------------- tải dữ liệu ----------------

    private void loadReasons() {
        setLoading(true);
        api.reasons().enqueue(new Callback<List<DisposalReasonDto>>() {
            @Override
            public void onResponse(Call<List<DisposalReasonDto>> call, Response<List<DisposalReasonDto>> response) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                setLoading(false);
                if (!response.isSuccessful() || response.body() == null) {
                    fail(ApiErrorDto.from(response).getMessage());
                    return;
                }
                reasons.clear();
                reasons.addAll(response.body());
                reasonAdapter.notifyDataSetChanged();
                if (disposalNo != null) {
                    loadExisting();
                } else {
                    edtBarcode.requestFocus();
                }
            }

            @Override
            public void onFailure(Call<List<DisposalReasonDto>> call, Throwable t) {
                if (!isFinishing() && !isDestroyed()) {
                    setLoading(false);
                    fail("Không kết nối được server");
                }
            }
        });
    }

    /** Sửa: nạp các dòng hiện có của phiếu */
    private void loadExisting() {
        setLoading(true);
        api.detail(disposalNo).enqueue(new Callback<DisposalDetailDto>() {
            @Override
            public void onResponse(Call<DisposalDetailDto> call, Response<DisposalDetailDto> response) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                setLoading(false);
                DisposalDetailDto d = response.body();
                if (!response.isSuccessful() || d == null) {
                    fail(ApiErrorDto.from(response).getMessage());
                    return;
                }
                if (!d.getActions().isEdit()) {
                    fail("Phiếu không còn sửa được (trạng thái " + d.getStatusName() + ")");
                    return;
                }
                version = d.getVersion();
                edtRemark.setText(d.getRemark());
                lines.clear();
                for (DisposalDetailDto.Line l : d.getItems()) {
                    lines.add(new EditLine(l.getItemCode(), l.getItemName(), l.getQty(), l.getReasonCode(),
                            reasonLabel(l.getReasonCode()), l.getAvailableQty(), l.getCostPrice()));
                }
                adapter.notifyDataSetChanged();
                updateSummary();
                edtBarcode.requestFocus();
            }

            @Override
            public void onFailure(Call<DisposalDetailDto> call, Throwable t) {
                if (!isFinishing() && !isDestroyed()) {
                    setLoading(false);
                    fail("Không kết nối được server");
                }
            }
        });
    }

    // ---------------- thêm / sửa / xóa dòng ----------------

    private void addScanned() {
        String code = edtBarcode.getText().toString().trim();
        if (code.isEmpty()) {
            edtBarcode.setError("Quét hoặc nhập barcode");
            return;
        }
        edtBarcode.setText("");
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).itemCode.equals(code)) {
                lines.get(i).qty++;
                adapter.notifyItemChanged(i);
                updateSummary();
                return;
            }
        }
        DisposalReasonDto reason = (DisposalReasonDto) spReason.getSelectedItem();
        if (reason == null) {
            Toast.makeText(this, "Chưa tải được danh sách lý do", Toast.LENGTH_SHORT).show();
            return;
        }
        btnAdd.setEnabled(false);
        api.item(code).enqueue(new Callback<InventoryItemDto>() {
            @Override
            public void onResponse(Call<InventoryItemDto> call, Response<InventoryItemDto> response) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                btnAdd.setEnabled(true);
                InventoryItemDto item = response.body();
                if (!response.isSuccessful() || item == null) {
                    Toast.makeText(DisposalEditActivity.this, ApiErrorDto.from(response).getMessage(),
                            Toast.LENGTH_LONG).show();
                    return;
                }
                // R3: hết tồn khả dụng thì hỏi người dùng (vẫn cho thêm; server chặn lúc 확정 nếu còn thiếu)
                if (item.getAvailableQty() <= 0) {
                    new AlertDialog.Builder(DisposalEditActivity.this)
                            .setTitle(item.getItemName())
                            .setMessage("Tồn khả dụng là " + item.getAvailableQty() + ". Vẫn thêm vào phiếu hủy?")
                            .setPositiveButton("Thêm", (d, w) -> addLine(item, reason))
                            .setNegativeButton("Không", null)
                            .show();
                } else {
                    addLine(item, reason);
                }
            }

            @Override
            public void onFailure(Call<InventoryItemDto> call, Throwable t) {
                if (!isFinishing() && !isDestroyed()) {
                    btnAdd.setEnabled(true);
                    Toast.makeText(DisposalEditActivity.this, "Không kết nối được server", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void addLine(InventoryItemDto item, DisposalReasonDto reason) {
        lines.add(new EditLine(item.getItemCode(), item.getItemName(), 1, reason.getCode(),
                reason.toString(), item.getAvailableQty(), item.getCostPrice()));
        adapter.notifyItemInserted(lines.size() - 1);
        updateSummary();
    }

    /** Hộp thoại sửa số lượng + lý do của 1 dòng */
    private void editLine(int position) {
        if (position < 0 || position >= lines.size()) {
            return;
        }
        EditLine line = lines.get(position);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);

        EditText qty = new EditText(this);
        qty.setInputType(InputType.TYPE_CLASS_NUMBER);
        qty.setText(String.valueOf(line.qty));
        qty.setSelectAllOnFocus(true);
        box.addView(qty);

        Spinner reason = new Spinner(this);
        reason.setAdapter(reasonAdapter);
        for (int i = 0; i < reasons.size(); i++) {
            if (reasons.get(i).getCode().equals(line.reasonCode)) {
                reason.setSelection(i);
            }
        }
        box.addView(reason);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(line.itemName)
                .setView(box)
                .setPositiveButton("OK", null)
                .setNegativeButton("Bỏ qua", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            long value;
            try {
                value = Long.parseLong(qty.getText().toString().trim());
            } catch (NumberFormatException e) {
                value = 0;
            }
            if (value <= 0 || value > 9_999) {
                qty.setError("Số lượng từ 1 đến 9999");
                return;
            }
            DisposalReasonDto r = (DisposalReasonDto) reason.getSelectedItem();
            line.qty = value;
            line.reasonCode = r.getCode();
            line.reasonLabel = r.toString();
            adapter.notifyItemChanged(position);
            updateSummary();
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void removeLine(int position) {
        if (position < 0 || position >= lines.size()) {
            return;
        }
        new AlertDialog.Builder(this)
                .setMessage("Xóa " + lines.get(position).itemName + " khỏi phiếu?")
                .setPositiveButton("Xóa", (d, w) -> {
                    lines.remove(position);
                    adapter.notifyItemRemoved(position);
                    updateSummary();
                })
                .setNegativeButton("Không", null)
                .show();
    }

    // ---------------- lưu ----------------

    private void save() {
        if (lines.isEmpty()) {
            Toast.makeText(this, "Chưa có mặt hàng nào", Toast.LENGTH_SHORT).show();
            return;
        }
        String remark = edtRemark.getText().toString().trim();
        // R5: server cũng kiểm tra lại; maxLength trong layout đã chặn khi gõ
        if (remark.length() > 100) {
            edtRemark.setError("Tối đa 100 ký tự");
            return;
        }
        DisposalSaveRequest body = new DisposalSaveRequest(version, remark.isEmpty() ? null : remark);
        for (EditLine l : lines) {
            body.addLine(l.itemCode, l.qty, l.reasonCode);
        }
        Call<DisposalDetailDto> call = disposalNo == null ? api.register(body) : api.update(disposalNo, body);
        setLoading(true);
        call.enqueue(new Callback<DisposalDetailDto>() {
            @Override
            public void onResponse(Call<DisposalDetailDto> c, Response<DisposalDetailDto> response) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                setLoading(false);
                DisposalDetailDto saved = response.body();
                if (!response.isSuccessful() || saved == null) {
                    ApiErrorDto error = ApiErrorDto.from(response);
                    String message = error.getMessage();
                    if (!error.getDetails().isEmpty()) {
                        message += "\n\n• " + TextUtils.join("\n• ", error.getDetails());
                    }
                    new AlertDialog.Builder(DisposalEditActivity.this)
                            .setTitle("Không lưu được")
                            .setMessage(message)
                            .setPositiveButton("OK", null)
                            .show();
                    return;
                }
                Toast.makeText(DisposalEditActivity.this, "Đã lưu phiếu " + saved.getDisposalNo(),
                        Toast.LENGTH_SHORT).show();
                if (disposalNo == null) {
                    // Đăng ký mới: mở luôn màn chi tiết của phiếu vừa tạo
                    Intent intent = new Intent(DisposalEditActivity.this, DisposalDetailActivity.class);
                    intent.putExtra(DisposalDetailActivity.EXTRA_DISPOSAL_NO, saved.getDisposalNo());
                    startActivity(intent);
                }
                finish();
            }

            @Override
            public void onFailure(Call<DisposalDetailDto> c, Throwable t) {
                if (!isFinishing() && !isDestroyed()) {
                    setLoading(false);
                    // Đăng ký mới mà mất mạng: có thể server đã tạo phiếu. Báo người dùng xem lại danh sách
                    Toast.makeText(DisposalEditActivity.this, "Mất kết nối. Kiểm tra lại danh sách phiếu "
                            + "trước khi lưu lại để tránh tạo trùng.", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    // ---------------- helpers ----------------

    private void updateSummary() {
        long qty = 0;
        long cost = 0;
        for (EditLine l : lines) {
            qty += l.qty;
            cost += l.qty * l.costPrice;
        }
        tvSummary.setText(lines.size() + " mặt hàng · SL " + qty + " · giá vốn " + DisposalUi.won(cost));
    }

    private String reasonLabel(String code) {
        for (DisposalReasonDto r : reasons) {
            if (r.getCode().equals(code)) {
                return r.toString();
            }
        }
        return code;
    }

    private void fail(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        finish();
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnSave.setEnabled(!loading);
    }
}
