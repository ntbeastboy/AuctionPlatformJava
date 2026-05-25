package com.auction.service;

import com.auction.concurrency.ItemLockManager;
import com.auction.concurrency.UserLockManager;
import com.auction.exception.AuctionClosedException;
import com.auction.exception.InvalidBidException;
import com.auction.exception.ProductNotFoundException;
import com.auction.exception.UnauthorizedActionException;
import com.auction.exception.UserNotFoundException;
import com.auction.model.AuctionStatus;
import com.auction.model.AutoBid;
import com.auction.model.Bid;
import com.auction.model.Bidder;
import com.auction.model.Item;
import com.auction.model.Seller;
import com.auction.model.User;
import com.auction.repository.AutoBidRepository;
import com.auction.repository.BidRepository;
import com.auction.repository.ItemRepository;
import com.auction.repository.TransactionRunner;
import com.auction.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

public class BidService {
    private static final Comparator<AutoBid> AUTO_BID_PRIORITY =
            Comparator.comparingDouble(AutoBid::getMaxBid).reversed()
                    .thenComparingLong(AutoBid::getCreatedAt);

    private final ItemRepository itemRepository;
    private final BidRepository bidRepository;
    private final UserRepository userRepository;
    private final AutoBidRepository autoBidRepository;
    private final TransactionRunner txRunner;

    public BidService(ItemRepository itemRepository, BidRepository bidRepository,
                      UserRepository userRepository, TransactionRunner txRunner) {
        this(itemRepository, bidRepository, userRepository, null, txRunner);
    }

    public BidService(ItemRepository itemRepository, BidRepository bidRepository,
                      UserRepository userRepository, AutoBidRepository autoBidRepository,
                      TransactionRunner txRunner) {
        this.itemRepository = itemRepository;
        this.bidRepository = bidRepository;
        this.userRepository = userRepository;
        this.autoBidRepository = autoBidRepository;
        this.txRunner = txRunner;
    }

    public BidService(ItemRepository itemRepository, BidRepository bidRepository, UserRepository userRepository) {
        this(itemRepository, bidRepository, userRepository, null, null);
    }

    public Bid placeBid(User user, String itemId, double amount) {
        if (!(user instanceof Bidder) && !(user instanceof Seller))
            throw new UnauthorizedActionException("Only bidders and sellers can place bids.");

        Bid bid;
        ReentrantLock userLock = UserLockManager.getLock(user.getId());
        ReentrantLock itemLock = ItemLockManager.getLock(itemId);
        userLock.lock();
        try {
            itemLock.lock();
            try {
                Item item = itemRepository.findById(itemId)
                        .orElseThrow(() -> new ProductNotFoundException("Item not found: " + itemId));

                if (user instanceof Seller && item.getSellerId().equals(user.getId()))
                    throw new UnauthorizedActionException("Sellers cannot bid on their own items.");

                if (item.getStatus() != AuctionStatus.RUNNING)
                    throw new AuctionClosedException("This auction is not currently running.");

                if (LocalDateTime.now().isAfter(item.getBidEndTime()))
                    throw new AuctionClosedException("This auction has ended.");

                if (user.getId().equals(item.getCurrentWinnerId()))
                    throw new InvalidBidException("You are already the highest bidder.");

                if (amount <= 0)
                    throw new InvalidBidException("Bid amount must be positive.");

                double minimumBid = item.getCurrentPrice() + item.getPriceStep();
                if (amount < minimumBid)
                    throw new InvalidBidException(
                            "Bid must be at least " + minimumBid + " (current price " + item.getCurrentPrice() + " + step " + item.getPriceStep() + ").");

                User freshUser = userRepository.findById(user.getId())
                        .orElseThrow(() -> new UserNotFoundException("User not found: " + user.getId()));

                double balance = balanceOf(freshUser);
                double committed = committedAmount(freshUser.getId(), itemId);
                double available = balance - committed;
                if (available < amount)
                    throw new InvalidBidException(
                            "Insufficient available balance: $" + String.format("%.2f", available) +
                                    " ($" + String.format("%.2f", balance) + " balance - $" +
                                    String.format("%.2f", committed) + " committed to other bids), need $" +
                                    String.format("%.2f", amount) + ".");

                item.setCurrentPrice(amount);
                item.setCurrentWinnerId(freshUser.getId());

                bid = new Bid(freshUser.getId(), itemId, amount);
                runInTx(() -> {
                    itemRepository.update(item);
                    bidRepository.save(bid);
                    userRepository.save(freshUser);
                });
            } finally {
                itemLock.unlock();
            }
        } finally {
            userLock.unlock();
        }

        resolveAutoBids(itemId);
        return bid;
    }

    public List<Bid> getBidsForItem(String itemId) {
        return bidRepository.findByItemId(itemId);
    }

