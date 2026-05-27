package com.auction.server.controller;

import com.auction.exception.ProductNotFoundException;
import com.auction.exception.UnauthorizedActionException;
import com.auction.exception.UserNotFoundException;
import com.auction.model.*;
import com.auction.repository.ItemRepository;
import com.auction.repository.UserRepository;
import com.auction.server.events.ItemEventBroadcaster;
import com.auction.service.AuctionService;
import com.auction.service.ItemService;
import io.javalin.http.Context;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

public class ItemController {

    private final ItemRepository itemRepo;
    private final UserRepository userRepo;
    private final ItemService itemService;
    private final AuctionService auctionService;
    private final ItemEventBroadcaster eventBroadcaster;
    private final ItemFactory itemFactory = ItemFactory.defaultFactory();

    public ItemController(ItemRepository itemRepo, UserRepository userRepo,
                          ItemService itemService, AuctionService auctionService) {
        this(itemRepo, userRepo, itemService, auctionService, null);
    }

    public ItemController(ItemRepository itemRepo, UserRepository userRepo,
                          ItemService itemService, AuctionService auctionService,
                          ItemEventBroadcaster eventBroadcaster) {
        this.itemRepo = itemRepo;
        this.userRepo = userRepo;
        this.itemService = itemService;
        this.auctionService = auctionService;
        this.eventBroadcaster = eventBroadcaster;
    }

    public void handleGetAllItems(Context ctx) {
        ctx.json(itemRepo.findAll().stream().map(this::itemToMap).toList());
    }

    public void handleGetItem(Context ctx) {
        String id = ctx.pathParam("id");
        Item item = itemRepo.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Item not found: " + id));
        ctx.json(itemToMap(item));
    }

    public void handleGetAvailableItems(Context ctx) {
        List<Item> available = itemRepo.findAll().stream()
                .filter(i -> i.getStatus() == AuctionStatus.RUNNING || i.getStatus() == AuctionStatus.OPEN)
                .toList();
        ctx.json(available.stream().map(this::itemToMap).toList());
    }

    public void handleGetItemsByCategory(Context ctx) {
        String category = ctx.pathParam("category");
        List<Item> filtered = itemRepo.findAll().stream()
                .filter(i -> {
                    String type = itemType(i);
                    return type.equalsIgnoreCase(category);
                })
                .toList();
        ctx.json(filtered.stream().map(this::itemToMap).toList());
    }

    public void handleGetSellerItems(Context ctx) {
        String sellerId = ctx.pathParam("sellerId");
        List<Item> items = itemRepo.findAll().stream()
                .filter(i -> i.getSellerId().equals(sellerId))
                .toList();
        ctx.json(items.stream().map(this::itemToMap).toList());
    }

    @SuppressWarnings("unchecked")
    public void handleCreateItem(Context ctx) {
        String sellerId = ctx.attribute("userId");
        User seller = userRepo.findById(sellerId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + sellerId));

        Map<String, Object> body = ctx.bodyAsClass(Map.class);
        String id = UUID.randomUUID().toString();
        String name = (String) body.get("name");
        String description = (String) body.get("description");
        double startPrice = ((Number) body.get("startPrice")).doubleValue();
        double priceStep = ((Number) body.getOrDefault("priceStep", 1.0)).doubleValue();
        int durationMinutes = ((Number) body.getOrDefault("durationMinutes", 60)).intValue();
        String type = (String) body.getOrDefault("type", "Other");

        LocalDateTime start = LocalDateTime.now();
        LocalDateTime end = start.plusMinutes(durationMinutes);

        Item item = itemFactory.create(type, id, name, description, startPrice, priceStep, start, end, sellerId, body);

