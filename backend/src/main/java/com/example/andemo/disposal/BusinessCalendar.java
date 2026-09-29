package com.example.andemo.disposal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 영업일자 (ngày kinh doanh) hiện tại của cửa hàng: ngày theo giờ Hàn Quốc.
 * (Nhiều chuỗi cửa hàng 24h đổi ngày kinh doanh ở một giờ cố định, ví dụ 0h; demo dùng 0h.)
 */
@Component
public class BusinessCalendar {

    private final Clock clock;

    public BusinessCalendar(@Value("${app.store.zone:Asia/Seoul}") String zone) {
        this.clock = Clock.system(ZoneId.of(zone));
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }
}
