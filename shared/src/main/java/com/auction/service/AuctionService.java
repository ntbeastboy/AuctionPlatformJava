package com.auction.service;

import com.auction.concurrency.ItemLockManager;
import com.auction.exception.ProductNotFoundException;
import com.auction.exception.UnauthorizedActionException;
import com.auction.model.Admin;
import com.auction.model.AuctionStatus;
import com.auction.model.BannableUser;
import com.auction.model.Bidder;
import com.auction.model.Item;
import com.auction.model.Seller;
import com.auction.model.User;
import com.auction.repository.ItemRepository;
import com.auction.repository.TransactionRunner;
import com.auction.repository.UserRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class AuctionService {

  private static final Duration PAYMENT_WINDOW = Duration.ofHours(8);
  private static final long NON_PAYMENT_BAN_SECONDS = Duration.ofDays(7).toSeconds();

  private final ItemRepository itemRepository;
  private final UserRepository userRepository;
  private final TransactionRunner txRunner;
  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
  private Runnable statusChangeCallback;
  private Consumer<String> itemStatusChangeCallback;

  public void setStatusChangeCallback(Runnable callback) {
    this.statusChangeCallback = callback;
  }

  public void setItemStatusChangeCallback(Consumer<String> callback) {
    this.itemStatusChangeCallback = callback;
  }

  public AuctionService(
      ItemRepository itemRepository, UserRepository userRepository, TransactionRunner txRunner) {
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
   * Recover from server restarts: RUNNING auctions are closed or rescheduled, and FINISHED auctions
   * are expired or rescheduled for their payment window.
   */
  public void recoverScheduledAuctions() {
    LocalDateTime now = LocalDateTime.now();
    for (Item item : itemRepository.findAll()) {
      if (item.getStatus() == AuctionStatus.RUNNING) {
        if (item.getBidEndTime() == null || !now.isBefore(item.getBidEndTime())) {
          closeAuction(item.getId());
        } else {
          scheduleClose(item.getId(), Duration.between(now, item.getBidEndTime()));
        }
      } else if (item.getStatus() == AuctionStatus.FINISHED) {
        scheduleOrExpireFinishedAuction(item);
      }
    }
  }

  public void startAuction(String itemId, User requestingUser) {
    boolean isAdmin = requestingUser instanceof Admin;

    ReentrantLock lock = ItemLockManager.getLock(itemId);
    lock.lock();
    try {
      Item item = getItem(itemId);

      boolean isOwner =
          requestingUser instanceof Seller && item.getSellerId().equals(requestingUser.getId());
      if (!isAdmin && !isOwner)
        throw new UnauthorizedActionException(
            "Only the item's seller or an admin can start this auction.");

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
        scheduleClose(itemId, Duration.ofMillis(delayMs));
        notifyStatusChange(itemId);
      }
    } finally {
      lock.unlock();
    }
  }

  /**
   * Close bidding. Auctions with a winner move to FINISHED and wait for the winner to pay the
   * seller; auctions without a winner are canceled.
   */
  public void closeAuction(String itemId) {
    closeAuction(itemId, false);
  }

  private void closeAuction(String itemId, boolean force) {
    ReentrantLock lock = ItemLockManager.getLock(itemId);
    lock.lock();
    try {
      Item item = getItem(itemId);

      if (item.getStatus() != AuctionStatus.RUNNING) return;

      LocalDateTime now = LocalDateTime.now();
      if (!force && item.getBidEndTime() != null && now.isBefore(item.getBidEndTime())) {
        long delayMs = Duration.between(now, item.getBidEndTime()).toMillis();
        scheduleClose(itemId, Duration.ofMillis(delayMs));
        return;
      }
      if (force || item.getBidEndTime() == null) {
        item.setBidEndTime(now);
      }

      String winnerId = item.getCurrentWinnerId();
      if (winnerId == null) {
        item.setStatus(AuctionStatus.CANCELED);
        runInTx(() -> itemRepository.update(item));
        notifyStatusChange(itemId);
        return;
      }

      userRepository
          .findById(winnerId)
          .orElseThrow(() -> new ProductNotFoundException("Winner not found: " + winnerId));
      userRepository
          .findById(item.getSellerId())
          .orElseThrow(
              () -> new ProductNotFoundException("Seller not found: " + item.getSellerId()));

      item.setStatus(AuctionStatus.FINISHED);
      runInTx(() -> itemRepository.update(item));
      schedulePaymentExpiry(item.getId(), paymentDeadline(item));
      notifyStatusChange(itemId);
    } finally {
      lock.unlock();
    }
  }

  public void paySeller(String itemId, User requestingUser) {
    ReentrantLock lock = ItemLockManager.getLock(itemId);
    lock.lock();
    try {
      Item item = getItem(itemId);

      if (item.getStatus() != AuctionStatus.FINISHED)
        throw new IllegalStateException("Auction must be FINISHED before payment.");

      String winnerId = item.getCurrentWinnerId();
      if (winnerId == null)
        throw new IllegalStateException("Cannot pay seller because this auction has no winner.");
      if (!winnerId.equals(requestingUser.getId()))
        throw new UnauthorizedActionException("Only the winning bidder can pay the seller.");

      LocalDateTime deadline = paymentDeadline(item);
      if (!LocalDateTime.now().isBefore(deadline)) {
        expireFinishedAuction(itemId);
        throw new IllegalStateException("Payment window has expired.");
      }

      User winnerUser =
          userRepository
              .findById(winnerId)
              .orElseThrow(() -> new ProductNotFoundException("Winner not found: " + winnerId));
      User sellerUser =
          userRepository
              .findById(item.getSellerId())
              .orElseThrow(
                  () -> new ProductNotFoundException("Seller not found: " + item.getSellerId()));
      if (!(sellerUser instanceof Seller seller))
        throw new IllegalStateException(
            "Seller account is invalid for payout: " + item.getSellerId());

      double winnerBalance;
      if (winnerUser instanceof Bidder b) winnerBalance = b.getBalance();
      else if (winnerUser instanceof Seller s) winnerBalance = s.getBalance();
      else throw new IllegalStateException("Winner account is invalid for payment: " + winnerId);

      double price = item.getCurrentPrice();
      if (winnerBalance < price)
        throw new IllegalStateException("Insufficient balance to pay seller.");

      if (winnerUser instanceof Bidder b) b.deductFunds(price);
      else ((Seller) winnerUser).withdraw(price);
      seller.addFunds(price);
      item.setStatus(AuctionStatus.PAID);

      // Atomic: status flip + winner debit + seller credit commit together.
      final User w = winnerUser;
      runInTx(
          () -> {
            itemRepository.update(item);
            userRepository.save(w);
            userRepository.save(sellerUser);
          });
      notifyStatusChange(itemId);
    } finally {
      lock.unlock();
    }
  }

  public void endAuctionEarly(String itemId, User requestingUser) {
    if (!(requestingUser instanceof Admin))
      throw new UnauthorizedActionException("Only admins can end auctions early.");
    closeAuction(itemId, true);
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
        throw new IllegalStateException("Cannot cancel a settled auction.");
      if (current == AuctionStatus.CANCELED)
        throw new IllegalStateException("Auction is already CANCELED.");

      item.setStatus(AuctionStatus.CANCELED);
      runInTx(() -> itemRepository.update(item));
      notifyStatusChange(itemId);
    } finally {
      lock.unlock();
    }
  }

  public void shutdown() {
    scheduler.shutdownNow();
  }

  private Item getItem(String itemId) {
    return itemRepository
        .findById(itemId)
        .orElseThrow(() -> new ProductNotFoundException("Item not found: " + itemId));
  }

  private void scheduleClose(String itemId, Duration delay) {
    scheduler.schedule(
        () -> closeAuction(itemId), Math.max(0, delay.toMillis()), TimeUnit.MILLISECONDS);
  }

  private void scheduleOrExpireFinishedAuction(Item item) {
    LocalDateTime deadline = paymentDeadline(item);
    if (!LocalDateTime.now().isBefore(deadline)) {
      expireFinishedAuction(item.getId());
    } else {
      schedulePaymentExpiry(item.getId(), deadline);
    }
  }

  private void schedulePaymentExpiry(String itemId, LocalDateTime deadline) {
    long delayMs = Math.max(0, Duration.between(LocalDateTime.now(), deadline).toMillis());
    scheduler.schedule(() -> expireFinishedAuction(itemId), delayMs, TimeUnit.MILLISECONDS);
  }

  private LocalDateTime paymentDeadline(Item item) {
    LocalDateTime finishedAt =
        item.getBidEndTime() != null ? item.getBidEndTime() : LocalDateTime.now();
    return finishedAt.plus(PAYMENT_WINDOW);
  }

  private void expireFinishedAuction(String itemId) {
    ReentrantLock lock = ItemLockManager.getLock(itemId);
    lock.lock();
    try {
      Item item = getItem(itemId);
      if (item.getStatus() != AuctionStatus.FINISHED) return;
      if (LocalDateTime.now().isBefore(paymentDeadline(item))) {
        schedulePaymentExpiry(itemId, paymentDeadline(item));
        return;
      }

      User winnerUser = null;
      if (item.getCurrentWinnerId() != null) {
        winnerUser = userRepository.findById(item.getCurrentWinnerId()).orElse(null);
        if (winnerUser instanceof BannableUser bu) {
          bu.banTemporary(NON_PAYMENT_BAN_SECONDS);
        }
      }

      item.setStatus(AuctionStatus.CANCELED);
      final User bannedWinner = winnerUser;
      runInTx(
          () -> {
            itemRepository.update(item);
            if (bannedWinner != null) userRepository.save(bannedWinner);
          });
      notifyStatusChange(itemId);
    } finally {
      lock.unlock();
    }
  }

  private void notifyStatusChange(String itemId) {
    if (statusChangeCallback != null) statusChangeCallback.run();
    if (itemStatusChangeCallback != null && itemId != null && !itemId.isBlank()) {
      itemStatusChangeCallback.accept(itemId);
    }
  }
}
