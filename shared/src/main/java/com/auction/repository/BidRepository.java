package com.auction.repository;

import com.auction.model.Bid;

import java.util.List;
import java.util.Optional;

public interface BidRepository {
    void save(Bid bid);
    Optional<Bid> findById(int id);
    List<Bid> findByItemId(String itemId);
    List<Bid> findByBidderId(String bidderId);
    void deleteLatestByBidderAndItem(String bidderId, String itemId);
}
