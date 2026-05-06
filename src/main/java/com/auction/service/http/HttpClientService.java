package com.auction.service.http;

import com.google.gson.Gson;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class HttpClientService {

    private static final String BASE_URL = "http://localhost:8080/api";
    private final OkHttpClient httpClient;
    private final Gson gson;
    private String authToken;

    public HttpClientService() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
    }

    public void setAuthToken(String token) {
        this.authToken = token;
    }

    public String getAuthToken() {
        return authToken;
    }

    public String get(String endpoint) throws IOException {
        Request.Builder builder = new Request.Builder()
                .url(BASE_URL + endpoint);
        addAuth(builder);

        try (Response response = httpClient.newCall(builder.build()).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                throw new IOException("HTTP " + response.code() + ": " + errorBody);
            }
            return response.body() != null ? response.body().string() : "";
        }
    }

    public String post(String endpoint, String jsonBody) throws IOException {
        okhttp3.MediaType mediaType = okhttp3.MediaType.parse("application/json; charset=utf-8");
        okhttp3.RequestBody body = okhttp3.RequestBody.create(jsonBody, mediaType);

        Request.Builder builder = new Request.Builder()
                .url(BASE_URL + endpoint)
                .post(body);
        addAuth(builder);

        try (Response response = httpClient.newCall(builder.build()).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                throw new IOException("HTTP " + response.code() + ": " + errorBody);
            }
            return response.body() != null ? response.body().string() : "";
        }
    }

    public String put(String endpoint, String jsonBody) throws IOException {
        okhttp3.MediaType mediaType = okhttp3.MediaType.parse("application/json; charset=utf-8");
        okhttp3.RequestBody body = okhttp3.RequestBody.create(jsonBody, mediaType);

        Request.Builder builder = new Request.Builder()
                .url(BASE_URL + endpoint)
                .put(body);
        addAuth(builder);

        try (Response response = httpClient.newCall(builder.build()).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                throw new IOException("HTTP " + response.code() + ": " + errorBody);
            }
            return response.body() != null ? response.body().string() : "";
        }
    }

    public Gson getGson() {
        return gson;
    }

    private void addAuth(Request.Builder builder) {
        if (authToken != null && !authToken.isBlank()) {
            builder.addHeader("Authorization", "Bearer " + authToken);
        }
    }
}
