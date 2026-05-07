package com.auction.model;

public enum AuctionStatus {
    OPEN,       // created, not yet accepting bids
    RUNNING,    // accepting bids
    FINISHED,   // bidding ended, winner debited and seller paid (terminal)
    CANCELED    // auction voided (no winner, insolvent winner, or admin cancel)
}
