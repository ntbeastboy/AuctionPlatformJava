package com.auction.server.network;

import com.auction.server.AuthMiddleware;
import com.auction.server.ExceptionMapper;
import com.auction.server.controller.AuctionController;
import com.auction.server.controller.BidController;
import com.auction.server.controller.ItemController;
import com.auction.server.controller.UserController;
import com.auction.server.events.ItemEventBroadcaster;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.javalin.Javalin;

public class AuctionServer {

    private final Javalin app;

    public AuctionServer(UserController userController, ItemController itemController,
                         BidController bidController, AuctionController auctionController,
                         ItemEventBroadcaster eventBroadcaster) {
        this.app = Javalin.create(config -> {
            config.jsonMapper(new io.javalin.json.JavalinJackson(createObjectMapper(), false));
            config.showJavalinBanner = false;
        });

        app.before(new AuthMiddleware());
        ExceptionMapper.register(app);
        eventBroadcaster.register(app);
        registerRoutes(userController, itemController, bidController, auctionController);
    }

    public void start(int port) {
        app.start(port);
        System.out.println("Auction API Server started on http://localhost:" + port + "/api");
    }

    public void stop() {
        app.stop();
    }

    private void registerRoutes(UserController user, ItemController item,
                                BidController bid, AuctionController auction) {
        // ── Users ──
        app.post("/api/users/login", user::handleLogin);
        app.post("/api/users/register", user::handleRegister);
        app.get("/api/users", user::handleGetAllUsers);
        app.get("/api/users/username/{username}", user::handleGetUserByUsername);
        app.get("/api/users/{id}", user::handleGetUser);
        app.post("/api/users/{id}/balance/add", user::handleAddBalance);
        app.post("/api/users/{id}/balance/deduct", user::handleDeductBalance);
        app.post("/api/users/{id}/ban", user::handleBanUser);

        // ── Items (static paths before {id}) ──
        app.get("/api/items", item::handleGetAllItems);
        app.get("/api/items/available", item::handleGetAvailableItems);
        app.get("/api/items/category/{category}", item::handleGetItemsByCategory);
        app.get("/api/items/seller/{sellerId}", item::handleGetSellerItems);
        app.post("/api/items", item::handleCreateItem);
        app.get("/api/items/{id}", item::handleGetItem);
        app.put("/api/items/{id}/price", item::handleUpdateItemPrice);
        app.put("/api/items/{id}/close", item::handleCloseItem);
        app.put("/api/items/{id}", item::handleUpdateItem);
        app.delete("/api/items/{id}", item::handleDeleteItem);

        // ── Bids (static paths before {id}) ──
        app.post("/api/bids", bid::handlePlaceBid);
        app.get("/api/bids/item/{itemId}/highest", bid::handleGetHighestBidForItem);
        app.get("/api/bids/item/{itemId}", bid::handleGetBidsForItem);
        app.get("/api/bids/bidder/{bidderId}", bid::handleGetBidsByBidder);
        app.get("/api/bids/{id}", bid::handleGetBidById);

        app.post("/api/auto-bids", bid::handleSetAutoBid);
        app.get("/api/auto-bids/item/{itemId}/me", bid::handleGetMyAutoBid);
        app.delete("/api/auto-bids/item/{itemId}/me", bid::handleCancelAutoBid);

        // ── Auction Control ──
        app.post("/api/auction/{id}/start", auction::handleStartAuction);
        app.post("/api/auction/{id}/end", auction::handleEndAuction);
        app.post("/api/auction/{id}/cancel", auction::handleCancelAuction);
    }

    private ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
