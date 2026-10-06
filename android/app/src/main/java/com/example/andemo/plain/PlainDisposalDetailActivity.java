package com.example.andemo.plain;

import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.andemo.R;
import com.example.andemo.rules.DisposalRules;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Chi tiết phiếu hủy, kiểu "Java thuần": TextView / Button cất trong HashMap, 1 OnClickListener chung,
 * gọi API bằng HttpTask, đọc JSON bằng org.json. Thao tác: xác nhận, hủy phiếu, hủy xác nhận,
 * sửa số lượng từng dòng.
 *
 * Sửa số lượng (thay cho cell edittype="normal" của grid Nexacro): bấm 1 dòng → dialog nhập số →
 * dòng đổi nền + "(chưa lưu)" → nút "Lưu" gửi PUT /api/disposals/{no}. Không đặt EditText trong
 * từng dòng ListView: view bị dùng lại khi cuộn nên dễ mất focus / ghi nhầm dòng (xem docs).
 *
 * Cùng chức năng với disposal/DisposalDetailActivity (bản Retrofit).
 */
public class PlainDisposalDetailActivity extends AppCompatActivity
        implements View.OnClickListener, PlainDisposalLineAdapter.OnRowButtonListener {

    public static final String EXTRA_DISPOSAL_NO = "disposalNo";

    private final HashMap<String, TextView> tvMap = new HashMap<>();
    private final HashMap<String, Button> btnMap = new HashMap<>();
    private final List<HashMap<String, String>> lines = new ArrayList<>();
    private PlainDisposalLineAdapter lineAdapter;

    private String disposalNo;
    /** Phiếu đang hiển thị (JSON gốc từ server): lấy version, tổng tiền… khi bấm nút */
    private JSONObject current;
    /** Server cho sửa phiếu này không (actions.edit: trạng thái 등록, chưa 마감, đúng người) */
    private boolean canEdit;
    private int issueCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_plain_disposal_detail);
        setTitle("Chi tiết (Java thuần)");
        disposalNo = getIntent().getStringExtra(EXTRA_DISPOSAL_NO);

        tvMap.put("tvNo", findViewById(R.id.tvNo));
        tvMap.put("tvStatus", findViewById(R.id.tvStatus));
        tvMap.put("tvInfo", findViewById(R.id.tvInfo));
        tvMap.put("tvIssues", findViewById(R.id.tvIssues));
        tvMap.put("tvTotal", findViewById(R.id.tvTotal));

        btnMap.put("btnConfirm", findViewById(R.id.btnConfirm));
        btnMap.put("btnCancel", findViewById(R.id.btnCancel));
        btnMap.put("btnCancelConfirm", findViewById(R.id.btnCancelConfirm));
        btnMap.put("btnSave", findViewById(R.id.btnSave));
        for (Button b : btnMap.values()) {
            b.setOnClickListener(this);
        }

        // Adapter tự viết; nút "Tồn" trong từng dòng báo về onStockClick(...) bên dưới
        lineAdapter = new PlainDisposalLineAdapter(this, lines, this);
        ListView lvLines = findViewById(R.id.lvLines);
        lvLines.setAdapter(lineAdapter);
        // Bấm 1 dòng = sửa số lượng (giống grd_detail_oncellclick → div_line trong frm_disposal_reg)
        lvLines.setOnItemClickListener((parent, view, position, id) -> {
            if (canEdit) {
                editQty(position);
            }
        });

        // Rời màn hình khi còn dòng sửa chưa lưu: hỏi trước (giống canrowposchange return false)
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!lineAdapter.hasModified()) {
                    finish();
                    return;
                }
                new AlertDialog.Builder(PlainDisposalDetailActivity.this)
                        .setMessage("Có dòng đã sửa nhưng chưa lưu. Bỏ thay đổi và thoát?")
                        .setPositiveButton("Bỏ thay đổi", (d, w) -> finish())
                        .setNegativeButton("Ở lại", null)
                        .show();
            }
        });

        load();
    }

    /** if / else theo id (không switch: R.id không còn là hằng số từ AGP 8) */
    @Override
    public void onClick(View v) {
        if (current == null) {
            return;
        }
        int id = v.getId();
        if (id == R.id.btnConfirm) {
            askConfirm();
        } else if (id == R.id.btnCancel) {
            askReason("Hủy phiếu (취소)", "cancel");
        } else if (id == R.id.btnCancelConfirm) {
            askReason("Hủy xác nhận (확정취소)", "cancel-confirm");
        } else if (id == R.id.btnSave) {
            save();
        }
    }

    // ---------------- tải + hiển thị ----------------

    /** Nút "Tồn" trong 1 dòng hàng (adapter báo về): hiện tồn kho / giá của mặt hàng đó */
    @Override
    public void onStockClick(int position, HashMap<String, String> row) {
        new AlertDialog.Builder(this)
                .setTitle(row.get("itemName"))
                .setMessage("Mã: " + row.get("itemCode")
                        + "\nTồn (재고): " + row.get("onHandQty")
                        + "\nKhả dụng: " + row.get("availableQty")
                        + "\nGiá vốn (원가): " + JsonRows.won(row.get("costPrice"))
                        + "\nGiá bán (매가): " + JsonRows.won(row.get("salePrice")))
                .setPositiveButton("Đóng", null)
                .show();
    }

    private void load() {
        HttpTask.get(this, "api/disposals/" + disposalNo, new HttpTask.Callback() {
            @Override
            public void onSuccess(String body) {
                if (!isFinishing() && !isDestroyed()) {
                    render(body);
                }
            }

            @Override
            public void onError(int httpCode, String message) {
                if (!isFinishing() && !isDestroyed()) {
                    Toast.makeText(PlainDisposalDetailActivity.this, message, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void render(String body) {
        try {
            current = new JSONObject(body);
            String status = current.getString("status");
            tvMap.get("tvNo").setText(current.getString("disposalNo"));
            tvMap.get("tvStatus").setText(JsonRows.statusLabel(status));
            tvMap.get("tvInfo").setText("영업일자 " + current.getString("businessDate")
                    + (current.optBoolean("closed") ? " (đã 마감)" : "")
                    + "\nĐăng ký: " + current.getString("registeredBy")
                    + (current.isNull("confirmedBy") ? "" : "\nXác nhận: " + current.getString("confirmedBy"))
                    + (current.isNull("remark") ? "" : "\nGhi chú: " + current.getString("remark")));
            tvMap.get("tvTotal").setText("SL " + current.getLong("totalQty")
                    + " · giá vốn " + JsonRows.won(String.valueOf(current.getLong("totalCostAmount"))));

            // Lý do chưa xác nhận được (server tính: thiếu tồn, ngày đã 마감, không phải hôm nay…)
            JSONArray issues = current.getJSONArray("issues");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < issues.length(); i++) {
                sb.append(i == 0 ? "• " : "\n• ").append(issues.getString(i));
            }
            boolean registered = "REGISTERED".equals(status);
            issueCount = issues.length();
            tvMap.get("tvIssues").setText(sb);
            tvMap.get("tvIssues").setVisibility(registered && issues.length() > 0 ? View.VISIBLE : View.GONE);

            // Dòng hàng → List<HashMap>; adapter tự quyết định chữ / màu trong getView.
            // Tải lại từ server = bỏ mọi dòng đã sửa chưa lưu (giống transaction ghi đè ds_detail)
            lines.clear();
            JSONArray items = current.getJSONArray("items");
            for (int i = 0; i < items.length(); i++) {
                lines.add(JsonRows.row(items.getJSONObject(i)));
            }
            lineAdapter.setCheckStock(registered); // chỉ phiếu đăng ký mới cảnh báo thiếu tồn
            lineAdapter.notifyDataSetChanged();

            // Nút hiện theo "actions" server trả về (server quyết định quyền, app chỉ hiển thị)
            JSONObject actions = current.getJSONObject("actions");
            canEdit = actions.optBoolean("edit");
            show("btnConfirm", actions.optBoolean("confirm"));
            show("btnCancel", actions.optBoolean("cancel"));
            show("btnCancelConfirm", actions.optBoolean("cancelConfirm"));
            updateEditButtons();
        } catch (JSONException e) {
            Toast.makeText(this, "Dữ liệu server không đúng định dạng", Toast.LENGTH_LONG).show();
        }
    }

    private void show(String key, boolean visible) {
        btnMap.get(key).setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /** Có dòng chưa lưu: hiện "Lưu" và khóa "Xác nhận" (xác nhận phải theo số đã lưu trên server) */
    private void updateEditButtons() {
        boolean modified = lineAdapter.hasModified();
        show("btnSave", canEdit && modified);
        btnMap.get("btnConfirm").setEnabled(issueCount == 0 && !modified);
    }

    // ---------------- sửa số lượng ----------------

    /** Dialog nhập số lượng cho 1 dòng (giống div_line của frm_disposal_reg: edt_qty inputtype=number) */
    private void editQty(int position) {
        HashMap<String, String> row = lineAdapter.getItem(position);
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER); // PDA hiện bàn phím số
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)}); // tối đa 9999, như maxlength="4"
        input.setText(row.get("qty"));
        input.setSelectAllOnFocus(true); // gõ là thay luôn số cũ

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(row.get("lineNo") + ". " + row.get("itemName"))
                .setMessage("Số lượng hủy (khả dụng " + row.get("availableQty") + ")")
                .setView(input)
                .setPositiveButton("OK", null) // gắn listener sau để không tự đóng khi nhập sai
                .setNegativeButton("Hủy", null)
                .create();
        dialog.setOnShowListener(d -> {
            input.requestFocus();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                long qty = parseQty(input.getText().toString().trim());
                if (qty < 1 || qty > MAX_QTY) {
                    input.setError("Nhập từ 1 đến " + MAX_QTY);
                    return;
                }
                dialog.dismiss();
                lineAdapter.setQty(position, qty);
                updateEditButtons();
            });
        });
        // Mở sẵn bàn phím khi dialog hiện
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        dialog.show();
    }

    private static long parseQty(String text) {
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * PUT /api/disposals/{no}: body {"version", "remark", "items": [{itemCode, qty, reasonCode}]}.
     * API thay toàn bộ dòng của phiếu nên gửi tất cả dòng (đã sửa + chưa sửa), kèm version để
     * server chặn khi người khác vừa sửa phiếu (409).
     */
    private void save() {
        JSONObject body = new JSONObject();
        try {
            body.put("version", current.getLong("version"));
            body.put("remark", current.isNull("remark") ? JSONObject.NULL : current.getString("remark"));
            JSONArray items = new JSONArray();
            for (HashMap<String, String> r : lines) {
                items.put(new JSONObject()
                        .put("itemCode", r.get("itemCode"))
                        .put("qty", Long.parseLong(r.get("qty")))
                        .put("reasonCode", r.get("reasonCode")));
            }
            body.put("items", items);
        } catch (JSONException | NumberFormatException e) {
            return;
        }
        for (Button b : btnMap.values()) {
            b.setEnabled(false); // chống bấm 2 lần
        }
        HttpTask.put(this, "api/disposals/" + disposalNo, body, new HttpTask.Callback() {
            @Override
            public void onSuccess(String responseBody) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                Toast.makeText(PlainDisposalDetailActivity.this, "Đã lưu", Toast.LENGTH_SHORT).show();
                for (Button b : btnMap.values()) {
                    b.setEnabled(true);
                }
                render(responseBody); // server trả lại chi tiết mới (version mới, thiếu tồn tính lại)
            }

            @Override
            public void onError(int httpCode, String message) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                for (Button b : btnMap.values()) {
                    b.setEnabled(true);
                }
                updateEditButtons();
                // Giữ nguyên các dòng đã sửa để người dùng sửa tiếp / lưu lại.
                // 409 = người khác vừa sửa phiếu: phải tải lại (mất thay đổi của mình)
                new AlertDialog.Builder(PlainDisposalDetailActivity.this)
                        .setTitle(httpCode == 0 ? "Mất kết nối" : "Không lưu được")
                        .setMessage(message)
                        .setPositiveButton("Đóng", null)
                        .setNegativeButton("Tải lại", (d, w) -> load())
                        .show();
            }
        });
    }

    // ---------------- thao tác ----------------

    private void askConfirm() {
        long cost = current.optLong("totalCostAmount");
        new AlertDialog.Builder(this)
                .setTitle("Xác nhận hủy hàng (확정)")
                .setMessage("Giá vốn " + JsonRows.won(String.valueOf(cost)) + " sẽ bị trừ khỏi tồn kho. Xác nhận?")
                .setPositiveButton("Xác nhận", (d, w) -> {
                    if (DisposalRules.needsSecondConfirm(cost)) { // R10
                        new AlertDialog.Builder(this)
                                .setMessage("Phiếu giá trị lớn. Bạn chắc chắn?")
                                .setPositiveButton("Chắc chắn", (d2, w2) -> send("confirm", null))
                                .setNegativeButton("Không", null)
                                .show();
                    } else {
                        send("confirm", null);
                    }
                })
                .setNegativeButton("Không", null)
                .show();
    }

    private void askReason(String title, String action) {
        EditText input = new EditText(this);
        input.setHint("Lý do (bắt buộc)");
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(input)
                .setPositiveButton("Đồng ý", null)
                .setNegativeButton("Không", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String reason = input.getText().toString().trim();
            if (reason.isEmpty()) {
                input.setError("Nhập lý do");
                return;
            }
            dialog.dismiss();
            send(action, reason);
        }));
        dialog.show();
    }

    private static final long MAX_QTY = 9_999; // = MAX_QTY_PER_LINE của server

    /** POST /api/disposals/{no}/{action} body {"version": …, "reason": …} */
    private void send(String action, String reason) {
        JSONObject body = new JSONObject();
        try {
            body.put("version", current.getLong("version"));
            if (reason != null) {
                body.put("reason", reason);
            }
        } catch (JSONException e) {
            return;
        }
        for (Button b : btnMap.values()) {
            b.setEnabled(false); // chống bấm 2 lần
        }
        HttpTask.post(this, "api/disposals/" + disposalNo + "/" + action, body, new HttpTask.Callback() {
            @Override
            public void onSuccess(String responseBody) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                Toast.makeText(PlainDisposalDetailActivity.this, "Đã xử lý", Toast.LENGTH_SHORT).show();
                for (Button b : btnMap.values()) {
                    b.setEnabled(true);
                }
                render(responseBody); // server trả lại chi tiết mới
            }

            @Override
            public void onError(int httpCode, String message) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                for (Button b : btnMap.values()) {
                    b.setEnabled(true);
                }
                updateEditButtons(); // vẫn khóa "Xác nhận" nếu còn thiếu tồn / dòng chưa lưu
                // httpCode 0 = mất mạng: không biết server đã xử lý chưa → tải lại, không gửi lại
                new AlertDialog.Builder(PlainDisposalDetailActivity.this)
                        .setTitle(httpCode == 0 ? "Mất kết nối" : "Không thực hiện được")
                        .setMessage(message)
                        .setPositiveButton("Tải lại", (d, w) -> load())
                        .show();
            }
        });
    }
}
