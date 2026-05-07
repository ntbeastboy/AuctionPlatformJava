package com.auction.service;

import com.auction.exception.ProductNotFoundException;
import com.auction.exception.UnauthorizedActionException;
import com.auction.model.Admin;
import com.auction.model.AuctionStatus;
import com.auction.model.Bidder;
import com.auction.model.Item;
import com.auction.model.Seller;
import com.auction.model.User;
import com.auction.repository.ItemRepository;
import com.auction.repository.TransactionRunner;
import com.auction.repository.UserRepository;
import com.auction.concurrency.ItemLockManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class AuctionService {

    private final ItemRepository itemRepository;
    private final UserRepository userRepository;
    private final TransactionRunner txRunner;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private Runnable statusChangeCallback;

    public void setStatusChangeCallback(Runnable callback) {
        this.statusChangeCallback = callback;
    }

    public AuctionService(ItemRepository itemRepository, UserRepository userRepository,
                          TransactionRunner txRunner) {
        this.itemRepository = itemRepository;
        this.userRepository = userRepository;
        this.txRunner = txRunner;
    }

    /** Backwards-compatible constructor for in-memory tests / non-SQLite repos. */
    public AuctionService(ItemRepository itemRepository, UserRepository userRepository) {
        this(itemRepository, userRepository, null);
    }

    private void runInTx(Runnable action) {
        if (txRunner != null) {
            txRunner.run(action);
        } else {
            action.run();
        }
    }

    /**
     * Recover from server restarts: any auction left in RUNNING status whose
     * end-time has already passed is closed immediately; any still-active
     * RUNNING auction has its close timer rescheduled.
     */
    public void recoverScheduledAuctions() {
        LocalDateTime now = LocalDateTime.now();
        for (Item item : itemRepository.findAll()) {
            if (item.getStatus() != AuctionStatus.RUNNING) continue;
            if (item.getBidEndTime() == null || !now.isBefore(item.getBidEndTime())) {
                // Already past the end time -> close now.
                closeAuction(item.getId());
            } else {
                long delayMs = Duration.between(now, item.getBidEndTime()).toMillis();
                String itemId = item.getId();
                scheduler.schedule(() -> closeAuction(itemId), delayMs, TimeUnit.MILLISECONDS);
            }
        }
    }

    public void startAuction(String itemId, User requestingUser) {
        boolean isAdmin = requestingUser instanceof Admin;

        ReentrantLock lock = ItemLockManager.getLock(itemId);
        lock.lock();
        try {
            Item item = getItem(itemId);

            boolean isOwner = requestingUser instanceof Seller && item.getSellerId().equals(requestingUser.getId());
            if (!isAdmin && !isOwner)
                throw new UnauthorizedActionException("Only the item's seller or an admin can start this auction.");

            if (item.getStatus() != AuctionStatus.OPEN)
                throw new IllegalStateException(
                        "Auction must be OPEN to start. Current status: " + item.getStatus());

            item.setStatus(AuctionStatus.RUNNING);

            Duration dur = Duration.between(item.getBidStartTime(), item.getBidEndTime());
            LocalDateTime now = LocalDateTime.now();
            item.setBidStartTime(now);
            item.setBidEndTime(now.plus(dur));

            long delayMs = dur.toMillis();
            runInTx(() -> itemRepository.update(item));
            if (delayMs <= 0) {
                closeAuction(itemId);
            } else {
                scheduler.schedule(() -> closeAuction(itemId), delayMs, TimeUnit.MILLISECONDS);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Settle the auction: if there's a winner with sufficient funds, debit
     * the winner, credit the seller, and mark FINISHED. If there are no
     * bids or the winner can't afford the price, mark CANCELED. Either way
     * the work commits atomically with the status flip.
     */
    public void closeAuction(String itemId) {
        ReentrantLock lock = ItemLockManager.getLock(itemId);
        lock.lock();
        try {
            Item item = getItem(itemId);

            if (item.getStatus() != AuctionStatus.RUNNING)
                return;

            String winnerId = item.getCurrentWinnerId();
            if (winnerId == null) {
                item.setStatus(AuctionStatus.CANCELED);
                runInTx(() -> itemRepository.update(item));
                if (statusChangeCallback != null) statusChangeCallback.run();
                return;
            }

            User winnerUser = userRepository.findById(winnerId)
                    .orElseThrow(() -> new ProductNotFoundException("Winner not found: " + winnerId));
            User sellerUser = userRepository.findById(item.getSellerId())
                    .orElseThrow(() -> new ProductNotFoundException("Seller not found: " + item.getSellerId()));
            if (!(sellerUser instanceof Seller seller))
                throw new IllegalStateException("Seller account is invalid for payout: " + item.getSellerId());

            double winnerBalance;
            if (winnerUser instanceof Bidder b) winnerBalance = b.getBalance();
            else if (winnerUser instanceof Seller s) winnerBalance = s.getBalance();
            else throw new IllegalStateException("Winner account is invalid for payment: " + winnerId);

            double price = item.getCurrentPrice();
            if (winnerBalance < price) {
                // Winner can't afford it (e.g. they withdrew before settlement).
                // Cancel the auction so the seller doesn't get a phantom payout.
                item.setStatus(AuctionStatus.CANCELED);
                runInTx(() -> itemRepository.update(item));
                if (statusChangeCallback != null) statusChangeCallback.run();
                return;
            }

            if (winnerUser instanceof Bidder b) b.deductFunds(price);
            else ((Seller) winnerUser).withdraw(price);
            seller.addFunds(price);
            item.setStatus(AuctionStatus.FINISHED);

            // Atomic: status flip + winner debit + seller credit commit together.
            final User w = winnerUser;
            runInTx(() -> {
                itemRepository.update(item);
                userRepository.save(w);
                userRepository.save(sellerUser);
            });
            if (statusChangeCallback != null) statusChangeCallback.run();
        } finally {
            lock.unlock();
        }
    }

    public void endAuctionEarly(String itemId, User requestingUser) {
        if (!(requestingUser instanceof Admin))
            throw new UnauthorizedActionException("Only admins can end auctions early.");
        closeAuction(itemId);
    }

    public void cancelAuction(String itemId, User requestingUser) {
        if (!(requestingUser instanceof Admin))
            throw new UnauthorizedActionException("Only admins can cancel auctions.");

        ReentrantLock lock = ItemLockManager.getLock(itemId);
        lock.lock();
        try {
            Item item = getItem(itemId);
            AuctionStatus current = item.getStatus();

            if (current == AuctionStatus.FINISHED)
                throw new IllegalStateException("Cannot cancel a FINISHED auction.");
            if (current == AuctionStatus.CANCELED)
                throw new IllegalStateException("Auction is already CANCELED.");

            item.setStatus(AuctionStatus.CANCELED);
            runInTx(() -> itemRepository.update(item));
        } finally {
            lock.unlock();
        }
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }

    private Item getItem(String itemId) {
        return itemRepository.findById(itemId)
                .orElseThrow(() -> new ProductNotFoundException("Item not found: " + itemId));
    }
}
