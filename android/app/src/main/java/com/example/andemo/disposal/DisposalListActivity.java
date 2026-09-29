package com.example.andemo.disposal;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.andemo.R;
import com.example.andemo.api.ApiClient;
import com.example.andemo.api.DisposalApi;
import com.example.andemo.model.ApiErrorDto;
import com.example.andemo.model.DisposalSummaryDto;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Task 17 – Disposal Inquiry Screen: lọc theo trạng thái, bấm 1 phiếu để xem chi tiết. */
public class DisposalListActivity extends AppCompatActivity {

    /** Nhãn hiển thị ↔ giá trị gửi server (null = tất cả). Mặc định: chờ xác nhận */
    private static final String[] STATUS_LABELS = {"Chờ xác nhận", "Đã xác nhận", "Đã hủy", "Tất cả"};
    private static final String[] STATUS_VALUES = {"REQUESTED", "CONFIRMED", "CANCELLED", null};

    private DisposalApi api;
    private DisposalListAdapter adapter;
    private Spinner spStatus;
    private TextView tvCount;
    private ProgressBar progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_disposal_list);
        setTitle("Phiếu hủy hàng");

        api = ApiClient.getClient(this).create(DisposalApi.class);
        tvCount = findViewById(R.id.tvCount);
        progress = findViewById(R.id.progress);

        RecyclerView rv = findViewById(R.id.rvDisposals);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        adapter = new DisposalListAdapter(d -> {
            Intent intent = new Intent(this, DisposalDetailActivity.class);
            intent.putExtra(DisposalDetailActivity.EXTRA_DISPOSAL_NO, d.getDisposalNo());
            startActivity(intent);
        });
        rv.setAdapter(adapter);

        spStatus = findViewById(R.id.spStatus);
        ArrayAdapter<String> statusAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, STATUS_LABELS);
        statusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spStatus.setAdapter(statusAdapter);
        spStatus.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                load();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    /** Quay lại từ màn chi tiết (có thể vừa xác nhận): tải lại để trạng thái mới nhất */
    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        String status = STATUS_VALUES[spStatus.getSelectedItemPosition()];
        progress.setVisibility(View.VISIBLE);
        api.list(status).enqueue(new Callback<List<DisposalSummaryDto>>() {
            @Override
            public void onResponse(Call<List<DisposalSummaryDto>> call, Response<List<DisposalSummaryDto>> response) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                progress.setVisibility(View.GONE);
                if (!response.isSuccessful() || response.body() == null) {
                    Toast.makeText(DisposalListActivity.this, ApiErrorDto.from(response).getMessage(),
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                List<DisposalSummaryDto> list = response.body();
                adapter.submit(list);
                tvCount.setText(list.isEmpty() ? "Không có phiếu nào" : list.size() + " phiếu");
            }

            @Override
            public void onFailure(Call<List<DisposalSummaryDto>> call, Throwable t) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                progress.setVisibility(View.GONE);
                Toast.makeText(DisposalListActivity.this, "Không kết nối được server", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
