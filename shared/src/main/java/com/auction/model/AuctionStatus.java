package com.auction.model;

public enum AuctionStatus {
  OPEN, // created, not yet accepting bids
  RUNNING, // accepting bids
  FINISHED, // bidding ended and settlement is in progress
  PAID, // settlement completed successfully
  CANCELED // auction voided (no winner, insolvent winner, or admin cancel)
}
