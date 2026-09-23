package com.example.andemo;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.example.andemo.api.ApiClient;
import com.example.andemo.api.ProductService;
import com.example.andemo.model.ProductDto;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * US-05: Hiển thị thông tin + ảnh sản phẩm sau khi quét barcode.
 */
public class ProductDetailActivity extends AppCompatActivity {

    public static final String EXTRA_BARCODE = "barcode";

    private ProgressBar progressBar;
    private TextView tvBarcode, tvName, tvDescription, tvStock, tvError;
    private ImageView ivProduct;
    private Button btnBack, btnRetry;

    private String barcode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_detail);

        progressBar = findViewById(R.id.progressBar);
        tvBarcode = findViewById(R.id.tvBarcode);
        tvName = findViewById(R.id.tvName);
        tvDescription = findViewById(R.id.tvDescription);
        tvStock = findViewById(R.id.tvStock);
        tvError = findViewById(R.id.tvError);
        ivProduct = findViewById(R.id.ivProduct);
        btnBack = findViewById(R.id.btnBack);
        btnRetry = findViewById(R.id.btnRetry);

        barcode = getIntent().getStringExtra(EXTRA_BARCODE);
        if (barcode == null || barcode.isEmpty()) {
            showError("Không có mã barcode");
            return;
        }

        tvBarcode.setText("Barcode: " + barcode);
        btnBack.setOnClickListener(v -> finish());
        btnRetry.setOnClickListener(v -> loadProduct(barcode));

        // Click ảnh → có thể mở zoom sau (US-05 zoom)
        ivProduct.setOnClickListener(v -> {
            // TODO: mở popup phóng to ảnh nếu cần
            Toast.makeText(this, "Zoom ảnh (có thể bổ sung sau)", Toast.LENGTH_SHORT).show();
        });

        loadProduct(barcode);
    }

    private void loadProduct(String barcode) {
        showLoading(true);
        tvError.setVisibility(View.GONE);
        btnRetry.setVisibility(View.GONE);

        ProductService service = ApiClient.getClient(this).create(ProductService.class);
        service.getByBarcode(barcode).enqueue(new Callback<ProductDto>() {
            @Override
            public void onResponse(Call<ProductDto> call, Response<ProductDto> response) {
                showLoading(false);
                if (response.isSuccessful() && response.body() != null) {
                    bindProduct(response.body());
                } else if (response.code() == 404) {
                    showError("Không tìm thấy sản phẩm với barcode: " + barcode);
                } else {
                    showError("Lỗi server: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<ProductDto> call, Throwable t) {
                showLoading(false);
                showError("Lỗi kết nối: " + t.getMessage());
            }
        });
    }

    private void bindProduct(ProductDto product) {
        tvName.setText(product.getName() != null ? product.getName() : "-");
        tvDescription.setText(product.getDescription() != null ? product.getDescription() : "-");
        tvStock.setText("Tồn kho: " + product.getStockQuantity());

        String imageUrl = product.getImageUrl();
        if (imageUrl != null && !imageUrl.isEmpty()) {
            Glide.with(this)
                    .load(imageUrl)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_report_image)
                    .into(ivProduct);
        } else {
            // imageUrl null → hiện placeholder
            ivProduct.setImageResource(android.R.drawable.ic_menu_gallery);
            Toast.makeText(this, "Sản phẩm chưa có ảnh", Toast.LENGTH_SHORT).show();
        }
    }

    private void showLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
        btnRetry.setVisibility(View.VISIBLE);
        tvName.setText("-");
        tvDescription.setText("-");
        tvStock.setText("-");
        ivProduct.setImageResource(android.R.drawable.ic_menu_report_image);
    }
}
