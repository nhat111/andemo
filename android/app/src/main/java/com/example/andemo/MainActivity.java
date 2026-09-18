package com.example.andemo;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.andemo.util.PreferenceManager;

public class MainActivity extends AppCompatActivity {

    private PreferenceManager pref;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        pref = new PreferenceManager(this);

        if (!pref.isLoggedIn()) {
            goToLogin();
            return;
        }

        TextView tvWelcome = findViewById(R.id.tvWelcome);
        Button btnAdminOnly = findViewById(R.id.btnAdminOnly);
        Button btnUserMenu = findViewById(R.id.btnUserMenu);
        Button btnLogout = findViewById(R.id.btnLogout);

        String role = pref.getRole();
        String username = pref.getUsername();

        tvWelcome.setText("Xin chào " + username + " (" + role + ")");

        // Ẩn/hiện menu theo role
        if ("ADMIN".equals(role)) {
            btnAdminOnly.setVisibility(View.VISIBLE);
            btnUserMenu.setVisibility(View.VISIBLE);
        } else {
            btnAdminOnly.setVisibility(View.GONE);   // USER không thấy
            btnUserMenu.setVisibility(View.VISIBLE);
        }

        btnAdminOnly.setOnClickListener(v ->
                Toast.makeText(this, "Đây là chức năng chỉ ADMIN mới dùng được", Toast.LENGTH_SHORT).show()
        );

        btnUserMenu.setOnClickListener(v ->
                Toast.makeText(this, "Menu chung cho mọi role", Toast.LENGTH_SHORT).show()
        );

        btnLogout.setOnClickListener(v -> {
            pref.clear();
            Toast.makeText(this, "Đã logout", Toast.LENGTH_SHORT).show();
            goToLogin();
        });
    }

    private void goToLogin() {
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }
}
