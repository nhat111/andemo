package com.example.andemo.model;

public class ProductDto {
    private String barcode;
    private String name;
    private String description;
    private String imageUrl;
    private int stockQuantity;

    public String getBarcode() { return barcode; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getImageUrl() { return imageUrl; }
    public int getStockQuantity() { return stockQuantity; }
}
