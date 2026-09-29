package com.example.andemo.api;

import com.example.andemo.model.ProductDto;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ProductService {

    @GET("api/products/barcode/{barcode}")
    Call<ProductDto> getByBarcode(@Path("barcode") String barcode);

    /** Tra cứu theo tên / barcode; keyword null hoặc rỗng = tất cả. */
    @GET("api/products")
    Call<List<ProductDto>> search(@Query("keyword") String keyword);
}
