-- 1 PDA = 1 dòng: cửa hàng, người dùng, thời điểm hoạt động gần nhất (xem pda/DeviceActivityService)
CREATE TABLE PDA_DEVICE_ACTIVITY (
    UNIQUE_ID       VARCHAR2(64) PRIMARY KEY,
    STORE_CD        VARCHAR2(10) NOT NULL,
    USER_ID         VARCHAR2(20) NOT NULL,   -- người dùng gần nhất (giữ nguyên sau khi đăng xuất)
    LAST_ACTIVE_AT  DATE         NOT NULL,
    LOGOUT_AT       DATE                     -- chỉ để hiển thị, KHÔNG dùng để loại máy
);
CREATE INDEX IX_PDA_ACT_STORE ON PDA_DEVICE_ACTIVITY (STORE_CD, LAST_ACTIVE_AT);
