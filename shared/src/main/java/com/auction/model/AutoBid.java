package com.auction.model;

public class AutoBid {
    private final String userId;
    private final String itemId;
    private final double maxBid;
    private final double increment;
    private final long createdAt;
    private final long lastBidAt;
    private final long nextCheckAt;

    public AutoBid(String userId, String itemId, double maxBid, double increment, long createdAt) {
        this(userId, itemId, maxBid, increment, createdAt, 0L);
    }

    public AutoBid(String userId, String itemId, double maxBid, double increment, long createdAt, long lastBidAt) {
        this(userId, itemId, maxBid, increment, createdAt, lastBidAt,
                lastBidAt > 0L ? lastBidAt + 5_000L : createdAt);
    }

    public AutoBid(String userId, String itemId, double maxBid, double increment,
                   long createdAt, long lastBidAt, long nextCheckAt) {
        this.userId = userId;
        this.itemId = itemId;
        this.maxBid = maxBid;
        this.increment = increment;
        this.createdAt = createdAt;
        this.lastBidAt = lastBidAt;
        this.nextCheckAt = nextCheckAt > 0L
                ? nextCheckAt
                : (lastBidAt > 0L ? lastBidAt + 5_000L : createdAt);
    }

    public String getUserId() { return userId; }

    public String getItemId() { return itemId; }

    public double getMaxBid() { return maxBid; }

    public double getIncrement() { return increment; }

    public long getCreatedAt() { return createdAt; }

    public long getLastBidAt() { return lastBidAt; }

    public long getNextCheckAt() { return nextCheckAt; }
}
