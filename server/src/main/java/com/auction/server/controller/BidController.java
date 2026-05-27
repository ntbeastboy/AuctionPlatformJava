package com.auction.server.controller;

import com.auction.exception.UserNotFoundException;
import com.auction.model.AutoBid;
import com.auction.model.Bid;
import com.auction.model.User;
import com.auction.repository.BidRepository;
import com.auction.repository.UserRepository;
import com.auction.server.events.ItemEventBroadcaster;
import com.auction.service.BidService;
import io.javalin.http.Context;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

public class BidController {

    private final BidRepository bidRepo;
    private final UserRepository userRepo;
    private final BidService bidService;
    private final ItemEventBroadcaster eventBroadcaster;

    public BidController(BidRepository bidRepo, UserRepository userRepo, BidService bidService) {
        this(bidRepo, userRepo, bidService, null);
    }

    public BidController(BidRepository bidRepo, UserRepository userRepo,
                         BidService bidService, ItemEventBroadcaster eventBroadcaster) {
        this.bidRepo = bidRepo;
        this.userRepo = userRepo;
        this.bidService = bidService;
        this.eventBroadcaster = eventBroadcaster;
    }

    @SuppressWarnings("unchecked")
    public void handlePlaceBid(Context ctx) {
        String bidderId = ctx.attribute("userId");
        Map<String, Object> body = ctx.bodyAsClass(Map.class);
        String itemId = (String) body.get("itemId");
        double amount = ((Number) body.get("bidAmount")).doubleValue();

        User user = userRepo.findById(bidderId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + bidderId));
        Bid bid = bidService.placeBid(user, itemId, amount);
        broadcastItemUpdated(itemId);

        ctx.status(201).json(bidToMap(bid));
    }

    public void handleGetBidById(Context ctx) {
        int id = Integer.parseInt(ctx.pathParam("id"));
        Bid bid = bidRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Bid not found: " + id));
        ctx.json(bidToMap(bid));
    }

    public void handleGetBidsForItem(Context ctx) {
        String itemId = ctx.pathParam("itemId");
        ctx.json(bidRepo.findByItemId(itemId).stream().map(this::bidToMap).toList());
    }

    public void handleGetHighestBidForItem(Context ctx) {
        String itemId = ctx.pathParam("itemId");
        var bids = bidRepo.findByItemId(itemId);
        if (bids.isEmpty()) {
            ctx.status(404).json(Map.of("error", "No bids for item: " + itemId));
            return;
        }
        Bid highest = bids.stream()
                .max(Comparator.comparingDouble(Bid::getAmount))
                .orElseThrow();
        ctx.json(bidToMap(highest));
    }

    public void handleGetBidsByBidder(Context ctx) {
        String bidderId = ctx.pathParam("bidderId");
        ctx.json(bidRepo.findByBidderId(bidderId).stream().map(this::bidToMap).toList());
    }

    @SuppressWarnings("unchecked")
    public void handleSetAutoBid(Context ctx) {
        String userId = ctx.attribute("userId");
        Map<String, Object> body = ctx.bodyAsClass(Map.class);
        String itemId = (String) body.get("itemId");
        double maxBid = ((Number) body.get("maxBid")).doubleValue();
        double increment = ((Number) body.get("increment")).doubleValue();

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        AutoBid autoBid = bidService.setAutoBid(user, itemId, maxBid, increment);
        broadcastItemUpdated(itemId);
        ctx.status(201).json(autoBidToMap(autoBid));
    }

    public void handleGetMyAutoBid(Context ctx) {
        String userId = ctx.attribute("userId");
        String itemId = ctx.pathParam("itemId");
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        bidService.getAutoBid(user, itemId)
                .ifPresentOrElse(
                        autoBid -> ctx.json(autoBidToMap(autoBid)),
                        () -> ctx.status(404).json(Map.of("error", "No auto-bid for item: " + itemId))
                );
    }

    public void handleCancelAutoBid(Context ctx) {
        String userId = ctx.attribute("userId");
        String itemId = ctx.pathParam("itemId");
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        bidService.cancelAutoBid(user, itemId);
        broadcastItemUpdated(itemId);
        ctx.json(Map.of("message", "Auto-bid canceled."));
    }

    private Map<String, Object> bidToMap(Bid bid) {
        Map<String, Object> map = new HashMap<>();
        map.put("bidderId", bid.getBidderId());
        map.put("itemId", bid.getItemId());
        map.put("bidAmount", bid.getAmount());
        map.put("timestamp", bid.getTimestamp());
        // Resolve bidder name
        userRepo.findById(bid.getBidderId()).ifPresent(u -> map.put("bidderName", u.getUsername()));
        return map;
    }

    private Map<String, Object> autoBidToMap(AutoBid autoBid) {
        Map<String, Object> map = new HashMap<>();
        map.put("userId", autoBid.getUserId());
        map.put("itemId", autoBid.getItemId());
        map.put("maxBid", autoBid.getMaxBid());
        map.put("increment", autoBid.getIncrement());
        map.put("createdAt", autoBid.getCreatedAt());
        map.put("lastBidAt", autoBid.getLastBidAt());
        map.put("nextCheckAt", autoBid.getNextCheckAt());
        return map;
    }

    private void broadcastItemUpdated(String itemId) {
        if (eventBroadcaster != null) eventBroadcaster.broadcastItemUpdated(itemId);
    }
}
