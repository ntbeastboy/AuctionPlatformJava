package com.auction.model;

import java.time.LocalDateTime;

public class AuctionItem extends Item {
    private String category;
    private String condition;

    public AuctionItem(String id, String name, String description, double startingPrice, double priceStep,
                       LocalDateTime bidStartTime, LocalDateTime bidEndTime, String sellerId,
                       String category, String condition) {
        super(id, name, description, startingPrice, priceStep, bidStartTime, bidEndTime, sellerId);
        this.category = category;
        this.condition = condition;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }
}