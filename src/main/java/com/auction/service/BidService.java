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
import com.auction.repository.BidRepository;
import com.auction.repository.ItemRepository;
import com.auction.repository.UserRepository;
import com.auction.server.ItemLockManager;

import java.time.LocalDateTime;
import java.util.concurrent.locks.ReentrantLock;

public class BidService {
    private final ItemRepository itemRepository;
    private final BidRepository bidRepository;
    private final UserRepository userRepository;

    public BidService(ItemRepository itemRepository, BidRepository bidRepository, UserRepository userRepository) {
        this.itemRepository = itemRepository;
        this.bidRepository = bidRepository;
        this.userRepository = userRepository;
    }

    public Bid placeBid(User user, String itemId, double amount) {
        if (!(user instanceof Bidder) && !(user instanceof Seller))
            throw new UnauthorizedActionException("Only bidders and sellers can place bids.");

        ReentrantLock lock = ItemLockManager.getLock(itemId);
        lock.lock();
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

            double balance = user instanceof Bidder b ? b.getBalance() : ((Seller) user).getBalance();
            double committed = committedAmount(user.getId(), itemId);
            double available = balance - committed;
            if (available < amount)
                throw new InvalidBidException(
                        "Insufficient available balance: $" + String.format("%.2f", available) +
                                " ($" + String.format("%.2f", balance) + " balance - $" +
                                String.format("%.2f", committed) + " committed to other bids), need $" +
                                String.format("%.2f", amount) + ".");

            item.setCurrentPrice(amount);
            item.setCurrentWinnerId(user.getId());
            itemRepository.update(item);

            Bid bid = new Bid(user.getId(), itemId, amount);
            bidRepository.save(bid);
            userRepository.save(user);

            return bid;
        } finally {
            lock.unlock();
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
