package com.auction.dto;

import java.time.LocalDateTime;

public class BidRemoteDto {
    private String id;
    private String itemId;
    private String bidderId;
    private String bidderName;
    private Double bidAmount;
    private LocalDateTime bidTime;

    public BidRemoteDto() {}

    public BidRemoteDto(String id, String itemId, String bidderId, String bidderName, Double bidAmount, LocalDateTime bidTime) {
        this.id = id;
        this.itemId = itemId;
        this.bidderId = bidderId;
        this.bidderName = bidderName;
        this.bidAmount = bidAmount;
        this.bidTime = bidTime;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getItemId() { return itemId; }
    public void setItemId(String itemId) { this.itemId = itemId; }
    public String getBidderId() { return bidderId; }
    public void setBidderId(String bidderId) { this.bidderId = bidderId; }
    public String getBidderName() { return bidderName; }
    public void setBidderName(String bidderName) { this.bidderName = bidderName; }
    public Double getBidAmount() { return bidAmount; }
    public void setBidAmount(Double bidAmount) { this.bidAmount = bidAmount; }
    public LocalDateTime getBidTime() { return bidTime; }
    public void setBidTime(LocalDateTime bidTime) { this.bidTime = bidTime; }
}
