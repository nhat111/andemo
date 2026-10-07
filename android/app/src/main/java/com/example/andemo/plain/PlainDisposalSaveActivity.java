package com.example.andemo.plain;

import android.net.Uri;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.andemo.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Màn "Lưu hủy", viết theo kiểu code của khách (HashMap view, 1 OnClickListener, HttpTask, ListView + adapter):
 *
 *   1. Bấm Tìm      → GET api/disposals?status=REGISTERED&keyword=…  → hiện kết quả trong DIALOG có ListView
 *   2. Bấm 1 phiếu  → GET api/disposals/{no}                         → hiện dòng hàng lên ListView của màn này
 *   3. Bấm 1 dòng   → dialog sửa số lượng → đóng dialog thì ListView hiện số mới (dòng tô vàng = chưa lưu)
 *   4. Bấm Lưu      → PUT api/disposals/{no} (gửi tất cả dòng + version)
 *
 * Nexacro tương ứng: btn_search → transaction("search") → popup chọn phiếu → transaction("detail")
 * → grd_detail oncellclick (div sửa SL) → btn_save → transaction("save", "ds_detail=ds_detail:U").
 * Chạy với mock: tools/MockPdaAlertServer.java (có sẵn phiếu mẫu).
 */
public class PlainDisposalSaveActivity extends AppCompatActivity
        implements View.OnClickListener, PlainDisposalLineAdapter.OnRowButtonListener {

    /** Số lượng tối đa 1 dòng (như maxlength="4" của ô nhập bên Nexacro) */
    private static final long MAX_QTY = 9999;

    /** View của màn hình, lấy theo tên = id trong XML */
    private final HashMap<String, View> viewMap = new HashMap<>();
    /** Dòng hàng của phiếu đang mở (≈ ds_detail) */
    private final List<HashMap<String, String>> lines = new ArrayList<>();
    private PlainDisposalLineAdapter lineAdapter;

    /** Phiếu đang mở (JSON gốc từ server: lấy version, remark khi lưu). null = chưa chọn phiếu */
    private JSONObject current;
    /** Server cho phép sửa phiếu này không (actions.edit) */
    private boolean canEdit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_plain_disposal_save);
        setTitle("Lưu hủy (Java thuần)");

        // 1) Cất view vào HashMap
        viewMap.put("edtKeyword", findViewById(R.id.edtKeyword));
        viewMap.put("btnSearch", findViewById(R.id.btnSearch));
        viewMap.put("btnReload", findViewById(R.id.btnReload));
        viewMap.put("btnSave", findViewById(R.id.btnSave));
        viewMap.put("tvHeader", findViewById(R.id.tvHeader));
        viewMap.put("tvTotal", findViewById(R.id.tvTotal));
        viewMap.put("lvLines", findViewById(R.id.lvLines));

        // 2) Các nút dùng chung 1 listener: onClick(View) bên dưới
        for (String key : new String[]{"btnSearch", "btnReload", "btnSave"}) {
            viewMap.get(key).setOnClickListener(this);
        }

        // Enter / nút tìm trên bàn phím (máy quét PDA cũng gửi Enter) = bấm Tìm
        ((EditText) viewMap.get("edtKeyword")).setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_SEARCH || enter) {
                search();
                return true;
            }
            return false;
        });

        // 3) ListView dòng hàng: adapter tự viết (tô vàng dòng đã sửa, đỏ khi thiếu tồn)
        lineAdapter = new PlainDisposalLineAdapter(this, lines, this);
        ListView lvLines = (ListView) viewMap.get("lvLines");
        lvLines.setAdapter(lineAdapter);
        lvLines.setOnItemClickListener((parent, view, position, id) -> editQty(position));

        // Rời màn khi còn dòng sửa chưa lưu: hỏi trước
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                confirmDiscard(PlainDisposalSaveActivity.this::finish);
            }
        });

        updateButtons();
    }

    /** if / else theo id, KHÔNG switch (AGP 8+: R.id không còn là hằng số) */
    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btnSearch) {
            search();
        } else if (id == R.id.btnSave) {
            save();
        } else if (id == R.id.btnReload) {
            if (current != null) {
                confirmDiscard(() -> loadDetail(current.optString("disposalNo")));
            }
        }
    }

    // ---------------- 1. Tìm → dialog chọn phiếu ----------------

    /** ≈ fn_search: chỉ tìm phiếu 등록 vì chỉ phiếu đó mới sửa được số lượng */
    private void search() {
        String keyword = ((EditText) viewMap.get("edtKeyword")).getText().toString().trim();
        viewMap.get("btnSearch").setEnabled(false); // chống bấm 2 lần
        HttpTask.get(this, "api/disposals?status=REGISTERED&keyword=" + Uri.encode(keyword), new HttpTask.Callback() {
            @Override
            public void onSuccess(String body) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                viewMap.get("btnSearch").setEnabled(true);
                try {
                    List<HashMap<String, String>> result = JsonRows.rows(body);
                    if (result.isEmpty()) {
                        Toast.makeText(PlainDisposalSaveActivity.this,
                                "Không có phiếu 등록 nào khớp \"" + keyword + "\"", Toast.LENGTH_SHORT).show();
                    } else {
                        showPickDialog(result);
                    }
                } catch (JSONException e) {
                    onError(200, "Dữ liệu server không đúng định dạng");
                }
            }

            @Override
            public void onError(int httpCode, String message) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                viewMap.get("btnSearch").setEnabled(true);
                showError(httpCode == 0 ? "Mất kết nối" : "Không tìm được", message);
            }
        });
    }

    /** Dialog có ListView kết quả (dùng lại adapter của màn danh sách). Bấm 1 phiếu = mở phiếu đó. */
    private void showPickDialog(List<HashMap<String, String>> rows) {
        ListView lv = new ListView(this);
        lv.setAdapter(new PlainDisposalListAdapter(this, rows));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Chọn phiếu (" + rows.size() + ")")
                .setView(lv)
                .setNegativeButton("Đóng", null)
                .create();
        lv.setOnItemClickListener((parent, view, position, id) -> {
            dialog.dismiss();
            String no = rows.get(position).get("disposalNo");
            confirmDiscard(() -> loadDetail(no)); // đang sửa dở phiếu khác thì hỏi trước
        });
        dialog.show();
    }

    // ---------------- 2. Chi tiết → ListView của màn hình ----------------

    private void loadDetail(String disposalNo) {
        ((TextView) viewMap.get("tvHeader")).setText("Đang tải " + disposalNo + "…");
        HttpTask.get(this, "api/disposals/" + disposalNo, new HttpTask.Callback() {
            @Override
            public void onSuccess(String body) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                render(body);
            }

            @Override
            public void onError(int httpCode, String message) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                ((TextView) viewMap.get("tvHeader")).setText("Không tải được phiếu " + disposalNo);
                showError(httpCode == 0 ? "Mất kết nối" : "Không tải được", message);
            }
        });
    }

    /** JSON chi tiết → tiêu đề + List<HashMap> dòng hàng. Tải lại = bỏ mọi sửa chưa lưu. */
    private void render(String body) {
        try {
            current = new JSONObject(body);
            String status = current.getString("status");
            ((TextView) viewMap.get("tvHeader")).setText(current.getString("disposalNo")
                    + "  ·  " + JsonRows.statusLabel(status)
                    + "\n영업일자 " + current.getString("businessDate")
                    + (current.isNull("remark") ? "" : " · " + current.getString("remark")));

            lines.clear();
            JSONArray items = current.getJSONArray("items");
            for (int i = 0; i < items.length(); i++) {
                lines.add(JsonRows.row(items.getJSONObject(i)));
            }
            canEdit = current.getJSONObject("actions").optBoolean("edit");
            lineAdapter.setCheckStock("REGISTERED".equals(status));
            lineAdapter.notifyDataSetChanged();
            updateTotal();
            updateButtons();
        } catch (JSONException e) {
            current = null;
            Toast.makeText(this, "Dữ liệu server không đúng định dạng", Toast.LENGTH_LONG).show();
        }
    }

    // ---------------- 3. Sửa số lượng 1 dòng ----------------

    /** Dialog nhập số lượng; OK hợp lệ thì ghi vào HashMap của dòng → ListView vẽ lại số mới */
    private void editQty(int position) {
        if (current == null) {
            return;
        }
        if (!canEdit) {
            Toast.makeText(this, "Phiếu này không sửa được", Toast.LENGTH_SHORT).show();
            return;
        }
        HashMap<String, String> row = lineAdapter.getItem(position);
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER); // bàn phím số
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
        input.setText(row.get("qty"));
        input.setSelectAllOnFocus(true); // gõ là thay luôn số cũ

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(row.get("lineNo") + ". " + row.get("itemName"))
                .setMessage("Số lượng hủy (gốc " + PlainDisposalLineAdapter.orgQty(row)
                        + ", khả dụng " + row.get("availableQty") + ")")
                .setView(input)
                .setPositiveButton("OK", null) // gắn listener sau để nhập sai thì dialog không tự đóng
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
                lineAdapter.setQty(position, qty); // ghi vào HashMap + notifyDataSetChanged
                updateTotal();
                updateButtons();
            });
        });
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

    // ---------------- 4. Lưu ----------------

    /** PUT api/disposals/{no}: gửi TẤT CẢ dòng (API thay toàn bộ dòng) + version (server chặn ghi đè → 409) */
    private void save() {
        if (current == null || !lineAdapter.hasModified()) {
            Toast.makeText(this, "Chưa có thay đổi", Toast.LENGTH_SHORT).show();
            return;
        }
        String disposalNo = current.optString("disposalNo");
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
        setBusy(true);
        HttpTask.put(this, "api/disposals/" + disposalNo, body, new HttpTask.Callback() {
            @Override
            public void onSuccess(String responseBody) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                setBusy(false);
                Toast.makeText(PlainDisposalSaveActivity.this, "Đã lưu", Toast.LENGTH_SHORT).show();
                render(responseBody); // server trả chi tiết mới (version mới, hết tô vàng)
            }

            @Override
            public void onError(int httpCode, String message) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                setBusy(false);
                // 0 = mất mạng (không biết server đã lưu chưa), 409 = người khác vừa sửa: đều nên tải lại
                if (httpCode == 0 || httpCode == 409) {
                    new AlertDialog.Builder(PlainDisposalSaveActivity.this)
                            .setTitle(httpCode == 0 ? "Mất kết nối" : "Không lưu được")
                            .setMessage(message + "\n\nTải lại phiếu? (số đã sửa sẽ mất)")
                            .setPositiveButton("Tải lại", (d, w) -> loadDetail(disposalNo))
                            .setNegativeButton("Để sau", null)
                            .show();
                } else {
                    showError("Không lưu được", message); // 400: giữ nguyên số đã sửa để bro sửa lại
                }
            }
        });
    }

    // ---------------- tiện ích ----------------

    /** Tổng theo số ĐANG hiển thị (kể cả số đã sửa chưa lưu) */
    private void updateTotal() {
        long qty = 0;
        long cost = 0;
        int modified = 0;
        for (HashMap<String, String> r : lines) {
            long q = parseQty(r.get("qty"));
            long price = parseQty(r.get("costPrice"));
            qty += Math.max(q, 0);
            cost += Math.max(q, 0) * Math.max(price, 0);
            if (PlainDisposalLineAdapter.isModified(r)) {
                modified++;
            }
        }
        ((TextView) viewMap.get("tvTotal")).setText(lines.size() + " dòng · SL " + qty
                + " · giá vốn " + JsonRows.won(String.valueOf(cost))
                + (modified > 0 ? "  ·  " + modified + " dòng chưa lưu" : ""));
    }

    private void updateButtons() {
        viewMap.get("btnSave").setEnabled(current != null && canEdit && lineAdapter.hasModified());
        viewMap.get("btnReload").setEnabled(current != null);
    }

    private void setBusy(boolean busy) {
        for (String key : new String[]{"btnSearch", "btnReload", "btnSave"}) {
            viewMap.get(key).setEnabled(!busy);
        }
        if (!busy) {
            updateButtons();
        }
    }

    /** Còn dòng sửa chưa lưu thì hỏi trước khi làm action (mở phiếu khác, tải lại, thoát) */
    private void confirmDiscard(Runnable action) {
        if (!lineAdapter.hasModified()) {
            action.run();
            return;
        }
        new AlertDialog.Builder(this)
                .setMessage("Có dòng đã sửa nhưng chưa lưu. Bỏ thay đổi?")
                .setPositiveButton("Bỏ thay đổi", (d, w) -> action.run())
                .setNegativeButton("Ở lại", null)
                .show();
    }

    private void showError(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Đóng", null)
                .show();
    }

    /** Nút "Tồn" trong dòng (adapter báo về): xem tồn kho / giá của mặt hàng */
    @Override
    public void onStockClick(int position, HashMap<String, String> row) {
        new AlertDialog.Builder(this)
                .setTitle(row.get("itemName"))
                .setMessage("Mã: " + row.get("itemCode")
                        + "\nTồn (재고): " + row.get("onHandQty")
                        + "\nKhả dụng: " + row.get("availableQty")
                        + "\nGiá vốn (원가): " + JsonRows.won(row.get("costPrice")))
                .setPositiveButton("Đóng", null)
                .show();
    }
}
