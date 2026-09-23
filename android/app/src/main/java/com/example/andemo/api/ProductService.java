package com.example.andemo.api;

import com.example.andemo.model.ProductDto;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

public interface ProductService {

    @GET("api/products/barcode/{barcode}")
    Call<ProductDto> getByBarcode(@PathVariable("barcode") String barcode);
}
