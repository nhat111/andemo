package com.example.andemo.plain;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.andemo.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Sửa số lượng nhiều dòng liên tục (cách B), cho nghiệp vụ mà bên Nexacro người dùng sửa thẳng
 * nhiều ô trên grid. Mỗi dòng 1 EditText; bàn phím số có Next để nhảy xuống dòng sau.
 *
 * Mở từ màn chi tiết (nút "Sửa nhiều dòng"); lưu xong trả RESULT_OK để màn chi tiết tải lại.
 * Lưu: PUT /api/disposals/{no} như màn chi tiết (gửi tất cả dòng + version).
 *
 * Sửa 1–2 dòng thì màn chi tiết (bấm dòng → dialog, cách A) vẫn tiện hơn.
 * So sánh 2 cách: docs/nexacro-migration/GRID_EDIT_TO_ANDROID.md
 */
public class PlainDisposalBulkEditActivity extends AppCompatActivity
        implements View.OnClickListener, PlainBulkQtyAdapter.Listener {

    public static final String EXTRA_DISPOSAL_NO = "disposalNo";

    private final HashMap<String, TextView> tvMap = new HashMap<>();
    private final HashMap<String, Button> btnMap = new HashMap<>();
    private final List<HashMap<String, String>> lines = new ArrayList<>();
    private PlainBulkQtyAdapter adapter;
    private RecyclerView rvLines;

    private String disposalNo;
    /** Phiếu lúc mở màn hình: lấy version + ghi chú khi lưu */
    private JSONObject current;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_plain_disposal_bulk_edit);
        setTitle("Sửa nhiều dòng");
        disposalNo = getIntent().getStringExtra(EXTRA_DISPOSAL_NO);

        tvMap.put("tvNo", findViewById(R.id.tvNo));
        tvMap.put("tvSummary", findViewById(R.id.tvSummary));
        btnMap.put("btnClose", findViewById(R.id.btnClose));
        btnMap.put("btnSave", findViewById(R.id.btnSave));
        for (Button b : btnMap.values()) {
            b.setOnClickListener(this);
        }

        rvLines = findViewById(R.id.rvLines);
        rvLines.setLayoutManager(new LinearLayoutManager(this));
        rvLines.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        adapter = new PlainBulkQtyAdapter(lines, this);
        rvLines.setAdapter(adapter);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                close();
            }
        });

        load();
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btnSave) {
            save();
        } else if (id == R.id.btnClose) {
            close();
        }
    }

    // ---------------- tải ----------------

    private void load() {
        btnMap.get("btnSave").setEnabled(false);
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
                    Toast.makeText(PlainDisposalBulkEditActivity.this, message, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void render(String body) {
        try {
            current = new JSONObject(body);
            if (!current.getJSONObject("actions").optBoolean("edit")) {
                // Vừa có người xác nhận / hủy phiếu, hoặc ngày đã 마감
                Toast.makeText(this, "Phiếu này không còn sửa được", Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            tvMap.get("tvNo").setText(current.getString("disposalNo"));
            lines.clear();
            JSONArray items = current.getJSONArray("items");
            for (int i = 0; i < items.length(); i++) {
                lines.add(JsonRows.row(items.getJSONObject(i)));
            }
            adapter.notifyDataSetChanged();
            onChanged();
        } catch (JSONException e) {
            Toast.makeText(this, "Dữ liệu server không đúng định dạng", Toast.LENGTH_LONG).show();
        }
    }

    // ---------------- adapter báo về ----------------

    /** Next ở dòng position: cuộn tới dòng sau rồi đặt focus vào ô số lượng của nó */
    @Override
    public void onNext(int position) {
        int next = position + 1;
        if (position == RecyclerView.NO_POSITION || next >= lines.size()) {
            return;
        }
        rvLines.scrollToPosition(next);
        // Dòng sau có thể chưa có view (đang khuất): chờ RecyclerView vẽ xong rồi mới lấy
        rvLines.post(() -> {
            RecyclerView.ViewHolder holder = rvLines.findViewHolderForAdapterPosition(next);
            if (holder instanceof PlainBulkQtyAdapter.Holder) {
                ((PlainBulkQtyAdapter.Holder) holder).edtQty.requestFocus();
            }
        });
    }

    /** Có dòng đổi số: cập nhật tổng + nút Lưu (không đụng tới list để giữ focus) */
    @Override
    public void onChanged() {
        int modified = 0;
        long total = 0;
        for (HashMap<String, String> r : lines) {
            if (PlainDisposalLineAdapter.isModified(r)) {
                modified++;
            }
            if (PlainBulkQtyAdapter.qtyError(r.get("qty")) == null) {
                total += Long.parseLong(r.get("qty"));
            }
        }
        tvMap.get("tvSummary").setText(lines.size() + " dòng · tổng SL " + total
                + (modified > 0 ? " · đã sửa " + modified + " dòng (chưa lưu)" : ""));
        btnMap.get("btnSave").setEnabled(modified > 0);
    }

    // ---------------- lưu / đóng ----------------

    private void save() {
        // Kiểm tra mọi dòng trước khi gửi; sai thì nhảy tới dòng sai đầu tiên
        for (int i = 0; i < lines.size(); i++) {
            String error = PlainBulkQtyAdapter.qtyError(lines.get(i).get("qty"));
            if (error != null) {
                Toast.makeText(this, "Dòng " + lines.get(i).get("lineNo") + ": " + error, Toast.LENGTH_LONG).show();
                rvLines.scrollToPosition(i);
                return;
            }
        }
        hideKeyboard();

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
        } catch (JSONException e) {
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
                Toast.makeText(PlainDisposalBulkEditActivity.this, "Đã lưu", Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK); // màn chi tiết tải lại
                finish();
            }

            @Override
            public void onError(int httpCode, String message) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                btnMap.get("btnClose").setEnabled(true);
                onChanged();
                // Giữ nguyên số đã nhập để lưu lại; 409 = người khác vừa sửa phiếu → phải tải lại
                new AlertDialog.Builder(PlainDisposalBulkEditActivity.this)
                        .setTitle(httpCode == 0 ? "Mất kết nối" : "Không lưu được")
                        .setMessage(message)
                        .setPositiveButton("Đóng", null)
                        .setNegativeButton("Tải lại", (d, w) -> load())
                        .show();
            }
        });
    }

    /** Đóng màn hình; còn dòng chưa lưu thì hỏi trước */
    private void close() {
        boolean modified = false;
        for (HashMap<String, String> r : lines) {
            if (PlainDisposalLineAdapter.isModified(r)) {
                modified = true;
                break;
            }
        }
        if (!modified) {
            finish();
            return;
        }
        new AlertDialog.Builder(this)
                .setMessage("Có dòng đã sửa nhưng chưa lưu. Bỏ thay đổi và thoát?")
                .setPositiveButton("Bỏ thay đổi", (d, w) -> finish())
                .setNegativeButton("Ở lại", null)
                .show();
    }

    private void hideKeyboard() {
        View focus = getCurrentFocus();
        if (focus != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
            focus.clearFocus();
        }
    }
}
