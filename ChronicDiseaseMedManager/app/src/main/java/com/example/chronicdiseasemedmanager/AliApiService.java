package com.example.chronicdiseasemedmanager;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;

public interface AliApiService {
    @POST("chat/completions")
    Call<AliApiResponse> chatCompletion(
            @Header("Authorization") String authorization,
            @Header("Content-Type") String contentType,
            @Body AliApiRequest request
    );
}