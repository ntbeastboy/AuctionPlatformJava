package com.auction.service;

import com.auction.exception.InvalidBidException;
import com.auction.model.AutoBid;
import com.auction.model.Bid;
import com.auction.model.Bidder;
import com.auction.model.Item;
import com.auction.model.Other;
import com.auction.model.Seller;
import com.auction.model.User;
import com.auction.repository.AutoBidRepository;
import com.auction.repository.BidRepository;
import com.auction.repository.ItemRepository;
import com.auction.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BidServiceAutoBidTest {

    @Test
    void equalMaxBidKeepsEarlierAutoBidderAhead() throws Exception {
        Fixture fx = new Fixture();

        fx.service.setAutoBid(fx.alice, fx.item.getId(), 200.0, 10.0);
        Thread.sleep(2);
        fx.service.setAutoBid(fx.bob, fx.item.getId(), 200.0, 10.0);

        Item item = fx.items.findById(fx.item.getId()).orElseThrow();
        assertEquals(fx.alice.getId(), item.getCurrentWinnerId());
        assertEquals(200.0, item.getCurrentPrice());
    }

    @Test
    void manualBidTriggersAutoBidResponse() {
        Fixture fx = new Fixture();
        fx.service.setAutoBid(fx.alice, fx.item.getId(), 150.0, 10.0);

        fx.service.placeBid(fx.bob, fx.item.getId(), 120.0);

        Item item = fx.items.findById(fx.item.getId()).orElseThrow();
        assertEquals(fx.alice.getId(), item.getCurrentWinnerId());
        assertEquals(130.0, item.getCurrentPrice());
    }

    @Test
    void autoBidIncrementMustMeetItemPriceStep() {
        Fixture fx = new Fixture();

        assertThrows(InvalidBidException.class,
                () -> fx.service.setAutoBid(fx.alice, fx.item.getId(), 200.0, 1.0));
    }

    private static class Fixture {
        final InMemoryUserRepository users = new InMemoryUserRepository();
        final InMemoryItemRepository items = new InMemoryItemRepository();
        final InMemoryBidRepository bids = new InMemoryBidRepository();
        final InMemoryAutoBidRepository autoBids = new InMemoryAutoBidRepository();
        final BidService service = new BidService(items, bids, users, autoBids, null);
        final Seller seller = new Seller("seller", "seller", "pw");
        final Bidder alice = new Bidder("alice", "alice", "pw");
        final Bidder bob = new Bidder("bob", "bob", "pw");
        final Item item = new Other("item", "Item", "Desc", 100.0, 5.0,
                LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusMinutes(10), seller.getId());

        Fixture() {
            alice.addFunds(1000.0);
            bob.addFunds(1000.0);
            users.save(seller);
            users.save(alice);
            users.save(bob);
            item.setStatus(com.auction.model.AuctionStatus.RUNNING);
            items.save(item);
        }
    }

    private static class InMemoryUserRepository implements UserRepository {
        private final Map<String, User> users = new HashMap<>();

        @Override public void save(User user) { users.put(user.getId(), user); }
        @Override public Optional<User> findByUsername(String username) {
            return users.values().stream().filter(u -> username.equals(u.getUsername())).findFirst();
        }
        @Override public boolean existsByUsername(String username) { return findByUsername(username).isPresent(); }
        @Override public Optional<User> findById(String id) { return Optional.ofNullable(users.get(id)); }
        @Override public List<User> findAll() { return new ArrayList<>(users.values()); }
    }

    private static class InMemoryItemRepository implements ItemRepository {
        private final Map<String, Item> items = new HashMap<>();

        @Override public void save(Item item) { items.put(item.getId(), item); }
        @Override public Optional<Item> findById(String id) { return Optional.ofNullable(items.get(id)); }
        @Override public void delete(String id) { items.remove(id); }
        @Override public List<Item> findAll() { return new ArrayList<>(items.values()); }
        @Override public void update(Item item) {
            item.setVersion(item.getVersion() + 1);
            items.put(item.getId(), item);
        }
    }

    private static class InMemoryBidRepository implements BidRepository {
        private final List<Bid> bids = new ArrayList<>();

        @Override public void save(Bid bid) { bids.add(bid); }
        @Override public Optional<Bid> findById(int id) {
            if (id < 0 || id >= bids.size()) return Optional.empty();
            return Optional.of(bids.get(id));
        }
        @Override public List<Bid> findByItemId(String itemId) {
            return bids.stream().filter(b -> itemId.equals(b.getItemId())).toList();
        }
        @Override public List<Bid> findByBidderId(String bidderId) {
            return bids.stream().filter(b -> bidderId.equals(b.getBidderId())).toList();
        }
    }

    private static class InMemoryAutoBidRepository implements AutoBidRepository {
        private final Map<String, AutoBid> autoBids = new HashMap<>();

        @Override public void save(AutoBid autoBid) {
            autoBids.put(key(autoBid.getUserId(), autoBid.getItemId()), autoBid);
        }
        @Override public Optional<AutoBid> findByUserAndItem(String userId, String itemId) {
            return Optional.ofNullable(autoBids.get(key(userId, itemId)));
        }
        @Override public List<AutoBid> findByItemId(String itemId) {
            return autoBids.values().stream().filter(a -> itemId.equals(a.getItemId())).toList();
        }
        @Override public List<AutoBid> findByUserId(String userId) {
            return autoBids.values().stream().filter(a -> userId.equals(a.getUserId())).toList();
        }
        @Override public void delete(String userId, String itemId) { autoBids.remove(key(userId, itemId)); }

        private String key(String userId, String itemId) {
            return userId + ":" + itemId;
        }
    }
}
