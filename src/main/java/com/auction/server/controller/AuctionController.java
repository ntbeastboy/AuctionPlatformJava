package com.auction.server.controller;

import com.auction.exception.UserNotFoundException;
import com.auction.model.User;
import com.auction.repository.UserRepository;
import com.auction.service.AuctionService;
import io.javalin.http.Context;

import java.util.Map;

public class AuctionController {

    private final UserRepository userRepo;
    private final AuctionService auctionService;

    public AuctionController(UserRepository userRepo, AuctionService auctionService) {
        this.userRepo = userRepo;
        this.auctionService = auctionService;
    }

    public void handleStartAuction(Context ctx) {
        String itemId = ctx.pathParam("id");
        User user = getAuthenticatedUser(ctx);
        auctionService.startAuction(itemId, user);
        ctx.json(Map.of("message", "Auction started."));
    }

    public void handleEndAuction(Context ctx) {
        String itemId = ctx.pathParam("id");
        User user = getAuthenticatedUser(ctx);
        auctionService.endAuctionEarly(itemId, user);
        ctx.json(Map.of("message", "Auction ended early."));
    }

    public void handleCancelAuction(Context ctx) {
        String itemId = ctx.pathParam("id");
        User user = getAuthenticatedUser(ctx);
        auctionService.cancelAuction(itemId, user);
        ctx.json(Map.of("message", "Auction canceled."));
    }

    public void handleMarkPaid(Context ctx) {
        String itemId = ctx.pathParam("id");
        User user = getAuthenticatedUser(ctx);
        auctionService.markPaid(itemId, user);
        ctx.json(Map.of("message", "Marked as paid."));
    }

    private User getAuthenticatedUser(Context ctx) {
        String userId = ctx.attribute("userId");
        return userRepo.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
    }
}
