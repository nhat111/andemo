package com.example.andemo;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import java.io.InputStream;

public class QrScanActivity extends AppCompatActivity {

    private TextView tvQrResult;

    // Launcher quét bằng camera (ZXing)
    private final ActivityResultLauncher<ScanOptions> barcodeLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                if (result.getContents() != null) {
                    showResult(result.getContents());
                } else {
                    Toast.makeText(this, "Đã hủy quét", Toast.LENGTH_SHORT).show();
                }
            });

    // Launcher chọn ảnh từ gallery
    private final ActivityResultLauncher<String> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    decodeQrFromUri(uri);
                }
            });

    // Launcher xin quyền camera
    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startCameraScan();
                } else {
                    Toast.makeText(this, "Cần quyền Camera để quét QR", Toast.LENGTH_LONG).show();
                }
            });

    // Launcher xin quyền đọc ảnh
    private final ActivityResultLauncher<String> storagePermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    openGallery();
                } else {
                    Toast.makeText(this, "Cần quyền truy cập ảnh", Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_scan);

        tvQrResult = findViewById(R.id.tvQrResult);
        Button btnOpenCamera = findViewById(R.id.btnOpenCamera);
        Button btnPickGallery = findViewById(R.id.btnPickGallery);
        Button btnBack = findViewById(R.id.btnBack);

        btnOpenCamera.setOnClickListener(v -> checkCameraPermissionAndScan());
        btnPickGallery.setOnClickListener(v -> checkStoragePermissionAndPick());
        btnBack.setOnClickListener(v -> finish());
    }

    private void checkCameraPermissionAndScan() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCameraScan();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void startCameraScan() {
        ScanOptions options = new ScanOptions();
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
        options.setPrompt("Đưa mã QR vào khung hình");
        options.setCameraId(0);
        options.setBeepEnabled(true);
        options.setBarcodeImageEnabled(true);
        options.setOrientationLocked(true);
        barcodeLauncher.launch(options);
    }

    private void checkStoragePermissionAndPick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    == PackageManager.PERMISSION_GRANTED) {
                openGallery();
            } else {
                storagePermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES);
            }
        } else {
            // Android 12 trở xuống
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                openGallery();
            } else {
                storagePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }
    }

    private void openGallery() {
        galleryLauncher.launch("image/*");
    }

    private void decodeQrFromUri(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                Toast.makeText(this, "Không đọc được ảnh", Toast.LENGTH_SHORT).show();
                return;
            }

            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            inputStream.close();

            if (bitmap == null) {
                Toast.makeText(this, "Ảnh không hợp lệ", Toast.LENGTH_SHORT).show();
                return;
            }

            // Convert bitmap → pixel array
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            int[] pixels = new int[width * height];
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

            RGBLuminanceSource source = new RGBLuminanceSource(width, height, pixels);
            BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(source));

            Result result = new MultiFormatReader().decode(binaryBitmap);
            showResult(result.getText());

        } catch (com.google.zxing.NotFoundException e) {
            Toast.makeText(this, "Không tìm thấy mã QR trong ảnh", Toast.LENGTH_LONG).show();
            tvQrResult.setText("Không detect được QR trong ảnh này.");
        } catch (Exception e) {
            Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_LONG).show();
            tvQrResult.setText("Lỗi đọc ảnh: " + e.getMessage());
        }
    }

    private void showResult(String content) {
        tvQrResult.setText(content);
        Toast.makeText(this, "Quét thành công!", Toast.LENGTH_SHORT).show();
    }
}
