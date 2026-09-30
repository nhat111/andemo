package com.example.andemo.migration;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.andemo.R;
import com.example.andemo.api.ApiClient;
import com.example.andemo.api.NexacroGatewayApi;
import com.example.andemo.model.NxRequest;
import com.example.andemo.model.NxResponse;
import com.example.andemo.model.ProductDto;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Demo: app Android gọi service Nexacro cũ (X-API, XML Dataset) mà server không phải sửa,
 * nhờ cổng chuyển đổi JSON /api/nx/** của backend. Xem docs/nexacro-migration/NEXACRO_XAPI_TO_JSON.md.
 */
public class NexacroGatewayDemoActivity extends AppCompatActivity {

    /** Cà phê G7: dùng cho demo lưu nhập kho */
    private static final String DEMO_BARCODE = "8936036020151";

    private NexacroGatewayApi api;
    private TextView tvResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nexacro_gateway_demo);
        setTitle("Demo X-API → JSON");

        api = ApiClient.getClient(this).create(NexacroGatewayApi.class);
        tvResult = findViewById(R.id.tvResult);

        findViewById(R.id.btnDemoSearch).setOnClickListener(v -> demoSearch());
        findViewById(R.id.btnDemoCodes).setOnClickListener(v -> demoCodes());
        findViewById(R.id.btnDemoSave).setOnClickListener(v -> demoSave(10));
        findViewById(R.id.btnDemoError).setOnClickListener(v -> demoSave(0));
    }

    /**
     * Nexacro:
     * this.transaction("search", "svc::product/search.do", "ds_search=ds_search", "ds_list=ds_list", "", "fn_callback");
     */
    private void demoSearch() {
        NxRequest req = new NxRequest();
        req.row("ds_search").put("keyword", "sữa");

        send("product/search", req, res -> {
            // Dataset → List<ProductDto>: dùng lại model của API JSON
            List<ProductDto> products = res.dataset("ds_list", ProductDto.class);
            StringBuilder sb = new StringBuilder("ds_list: " + products.size() + " dòng\n\n");
            for (ProductDto p : products) {
                sb.append("• ").append(p.getName()).append(" — tồn ").append(p.getStockQuantity()).append('\n');
            }
            return sb.toString();
        });
    }

    /** 1 transaction, nhiều out-dataset: "ds_category=ds_category ds_unit=ds_unit" */
    private void demoCodes() {
        send("code/list", new NxRequest(), res ->
                "codeVersion = " + res.param("codeVersion")
                        + "\n\nds_category:\n" + res.datasetJson("ds_category")
                        + "\n\nds_unit:\n" + res.datasetJson("ds_unit"));
    }

    /** Gửi ds_stock:U gồm 1 dòng insert + 1 dòng update (kèm giá trị gốc) */
    private void demoSave(int qty) {
        NxRequest req = new NxRequest();
        Map<String, Object> inserted = req.row("ds_stock", "insert");
        inserted.put("barcode", DEMO_BARCODE);
        inserted.put("qty", qty);
        inserted.put("note", "Nhập từ app Android");

        Map<String, Object> updated = req.row("ds_stock", "update");
        updated.put("barcode", DEMO_BARCODE);
        updated.put("qty", 8);
        updated.put("_orgRow", Map.of("barcode", DEMO_BARCODE, "qty", 5));

        send("stock/save", req, res ->
                "Lưu thành công: savedCount = " + res.param("savedCount")
                        + "\n\nds_result:\n" + res.datasetJson("ds_result"));
    }

    private interface OnSuccess {
        String render(NxResponse res);
    }

    /** Tương đương transaction + fn_callback(svcID, errorCode, errorMsg) */
    private void send(String service, NxRequest req, OnSuccess onSuccess) {
        tvResult.setText("Đang gọi " + service + ".do …");
        api.call(service, req).enqueue(new Callback<NxResponse>() {
            @Override
            public void onResponse(Call<NxResponse> call, Response<NxResponse> response) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                NxResponse res = response.isSuccessful() && response.body() != null
                        ? response.body()
                        : NxResponse.fromError(response);
                if (!res.isSuccess()) {                                   // ~ if (errorCode < 0)
                    tvResult.setText("ErrorCode = " + res.getErrorCode() + "\nErrorMsg = " + res.getErrorMsg());
                    return;
                }
                tvResult.setText(onSuccess.render(res));
            }

            @Override
            public void onFailure(Call<NxResponse> call, Throwable t) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                tvResult.setText("Không kết nối được server: " + t.getMessage());
            }
        });
    }
}
