package com.auction.repository;

import com.auction.model.User;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class UserRepository {
    private final Map<String, User> store = new HashMap<>();

    public void save(User user) {
        store.put(user.getUsername(), user);
    }

    public Optional<User> findByUsername(String username) {
        return Optional.ofNullable(store.get(username));
    }

    public boolean existsByUsername(String username) {
        return store.containsKey(username);
    }
}
