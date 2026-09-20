package com.example.andemo.api;

import com.example.andemo.model.LocationRequest;
import com.example.andemo.model.NearbyUserDto;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface LocationService {
    @POST("api/location/update")
    Call<Void> updateLocation(@Body LocationRequest request);

    @GET("api/location/nearby")
    Call<List<NearbyUserDto>> getNearby(@Query("radiusKm") double radiusKm);
}
