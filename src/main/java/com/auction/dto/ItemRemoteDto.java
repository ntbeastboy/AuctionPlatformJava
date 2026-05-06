package com.auction.dto;

public class ItemRemoteDto {
    private String id;
    private String name;
    private String description;
    private String category;
    private Double startPrice;
    private Double currentPrice;
    private String sellerId;
    private String sellerName;
    private String status;
    private Double priceStep;
    private String currentWinnerId;
    private String bidStartTime;
    private String bidEndTime;
    private String type;

    public ItemRemoteDto() {}

    public ItemRemoteDto(String id, String name, String description, String category, Double startPrice, Double currentPrice, String sellerId, String sellerName, String status) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.startPrice = startPrice;
        this.currentPrice = currentPrice;
        this.sellerId = sellerId;
        this.sellerName = sellerName;
        this.status = status;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public Double getStartPrice() { return startPrice; }
    public void setStartPrice(Double startPrice) { this.startPrice = startPrice; }
    public Double getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(Double currentPrice) { this.currentPrice = currentPrice; }
    public String getSellerId() { return sellerId; }
    public void setSellerId(String sellerId) { this.sellerId = sellerId; }
    public String getSellerName() { return sellerName; }
    public void setSellerName(String sellerName) { this.sellerName = sellerName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Double getPriceStep() { return priceStep; }
    public void setPriceStep(Double priceStep) { this.priceStep = priceStep; }
    public String getCurrentWinnerId() { return currentWinnerId; }
    public void setCurrentWinnerId(String currentWinnerId) { this.currentWinnerId = currentWinnerId; }
    public String getBidStartTime() { return bidStartTime; }
    public void setBidStartTime(String bidStartTime) { this.bidStartTime = bidStartTime; }
    public String getBidEndTime() { return bidEndTime; }
    public void setBidEndTime(String bidEndTime) { this.bidEndTime = bidEndTime; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}
