package com.example.andemo.plain;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

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
 * Chi tiết phiếu hủy, kiểu "Java thuần": TextView / Button cất trong HashMap, 1 OnClickListener chung,
 * gọi API bằng HttpTask, đọc JSON bằng org.json. Thao tác: xác nhận, hủy phiếu, hủy xác nhận.
 *
 * Cùng chức năng với disposal/DisposalDetailActivity (bản Retrofit).
 */
public class PlainDisposalDetailActivity extends AppCompatActivity implements View.OnClickListener {

    public static final String EXTRA_DISPOSAL_NO = "disposalNo";
    /** R10: phiếu giá vốn từ mức này phải xác nhận thêm 1 lần */
    private static final long BIG_AMOUNT = 100_000;

    private final HashMap<String, TextView> tvMap = new HashMap<>();
    private final HashMap<String, Button> btnMap = new HashMap<>();
    private final List<HashMap<String, String>> lines = new ArrayList<>();
    private SimpleAdapter lineAdapter;

    private String disposalNo;
    /** Phiếu đang hiển thị (JSON gốc từ server): lấy version, tổng tiền… khi bấm nút */
    private JSONObject current;

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
        for (Button b : btnMap.values()) {
            b.setOnClickListener(this);
        }

        lineAdapter = new SimpleAdapter(this, lines, R.layout.item_plain_row,
                new String[]{"line1", "line2", "line3"},
                new int[]{R.id.txtLine1, R.id.txtLine2, R.id.txtLine3});
        ((ListView) findViewById(R.id.lvLines)).setAdapter(lineAdapter);

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
        }
    }

    // ---------------- tải + hiển thị ----------------

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
            tvMap.get("tvIssues").setText(sb);
            tvMap.get("tvIssues").setVisibility(registered && issues.length() > 0 ? View.VISIBLE : View.GONE);

            // Dòng hàng → List<HashMap> cho SimpleAdapter
            lines.clear();
            JSONArray items = current.getJSONArray("items");
            for (int i = 0; i < items.length(); i++) {
                HashMap<String, String> r = JsonRows.row(items.getJSONObject(i));
                r.put("line1", r.get("lineNo") + ". " + r.get("itemName"));
                r.put("line2", "Hủy " + r.get("qty") + " · khả dụng " + r.get("availableQty")
                        + (registered && !"true".equals(r.get("sufficient")) ? "  ⚠ thiếu tồn" : ""));
                r.put("line3", r.get("itemCode") + " · " + r.get("reasonName")
                        + " · giá vốn " + JsonRows.won(r.get("costAmount")));
                lines.add(r);
            }
            lineAdapter.notifyDataSetChanged();

            // Nút hiện theo "actions" server trả về (server quyết định quyền, app chỉ hiển thị)
            JSONObject actions = current.getJSONObject("actions");
            show("btnConfirm", actions.optBoolean("confirm"));
            btnMap.get("btnConfirm").setEnabled(issues.length() == 0);
            show("btnCancel", actions.optBoolean("cancel"));
            show("btnCancelConfirm", actions.optBoolean("cancelConfirm"));
        } catch (JSONException e) {
            Toast.makeText(this, "Dữ liệu server không đúng định dạng", Toast.LENGTH_LONG).show();
        }
    }

    private void show(String key, boolean visible) {
        btnMap.get(key).setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    // ---------------- thao tác ----------------

    private void askConfirm() {
        long cost = current.optLong("totalCostAmount");
        new AlertDialog.Builder(this)
                .setTitle("Xác nhận hủy hàng (확정)")
                .setMessage("Giá vốn " + JsonRows.won(String.valueOf(cost)) + " sẽ bị trừ khỏi tồn kho. Xác nhận?")
                .setPositiveButton("Xác nhận", (d, w) -> {
                    if (cost >= BIG_AMOUNT) {
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
