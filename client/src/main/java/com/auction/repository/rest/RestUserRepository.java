package com.auction.repository.rest;

import com.auction.model.User;
import com.auction.repository.UserRepository;
import com.auction.service.http.HttpClientService;
import com.auction.service.http.JsonMappers;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Read-only view of users backed by the REST API. Mutating operations
 * are intentionally unsupported here — the desktop client must mutate
 * users through {@code UserService}/{@code REST} endpoints, never via
 * direct repository writes.
 */
public class RestUserRepository implements UserRepository {

    private static final Type LIST_OF_MAP = new TypeToken<List<Map<String, Object>>>(){}.getType();
    private static final Type MAP = new TypeToken<Map<String, Object>>(){}.getType();

    private final HttpClientService http;

    public RestUserRepository(HttpClientService http) {
        this.http = http;
    }

    @Override
    public void save(User user) {
        throw new UnsupportedOperationException(
                "RestUserRepository is read-only. Use UserService for registration / balance changes.");
    }

    @Override
    public Optional<User> findByUsername(String username) {
        try {
            String body = http.get("/users/username/" + java.net.URLEncoder.encode(username, java.nio.charset.StandardCharsets.UTF_8));
            Map<String, Object> map = http.getGson().fromJson(body, MAP);
            return Optional.ofNullable(JsonMappers.toUser(map));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean existsByUsername(String username) {
        return findByUsername(username).isPresent();
    }

    @Override
    public Optional<User> findById(String id) {
        try {
            String body = http.get("/users/" + id);
            Map<String, Object> map = http.getGson().fromJson(body, MAP);
            return Optional.ofNullable(JsonMappers.toUser(map));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<User> findAll() {
        try {
            String body = http.get("/users");
            List<Map<String, Object>> list = http.getGson().fromJson(body, LIST_OF_MAP);
            return list.stream().map(JsonMappers::toUser).toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    @Override
    public void delete(String id) {
        throw new UnsupportedOperationException(
                "RestUserRepository is read-only. Use UserService for admin account changes.");
    }
}
