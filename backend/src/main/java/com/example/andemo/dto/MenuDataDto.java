package com.example.andemo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class MenuDataDto {
    private String menuCode;
    private boolean masked;          // true = cột nhạy cảm đang bị để trống
    private List<String> columns;
    private List<List<String>> rows; // ô bị ẩn trả về ""
}
