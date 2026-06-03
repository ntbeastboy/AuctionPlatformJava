package com.auction.service.rest;

import com.auction.exception.UnauthorizedActionException;
import com.auction.model.*;
import com.auction.repository.ItemRepository;
import com.auction.service.ItemService;
import com.auction.service.http.HttpClientService;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Network-backed ItemService that proxies create/delete to the REST API.
 *
 * Only the two methods that mutate state are overridden; everything else
 * stays inherited (there is nothing else on the parent today).
 */
public class RestItemService extends ItemService {

    private final HttpClientService http;

    public RestItemService(ItemRepository itemRepository, HttpClientService http) {
        super(itemRepository);
        this.http = http;
    }

    @Override
    public void createItem(User user, Item item) {
        if (!(user instanceof Seller))
            throw new UnauthorizedActionException("Only sellers can create items.");

        try {
            http.post("/items", http.getGson().toJson(itemToBody(item)));
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    @Override
    public void updateItem(User user, Item item) {
        if (!(user instanceof Seller) && !(user instanceof Admin))
            throw new UnauthorizedActionException("Only sellers or admins can update items.");
        try {
            http.put("/items/" + encode(item.getId()), http.getGson().toJson(itemToBody(item)));
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    @Override
    public void deleteItem(User user, String itemId) {
        if (!(user instanceof Seller) && !(user instanceof Admin))
            throw new UnauthorizedActionException("Only sellers or admins can delete items.");
        try {
            http.delete("/items/" + encode(itemId));
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    private Map<String, Object> itemToBody(Item item) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", item.getName());
        body.put("description", item.getDescription());
        body.put("startPrice", item.getStartingPrice());
        body.put("priceStep", item.getPriceStep());
        body.put("imageData", item.getImageData());
        body.put("imageDataList", item.getImageDataList());

        // Server expects durationMinutes — convert from the start/end window
        // the local controller already computed.
        long mins = 60;
        if (item.getBidStartTime() != null && item.getBidEndTime() != null) {
            mins = Math.max(1, Duration.between(item.getBidStartTime(), item.getBidEndTime()).toMinutes());
        } else if (item.getBidEndTime() != null) {
            mins = Math.max(1, Duration.between(LocalDateTime.now(), item.getBidEndTime()).toMinutes());
        }
        body.put("durationMinutes", mins);

        if (item instanceof Art a) {
            body.put("type", "Art");
            body.put("artist", a.getArtist());
            body.put("paintingStyle", a.getPaintingStyle());
            body.put("origin", a.getOrigin());
        } else if (item instanceof Electronics e) {
            body.put("type", "Electronics");
            body.put("wattage", e.getWattage());
            body.put("origin", e.getOrigin());
            body.put("warrantyMonths", e.getWarrantyMonths());
            body.put("serialNumber", e.getSerialNumber());
        } else if (item instanceof Vehicle v) {
            body.put("type", "Vehicle");
            body.put("miles", v.getMiles());
            if (v.getManufacturingDate() != null)
                body.put("manufacturingDate", v.getManufacturingDate().toString());
            body.put("brand", v.getBrand());
            body.put("vin", v.getVin());
            body.put("accidentHistory", v.hasAccidentHistory());
        } else {
            body.put("type", "Other");
        }

        return body;
    }

    private String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
