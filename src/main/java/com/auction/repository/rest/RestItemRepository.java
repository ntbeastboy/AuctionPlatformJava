package com.auction.repository.rest;

import com.auction.model.Item;
import com.auction.repository.ItemRepository;
import com.auction.service.http.HttpClientService;
import com.auction.service.http.JsonMappers;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Read-only view of items backed by the REST API. Use {@code ItemService}
 * for create/delete and {@code BidService}/{@code AuctionService} for any
 * mutating actions on auctions.
 */
public class RestItemRepository implements ItemRepository {

    private static final Type LIST_OF_MAP = new TypeToken<List<Map<String, Object>>>(){}.getType();
    private static final Type MAP = new TypeToken<Map<String, Object>>(){}.getType();

    private final HttpClientService http;

    public RestItemRepository(HttpClientService http) {
        this.http = http;
    }

    @Override
    public void save(Item item) {
        throw new UnsupportedOperationException(
                "RestItemRepository is read-only. Use ItemService.createItem to create items.");
    }

    @Override
    public Optional<Item> findById(String id) {
        try {
            String body = http.get("/items/" + id);
            Map<String, Object> map = http.getGson().fromJson(body, MAP);
            return Optional.ofNullable(JsonMappers.toItem(map));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    @Override
    public void delete(String id) {
        throw new UnsupportedOperationException(
                "RestItemRepository is read-only. Use ItemService.deleteItem to delete items.");
    }

    @Override
    public List<Item> findAll() {
        try {
            String body = http.get("/items");
            List<Map<String, Object>> list = http.getGson().fromJson(body, LIST_OF_MAP);
            return list.stream().map(JsonMappers::toItem).toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    @Override
    public void update(Item item) {
        throw new UnsupportedOperationException(
                "RestItemRepository is read-only. Mutations happen on the server through service endpoints.");
    }
}
