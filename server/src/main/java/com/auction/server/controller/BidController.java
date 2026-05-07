package com.auction.server.controller;

import com.auction.exception.UserNotFoundException;
import com.auction.model.Bid;
import com.auction.model.User;
import com.auction.repository.BidRepository;
import com.auction.repository.UserRepository;
import com.auction.service.BidService;
import io.javalin.http.Context;

import java.util.HashMap;
import java.util.Map;

public class BidController {

    private final BidRepository bidRepo;
    private final UserRepository userRepo;
    private final BidService bidService;

    public BidController(BidRepository bidRepo, UserRepository userRepo, BidService bidService) {
        this.bidRepo = bidRepo;
        this.userRepo = userRepo;
        this.bidService = bidService;
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
        ctx.json(bidToMap(bids.getFirst()));
    }

    public void handleGetBidsByBidder(Context ctx) {
        String bidderId = ctx.pathParam("bidderId");
        ctx.json(bidRepo.findByBidderId(bidderId).stream().map(this::bidToMap).toList());
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
}
