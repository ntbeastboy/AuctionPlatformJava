package com.auction.repository;

import com.auction.model.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryItemRepository implements ItemRepository {
    private final Map<String, Item> store = new ConcurrentHashMap<>();

    @Override
    public void save(Item item) {
        store.put(item.getId(), item);
    }

    @Override
    public Optional<Item> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public void delete(String id) {
        store.remove(id);
    }

    @Override
    public List<Item> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public void update(Item item) {
        store.put(item.getId(), item);
    }
}
