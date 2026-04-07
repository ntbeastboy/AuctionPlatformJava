package AuctionPlatformJava.src.main.java.com.auction.repository;

import AuctionPlatformJava.src.main.java.com.auction.model.Item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ItemRepository {
    private final Map<String, Item> store = new HashMap<>();

    public void save(Item item) {
        store.put(item.getId(), item);
    }

    public Optional<Item> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    public void delete(String id) {
        store.remove(id);
    }

    public List<Item> findAll() {
        return new ArrayList<>(store.values());
    }
}
