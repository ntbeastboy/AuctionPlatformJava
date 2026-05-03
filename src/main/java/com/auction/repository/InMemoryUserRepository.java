package com.auction.repository;

import com.auction.model.User;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryUserRepository implements UserRepository {
    private final Map<String, User> store = new ConcurrentHashMap<>();

    @Override
    public void save(User user) {
        store.put(user.getUsername(), user);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return Optional.ofNullable(store.get(username));
    }

    @Override
    public boolean existsByUsername(String username) {
        return store.containsKey(username);
    }

    @Override
    public Optional<User> findById(String id) {
        return store.values().stream().filter(u -> u.getId().equals(id)).findFirst();
    }

    @Override
    public List<User> findAll() {
        return new ArrayList<>(store.values());
    }
}