    public AutoBid setAutoBid(User user, String itemId, double maxBid, double increment) {
        requireAutoBidRepository();
        if (!(user instanceof Bidder) && !(user instanceof Seller))
            throw new UnauthorizedActionException("Only bidders and sellers can use auto-bidding.");

        AutoBid autoBid;
        ReentrantLock userLock = UserLockManager.getLock(user.getId());
        ReentrantLock itemLock = ItemLockManager.getLock(itemId);
        userLock.lock();
        try {
            itemLock.lock();
            try {
                Item item = itemRepository.findById(itemId)
                        .orElseThrow(() -> new ProductNotFoundException("Item not found: " + itemId));

                if (user instanceof Seller && item.getSellerId().equals(user.getId()))
                    throw new UnauthorizedActionException("Sellers cannot auto-bid on their own items.");

                if (item.getStatus() != AuctionStatus.RUNNING)
                    throw new AuctionClosedException("This auction is not currently running.");

                if (LocalDateTime.now().isAfter(item.getBidEndTime()))
                    throw new AuctionClosedException("This auction has ended.");

                if (increment < item.getPriceStep())
                    throw new InvalidBidException("Auto-bid increment must be at least the item price step.");

                double minimumMax = user.getId().equals(item.getCurrentWinnerId())
                        ? item.getCurrentPrice()
                        : item.getCurrentPrice() + item.getPriceStep();
                if (maxBid < minimumMax)
                    throw new InvalidBidException("Max auto-bid must be at least " + minimumMax + ".");

                User freshUser = userRepository.findById(user.getId())
                        .orElseThrow(() -> new UserNotFoundException("User not found: " + user.getId()));
                double balance = balanceOf(freshUser);
                double committed = committedAmount(freshUser.getId(), itemId);
                double available = balance - committed;
                if (available < maxBid)
                    throw new InvalidBidException(
                            "Insufficient available balance for max auto-bid: $" + String.format("%.2f", available) +
                                    " available, need $" + String.format("%.2f", maxBid) + ".");

                long createdAt = autoBidRepository.findByUserAndItem(user.getId(), itemId)
                        .map(AutoBid::getCreatedAt)
                        .orElse(System.currentTimeMillis());
                autoBid = new AutoBid(user.getId(), itemId, maxBid, increment, createdAt);
                runInTx(() -> autoBidRepository.save(autoBid));
            } finally {
                itemLock.unlock();
            }
        } finally {
            userLock.unlock();
        }

        resolveAutoBids(itemId);
        return autoBid;
    }

    public Optional<AutoBid> getAutoBid(User user, String itemId) {
        requireAutoBidRepository();
        return autoBidRepository.findByUserAndItem(user.getId(), itemId);
    }

    public void cancelAutoBid(User user, String itemId) {
        requireAutoBidRepository();
        ReentrantLock userLock = UserLockManager.getLock(user.getId());
        ReentrantLock itemLock = ItemLockManager.getLock(itemId);
        userLock.lock();
        try {
            itemLock.lock();
            try {
                runInTx(() -> autoBidRepository.delete(user.getId(), itemId));
            } finally {
                itemLock.unlock();
            }
        } finally {
            userLock.unlock();
        }
    }

    private void resolveAutoBids(String itemId) {
        if (autoBidRepository == null) return;

        for (int attempt = 0; attempt < 5; attempt++) {
            List<String> userIds = autoBidRepository.findByItemId(itemId).stream()
                    .map(AutoBid::getUserId)
                    .distinct()
                    .sorted()
                    .toList();
            Set<String> lockedUserIds = new HashSet<>(userIds);
            List<ReentrantLock> userLocks = new ArrayList<>();
            for (String userId : userIds) {
                ReentrantLock lock = UserLockManager.getLock(userId);
                lock.lock();
                userLocks.add(lock);
            }

            ReentrantLock itemLock = ItemLockManager.getLock(itemId);
            itemLock.lock();
            try {
                List<AutoBid> autoBids = autoBidRepository.findByItemId(itemId);
                Set<String> actualUserIds = new HashSet<>();
                for (AutoBid autoBid : autoBids) actualUserIds.add(autoBid.getUserId());
                if (!lockedUserIds.containsAll(actualUserIds)) continue;

                resolveAutoBidsLocked(itemId);
                return;
            } finally {
                itemLock.unlock();
                for (int i = userLocks.size() - 1; i >= 0; i--) userLocks.get(i).unlock();
            }
        }

        throw new IllegalStateException("Auto-bids changed too quickly. Please try again.");
    }