        itemService.createItem(seller, item);
        broadcastItemsChanged();
        ctx.status(201).json(itemToMap(item));
    }

    @SuppressWarnings("unchecked")
    public void handleUpdateItem(Context ctx) {
        String id = ctx.pathParam("id");
        Item existing = itemRepo.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Item not found: " + id));
        String userId = ctx.attribute("userId");
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        Map<String, Object> body = ctx.bodyAsClass(Map.class);
        String name = (String) body.getOrDefault("name", existing.getName());
        String description = (String) body.getOrDefault("description", existing.getDescription());
        double startPrice = number(body, "startPrice", existing.getStartingPrice()).doubleValue();
        double priceStep = number(body, "priceStep", existing.getPriceStep()).doubleValue();
        int durationMinutes = number(body, "durationMinutes", durationMinutes(existing)).intValue();
        String type = (String) body.getOrDefault("type", itemType(existing));
        LocalDateTime start = existing.getBidStartTime() != null ? existing.getBidStartTime() : LocalDateTime.now();
        LocalDateTime end = start.plusMinutes(durationMinutes);

        Item updated = itemFactory.create(type, id, name, description, startPrice, priceStep,
                start, end, existing.getSellerId(), body);
        itemService.updateItem(user, updated);
        broadcastItemUpdated(id);
        ctx.json(itemToMap(updated));
    }

    public void handleUpdateItemPrice(Context ctx) {
        String id = ctx.pathParam("id");
        double newPrice = Double.parseDouble(ctx.queryParam("newPrice"));
        Item item = itemRepo.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Item not found: " + id));

        String userId = ctx.attribute("userId");
        String role = ctx.attribute("role");
        if (!"ADMIN".equals(role) && !item.getSellerId().equals(userId))
            throw new UnauthorizedActionException("Only the seller or an admin can update the price.");

        item.setCurrentPrice(newPrice);
        itemRepo.update(item);
        broadcastItemUpdated(id);
        ctx.json(itemToMap(item));
    }

    public void handleCloseItem(Context ctx) {
        String id = ctx.pathParam("id");
        auctionService.closeAuction(id);
        broadcastItemUpdated(id);
        ctx.json(Map.of("message", "Auction closed."));
    }

    public void handleDeleteItem(Context ctx) {
        String id = ctx.pathParam("id");
        String userId = ctx.attribute("userId");
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        itemService.deleteItem(user, id);
        broadcastItemsChanged();
        ctx.json(Map.of("message", "Item deleted."));
    }

    private Map<String, Object> itemToMap(Item item) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", item.getId());
        map.put("name", item.getName());
        map.put("description", item.getDescription());
        map.put("startPrice", item.getStartingPrice());
        map.put("currentPrice", item.getCurrentPrice());
        map.put("priceStep", item.getPriceStep());
        map.put("sellerId", item.getSellerId());
        map.put("status", item.getStatus().name());
        map.put("currentWinnerId", item.getCurrentWinnerId());
        if (item.getBidStartTime() != null) map.put("bidStartTime", item.getBidStartTime().toString());
        if (item.getBidEndTime() != null) map.put("bidEndTime", item.getBidEndTime().toString());

        String type = itemType(item);
        map.put("type", type);

        // Subtype-specific fields so the desktop client can render the
        // detail dialog without an extra round trip.
        if (item instanceof Art a) {
            map.put("artist", a.getArtist());
            map.put("paintingStyle", a.getPaintingStyle());
            map.put("origin", a.getOrigin());
        } else if (item instanceof Electronics e) {
            map.put("wattage", e.getWattage());
            map.put("origin", e.getOrigin());
            map.put("warrantyMonths", e.getWarrantyMonths());
            map.put("serialNumber", e.getSerialNumber());
        } else if (item instanceof Vehicle v) {
            map.put("miles", v.getMiles());
            if (v.getManufacturingDate() != null)
                map.put("manufacturingDate", v.getManufacturingDate().toString());
            map.put("brand", v.getBrand());
            map.put("vin", v.getVin());
            map.put("accidentHistory", v.hasAccidentHistory());
        }

        // Resolve seller name
        userRepo.findById(item.getSellerId()).ifPresent(u -> map.put("sellerName", u.getUsername()));

        return map;
    }

    private String itemType(Item item) {
        return item.getTypeName();
    }

    private Number number(Map<String, Object> body, String key, Number fallback) {
        Object value = body.get(key);
        return value instanceof Number n ? n : fallback;
    }

    private int durationMinutes(Item item) {
        if (item.getBidStartTime() == null || item.getBidEndTime() == null) return 60;
        return Math.max(1, (int) java.time.Duration.between(item.getBidStartTime(), item.getBidEndTime()).toMinutes());
    }

    private void broadcastItemUpdated(String itemId) {
        if (eventBroadcaster != null) eventBroadcaster.broadcastItemUpdated(itemId);
    }

    private void broadcastItemsChanged() {
        if (eventBroadcaster != null) eventBroadcaster.broadcastItemsChanged();
    }
}
