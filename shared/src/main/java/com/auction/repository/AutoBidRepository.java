package com.auction.repository;

import com.auction.model.AutoBid;

import java.util.List;
import java.util.Optional;

public interface AutoBidRepository {
    void save(AutoBid autoBid);
    Optional<AutoBid> findByUserAndItem(String userId, String itemId);
    List<AutoBid> findByItemId(String itemId);
    List<AutoBid> findByUserId(String userId);
    void delete(String userId, String itemId);

    default void recordBid(String userId, String itemId, long bidAt) {
        findByUserAndItem(userId, itemId).ifPresent(autoBid -> save(new AutoBid(
                autoBid.getUserId(),
                autoBid.getItemId(),
                autoBid.getMaxBid(),
                autoBid.getIncrement(),
                autoBid.getCreatedAt(),
                bidAt
        )));
    }
}
