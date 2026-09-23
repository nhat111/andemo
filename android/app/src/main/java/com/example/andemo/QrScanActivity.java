package com.example.andemo;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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

/**
 * Màn hình quét barcode/QR.
 *
 * Hỗ trợ 3 cách:
 * 1. Camera (ZXing)
 * 2. Chọn ảnh từ gallery
 * 3. Hardware scanner của PDA (nhận Intent từ DataWedge / các hãng khác)
 */
public class QrScanActivity extends AppCompatActivity {

    private static final String TAG = "QrScanActivity";

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

        // Nhận barcode từ hardware scanner (nếu activity được mở bởi Intent)
        handleScannerIntent(getIntent());
    }

    /**
     * Khi activity đang mở mà nhận Intent mới từ scanner cứng.
     * Cần android:launchMode="singleTop" trong Manifest.
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleScannerIntent(intent);
    }

    /**
     * Xử lý Intent từ hardware scanner của PDA.
     * Hỗ trợ nhiều key phổ biến (Zebra DataWedge + các hãng khác).
     */
    private void handleScannerIntent(Intent intent) {
        if (intent == null) return;

        String barcode = extractBarcodeFromIntent(intent);
        if (barcode != null && !barcode.isEmpty()) {
            Log.d(TAG, "Received barcode from hardware scanner: " + barcode);
            showResult(barcode);
        }
    }

    /**
     * Lấy chuỗi barcode từ các extra phổ biến của PDA.
     * Không cần import SDK hãng.
     */
    private String extractBarcodeFromIntent(Intent intent) {
        // Zebra DataWedge (phổ biến nhất)
        if (intent.hasExtra("com.symbol.data.scannerdata")) {
            return intent.getStringExtra("com.symbol.data.scannerdata");
        }
        // Zebra DataWedge - một số profile dùng key này
        if (intent.hasExtra("com.motorolasolutions.emdk.datawedge.data_string")) {
            return intent.getStringExtra("com.motorolasolutions.emdk.datawedge.data_string");
        }
        // Honeywell / nhiều máy khác
        if (intent.hasExtra("data")) {
            return intent.getStringExtra("data");
        }
        if (intent.hasExtra("barcode")) {
            return intent.getStringExtra("barcode");
        }
        // Một số máy dùng key này
        if (intent.hasExtra("SCAN_BARCODE1")) {
            return intent.getStringExtra("SCAN_BARCODE1");
        }
        if (intent.hasExtra("barcode_string")) {
            return intent.getStringExtra("barcode_string");
        }
        // Urovo / một số model
        if (intent.hasExtra("scan_data")) {
            return intent.getStringExtra("scan_data");
        }

        return null;
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
        // Hỗ trợ nhiều loại barcode (không chỉ QR) – phù hợp PDA kho
        options.setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES);
        options.setPrompt("Đưa mã vào khung hình");
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
            Toast.makeText(this, "Không tìm thấy mã trong ảnh", Toast.LENGTH_LONG).show();
            tvQrResult.setText("Không detect được mã trong ảnh này.");
        } catch (Exception e) {
            Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_LONG).show();
            tvQrResult.setText("Lỗi đọc ảnh: " + e.getMessage());
        }
    }

    private void showResult(String content) {
        tvQrResult.setText(content);
        Toast.makeText(this, "Quét thành công!", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Barcode result: " + content);
    }
}
