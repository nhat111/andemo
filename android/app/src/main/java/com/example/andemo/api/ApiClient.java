package com.example.andemo.api;

import android.content.Context;

import com.example.andemo.BuildConfig;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    // Mặc định là backend trên Render; đổi lúc build bằng -PapiBaseUrl (xem app/build.gradle)
    private static final String BASE_URL = BuildConfig.API_BASE_URL;

    private static Retrofit retrofit = null;
    private static Retrofit publicRetrofit = null;

    /**
     * Client cho API cần đăng nhập: tự gắn Bearer token, và tự làm mới token khi server trả 401.
     */
    public static synchronized Retrofit getClient(Context context) {
        if (retrofit == null) {
            // Dùng application context: client sống suốt vòng đời app, giữ Activity sẽ bị leak
            Context appContext = context.getApplicationContext();
            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(new AuthInterceptor(appContext))  // ← tự gắn Bearer token
                    .authenticator(new TokenAuthenticator(appContext)) // ← 401 thì làm mới token rồi gửi lại
                    .addInterceptor(buildLogging())
                    .build();

            retrofit = build(client);
        }
        return retrofit;
    }

    /**
     * Client không gắn token, không tự làm mới token. Dùng cho refresh token:
     * nếu dùng client ở trên, lời gọi refresh bị 401 sẽ lại kích hoạt refresh, lặp vô hạn.
     */
    static synchronized Retrofit getPublicClient() {
        if (publicRetrofit == null) {
            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(buildLogging())
                    .build();
            publicRetrofit = build(client);
        }
        return publicRetrofit;
    }

    private static HttpLoggingInterceptor buildLogging() {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        // Bản release không log body: body chứa token và refresh token
        logging.setLevel(BuildConfig.DEBUG ? HttpLoggingInterceptor.Level.BODY : HttpLoggingInterceptor.Level.NONE);
        return logging;
    }

    private static Retrofit build(OkHttpClient client) {
        return new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }
}
