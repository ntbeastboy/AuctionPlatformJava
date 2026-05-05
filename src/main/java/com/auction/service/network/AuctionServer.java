package com.auction.service.network;

import com.auction.model.*;
import com.auction.repository.ItemRepository;
import com.auction.repository.SqliteBidRepository;
import com.auction.repository.UserRepository;
import com.auction.service.AuctionService;
import com.auction.service.BidService;
import com.auction.service.ItemService;
import com.auction.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AuctionServer {

    private final Javalin app;
    private final UserRepository userRepo;
    private final ItemRepository itemRepo;
    private final SqliteBidRepository bidRepo;
    private final UserService userService;
    private final ItemService itemService;
    private final BidService bidService;
    private final AuctionService auctionService;

    public AuctionServer(UserRepository userRepo, ItemRepository itemRepo,
                         SqliteBidRepository bidRepo, UserService userService,
                         ItemService itemService, BidService bidService,
                         AuctionService auctionService) {
        this.userRepo = userRepo;
        this.itemRepo = itemRepo;
        this.bidRepo = bidRepo;
        this.userService = userService;
        this.itemService = itemService;
        this.bidService = bidService;
        this.auctionService = auctionService;

        this.app = Javalin.create(config -> {
            config.jsonMapper(new io.javalin.json.JavalinJackson(createObjectMapper(), false));
            config.showJavalinBanner = false;
        });
        registerRoutes();
    }

    public void start(int port) {
        app.start(port);
        System.out.println("Auction API Server started on http://localhost:" + port + "/api");
    }

    public void stop() {
        app.stop();
    }

    private ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    private void registerRoutes() {
        // ── Users ──
        app.post("/api/users/login", this::handleLogin);
        app.post("/api/users/register", this::handleRegister);
        app.get("/api/users/{id}", this::handleGetUser);
        app.get("/api/users", this::handleGetAllUsers);

        // ── Items ──
        app.get("/api/items", this::handleGetAllItems);
        app.get("/api/items/{id}", this::handleGetItem);
        app.get("/api/items/available", this::handleGetAvailableItems);
        app.post("/api/items", this::handleCreateItem);
        app.put("/api/items/{id}/close", this::handleCloseItem);

        // ── Bids ──
        app.post("/api/bids", this::handlePlaceBid);
        app.get("/api/bids/item/{itemId}", this::handleGetBidsForItem);
        app.get("/api/bids/bidder/{bidderId}", this::handleGetBidsByBidder);

        // ── Auction Control ──
        app.post("/api/auction/{id}/start", this::handleStartAuction);
        app.post("/api/auction/{id}/end", this::handleEndAuction);
        app.post("/api/auction/{id}/cancel", this::handleCancelAuction);
        app.post("/api/auction/{id}/pay", this::handleMarkPaid);

        // Global error handler
        app.exception(Exception.class, (e, ctx) -> {
            ctx.status(400);
            ctx.json(Map.of("error", e.getMessage() != null ? e.getMessage() : "Unknown error"));
        });
    }

    // ── User Handlers ──

    private void handleLogin(Context ctx) {
        @SuppressWarnings("unchecked")
        Map<String, String> body = ctx.bodyAsClass(Map.class);
        User user = userService.login(body.get("username"), body.get("password"));
        ctx.json(userToMap(user));
    }

    private void handleRegister(Context ctx) {
        @SuppressWarnings("unchecked")
        Map<String, String> body = ctx.bodyAsClass(Map.class);
        String roleStr = body.getOrDefault("role", "BIDDER").toUpperCase();
        UserService.RegisterRole role = UserService.RegisterRole.valueOf(roleStr);
        User user = userService.register(body.get("username"), body.get("password"), role);
        ctx.status(201).json(userToMap(user));
    }

    private void handleGetUser(Context ctx) {
        String id = ctx.pathParam("id");
        User user = userRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));
        ctx.json(userToMap(user));
    }

    private void handleGetAllUsers(Context ctx) {
        ctx.json(userRepo.findAll().stream().map(this::userToMap).toList());
    }

    // ── Item Handlers ──

    private void handleGetAllItems(Context ctx) {
        ctx.json(itemRepo.findAll().stream().map(this::itemToMap).toList());
    }

    private void handleGetItem(Context ctx) {
        String id = ctx.pathParam("id");
        Item item = itemRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Item not found: " + id));
        ctx.json(itemToMap(item));
    }

    private void handleGetAvailableItems(Context ctx) {
        List<Item> running = itemRepo.findAll().stream()
                .filter(i -> i.getStatus() == AuctionStatus.RUNNING || i.getStatus() == AuctionStatus.OPEN)
                .toList();
        ctx.json(running.stream().map(this::itemToMap).toList());
    }

    private void handleCreateItem(Context ctx) {
        ctx.status(201).json(Map.of("message", "Use the desktop app to create items."));
    }

    private void handleCloseItem(Context ctx) {
        String id = ctx.pathParam("id");
        auctionService.closeAuction(id);
        ctx.json(Map.of("message", "Auction closed."));
    }

    // ── Bid Handlers ──

    private void handlePlaceBid(Context ctx) {
        @SuppressWarnings("unchecked")
        Map<String, Object> body = ctx.bodyAsClass(Map.class);
        String bidderId = ctx.queryParam("bidderId");
        String itemId = (String) body.get("itemId");
        double amount = ((Number) body.get("bidAmount")).doubleValue();

        User user = userRepo.findById(bidderId)
                .orElseThrow(() -> new RuntimeException("Bidder not found: " + bidderId));
        Bid bid = bidService.placeBid(user, itemId, amount);
        bidRepo.save(bid);

        // Persist updated item state
        itemRepo.findById(itemId).ifPresent(itemRepo::update);
        // Persist updated user balance
        userRepo.save(user);

        ctx.status(201).json(Map.of(
                "bidderId", bid.getBidderId(),
                "itemId", bid.getItemId(),
                "bidAmount", bid.getAmount(),
                "bidTime", bid.getTimestamp()
        ));
    }

    private void handleGetBidsForItem(Context ctx) {
        ctx.json(bidRepo.findByItemId(ctx.pathParam("itemId")).stream().map(b ->
                Map.of("bidderId", b.getBidderId(), "itemId", b.getItemId(),
                       "bidAmount", b.getAmount(), "timestamp", b.getTimestamp())
        ).toList());
    }

    private void handleGetBidsByBidder(Context ctx) {
        ctx.json(bidRepo.findByBidderId(ctx.pathParam("bidderId")).stream().map(b ->
                Map.of("bidderId", b.getBidderId(), "itemId", b.getItemId(),
                       "bidAmount", b.getAmount(), "timestamp", b.getTimestamp())
        ).toList());
    }

    // ── Auction Control Handlers ──

    private void handleStartAuction(Context ctx) {
        String id = ctx.pathParam("id");
        String userId = ctx.queryParam("userId");
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        auctionService.startAuction(id, user);
        itemRepo.findById(id).ifPresent(itemRepo::update);
        ctx.json(Map.of("message", "Auction started."));
    }

    private void handleEndAuction(Context ctx) {
        String id = ctx.pathParam("id");
        String userId = ctx.queryParam("userId");
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        auctionService.endAuctionEarly(id, user);
        itemRepo.findById(id).ifPresent(itemRepo::update);
        ctx.json(Map.of("message", "Auction ended early."));
    }

    private void handleCancelAuction(Context ctx) {
        String id = ctx.pathParam("id");
        String userId = ctx.queryParam("userId");
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        auctionService.cancelAuction(id, user);
        itemRepo.findById(id).ifPresent(itemRepo::update);
        ctx.json(Map.of("message", "Auction canceled."));
    }

    private void handleMarkPaid(Context ctx) {
        String id = ctx.pathParam("id");
        String userId = ctx.queryParam("userId");
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        auctionService.markPaid(id, user);
        itemRepo.findById(id).ifPresent(itemRepo::update);
        ctx.json(Map.of("message", "Marked as paid."));
    }

    // ── Mappers ──

    private Map<String, Object> userToMap(User user) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", user.getId());
        map.put("username", user.getUsername());
        map.put("role", user instanceof Admin ? "ADMIN" : user instanceof Seller ? "SELLER" : "BIDDER");
        if (user instanceof Bidder b) map.put("balance", b.getBalance());
        if (user instanceof Seller s) map.put("balance", s.getBalance());
        if (user instanceof BannableUser bu) map.put("banned", bu.isBanned());
        return map;
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

        String type = "Other";
        if (item instanceof Art) type = "Art";
        else if (item instanceof Electronics) type = "Electronics";
        else if (item instanceof Vehicle) type = "Vehicle";
        map.put("type", type);

        return map;
    }
}
