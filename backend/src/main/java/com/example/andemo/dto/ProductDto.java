package com.example.andemo.dto;

public class ProductDto {
    private String barcode;
    private String name;
    private String description;
    private String imageUrl;
    private int stockQuantity;
    private Integer salePrice;
    // Dữ liệu nhạy cảm (giá vốn / tỷ suất lợi nhuận): null khi menu COST đang bị khóa
    private Integer costPrice;
    private Double marginRate;

    public ProductDto() {}

    public ProductDto(String barcode, String name, String description, String imageUrl, int stockQuantity) {
        this.barcode = barcode;
        this.name = name;
        this.description = description;
        this.imageUrl = imageUrl;
        this.stockQuantity = stockQuantity;
    }

    public ProductDto(String barcode, String name, String description, String imageUrl, int stockQuantity,
                      Integer salePrice, Integer costPrice, Double marginRate) {
        this(barcode, name, description, imageUrl, stockQuantity);
        this.salePrice = salePrice;
        this.costPrice = costPrice;
        this.marginRate = marginRate;
    }

    /** Bản copy đã ẩn Giá vốn / Tỷ suất lợi nhuận (không sửa object gốc) */
    public ProductDto masked() {
        return new ProductDto(barcode, name, description, imageUrl, stockQuantity, salePrice, null, null);
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

    public Integer getSalePrice() { return salePrice; }
    public void setSalePrice(Integer salePrice) { this.salePrice = salePrice; }

    public Integer getCostPrice() { return costPrice; }
    public void setCostPrice(Integer costPrice) { this.costPrice = costPrice; }

    public Double getMarginRate() { return marginRate; }
    public void setMarginRate(Double marginRate) { this.marginRate = marginRate; }
}
