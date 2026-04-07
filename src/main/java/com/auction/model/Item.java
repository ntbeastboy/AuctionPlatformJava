package AuctionPlatformJava.src.main.java.com.auction.model;

import java.time.LocalDateTime;

public class Item implements Entity {
    final String id;
    private String name;
    private String description;
    final double startingPrice;
    private double currentPrice;
    private LocalDateTime bidStartTime;
    private LocalDateTime bidEndTime;

    public Item(String id, String name, String description, double startingPrice, LocalDateTime bidStartTime, LocalDateTime bidEndTime) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.startingPrice = startingPrice;
        this.currentPrice = startingPrice;
        this.bidStartTime = bidStartTime;
        this.bidEndTime = bidEndTime;
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

    public LocalDateTime getBidStartTime() { return bidStartTime; }

    public void setBidStartTime(LocalDateTime bidStartTime) { this.bidStartTime = bidStartTime; }

    public LocalDateTime getBidEndTime() { return bidEndTime; }

    public void setBidEndTime(LocalDateTime bidEndTime) { this.bidEndTime = bidEndTime; }
}
