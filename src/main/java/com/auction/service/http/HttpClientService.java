package com.auction.service.http;

import com.google.gson.Gson;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;

public class HttpClientService {

    private static final String BASE_URL = "http://localhost:8080/api";
    private final OkHttpClient httpClient;
    private final Gson gson;

    public HttpClientService() {
        this.httpClient = new OkHttpClient();
        this.gson = new Gson();
    }

    public String get(String endpoint) throws IOException {
        Request request = new Request.Builder()
                .url(BASE_URL + endpoint)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }
            return response.body() != null ? response.body().string() : "";
        }
    }

    public String post(String endpoint, String jsonBody) throws IOException {
        okhttp3.MediaType mediaType = okhttp3.MediaType.parse("application/json; charset=utf-8");
        okhttp3.RequestBody body = okhttp3.RequestBody.create(jsonBody, mediaType);

        Request request = new Request.Builder()
                .url(BASE_URL + endpoint)
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                throw new IOException("Error: " + response.code() + " - " + errorBody);
            }
            return response.body() != null ? response.body().string() : "";
        }
    }

    public String put(String endpoint, String jsonBody) throws IOException {
        okhttp3.MediaType mediaType = okhttp3.MediaType.parse("application/json; charset=utf-8");
        okhttp3.RequestBody body = okhttp3.RequestBody.create(jsonBody, mediaType);

        Request request = new Request.Builder()
                .url(BASE_URL + endpoint)
                .put(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }
            return response.body() != null ? response.body().string() : "";
        }
    }

    public Gson getGson() {
        return gson;
    }
}