    private void resolveAutoBidsLocked(String itemId) {
        int safetyLimit = 100;
        Set<String> skippedUserIds = new HashSet<>();

        while (safetyLimit-- > 0) {
            Item item = itemRepository.findById(itemId)
                    .orElseThrow(() -> new ProductNotFoundException("Item not found: " + itemId));

            if (item.getStatus() != AuctionStatus.RUNNING) return;
            if (item.getBidEndTime() != null && LocalDateTime.now().isAfter(item.getBidEndTime())) return;

            List<AutoBid> autoBids = autoBidRepository.findByItemId(itemId);
            AutoBid currentWinnerAutoBid = currentWinnerAutoBid(item, autoBids).orElse(null);
            List<AutoBid> candidates = autoBids.stream()
                    .filter(a -> !a.getUserId().equals(item.getCurrentWinnerId()))
                    .filter(a -> !a.getUserId().equals(item.getSellerId()))
                    .filter(a -> !skippedUserIds.contains(a.getUserId()))
                    .filter(a -> autoBidAmount(a, item, currentWinnerAutoBid).isPresent())
                    .sorted(AUTO_BID_PRIORITY)
                    .toList();

            boolean placed = false;
            for (AutoBid autoBid : candidates) {
                double amount = autoBidAmount(autoBid, item, currentWinnerAutoBid).orElseThrow();
                User user = userRepository.findById(autoBid.getUserId())
                        .orElseThrow(() -> new UserNotFoundException("User not found: " + autoBid.getUserId()));
                if (availableBalance(user, itemId) < amount) {
                    skippedUserIds.add(autoBid.getUserId());
                    continue;
                }

                item.setCurrentPrice(amount);
                item.setCurrentWinnerId(autoBid.getUserId());
                Bid bid = new Bid(autoBid.getUserId(), itemId, amount);
                runInTx(() -> {
                    itemRepository.update(item);
                    bidRepository.save(bid);
                    userRepository.save(user);
                });
                skippedUserIds.clear();
                placed = true;
                break;
            }

            if (!placed) return;
        }

        throw new IllegalStateException("Auto-bid resolution did not settle.");
    }

    private Optional<AutoBid> currentWinnerAutoBid(Item item, List<AutoBid> autoBids) {
        String winnerId = item.getCurrentWinnerId();
        if (winnerId == null) return Optional.empty();
        return autoBids.stream()
                .filter(a -> winnerId.equals(a.getUserId()))
                .findFirst();
    }

    private Optional<Double> autoBidAmount(AutoBid autoBid, Item item, AutoBid currentWinnerAutoBid) {
        double minimumBid = item.getCurrentPrice() + item.getPriceStep();
        double amount = Math.min(autoBid.getMaxBid(), item.getCurrentPrice() + autoBid.getIncrement());
        if (amount >= minimumBid) return Optional.of(amount);

        if (currentWinnerAutoBid != null
                && Double.compare(autoBid.getMaxBid(), currentWinnerAutoBid.getMaxBid()) == 0
                && autoBid.getCreatedAt() < currentWinnerAutoBid.getCreatedAt()
                && autoBid.getMaxBid() >= item.getCurrentPrice()) {
            return Optional.of(item.getCurrentPrice());
        }

        return Optional.empty();
    }

    private void runInTx(Runnable action) {
        if (txRunner != null) {
            txRunner.run(action);
        } else {
            action.run();
        }
    }

    private double committedAmount(String userId, String excludeItemId) {
        Map<String, Double> commitmentsByItem = new HashMap<>();
        for (Item item : itemRepository.findAll()) {
            if (item.getStatus() == AuctionStatus.RUNNING
                    && userId.equals(item.getCurrentWinnerId())
                    && !item.getId().equals(excludeItemId)) {
                commitmentsByItem.merge(item.getId(), item.getCurrentPrice(), Math::max);
            }
        }

        if (autoBidRepository != null) {
            for (AutoBid autoBid : autoBidRepository.findByUserId(userId)) {
                if (autoBid.getItemId().equals(excludeItemId)) continue;
                itemRepository.findById(autoBid.getItemId())
                        .filter(i -> i.getStatus() == AuctionStatus.RUNNING)
                        .ifPresent(i -> commitmentsByItem.merge(i.getId(), autoBid.getMaxBid(), Math::max));
            }
        }

        return commitmentsByItem.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    private double availableBalance(User user, String excludeItemId) {
        return balanceOf(user) - committedAmount(user.getId(), excludeItemId);
    }

    private double balanceOf(User user) {
        if (user instanceof Bidder b) return b.getBalance();
        if (user instanceof Seller s) return s.getBalance();
        throw new IllegalStateException("User account is invalid for bidding: " + user.getId());
    }

    private void requireAutoBidRepository() {
        if (autoBidRepository == null)
            throw new UnsupportedOperationException("Auto-bidding is not available for this BidService.");
    }
}
