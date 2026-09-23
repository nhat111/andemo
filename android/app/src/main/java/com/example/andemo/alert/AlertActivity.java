package com.example.andemo.alert;

import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.andemo.R;

/**
 * Full-screen popup khi PDA nhận lệnh tìm kiếm.
 * User bấm "Dừng Alert" để tắt.
 */
public class AlertActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Hiện trên lock screen + bật sáng màn hình
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        );

        setContentView(R.layout.activity_alert);

        String message = getIntent().getStringExtra(PdaAlertService.EXTRA_MESSAGE);
        String requestId = getIntent().getStringExtra(PdaAlertService.EXTRA_REQUEST_ID);

        TextView tvMessage = findViewById(R.id.tvAlertMessage);
        TextView tvRequestId = findViewById(R.id.tvRequestId);
        Button btnStop = findViewById(R.id.btnStopAlert);

        if (message != null) {
            tvMessage.setText(message);
        }
        if (requestId != null) {
            tvRequestId.setText("Request: " + requestId);
        }

        btnStop.setOnClickListener(v -> {
            // Gửi lệnh stop cho Service
            Intent stopIntent = new Intent(this, PdaAlertService.class);
            stopIntent.setAction(PdaAlertService.ACTION_STOP_ALERT);
            if (requestId != null) {
                stopIntent.putExtra(PdaAlertService.EXTRA_REQUEST_ID, requestId);
            }
            startService(stopIntent);
            finish();
        });
    }

    @Override
    public void onBackPressed() {
        // Không cho bấm back để thoát alert
        // super.onBackPressed();
    }
}
