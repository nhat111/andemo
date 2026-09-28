package com.example.andemo.requester;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.andemo.R;
import com.example.andemo.api.AlertApi;
import com.example.andemo.api.ApiClient;
import com.example.andemo.model.AlertStatusDto;
import com.example.andemo.model.CreateAlertRequest;
import com.example.andemo.model.PdaDeviceDto;
import com.example.andemo.util.DeviceIdProvider;
import com.example.andemo.util.PreferenceManager;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Màn hình requester (quản lý, role ADMIN): chọn PDA → gửi lệnh tìm → theo dõi trạng thái.
 *
 * Luồng: POST /api/pda/alerts → PDA nhận qua polling (hoặc WebSocket/FCM) → đổ chuông → ack.
 * Màn hình này hỏi trạng thái lệnh mỗi 3 giây: SENT → DELIVERED → STOPPED_BY_USER / TIMED_OUT.
 */
public class FindPdaActivity extends AppCompatActivity {

    private static final long STATUS_REFRESH_MS = 3_000L;
    // Lệnh hết hạn sau 2 phút: PDA offline lâu hơn thì không kêu nữa (tránh kêu cho lệnh cũ)
    private static final long ALERT_TTL_SECONDS = 120L;

    private AlertApi api;
    private String myDeviceId;
    private String myUsername;

