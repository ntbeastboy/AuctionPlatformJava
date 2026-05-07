package com.auction.service;

import com.auction.exception.AuctionClosedException;
import com.auction.exception.InvalidBidException;
import com.auction.exception.ProductNotFoundException;
import com.auction.exception.UnauthorizedActionException;
import com.auction.model.AuctionStatus;
import com.auction.model.Bid;
import com.auction.model.Bidder;
import com.auction.model.Item;
import com.auction.model.Seller;
import com.auction.model.User;
import com.auction.exception.UserNotFoundException;
import com.auction.repository.BidRepository;
import com.auction.repository.ItemRepository;
import com.auction.repository.TransactionRunner;
import com.auction.repository.UserRepository;
import com.auction.concurrency.ItemLockManager;
import com.auction.concurrency.UserLockManager;

import java.time.LocalDateTime;
import java.util.concurrent.locks.ReentrantLock;

public class BidService {
    private final ItemRepository itemRepository;
    private final BidRepository bidRepository;
    private final UserRepository userRepository;
    private final TransactionRunner txRunner;

    public BidService(ItemRepository itemRepository, BidRepository bidRepository,
                      UserRepository userRepository, TransactionRunner txRunner) {
        this.itemRepository = itemRepository;
        this.bidRepository = bidRepository;
        this.userRepository = userRepository;
        this.txRunner = txRunner;
    }

    /** Backwards-compatible constructor for in-memory tests / non-SQLite repos. */
    public BidService(ItemRepository itemRepository, BidRepository bidRepository, UserRepository userRepository) {
        this(itemRepository, bidRepository, userRepository, null);
    }

    public Bid placeBid(User user, String itemId, double amount) {
        if (!(user instanceof Bidder) && !(user instanceof Seller))
            throw new UnauthorizedActionException("Only bidders and sellers can place bids.");

        // Acquire user lock first (always in <userId, itemId> order to avoid
        // deadlocks: every other lock site that grabs both must follow the
        // same ordering — currently only placeBid takes the user lock).
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

                if (amount <= 0)
                    throw new InvalidBidException("Bid amount must be positive.");

                double minimumBid = item.getCurrentPrice() + item.getPriceStep();
                if (amount < minimumBid)
                    throw new InvalidBidException(
                            "Bid must be at least " + minimumBid + " (current price " + item.getCurrentPrice() + " + step " + item.getPriceStep() + ").");

                // Re-fetch the user inside the lock so we observe the latest
                // balance (e.g. after a concurrent settlement).
                User freshUser = userRepository.findById(user.getId())
                        .orElseThrow(() -> new UserNotFoundException("User not found: " + user.getId()));

                double balance = freshUser instanceof Bidder b ? b.getBalance() : ((Seller) freshUser).getBalance();
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

                Bid bid = new Bid(freshUser.getId(), itemId, amount);
                // All three writes commit atomically. itemRepository.update
                // also enforces optimistic concurrency via the version column.
                runInTx(() -> {
                    itemRepository.update(item);
                    bidRepository.save(bid);
                    userRepository.save(freshUser);
                });

                return bid;
            } finally {
                itemLock.unlock();
            }
        } finally {
            userLock.unlock();
        }
    }

    private void runInTx(Runnable action) {
        if (txRunner != null) {
            txRunner.run(action);
        } else {
            action.run();
        }
    }

    private double committedAmount(String userId, String excludeItemId) {
        return itemRepository.findAll().stream()
                .filter(i -> i.getStatus() == AuctionStatus.RUNNING
                        && userId.equals(i.getCurrentWinnerId())
                        && !i.getId().equals(excludeItemId))
                .mapToDouble(Item::getCurrentPrice)
                .sum();
    }
}
