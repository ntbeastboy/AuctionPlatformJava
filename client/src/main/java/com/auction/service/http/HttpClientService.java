package com.auction.service.http;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class HttpClientService {

    public static final String DEFAULT_BASE_URL = "http://localhost:8080/api";
    private final OkHttpClient httpClient;
    private final Gson gson;
    private final Set<Consumer<String>> updateListeners = new CopyOnWriteArraySet<>();
    private String baseUrl = DEFAULT_BASE_URL;
    private String authToken;
    private WebSocket eventSocket;
    private boolean eventSocketConnecting;

    public HttpClientService() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .pingInterval(30, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
    }

    public void setAuthToken(String token) {
        this.authToken = token;
        if (token == null || token.isBlank()) {
            disconnectEvents();
        } else if (!updateListeners.isEmpty()) {
            connectEvents();
        }
    }

    public String getAuthToken() {
        return authToken;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        String normalized = normalizeBaseUrl(baseUrl);
        if (normalized.equals(this.baseUrl)) return;
        this.baseUrl = normalized;
        setAuthToken(null);
    }

    public String get(String endpoint) throws IOException {
        Request.Builder builder = new Request.Builder()
                .url(url(endpoint));
        addAuth(builder);
        return execute(builder.build());
    }

    public String post(String endpoint, String jsonBody) throws IOException {
        okhttp3.MediaType mediaType = okhttp3.MediaType.parse("application/json; charset=utf-8");
        okhttp3.RequestBody body = okhttp3.RequestBody.create(jsonBody, mediaType);

        Request.Builder builder = new Request.Builder()
                .url(url(endpoint))
                .post(body);
        addAuth(builder);
        return execute(builder.build());
    }

    public String put(String endpoint, String jsonBody) throws IOException {
        okhttp3.MediaType mediaType = okhttp3.MediaType.parse("application/json; charset=utf-8");
        okhttp3.RequestBody body = okhttp3.RequestBody.create(jsonBody, mediaType);

        Request.Builder builder = new Request.Builder()
                .url(url(endpoint))
                .put(body);
        addAuth(builder);
        return execute(builder.build());
    }

    public String delete(String endpoint) throws IOException {
        Request.Builder builder = new Request.Builder()
                .url(url(endpoint))
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

    public void addUpdateListener(Consumer<String> listener) {
        updateListeners.add(listener);
        connectEvents();
    }

    public void removeUpdateListener(Consumer<String> listener) {
        updateListeners.remove(listener);
        if (updateListeners.isEmpty()) disconnectEvents();
    }

    public synchronized void connectEvents() {
        if (authToken == null || authToken.isBlank()) return;
        if (eventSocket != null || eventSocketConnecting) return;

        Request.Builder builder = new Request.Builder().url(eventsUrl());
        addAuth(builder);
        eventSocketConnecting = true;
        eventSocket = httpClient.newWebSocket(builder.build(), new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                eventSocketConnecting = false;
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                for (Consumer<String> listener : updateListeners) {
                    listener.accept(text);
                }
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                clearEventSocket(webSocket);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                clearEventSocket(webSocket);
            }
        });
    }

    public synchronized void disconnectEvents() {
        if (eventSocket != null) {
            eventSocket.close(1000, "client disconnect");
            eventSocket = null;
        }
        eventSocketConnecting = false;
    }

    private void addAuth(Request.Builder builder) {
        if (authToken != null && !authToken.isBlank()) {
            builder.addHeader("Authorization", "Bearer " + authToken);
        }
    }

    private String url(String endpoint) {
        return baseUrl + endpoint;
    }

    private String eventsUrl() {
        String wsBase = baseUrl.startsWith("https://")
                ? "wss://" + baseUrl.substring("https://".length())
                : "ws://" + baseUrl.substring("http://".length());
        return wsBase + "/events";
    }

    private String normalizeBaseUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Server URL cannot be empty.");
        }
        String normalized = raw.trim();
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "http://" + normalized;
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        URI uri;
        try {
            uri = URI.create(normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Server URL is not valid.");
        }
        if (uri.getHost() == null || uri.getScheme() == null)
            throw new IllegalArgumentException("Server URL must include a host.");
        if (!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme()))
            throw new IllegalArgumentException("Server URL must start with http:// or https://.");

        return normalized;
    }

    private synchronized void clearEventSocket(WebSocket webSocket) {
        if (eventSocket == webSocket) {
            eventSocket = null;
            eventSocketConnecting = false;
        }
    }
}
