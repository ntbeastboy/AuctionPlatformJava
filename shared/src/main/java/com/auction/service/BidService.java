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
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

public class BidService {
    private static final long AUTO_BID_COOLDOWN_MS = 5_000L;
    private static final long ANTI_SNIPING_WINDOW_SECONDS = 10L;
    private static final long ANTI_SNIPING_EXTENSION_SECONDS = 60L;
    private static final Comparator<AutoBid> AUTO_BID_PRIORITY =
            Comparator.comparingLong(AutoBid::getNextCheckAt)
                    .thenComparing(Comparator.comparingDouble(AutoBid::getMaxBid).reversed())
                    .thenComparing(AutoBid::getUserId, BidService::compareUserIds);

    private final ItemRepository itemRepository;
    private final BidRepository bidRepository;
    private final UserRepository userRepository;
    private final AutoBidRepository autoBidRepository;
    private final TransactionRunner txRunner;
    private final LongSupplier currentTimeMillis;
    private final ScheduledExecutorService autoBidScheduler;
    private final Map<String, Long> scheduledRetryAtByItem = new ConcurrentHashMap<>();
    private Consumer<String> itemUpdateCallback;

    public BidService(ItemRepository itemRepository, BidRepository bidRepository,
                      UserRepository userRepository, TransactionRunner txRunner) {
        this(itemRepository, bidRepository, userRepository, null, txRunner);
    }

    public BidService(ItemRepository itemRepository, BidRepository bidRepository,
                      UserRepository userRepository, AutoBidRepository autoBidRepository,
                      TransactionRunner txRunner) {
        this(itemRepository, bidRepository, userRepository, autoBidRepository, txRunner, System::currentTimeMillis);
    }

