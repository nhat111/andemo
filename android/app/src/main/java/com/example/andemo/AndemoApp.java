package com.example.andemo;

import android.app.Application;

import com.example.andemo.log.DeviceLog;

public class AndemoApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // Trước mọi thứ khác: service / receiver khởi động cùng process cũng được ghi log
        DeviceLog.init(this);
    }
}
