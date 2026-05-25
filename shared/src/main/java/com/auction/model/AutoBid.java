package com.auction.model;

public class AutoBid {
    private final String userId;
    private final String itemId;
    private final double maxBid;
    private final double increment;
    private final long createdAt;

    public AutoBid(String userId, String itemId, double maxBid, double increment, long createdAt) {
        this.userId = userId;
        this.itemId = itemId;
        this.maxBid = maxBid;
        this.increment = increment;
        this.createdAt = createdAt;
    }

    public String getUserId() { return userId; }

    public String getItemId() { return itemId; }

    public double getMaxBid() { return maxBid; }

    public double getIncrement() { return increment; }

    public long getCreatedAt() { return createdAt; }
}