    BidService(ItemRepository itemRepository, BidRepository bidRepository,
               UserRepository userRepository, AutoBidRepository autoBidRepository,
               TransactionRunner txRunner, LongSupplier currentTimeMillis) {
        this.itemRepository = itemRepository;
        this.bidRepository = bidRepository;
        this.userRepository = userRepository;
        this.autoBidRepository = autoBidRepository;
        this.txRunner = txRunner;
        this.currentTimeMillis = currentTimeMillis;
        this.autoBidScheduler = autoBidRepository == null ? null : Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "auto-bid-cooldown");
            t.setDaemon(true);
            return t;
        });
    }

    public BidService(ItemRepository itemRepository, BidRepository bidRepository, UserRepository userRepository) {
        this(itemRepository, bidRepository, userRepository, null, null);
    }

    public void setItemUpdateCallback(Consumer<String> itemUpdateCallback) {
        this.itemUpdateCallback = itemUpdateCallback;
    }

    public void shutdown() {
        if (autoBidScheduler != null) autoBidScheduler.shutdownNow();
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
                applyAntiSnipingExtension(item);

                bid = new Bid(freshUser.getId(), itemId, amount, currentTimeMillis.getAsLong());
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

                Optional<AutoBid> existingAutoBid = autoBidRepository.findByUserAndItem(user.getId(), itemId);
                long createdAt = existingAutoBid
                        .map(AutoBid::getCreatedAt)
                        .orElse(currentTimeMillis.getAsLong());
                long lastBidAt = existingAutoBid
                        .map(AutoBid::getLastBidAt)
                        .orElse(0L);
                long nextCheckAt = existingAutoBid
                        .map(AutoBid::getNextCheckAt)
                        .orElse(createdAt);
                autoBid = new AutoBid(user.getId(), itemId, maxBid, increment, createdAt, lastBidAt, nextCheckAt);
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
        resolveAutoBids(itemId, false);
    }

    private void resolveAutoBids(String itemId, boolean notifyIfChanged) {
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

                AutoBidResolution resolution = resolveAutoBidsLocked(itemId);
                resolution.nextRetryAt().ifPresent(retryAt -> scheduleAutoBidRetry(itemId, retryAt));
                if (notifyIfChanged && resolution.changed()) notifyItemUpdated(itemId);
                return;
            } finally {
                itemLock.unlock();
                for (int i = userLocks.size() - 1; i >= 0; i--) userLocks.get(i).unlock();
            }
        }

        throw new IllegalStateException("Auto-bids changed too quickly. Please try again.");
    }

    private AutoBidResolution resolveAutoBidsLocked(String itemId) {
        int safetyLimit = 100;
        boolean changed = false;

        while (safetyLimit-- > 0) {
            long now = currentTimeMillis.getAsLong();
            Item item = itemRepository.findById(itemId)
                    .orElseThrow(() -> new ProductNotFoundException("Item not found: " + itemId));

            if (item.getStatus() != AuctionStatus.RUNNING) return new AutoBidResolution(changed, OptionalLong.empty());
            if (item.getBidEndTime() != null && LocalDateTime.now().isAfter(item.getBidEndTime()))
                return new AutoBidResolution(changed, OptionalLong.empty());

            List<AutoBid> autoBids = autoBidRepository.findByItemId(itemId);
            AutoBid currentWinnerAutoBid = currentWinnerAutoBid(item, autoBids).orElse(null);
            List<AutoBid> candidates = autoBids.stream()
                    .filter(a -> nextCheckAt(a) <= now)
                    .sorted(AUTO_BID_PRIORITY)
                    .toList();

            if (candidates.isEmpty()) {
                return new AutoBidResolution(changed, nextRetryAt(autoBids, now));
            }

            boolean placed = false;
            boolean advanced = false;
            for (AutoBid autoBid : candidates) {
                long checkAt = nextCheckAt(autoBid);
                long nextCheckAt = nextFutureCheckAt(autoBid, now);

                if (autoBid.getUserId().equals(item.getCurrentWinnerId())
                        || autoBid.getUserId().equals(item.getSellerId())) {
                    List<AutoBid> sameCheckLowerPriority = autoBid.getUserId().equals(item.getCurrentWinnerId())
                            ? lowerPriorityAutoBidsAtSameCheck(autoBids, autoBid, checkAt)
                            : List.of();
                    advanceAutoBidChecks(autoBid, nextCheckAt, sameCheckLowerPriority, now);
                    advanced = true;
                    break;
                }

                Optional<Double> bidAmount = autoBidAmount(autoBid, item, currentWinnerAutoBid);
                if (bidAmount.isEmpty()) {
                    advanceAutoBidChecks(autoBid, nextCheckAt,
                            lowerPriorityAutoBidsAtSameCheck(autoBids, autoBid, checkAt), now);
                    advanced = true;
                    break;
                }

                double amount = bidAmount.orElseThrow();
                User user = userRepository.findById(autoBid.getUserId())
                        .orElseThrow(() -> new UserNotFoundException("User not found: " + autoBid.getUserId()));
                if (availableBalance(user, itemId) < amount) {
                    advanceAutoBidCheck(autoBid, nextCheckAt);
                    advanced = true;
                    continue;
                }

                item.setCurrentPrice(amount);
                item.setCurrentWinnerId(autoBid.getUserId());
                applyAntiSnipingExtension(item);
                Bid bid = new Bid(autoBid.getUserId(), itemId, amount, now);
                List<AutoBid> sameCheckLowerPriority = lowerPriorityAutoBidsAtSameCheck(autoBids, autoBid, checkAt);
                runInTx(() -> {
                    itemRepository.update(item);
                    bidRepository.save(bid);
                    userRepository.save(user);
                    autoBidRepository.recordBid(autoBid.getUserId(), itemId, now, nextCheckAt);
                    for (AutoBid other : sameCheckLowerPriority) {
                        autoBidRepository.recordCheck(other.getUserId(), itemId, nextFutureCheckAt(other, now));
                    }
                });
                placed = true;
                changed = true;
                break;
            }

            if (!placed && !advanced) return new AutoBidResolution(changed, nextRetryAt(autoBids, now));
        }

        throw new IllegalStateException("Auto-bid resolution did not settle.");
    }

    private void advanceAutoBidCheck(AutoBid autoBid, long nextCheckAt) {
        if (autoBid.getNextCheckAt() == nextCheckAt) return;
        runInTx(() -> autoBidRepository.recordCheck(autoBid.getUserId(), autoBid.getItemId(), nextCheckAt));
    }

    private void advanceAutoBidChecks(AutoBid autoBid, long nextCheckAt,
                                      List<AutoBid> sameCheckLowerPriority, long now) {
        if (sameCheckLowerPriority.isEmpty()) {
            advanceAutoBidCheck(autoBid, nextCheckAt);
            return;
        }

        runInTx(() -> {
            autoBidRepository.recordCheck(autoBid.getUserId(), autoBid.getItemId(), nextCheckAt);
            for (AutoBid other : sameCheckLowerPriority) {
                autoBidRepository.recordCheck(other.getUserId(), other.getItemId(), nextFutureCheckAt(other, now));
            }
        });
    }

    private List<AutoBid> lowerPriorityAutoBidsAtSameCheck(List<AutoBid> autoBids, AutoBid winner, long checkAt) {
        return autoBids.stream()
                .filter(a -> !a.getUserId().equals(winner.getUserId()))
                .filter(a -> nextCheckAt(a) == checkAt)
                .filter(a -> AUTO_BID_PRIORITY.compare(winner, a) < 0)
                .toList();
    }

    private OptionalLong nextRetryAt(List<AutoBid> autoBids, long now) {
        OptionalLong nextRetryAt = OptionalLong.empty();
        for (AutoBid autoBid : autoBids) {
            long checkAt = nextCheckAt(autoBid);
            if (checkAt <= now) checkAt = nextFutureCheckAt(autoBid, now);
            nextRetryAt = earliest(nextRetryAt, checkAt);
        }
        return nextRetryAt;
    }

    private long nextCheckAt(AutoBid autoBid) {
        return autoBid.getNextCheckAt() > 0L ? autoBid.getNextCheckAt() : autoBid.getCreatedAt();
    }

    private long nextFutureCheckAt(AutoBid autoBid, long now) {
        long nextCheckAt = nextCheckAt(autoBid);
        if (nextCheckAt > now) return nextCheckAt;
        long checksToSkip = ((now - nextCheckAt) / AUTO_BID_COOLDOWN_MS) + 1;
        return nextCheckAt + checksToSkip * AUTO_BID_COOLDOWN_MS;
    }

    private OptionalLong earliest(OptionalLong current, long value) {
        if (current.isEmpty() || value < current.getAsLong()) return OptionalLong.of(value);
        return current;
    }

    private void scheduleAutoBidRetry(String itemId, long retryAt) {
        if (autoBidScheduler == null) return;
        Long existingRetryAt = scheduledRetryAtByItem.get(itemId);
        if (existingRetryAt != null && existingRetryAt <= retryAt) return;
        scheduledRetryAtByItem.put(itemId, retryAt);

        long delayMs = Math.max(0L, retryAt - currentTimeMillis.getAsLong());
        autoBidScheduler.schedule(() -> {
            if (!scheduledRetryAtByItem.remove(itemId, retryAt)) return;
            try {
                resolveAutoBids(itemId, true);
            } catch (Exception e) {
                System.err.println("Auto-bid retry failed for item " + itemId + ": " + e.getMessage());
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    private void notifyItemUpdated(String itemId) {
        Consumer<String> callback = itemUpdateCallback;
        if (callback != null) callback.accept(itemId);
    }

    private void applyAntiSnipingExtension(Item item) {
        LocalDateTime endTime = item.getBidEndTime();
        if (endTime == null) return;
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(endTime)) return;
        long remainingSeconds = java.time.Duration.between(now, endTime).toSeconds();
        if (remainingSeconds <= ANTI_SNIPING_WINDOW_SECONDS) {
            item.setBidEndTime(endTime.plusSeconds(ANTI_SNIPING_EXTENSION_SECONDS));
        }
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

    private static int compareUserIds(String left, String right) {
        if (left == null && right == null) return 0;
        if (left == null) return -1;
        if (right == null) return 1;
        try {
            return Long.compare(Long.parseLong(left), Long.parseLong(right));
        } catch (NumberFormatException ignored) {
            return left.compareTo(right);
        }
    }

    private record AutoBidResolution(boolean changed, OptionalLong nextRetryAt) {}
}
