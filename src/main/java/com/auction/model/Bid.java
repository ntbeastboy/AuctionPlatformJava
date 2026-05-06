package com.auction.model;

public class Bid {
    private final String bidderId;
    private final String itemId;
    private final double amount;
    private final long timestamp;

    public Bid(String bidderId, String itemId, double amount) {
        this(bidderId, itemId, amount, System.currentTimeMillis() / 1000L);
    }

    public Bid(String bidderId, String itemId, double amount, long timestamp) {
        this.bidderId = bidderId;
        this.itemId = itemId;
        this.amount = amount;
        this.timestamp = timestamp;
    }

    public String getBidderId() { return bidderId; }

    public String getItemId() { return itemId; }

    public double getAmount() { return amount; }

    public long getTimestamp() { return timestamp; }
}
