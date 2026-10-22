package com.example.andemo.model;

import java.util.List;

public class MenuDataDto {
    private String menuCode;
    private boolean masked;
    private List<String> columns;
    private List<List<String>> rows;

    public String getMenuCode() { return menuCode; }
    public boolean isMasked() { return masked; }
    public List<String> getColumns() { return columns; }
    public List<List<String>> getRows() { return rows; }
}
