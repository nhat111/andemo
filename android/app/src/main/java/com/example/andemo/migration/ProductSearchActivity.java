package com.example.andemo.migration;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.andemo.ProductDetailActivity;
import com.example.andemo.R;
import com.example.andemo.api.ApiClient;
import com.example.andemo.api.ProductService;
import com.example.andemo.model.ProductDto;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Bài mẫu migrate: nexacro-sample/frm_product_search.xfdl → Activity.
 * Chữ (A)…(L) khớp với chú thích trong file .xfdl; đối chiếu đầy đủ: docs/nexacro-migration/MIGRATION_LAB.md.
 */
public class ProductSearchActivity extends AppCompatActivity {

    private EditText edtKeyword;
    private TextView tvCount;
    private ProgressBar progress;
    private Button btnSearch;
    private ProductAdapter adapter;
    private ProductService api;

    /** (G) form_onload */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_search);
        setTitle("Tra cứu sản phẩm");                                // ~ titletext

        edtKeyword = findViewById(R.id.edtKeyword);
        tvCount = findViewById(R.id.tvCount);
        progress = findViewById(R.id.progress);
        btnSearch = findViewById(R.id.btnSearch);

        // (C) Grid: gắn Adapter + kiểu hiển thị danh sách dọc
        RecyclerView rv = findViewById(R.id.rvProducts);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        adapter = new ProductAdapter(this::openDetail);             // (L) oncellclick
        rv.setAdapter(adapter);

        api = ApiClient.getClient(this).create(ProductService.class);

        // (H) btn_search_onclick
        btnSearch.setOnClickListener(v -> search());

        // (I) edt_keyword_onkeydown (Enter) → nút "Tìm" trên bàn phím ảo
        edtKeyword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                search();
                return true;
            }
            return false;
        });

        // (G) form_onload gọi fn_search()
        search();
    }

    /** (J) fn_search: ds_search → query param, transaction → Retrofit enqueue */
    private void search() {
        // (F) BindItem edt_keyword ↔ ds_search.keyword: Android không tự bind, đọc trực tiếp từ view
        String keyword = edtKeyword.getText().toString().trim();

        setLoading(true);
        api.search(keyword).enqueue(new Callback<List<ProductDto>>() {
            /** (K) fn_callback, trường hợp server trả lời (kể cả lỗi HTTP) */
            @Override
            public void onResponse(Call<List<ProductDto>> call, Response<List<ProductDto>> response) {
                if (isFinishing() || isDestroyed()) {
                    return; // người dùng đã thoát màn hình trong lúc chờ: không đụng vào view nữa
                }
                setLoading(false);
                if (!response.isSuccessful() || response.body() == null) {   // ~ errorCode < 0
                    Toast.makeText(ProductSearchActivity.this,
                            "Lỗi server: HTTP " + response.code(), Toast.LENGTH_SHORT).show();
                    return;
                }
                List<ProductDto> list = response.body();                    // ~ ds_list
                adapter.submit(list);
                tvCount.setText(list.isEmpty() ? "Không có sản phẩm nào" : list.size() + " sản phẩm");
            }

            /** (K) fn_callback, trường hợp không tới được server (mất mạng, timeout) */
            @Override
            public void onFailure(Call<List<ProductDto>> call, Throwable t) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                setLoading(false);
                Toast.makeText(ProductSearchActivity.this,
                        "Không kết nối được server", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** (L) grd_list_oncellclick: gv_barcode + go(...) → Intent extra + startActivity */
    private void openDetail(ProductDto product) {
        Intent intent = new Intent(this, ProductDetailActivity.class);
        intent.putExtra(ProductDetailActivity.EXTRA_BARCODE, product.getBarcode());
        startActivity(intent);
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnSearch.setEnabled(!loading);   // tránh bấm Tìm nhiều lần khi đang chờ
    }
}
