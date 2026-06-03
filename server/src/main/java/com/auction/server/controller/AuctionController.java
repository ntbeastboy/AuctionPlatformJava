package com.auction.server.controller;

import com.auction.exception.UserNotFoundException;
import com.auction.model.User;
import com.auction.repository.UserRepository;
import com.auction.server.events.ItemEventBroadcaster;
import com.auction.service.AuctionService;
import io.javalin.http.Context;

import java.util.Map;

public class AuctionController {

    private final UserRepository userRepo;
    private final AuctionService auctionService;
    private final ItemEventBroadcaster eventBroadcaster;

    public AuctionController(UserRepository userRepo, AuctionService auctionService) {
        this(userRepo, auctionService, null);
    }

    public AuctionController(UserRepository userRepo, AuctionService auctionService,
                             ItemEventBroadcaster eventBroadcaster) {
        this.userRepo = userRepo;
        this.auctionService = auctionService;
        this.eventBroadcaster = eventBroadcaster;
    }

    public void handleStartAuction(Context ctx) {
        String itemId = ctx.pathParam("id");
        User user = getAuthenticatedUser(ctx);
        auctionService.startAuction(itemId, user);
        broadcastItemUpdated(itemId);
        ctx.json(Map.of("message", "Auction started."));
    }

    public void handleEndAuction(Context ctx) {
        String itemId = ctx.pathParam("id");
        User user = getAuthenticatedUser(ctx);
        auctionService.endAuctionEarly(itemId, user);
        broadcastItemUpdated(itemId);
        ctx.json(Map.of("message", "Auction ended early."));
    }

    public void handleCancelAuction(Context ctx) {
        String itemId = ctx.pathParam("id");
        User user = getAuthenticatedUser(ctx);
        auctionService.cancelAuction(itemId, user);
        broadcastItemUpdated(itemId);
        ctx.json(Map.of("message", "Auction canceled."));
    }

    public void handlePaySeller(Context ctx) {
        String itemId = ctx.pathParam("id");
        User user = getAuthenticatedUser(ctx);
        auctionService.paySeller(itemId, user);
        broadcastItemUpdated(itemId);
        ctx.json(Map.of("message", "Seller paid."));
    }

    private User getAuthenticatedUser(Context ctx) {
        String userId = ctx.attribute("userId");
        return userRepo.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
    }

    private void broadcastItemUpdated(String itemId) {
        if (eventBroadcaster != null) eventBroadcaster.broadcastItemUpdated(itemId);
    }
}
