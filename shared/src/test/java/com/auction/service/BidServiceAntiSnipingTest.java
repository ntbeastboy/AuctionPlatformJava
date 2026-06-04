package com.auction.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.auction.model.AuctionStatus;
import com.auction.model.Bid;
import com.auction.model.Bidder;
import com.auction.model.Item;
import com.auction.model.Other;
import com.auction.model.Seller;
import com.auction.model.User;
import com.auction.repository.BidRepository;
import com.auction.repository.ItemRepository;
import com.auction.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BidServiceAntiSnipingTest {

  @Test
  void bidInFinalWindowExtendsAuctionEndTime() {
    InMemoryItemRepository items = new InMemoryItemRepository();
    InMemoryBidRepository bids = new InMemoryBidRepository();
    InMemoryUserRepository users = new InMemoryUserRepository();
    BidService service = new BidService(items, bids, users);

    Seller seller = new Seller("seller", "seller", "pw");
    Bidder bidder = new Bidder("bidder", "bidder", "pw");
    bidder.addFunds(1000.0);
    users.save(seller);
    users.save(bidder);
    LocalDateTime originalEnd = LocalDateTime.now().plusSeconds(5);
    Item item =
        new Other(
            "item",
            "Item",
            "Desc",
            100.0,
            5.0,
            LocalDateTime.now().minusMinutes(1),
            originalEnd,
            seller.getId());
    item.setStatus(AuctionStatus.RUNNING);
    items.save(item);

    service.placeBid(bidder, item.getId(), 110.0);

    assertTrue(items.findById(item.getId()).orElseThrow().getBidEndTime().isAfter(originalEnd));
  }

  private static class InMemoryUserRepository implements UserRepository {
    private final Map<String, User> users = new HashMap<>();

    @Override
    public void save(User user) {
      users.put(user.getId(), user);
    }

    @Override
    public Optional<User> findByUsername(String username) {
      return users.values().stream().filter(u -> username.equals(u.getUsername())).findFirst();
    }

    @Override
    public boolean existsByUsername(String username) {
      return findByUsername(username).isPresent();
    }

    @Override
    public Optional<User> findById(String id) {
      return Optional.ofNullable(users.get(id));
    }

    @Override
    public List<User> findAll() {
      return new ArrayList<>(users.values());
    }

    @Override
    public void delete(String id) {
      users.remove(id);
    }
  }

  private static class InMemoryItemRepository implements ItemRepository {
    private final Map<String, Item> items = new HashMap<>();

    @Override
    public void save(Item item) {
      items.put(item.getId(), item);
    }

    @Override
    public Optional<Item> findById(String id) {
      return Optional.ofNullable(items.get(id));
    }

    @Override
    public void delete(String id) {
      items.remove(id);
    }

    @Override
    public List<Item> findAll() {
      return new ArrayList<>(items.values());
    }

    @Override
    public void update(Item item) {
      item.setVersion(item.getVersion() + 1);
      items.put(item.getId(), item);
    }
  }

  private static class InMemoryBidRepository implements BidRepository {
    private final List<Bid> bids = new ArrayList<>();

    @Override
    public void save(Bid bid) {
      bids.add(bid);
    }

    @Override
    public Optional<Bid> findById(int id) {
      if (id < 0 || id >= bids.size()) return Optional.empty();
      return Optional.of(bids.get(id));
    }

    @Override
    public List<Bid> findByItemId(String itemId) {
      return bids.stream().filter(b -> itemId.equals(b.getItemId())).toList();
    }

    @Override
    public List<Bid> findByBidderId(String bidderId) {
      return bids.stream().filter(b -> bidderId.equals(b.getBidderId())).toList();
    }

    @Override
    public void deleteLatestByBidderAndItem(String bidderId, String itemId) {
      for (int i = bids.size() - 1; i >= 0; i--) {
        Bid bid = bids.get(i);
        if (bidderId.equals(bid.getBidderId()) && itemId.equals(bid.getItemId())) {
          bids.remove(i);
          return;
        }
      }
    }
  }
}
