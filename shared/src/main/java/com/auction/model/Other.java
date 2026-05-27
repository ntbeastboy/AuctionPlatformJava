package com.auction.model;

import java.time.LocalDateTime;

public class Other extends Item {
    public Other(String id, String name, String description, double startingPrice, double priceStep,
                 LocalDateTime bidStartTime, LocalDateTime bidEndTime, String sellerId) {
        super(id, name, description, startingPrice, priceStep, bidStartTime, bidEndTime, sellerId);
    }

    @Override
    public String getTypeName() { return "Other"; }
}