    private final List<PdaDeviceDto> devices = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private TextView tvEmpty;
    private TextView tvStatus;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable statusRunnable = this::refreshStatus;
    // Lệnh đang theo dõi; null = chưa gửi lệnh nào
    private String trackingRequestId;
    private String trackingDeviceLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_find_pda);

        api = ApiClient.getClient(this).create(AlertApi.class);
        myDeviceId = DeviceIdProvider.get(this);
        myUsername = new PreferenceManager(this).getUsername();

        tvEmpty = findViewById(R.id.tvEmpty);
        tvStatus = findViewById(R.id.tvStatus);
        ListView lvDevices = findViewById(R.id.lvDevices);
        Button btnRefresh = findViewById(R.id.btnRefresh);
        Button btnBack = findViewById(R.id.btnBack);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        lvDevices.setAdapter(adapter);
        lvDevices.setEmptyView(tvEmpty);
        lvDevices.setOnItemClickListener((parent, view, position, id) -> confirmFind(devices.get(position)));

        btnRefresh.setOnClickListener(v -> loadDevices());
        btnBack.setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadDevices();
        if (trackingRequestId != null) {
            handler.post(statusRunnable);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Không hỏi server khi màn hình không hiển thị
        handler.removeCallbacks(statusRunnable);
    }

    // ----- Danh sách PDA -----

    private void loadDevices() {
        tvEmpty.setText("Đang tải...");
        api.getDevices().enqueue(new Callback<List<PdaDeviceDto>>() {
            @Override
            public void onResponse(Call<List<PdaDeviceDto>> call, Response<List<PdaDeviceDto>> response) {
                if (isDestroyed()) {
                    return;
                }
                if (response.code() == 403) {
                    tvEmpty.setText("Chỉ tài khoản ADMIN mới được tìm PDA.");
                    return;
                }
                if (!response.isSuccessful() || response.body() == null) {
                    tvEmpty.setText("Lỗi server: " + response.code());
                    return;
                }
                showDevices(response.body());
            }

            @Override
            public void onFailure(Call<List<PdaDeviceDto>> call, Throwable t) {
                if (!isDestroyed()) {
                    tvEmpty.setText("Lỗi kết nối: " + t.getMessage());
                }
            }
        });
    }

    private void showDevices(List<PdaDeviceDto> result) {
        devices.clear();
        devices.addAll(result);
        List<String> labels = new ArrayList<>();
        for (PdaDeviceDto device : devices) {
            labels.add(label(device) + "\n"
                    + (device.isOnline() ? "🟢 Online" : "⚪ Offline")
                    + " · liên lạc " + formatAgo(device.getSecondsSinceLastSeen()) + " trước");
        }
        adapter.clear();
        adapter.addAll(labels);
        tvEmpty.setText("Chưa có PDA nào. PDA cần login và mở app ít nhất 1 lần để server biết đến.");
    }

    private String label(PdaDeviceDto device) {
        // Cả cửa hàng thường dùng cùng 1 model (VD 20 máy "Zebra TC21"): thêm đuôi deviceId để phân biệt
        String id = device.getDeviceId();
        String shortId = id.length() > 6 ? id.substring(id.length() - 6) : id;
        String name = device.getDeviceName() != null ? device.getDeviceName() + " · " + shortId : id;
        // Cho phép tìm chính máy này: tiện khi chỉ có 1 máy để test
        return device.getDeviceId().equals(myDeviceId) ? name + " (máy này)" : name;
    }

    private static String formatAgo(long seconds) {
        if (seconds < 60) {
            return seconds + " giây";
        }
        if (seconds < 3600) {
            return (seconds / 60) + " phút";
        }
        return (seconds / 3600) + " giờ";
    }

    // ----- Gửi lệnh -----

    private void confirmFind(PdaDeviceDto device) {
        String message = device.isOnline()
                ? "Gửi lệnh đổ chuông tới " + label(device) + "?"
                : label(device) + " đang offline. Lệnh có hiệu lực " + (ALERT_TTL_SECONDS / 60)
                + " phút: nếu máy liên lạc lại trong thời gian đó thì vẫn kêu. Vẫn gửi?";
        new AlertDialog.Builder(this)
                .setTitle("Tìm PDA")
                .setMessage(message)
                .setPositiveButton("Tìm", (dialog, which) -> sendAlert(device))
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void sendAlert(PdaDeviceDto device) {
        String text = "PDA đang được tìm bởi " + (myUsername != null ? myUsername : "quản lý");
        CreateAlertRequest body = new CreateAlertRequest(device.getDeviceId(), text, ALERT_TTL_SECONDS);
        api.createAlert(body).enqueue(new Callback<AlertStatusDto>() {
            @Override
            public void onResponse(Call<AlertStatusDto> call, Response<AlertStatusDto> response) {
                if (isDestroyed()) {
                    return;
                }
                if (response.code() == 403) {
                    Toast.makeText(FindPdaActivity.this, "Chỉ ADMIN mới được tìm PDA", Toast.LENGTH_LONG).show();
                    return;
                }
                if (!response.isSuccessful() || response.body() == null) {
                    Toast.makeText(FindPdaActivity.this, "Gửi lệnh thất bại: HTTP " + response.code(),
                            Toast.LENGTH_LONG).show();
                    return;
                }
                trackingRequestId = response.body().getRequestId();
                trackingDeviceLabel = label(device);
                render(response.body());
                handler.removeCallbacks(statusRunnable);
                handler.postDelayed(statusRunnable, STATUS_REFRESH_MS);
            }

            @Override
            public void onFailure(Call<AlertStatusDto> call, Throwable t) {
                if (!isDestroyed()) {
                    Toast.makeText(FindPdaActivity.this, "Lỗi kết nối: " + t.getMessage(),
                            Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    // ----- Theo dõi trạng thái lệnh -----

    private void refreshStatus() {
        if (trackingRequestId == null) {
            return;
        }
        api.getAlert(trackingRequestId).enqueue(new Callback<AlertStatusDto>() {
            @Override
            public void onResponse(Call<AlertStatusDto> call, Response<AlertStatusDto> response) {
                if (isDestroyed()) {
                    return;
                }
                AlertStatusDto alert = response.body();
                if (response.isSuccessful() && alert != null && alert.getRequestId().equals(trackingRequestId)) {
                    boolean finished = render(alert);
                    if (finished) {
                        loadDevices(); // cập nhật "liên lạc x giây trước"
                        return;
                    }
                }
                handler.postDelayed(statusRunnable, STATUS_REFRESH_MS);
            }

            @Override
            public void onFailure(Call<AlertStatusDto> call, Throwable t) {
                if (!isDestroyed()) {
                    handler.postDelayed(statusRunnable, STATUS_REFRESH_MS);
                }
            }
        });
    }

    /** @return true nếu lệnh đã kết thúc (không cần theo dõi nữa) */
    private boolean render(AlertStatusDto alert) {
        String text;
        boolean finished;
        String status = alert.getStatus() == null ? "" : alert.getStatus();
        switch (status) {
            case "DELIVERED":
                text = "🔔 PDA đã nhận lệnh và đang đổ chuông. Đi theo tiếng chuông để tìm máy.";
                finished = false;
                break;
            case "STOPPED_BY_USER":
                text = "✅ Đã có người tắt chuông trên PDA: máy đã được tìm thấy.";
                finished = true;
                break;
            case "TIMED_OUT":
                text = "⏱ Chuông đã kêu hết thời gian và tự tắt, chưa ai tắt trên máy.";
                finished = true;
                break;
            case "SENT":
                if (alert.isExpired()) {
                    text = "❌ Lệnh đã hết hạn mà PDA không nhận được (có thể máy tắt nguồn, mất mạng, "
                            + "hoặc app bị dừng).";
                    finished = true;
                } else {
                    text = "⏳ Đã gửi lệnh, đang chờ PDA nhận (PDA hỏi server mỗi ~30 giây khi đang thức, "
                            + "lâu hơn nếu máy đang ngủ).";
                    finished = false;
                }
                break;
            default:
                text = "Trạng thái: " + status;
                finished = false;
        }
        tvStatus.setVisibility(View.VISIBLE);
        tvStatus.setText(trackingDeviceLabel + "\n" + text + "\n\nMã lệnh: " + alert.getRequestId());
        return finished;
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
