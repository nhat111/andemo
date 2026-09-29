package com.example.andemo.model;

/** Thông tin mặt hàng khi quét barcode trên màn đăng ký (GET /api/inventory/{code}). */
public class InventoryItemDto {
    private String itemCode;
    private String itemName;
    private long onHandQty;
    private long availableQty;
    private long costPrice;
    private long salePrice;

    public String getItemCode() { return itemCode; }
    public String getItemName() { return itemName; }
    public long getOnHandQty() { return onHandQty; }
    public long getAvailableQty() { return availableQty; }
    public long getCostPrice() { return costPrice; }
    public long getSalePrice() { return salePrice; }
}
