package com.auction.model;

import java.time.LocalDateTime;

public abstract class Item implements Entity {
    final String id;
    private String name;
    private String description;
    final double startingPrice;
    private double currentPrice;
    final double priceStep;
    private LocalDateTime bidStartTime;
    private LocalDateTime bidEndTime;
    final String sellerId;
    private AuctionStatus status;
    private String currentWinnerId;
    private long version;

    public Item(String id, String name, String description, double startingPrice, double priceStep, LocalDateTime bidStartTime, LocalDateTime bidEndTime, String sellerId) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.startingPrice = startingPrice;
        this.currentPrice = startingPrice;
        this.priceStep = priceStep;
        this.bidStartTime = bidStartTime;
        this.bidEndTime = bidEndTime;
        this.sellerId = sellerId;
        this.status = AuctionStatus.OPEN;
        this.currentWinnerId = null;
        this.version = 0;
    }

    @Override
    public String getId() { return id; }

    public String getName() { return name; }

    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }

    public void setDescription(String description) { this.description = description; }

    public double getStartingPrice() { return startingPrice; }

    public double getCurrentPrice() { return currentPrice; }

    public void setCurrentPrice(double currentPrice) { this.currentPrice = currentPrice; }

    public double getPriceStep() { return priceStep; }

    public LocalDateTime getBidStartTime() { return bidStartTime; }

    public void setBidStartTime(LocalDateTime bidStartTime) { this.bidStartTime = bidStartTime; }

    public LocalDateTime getBidEndTime() { return bidEndTime; }

    public void setBidEndTime(LocalDateTime bidEndTime) { this.bidEndTime = bidEndTime; }

    public String getSellerId() { return sellerId; }

    public AuctionStatus getStatus() { return status; }

    public void setStatus(AuctionStatus status) { this.status = status; }

    public String getCurrentWinnerId() { return currentWinnerId; }

    public void setCurrentWinnerId(String currentWinnerId) { this.currentWinnerId = currentWinnerId; }

    public long getVersion() { return version; }

    public void setVersion(long version) { this.version = version; }
}
