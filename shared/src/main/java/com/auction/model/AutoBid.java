package com.auction.model;

public class AutoBid {
    private final String userId;
    private final String itemId;
    private final double maxBid;
    private final double increment;
    private final long createdAt;
    private final long lastBidAt;

    public AutoBid(String userId, String itemId, double maxBid, double increment, long createdAt) {
        this(userId, itemId, maxBid, increment, createdAt, 0L);
    }

    public AutoBid(String userId, String itemId, double maxBid, double increment, long createdAt, long lastBidAt) {
        this.userId = userId;
        this.itemId = itemId;
        this.maxBid = maxBid;
        this.increment = increment;
        this.createdAt = createdAt;
        this.lastBidAt = lastBidAt;
    }

    public String getUserId() { return userId; }

    public String getItemId() { return itemId; }

    public double getMaxBid() { return maxBid; }

    public double getIncrement() { return increment; }

    public long getCreatedAt() { return createdAt; }

    public long getLastBidAt() { return lastBidAt; }
}
