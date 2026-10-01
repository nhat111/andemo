package com.example.andemo.plain;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.andemo.R;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Danh sách phiếu hủy, viết theo kiểu "Java thuần":
 * - View cất trong HashMap, 1 OnClickListener chung (implements View.OnClickListener)
 * - Gọi API bằng HttpTask (thread pool + HttpURLConnection), JSON → List&lt;HashMap&gt;
 * - ListView + adapter tự viết PlainDisposalListAdapter (BaseAdapter + ViewHolder), không RecyclerView
 *
 * Cùng chức năng với disposal/DisposalListActivity (bản Retrofit + RecyclerView).
 */
public class PlainDisposalListActivity extends AppCompatActivity implements View.OnClickListener {

    /** View của màn hình, lấy theo tên: viewMap.get("tvCount") */
    private final HashMap<String, View> viewMap = new HashMap<>();
    /** Dữ liệu danh sách: mỗi dòng 1 HashMap (giống 1 dòng Dataset) */
    private final List<HashMap<String, String>> rows = new ArrayList<>();
    private PlainDisposalListAdapter adapter;
    /** Trạng thái đang lọc: REGISTERED / CONFIRMED / "" (tất cả) */
    private String status = "REGISTERED";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_plain_disposal_list);
        setTitle("Phiếu hủy (Java thuần)");

        // 1) Cất view vào HashMap
        viewMap.put("btnReg", findViewById(R.id.btnReg));
        viewMap.put("btnCfm", findViewById(R.id.btnCfm));
        viewMap.put("btnAll", findViewById(R.id.btnAll));
        viewMap.put("btnRefresh", findViewById(R.id.btnRefresh));
        viewMap.put("tvCount", findViewById(R.id.tvCount));
        viewMap.put("lvList", findViewById(R.id.lvList));

        // 2) Mọi nút dùng chung 1 listener: onClick(View) bên dưới
        for (String key : new String[]{"btnReg", "btnCfm", "btnAll", "btnRefresh"}) {
            viewMap.get(key).setOnClickListener(this);
        }

        // 3) Adapter tự viết: dùng chung list "rows" với Activity (sửa rows rồi notifyDataSetChanged)
        adapter = new PlainDisposalListAdapter(this, rows);
        ListView lv = (ListView) viewMap.get("lvList");
        lv.setAdapter(adapter);
        lv.setOnItemClickListener(this::onRowClick);
    }

    @Override
    protected void onResume() {
        super.onResume();
        search();
    }

    /**
     * Listener chung. Dùng if / else theo id, KHÔNG dùng switch (R.id.x):
     * từ Android Gradle Plugin 8, R.id không còn là hằng số nên switch báo lỗi build.
     */
    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btnReg) {
            status = "REGISTERED";
        } else if (id == R.id.btnCfm) {
            status = "CONFIRMED";
        } else if (id == R.id.btnAll) {
            status = "";
        } else if (id != R.id.btnRefresh) {
            return;
        }
        search();
    }

    private void onRowClick(AdapterView<?> parent, View view, int position, long id) {
        Intent intent = new Intent(this, PlainDisposalDetailActivity.class);
        intent.putExtra(PlainDisposalDetailActivity.EXTRA_DISPOSAL_NO, rows.get(position).get("disposalNo"));
        startActivity(intent);
    }

    private void search() {
        ((TextView) viewMap.get("tvCount")).setText("Đang tải…");
        HttpTask.get(this, "api/disposals?status=" + status, new HttpTask.Callback() {
            @Override
            public void onSuccess(String body) {
                if (isFinishing() || isDestroyed()) {
                    return; // màn đã đóng trong lúc chờ
                }
                try {
                    List<HashMap<String, String>> result = JsonRows.rows(body); // parse lỗi thì rows giữ nguyên
                    rows.clear();
                    rows.addAll(result);
                    adapter.notifyDataSetChanged(); // ListView gọi lại getView cho các dòng đang hiện
                    ((TextView) viewMap.get("tvCount")).setText(rows.size() + " phiếu");
                } catch (JSONException e) {
                    onError(200, "Dữ liệu server không đúng định dạng");
                }
            }

            @Override
            public void onError(int httpCode, String message) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                ((TextView) viewMap.get("tvCount")).setText("");
                Toast.makeText(PlainDisposalListActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }
}
