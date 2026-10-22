package com.example.andemo.model;

public class ProductDto {
    private String barcode;
    private String name;
    private String description;
    private String imageUrl;
    private int stockQuantity;
    private Integer salePrice;
    // null khi menu 원가 (COST) đang bị khóa
    private Integer costPrice;
    private Double marginRate;

    public String getBarcode() { return barcode; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getImageUrl() { return imageUrl; }
    public int getStockQuantity() { return stockQuantity; }
    public Integer getSalePrice() { return salePrice; }
    public Integer getCostPrice() { return costPrice; }
    public Double getMarginRate() { return marginRate; }
}
