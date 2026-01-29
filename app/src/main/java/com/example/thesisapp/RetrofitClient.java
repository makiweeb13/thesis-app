package com.example.thesisapp;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import java.util.concurrent.TimeUnit;

public class RetrofitClient {

    private static final String BASE_URL = "http://13.236.148.69:8000/"; // IMPORTANT: Must end with a slash '/'
    private static Retrofit retrofit = null;

    public static Retrofit getClient() {
        if (retrofit == null) {
            // For logging network requests and responses (Optional, but very helpful for debugging)
            HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
            loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY); // Log request and response lines and their respective headers and bodies (if present).

            OkHttpClient okHttpClient = new OkHttpClient.Builder()
                    .addInterceptor(loggingInterceptor) // Add the logging interceptor
                    .connectTimeout(30, TimeUnit.SECONDS) // Connection timeout
                    .readTimeout(180, TimeUnit.SECONDS)    // Read timeout
                    .writeTimeout(180, TimeUnit.SECONDS)   // Write timeout
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(okHttpClient) // Set the custom OkHttpClient
                    .addConverterFactory(GsonConverterFactory.create()) // Use Gson for JSON parsing
                    .build();
        }
        return retrofit;
    }

    public static ApiService getApiService() {
        return getClient().create(ApiService.class);
    }
}