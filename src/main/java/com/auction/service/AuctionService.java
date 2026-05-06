package com.auction.service;

import com.auction.exception.InvalidBidException;
import com.auction.exception.ProductNotFoundException;
import com.auction.exception.UnauthorizedActionException;
import com.auction.model.Admin;
import com.auction.model.AuctionStatus;
import com.auction.model.Bidder;
import com.auction.model.Item;
import com.auction.model.Seller;
import com.auction.model.User;
import com.auction.repository.ItemRepository;
import com.auction.repository.UserRepository;
import com.auction.server.ItemLockManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class AuctionService {

    private final ItemRepository itemRepository;
    private final UserRepository userRepository;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private Runnable statusChangeCallback;

    public void setStatusChangeCallback(Runnable callback) {
        this.statusChangeCallback = callback;
    }

    public AuctionService(ItemRepository itemRepository, UserRepository userRepository) {
        this.itemRepository = itemRepository;
        this.userRepository = userRepository;
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
            itemRepository.update(item);
            if (delayMs <= 0) {
                closeAuction(itemId);
            } else {
                scheduler.schedule(() -> closeAuction(itemId), delayMs, TimeUnit.MILLISECONDS);
            }
        } finally {
            lock.unlock();
        }
    }

    public void closeAuction(String itemId) {
        ReentrantLock lock = ItemLockManager.getLock(itemId);
        lock.lock();
        try {
            Item item = getItem(itemId);

            if (item.getStatus() != AuctionStatus.RUNNING)
                return;

            if (item.getCurrentWinnerId() == null) {
                item.setStatus(AuctionStatus.CANCELED);
            } else {
                item.setStatus(AuctionStatus.FINISHED);
            }

            itemRepository.update(item);
            if (statusChangeCallback != null) statusChangeCallback.run();
        } finally {
            lock.unlock();
        }
    }

    public void markPaid(String itemId, User requestingUser) {
        ReentrantLock lock = ItemLockManager.getLock(itemId);
        lock.lock();
        try {
            Item item = getItem(itemId);

            if (item.getStatus() != AuctionStatus.FINISHED)
                throw new IllegalStateException(
                        "Auction must be FINISHED before marking PAID. Current status: " + item.getStatus());

            String winnerId = item.getCurrentWinnerId();
            if (winnerId == null)
                throw new IllegalStateException(
                        "No bids were placed on item " + itemId + " — cannot mark as PAID.");

            boolean isAdmin = requestingUser instanceof Admin;
            boolean isWinner = winnerId.equals(requestingUser.getId());
            if (!isAdmin && !isWinner)
                throw new UnauthorizedActionException("Only the winner can confirm payment.");

            User winnerUser = userRepository.findById(winnerId)
                    .orElseThrow(() -> new ProductNotFoundException("Winner not found: " + winnerId));

            double winnerBalance;
            if (winnerUser instanceof Bidder bidderWinner) {
                winnerBalance = bidderWinner.getBalance();
            } else if (winnerUser instanceof Seller sellerWinner) {
                winnerBalance = sellerWinner.getBalance();
            } else {
                throw new IllegalStateException("Winner account is invalid for payment: " + winnerId);
            }

            User sellerUser = userRepository.findById(item.getSellerId())
                    .orElseThrow(() -> new ProductNotFoundException("Seller not found: " + item.getSellerId()));
            if (!(sellerUser instanceof Seller seller))
                throw new IllegalStateException("Seller account is invalid for payout: " + item.getSellerId());

            double winningPrice = item.getCurrentPrice();
            if (winnerBalance < winningPrice) {
                item.setStatus(AuctionStatus.CANCELED);
                itemRepository.update(item);
                throw new InvalidBidException(
                        "Winner has insufficient balance (" + winnerBalance +
                                ") to pay " + winningPrice + ". Auction canceled.");
            }

            if (winnerUser instanceof Bidder bidderWinner) bidderWinner.deductFunds(winningPrice);
            else ((Seller) winnerUser).withdraw(winningPrice);
            seller.addFunds(winningPrice);
            item.setStatus(AuctionStatus.PAID);
            itemRepository.update(item);
            userRepository.save(winnerUser);
            userRepository.save(sellerUser);
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

            if (current == AuctionStatus.PAID)
                throw new IllegalStateException("Cannot cancel a PAID auction.");
            if (current == AuctionStatus.CANCELED)
                throw new IllegalStateException("Auction is already CANCELED.");

            item.setStatus(AuctionStatus.CANCELED);
            itemRepository.update(item);
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
