package com.example.andemo.dto;

public class ProductDto {
    private String barcode;
    private String name;
    private String description;
    private String imageUrl;
    private int stockQuantity;

    public ProductDto() {}

    public ProductDto(String barcode, String name, String description, String imageUrl, int stockQuantity) {
        this.barcode = barcode;
        this.name = name;
        this.description = description;
        this.imageUrl = imageUrl;
        this.stockQuantity = stockQuantity;
    }

    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public int getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(int stockQuantity) { this.stockQuantity = stockQuantity; }
}
