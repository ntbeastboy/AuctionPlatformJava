package com.auction.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.auction.model.Admin;
import com.auction.model.AuctionStatus;
import com.auction.model.Bidder;
import com.auction.model.Item;
import com.auction.model.Other;
import com.auction.model.Seller;
import com.auction.model.User;
import com.auction.repository.ItemRepository;
import com.auction.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AuctionServiceTest {

  @Test
  void closeAuctionWithWinnerMarksFinishedUntilWinnerPaysSeller() {
    InMemoryItemRepository items = new InMemoryItemRepository();
    InMemoryUserRepository users = new InMemoryUserRepository();
    AuctionService service = new AuctionService(items, users);
    try {
      Seller seller = new Seller("seller", "seller", "pw");
      Bidder bidder = new Bidder("bidder", "bidder", "pw");
      bidder.addFunds(500.0);
      users.save(seller);
      users.save(bidder);

      Item item = runningItem(seller.getId());
      item.setCurrentPrice(125.0);
      item.setCurrentWinnerId(bidder.getId());
      items.save(item);

      service.closeAuction(item.getId());

      Item settled = items.findById(item.getId()).orElseThrow();
      assertEquals(AuctionStatus.FINISHED, settled.getStatus());
      assertEquals(500.0, ((Bidder) users.findById(bidder.getId()).orElseThrow()).getBalance());
      assertEquals(0.0, ((Seller) users.findById(seller.getId()).orElseThrow()).getBalance());

      service.paySeller(item.getId(), bidder);

      settled = items.findById(item.getId()).orElseThrow();
      assertEquals(AuctionStatus.PAID, settled.getStatus());
      assertEquals(375.0, ((Bidder) users.findById(bidder.getId()).orElseThrow()).getBalance());
      assertEquals(125.0, ((Seller) users.findById(seller.getId()).orElseThrow()).getBalance());
    } finally {
      service.shutdown();
    }
  }

  @Test
  void closeAuctionWithoutWinnerCancelsAuction() {
    InMemoryItemRepository items = new InMemoryItemRepository();
    InMemoryUserRepository users = new InMemoryUserRepository();
    AuctionService service = new AuctionService(items, users);
    try {
      Seller seller = new Seller("seller", "seller", "pw");
      users.save(seller);
      Item item = runningItem(seller.getId());
      items.save(item);

      service.closeAuction(item.getId());

      assertEquals(AuctionStatus.CANCELED, items.findById(item.getId()).orElseThrow().getStatus());
    } finally {
      service.shutdown();
    }
  }

  @Test
  void adminCanEndAuctionEarlyBeforeEndTime() {
    InMemoryItemRepository items = new InMemoryItemRepository();
    InMemoryUserRepository users = new InMemoryUserRepository();
    AuctionService service = new AuctionService(items, users);
    try {
      Admin admin = new Admin("admin", "admin", "pw");
      Seller seller = new Seller("seller", "seller", "pw");
      Bidder bidder = new Bidder("bidder", "bidder", "pw");
      bidder.addFunds(500.0);
      users.save(admin);
      users.save(seller);
      users.save(bidder);

      Item item =
          new Other(
              "item",
              "Item",
              "Desc",
              100.0,
              5.0,
              LocalDateTime.now().minusMinutes(1),
              LocalDateTime.now().plusMinutes(10),
              seller.getId());
      item.setStatus(AuctionStatus.RUNNING);
      item.setCurrentPrice(125.0);
      item.setCurrentWinnerId(bidder.getId());
      items.save(item);

      service.endAuctionEarly(item.getId(), admin);

      assertEquals(AuctionStatus.FINISHED, items.findById(item.getId()).orElseThrow().getStatus());
    } finally {
      service.shutdown();
    }
  }

  @Test
  void recoverExpiredFinishedAuctionCancelsAndBansWinnerForNonPayment() {
    InMemoryItemRepository items = new InMemoryItemRepository();
    InMemoryUserRepository users = new InMemoryUserRepository();
    AuctionService service = new AuctionService(items, users);
    try {
      Seller seller = new Seller("seller", "seller", "pw");
      Bidder bidder = new Bidder("bidder", "bidder", "pw");
      bidder.addFunds(500.0);
      users.save(seller);
      users.save(bidder);

      Item item =
          new Other(
              "item",
              "Item",
              "Desc",
              100.0,
              5.0,
              LocalDateTime.now().minusHours(10),
              LocalDateTime.now().minusHours(9),
              seller.getId());
      item.setStatus(AuctionStatus.FINISHED);
      item.setCurrentPrice(125.0);
      item.setCurrentWinnerId(bidder.getId());
      items.save(item);

      service.recoverScheduledAuctions();

      assertEquals(AuctionStatus.CANCELED, items.findById(item.getId()).orElseThrow().getStatus());
      assertTrue(((Bidder) users.findById(bidder.getId()).orElseThrow()).isBanned());
      assertEquals(500.0, ((Bidder) users.findById(bidder.getId()).orElseThrow()).getBalance());
      assertEquals(0.0, ((Seller) users.findById(seller.getId()).orElseThrow()).getBalance());
    } finally {
      service.shutdown();
    }
  }

  private Item runningItem(String sellerId) {
    Item item =
        new Other(
            "item",
            "Item",
            "Desc",
            100.0,
            5.0,
            LocalDateTime.now().minusMinutes(2),
            LocalDateTime.now().minusMinutes(1),
            sellerId);
    item.setStatus(AuctionStatus.RUNNING);
    return item;
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
}
