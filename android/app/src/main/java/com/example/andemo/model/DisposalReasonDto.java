package com.example.andemo.model;

/** 폐기사유: mã + tên tiếng Hàn + tên tiếng Việt. toString() để Spinner hiển thị. */
public class DisposalReasonDto {
    private String code;
    private String koreanName;
    private String vietnameseName;

    public String getCode() { return code; }
    public String getKoreanName() { return koreanName; }
    public String getVietnameseName() { return vietnameseName; }

    @Override
    public String toString() {
        return vietnameseName + " (" + koreanName + ")";
    }
}
