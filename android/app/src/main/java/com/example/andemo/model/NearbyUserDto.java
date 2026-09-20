package com.example.andemo.model;

public class NearbyUserDto {
    private String username;
    private String role;
    private double distanceKm;
    private double latitude;
    private double longitude;

    public String getUsername() { return username; }
    public String getRole() { return role; }
    public double getDistanceKm() { return distanceKm; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
}
