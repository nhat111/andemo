package com.example.andemo.model;

public class ItemDto {
    private Long id;
    private String name;
    private String description;
    private boolean adminOnly;

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public boolean isAdminOnly() { return adminOnly; }
}
