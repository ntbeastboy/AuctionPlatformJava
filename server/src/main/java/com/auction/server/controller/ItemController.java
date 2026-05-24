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

        Item item = switch (type) {
            case "Art" -> new Art(id, name, description, startPrice, priceStep, start, end, sellerId,
                    (String) body.get("artist"), (String) body.get("paintingStyle"), (String) body.get("origin"));
            case "Electronics" -> new Electronics(id, name, description, startPrice, priceStep, start, end, sellerId,
                    ((Number) body.getOrDefault("wattage", 0)).intValue(),
                    (String) body.get("origin"),
                    ((Number) body.getOrDefault("warrantyMonths", 0)).intValue(),
                    (String) body.get("serialNumber"));
            case "Vehicle" -> {
                String mfgStr = (String) body.get("manufacturingDate");
                LocalDate mfgDate = mfgStr != null ? LocalDate.parse(mfgStr) : null;
                yield new Vehicle(id, name, description, startPrice, priceStep, start, end, sellerId,
                        ((Number) body.getOrDefault("miles", 0)).intValue(),
                        mfgDate,
                        (String) body.get("brand"),
                        (String) body.get("vin"),
                        Boolean.TRUE.equals(body.get("accidentHistory")));
            }
            default -> new Other(id, name, description, startPrice, priceStep, start, end, sellerId);
        };

        itemService.createItem(seller, item);
        broadcastItemsChanged();
        ctx.status(201).json(itemToMap(item));
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
        String role = ctx.attribute("role");
        if (!"ADMIN".equals(role))
            throw new UnauthorizedActionException("Only admins can delete items.");

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
        if (item instanceof Art) return "Art";
        if (item instanceof Electronics) return "Electronics";
        if (item instanceof Vehicle) return "Vehicle";
        return "Other";
    }

    private void broadcastItemUpdated(String itemId) {
        if (eventBroadcaster != null) eventBroadcaster.broadcastItemUpdated(itemId);
    }

    private void broadcastItemsChanged() {
        if (eventBroadcaster != null) eventBroadcaster.broadcastItemsChanged();
    }
}
