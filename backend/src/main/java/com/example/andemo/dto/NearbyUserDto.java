package com.example.andemo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class NearbyUserDto {
    private String username;
    private String role;
    private double distanceKm; // khoảng cách (km)
    private double latitude;
    private double longitude;
}
