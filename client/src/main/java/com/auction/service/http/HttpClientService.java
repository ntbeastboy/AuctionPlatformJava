package com.auction.service.http;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
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
        return execute(builder.build());
    }

    public String post(String endpoint, String jsonBody) throws IOException {
        okhttp3.MediaType mediaType = okhttp3.MediaType.parse("application/json; charset=utf-8");
        okhttp3.RequestBody body = okhttp3.RequestBody.create(jsonBody, mediaType);

        Request.Builder builder = new Request.Builder()
                .url(BASE_URL + endpoint)
                .post(body);
        addAuth(builder);
        return execute(builder.build());
    }

    public String put(String endpoint, String jsonBody) throws IOException {
        okhttp3.MediaType mediaType = okhttp3.MediaType.parse("application/json; charset=utf-8");
        okhttp3.RequestBody body = okhttp3.RequestBody.create(jsonBody, mediaType);

        Request.Builder builder = new Request.Builder()
                .url(BASE_URL + endpoint)
                .put(body);
        addAuth(builder);
        return execute(builder.build());
    }

    public String delete(String endpoint) throws IOException {
        Request.Builder builder = new Request.Builder()
                .url(BASE_URL + endpoint)
                .delete();
        addAuth(builder);
        return execute(builder.build());
    }

    /**
     * Runs the request and translates any failure into a user-friendly
     * IOException whose message can be shown verbatim in the UI.
     *
     * - Connection failures → "Cannot reach the server. Please make
     *   sure the auction server is running."
     * - Server-rendered errors (JSON {"error": "..."}) → just the inner
     *   error string, no "HTTP 500:" prefix.
     * - Anything else → a short generic message.
     */
    private String execute(Request request) throws IOException {
        Response response;
        try {
            response = httpClient.newCall(request).execute();
        } catch (ConnectException | UnknownHostException e) {
            throw new IOException("Cannot reach the server. Please make sure the auction server is running.");
        } catch (SocketTimeoutException e) {
            throw new IOException("The server took too long to respond. Please try again.");
        } catch (IOException e) {
            throw new IOException("Cannot reach the server. Please make sure the auction server is running.");
        }

        try (Response r = response) {
            String responseBody = r.body() != null ? r.body().string() : "";
            if (!r.isSuccessful()) {
                throw new IOException(extractErrorMessage(responseBody, r.code()));
            }
            return responseBody;
        }
    }

    private String extractErrorMessage(String body, int status) {
        if (body != null && !body.isBlank()) {
            try {
                JsonObject obj = JsonParser.parseString(body).getAsJsonObject();
                if (obj.has("error") && !obj.get("error").isJsonNull()) {
                    return obj.get("error").getAsString();
                }
            } catch (Exception ignored) {
                // body wasn't JSON — fall through
            }
        }
        return switch (status) {
            case 401 -> "You are not signed in. Please log in again.";
            case 403 -> "You don't have permission to perform this action.";
            case 404 -> "The requested resource was not found.";
            case 409 -> "This action conflicts with the current state.";
            case 500, 502, 503 -> "The server ran into a problem. Please try again later.";
            default -> "Request failed (status " + status + ").";
        };
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
