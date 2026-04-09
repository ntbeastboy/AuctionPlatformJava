package com.auction.service.network;

import com.auction.dto.UserRemoteDto;
import com.auction.service.http.HttpClientService;
import com.google.gson.reflect.TypeToken;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
public class NetworkUserService {

    private final HttpClientService httpClient;

    public UserRemoteDto login(String username, String password) throws IOException {
        Map<String, String> loginData = new HashMap<>();
        loginData.put("username", username);
        loginData.put("password", password);

        String response = httpClient.post("/users/login", httpClient.getGson().toJson(loginData));
        return httpClient.getGson().fromJson(response, UserRemoteDto.class);
    }

    public UserRemoteDto register(String username, String password, String email, String fullName, String role) throws IOException {
        Map<String, String> registerData = new HashMap<>();
        registerData.put("username", username);
        registerData.put("password", password);
        registerData.put("email", email);
        registerData.put("fullName", fullName);
        registerData.put("role", role);

        String response = httpClient.post("/users/register", httpClient.getGson().toJson(registerData));
        return httpClient.getGson().fromJson(response, UserRemoteDto.class);
    }

    public UserRemoteDto getUserById(String id) throws IOException {
        String response = httpClient.get("/users/" + id);
        return httpClient.getGson().fromJson(response, UserRemoteDto.class);
    }

    public UserRemoteDto getUserByUsername(String username) throws IOException {
        String response = httpClient.get("/users/username/" + username);
        return httpClient.getGson().fromJson(response, UserRemoteDto.class);
    }

    public void addBalance(String userId, Double amount) throws IOException {
        httpClient.post("/users/" + userId + "/balance/add?amount=" + amount, "");
    }

    public void deductBalance(String userId, Double amount) throws IOException {
        httpClient.post("/users/" + userId + "/balance/deduct?amount=" + amount, "");
    }

    public void banUser(String userId) throws IOException {
        httpClient.post("/users/" + userId + "/ban", "");
    }
}
