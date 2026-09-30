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

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Task 17 – Disposal Inquiry Screen (폐기조회): lọc theo trạng thái, bấm 1 phiếu để xem chi tiết, nút đăng ký mới. */
public class DisposalListActivity extends AppCompatActivity {

    /** Nhãn hiển thị ↔ giá trị gửi server (null = tất cả). Mặc định: đã đăng ký, chờ 점장 xác nhận */
    private static final String[] STATUS_LABELS = {"Đã đăng ký (등록)", "Đã xác nhận (확정)", "Đã hủy (취소)", "Tất cả"};
    private static final String[] STATUS_VALUES = {"REGISTERED", "CONFIRMED", "CANCELLED", null};

    /** R1: khoảng 영업일자 tính lùi từ hôm nay (giờ Hàn Quốc); -1 = không giới hạn */
    private static final String[] PERIOD_LABELS = {"7 ngày gần nhất", "Hôm nay", "30 ngày", "Tất cả"};
    private static final int[] PERIOD_DAYS = {7, 0, 30, -1};

    private DisposalApi api;
    private DisposalListAdapter adapter;
    private Spinner spStatus;
    private Spinner spPeriod;
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

        findViewById(R.id.btnNew).setOnClickListener(v ->
                startActivity(new Intent(this, DisposalEditActivity.class)));

        // Đổi lựa chọn thì tìm lại (giống Nexacro gọi fn_search khi đổi điều kiện)
        AdapterView.OnItemSelectedListener reload = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                load();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        };

        spPeriod = findViewById(R.id.spPeriod);
        ArrayAdapter<String> periodAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, PERIOD_LABELS);
        periodAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spPeriod.setAdapter(periodAdapter);
        spPeriod.setOnItemSelectedListener(reload);

        spStatus = findViewById(R.id.spStatus);
        ArrayAdapter<String> statusAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, STATUS_LABELS);
        statusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spStatus.setAdapter(statusAdapter);
        spStatus.setOnItemSelectedListener(reload);
    }

    /** Quay lại từ màn chi tiết (có thể vừa xác nhận): tải lại để trạng thái mới nhất */
    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    /** Ngày kinh doanh theo giờ Hàn Quốc, lệch offsetDays ngày, dạng yyyy-MM-dd */
    private static String koreaDate(int offsetDays) {
        TimeZone kst = TimeZone.getTimeZone("Asia/Seoul");
        Calendar c = Calendar.getInstance(kst);
        c.add(Calendar.DAY_OF_MONTH, offsetDays);
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        f.setTimeZone(kst);
        return f.format(c.getTime());
    }

    private void load() {
        String status = STATUS_VALUES[spStatus.getSelectedItemPosition()];
        int days = PERIOD_DAYS[spPeriod.getSelectedItemPosition()];
        String to = days < 0 ? null : koreaDate(0);
        String from = days < 0 ? null : koreaDate(-days);
        progress.setVisibility(View.VISIBLE);
        api.list(status, from, to).enqueue(new Callback<List<DisposalSummaryDto>>() {
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
