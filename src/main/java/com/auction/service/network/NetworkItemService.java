package com.auction.service.network;

import com.auction.dto.ItemRemoteDto;
import com.auction.service.http.HttpClientService;
import com.google.gson.reflect.TypeToken;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class NetworkItemService {

    private final HttpClientService httpClient;

    public ItemRemoteDto createItem(String sellerId, String name, String description, String category, Double startPrice) throws IOException {
        Map<String, Object> itemData = new HashMap<>();
        itemData.put("name", name);
        itemData.put("description", description);
        itemData.put("category", category);
        itemData.put("startPrice", startPrice);

        String response = httpClient.post("/items?sellerId=" + sellerId, httpClient.getGson().toJson(itemData));
        return httpClient.getGson().fromJson(response, ItemRemoteDto.class);
    }

    public ItemRemoteDto getItemById(String id) throws IOException {
        String response = httpClient.get("/items/" + id);
        return httpClient.getGson().fromJson(response, ItemRemoteDto.class);
    }

    public List<ItemRemoteDto> getAllItems() throws IOException {
        String response = httpClient.get("/items");
        return httpClient.getGson().fromJson(response, new TypeToken<List<ItemRemoteDto>>(){}.getType());
    }

    public List<ItemRemoteDto> getAvailableItems() throws IOException {
        String response = httpClient.get("/items/available");
        return httpClient.getGson().fromJson(response, new TypeToken<List<ItemRemoteDto>>(){}.getType());
    }

    public List<ItemRemoteDto> getItemsByCategory(String category) throws IOException {
        String response = httpClient.get("/items/category/" + category);
        return httpClient.getGson().fromJson(response, new TypeToken<List<ItemRemoteDto>>(){}.getType());
    }

    public List<ItemRemoteDto> getSellerItems(String sellerId) throws IOException {
        String response = httpClient.get("/items/seller/" + sellerId);
        return httpClient.getGson().fromJson(response, new TypeToken<List<ItemRemoteDto>>(){}.getType());
    }

    public void updateItemPrice(String itemId, Double newPrice) throws IOException {
        httpClient.put("/items/" + itemId + "/price?newPrice=" + newPrice, "");
    }

    public void closeItem(String itemId) throws IOException {
        httpClient.put("/items/" + itemId + "/close", "");
    }

    public void markItemAsSold(String itemId) throws IOException {
        httpClient.put("/items/" + itemId + "/sold", "");
    }
}
