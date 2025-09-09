package com.example.thesisapp;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

public interface ApiService {

    @Multipart // Important for file uploads
    @POST("predict") // Your API endpoint path (relative to base URL)
    Call<FishDetailsResponse> uploadImageAndGetDetails(
            @Part MultipartBody.Part imageFile, // The image file itself
            @Part("apiKey") RequestBody apiKey // Example: If you need to send an API key as another part
            // You can add more @Part parameters as needed by your API
    );

    // Example of another part if your API expects a simple string:
    // @Part("description") RequestBody description
}