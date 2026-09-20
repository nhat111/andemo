package com.example.andemo.api;

import com.example.andemo.model.ItemDto;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;

public interface ItemService {
    @GET("api/items")
    Call<List<ItemDto>> getItems();
}
