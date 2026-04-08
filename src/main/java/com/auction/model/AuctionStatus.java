package com.auction.model;

public enum AuctionStatus {
    OPEN,       // created, not yet accepting bids
    RUNNING,    // accepting bids
    FINISHED,   // bidding time elapsed, winner determined
    PAID,       // winner paid, seller credited
    CANCELED    // auction voided (admin action)
}
