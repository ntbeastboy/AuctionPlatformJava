package com.auction.service;

import com.auction.exception.AuctionClosedException;
import com.auction.exception.InvalidBidException;
import com.auction.exception.ProductNotFoundException;
import com.auction.exception.UnauthorizedActionException;
import com.auction.model.Admin;
import com.auction.model.AuctionStatus;
import com.auction.model.Bid;
import com.auction.model.Bidder;
import com.auction.model.Item;
import com.auction.model.Seller;
import com.auction.model.User;
import com.auction.repository.ItemRepository;

import java.util.UUID;

public class ItemService {
    private final ItemRepository itemRepository;

    public ItemService(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    public void createItem(User user, Item item) {
        if (!(user instanceof Seller))
            throw new UnauthorizedActionException("Only sellers can create items.");
        itemRepository.save(item);
    }

    public Bid placeBid(User user, String itemId, double amount) {
        if (!(user instanceof Bidder) && !(user instanceof Seller))
            throw new UnauthorizedActionException("Only bidders and sellers can place bids.");

        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ProductNotFoundException("Item not found: " + itemId));

        if (user instanceof Seller && item.getSellerId().equals(user.getId()))
            throw new UnauthorizedActionException("Sellers cannot bid on their own items.");

        if (item.getStatus() == AuctionStatus.ENDED)
            throw new AuctionClosedException("This auction has already ended.");

        if (amount <= item.getCurrentPrice())
            throw new InvalidBidException("Bid must be higher than the current price of " + item.getCurrentPrice() + ".");

        item.setCurrentPrice(amount);
        return new Bid(user.getId(), itemId, amount);
    }

    public void deleteItem(User user, String itemId) {
        if (!(user instanceof Admin))
            throw new UnauthorizedActionException("Only admins can delete items.");

        if (!itemRepository.findById(itemId).isPresent())
            throw new ProductNotFoundException("Item not found: " + itemId);

        itemRepository.delete(itemId);
    }

    public void endAuctionEarly(User user, String itemId) {
        if (!(user instanceof Admin))
            throw new UnauthorizedActionException("Only admins can end auctions early.");

        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ProductNotFoundException("Item not found: " + itemId));

        if (item.getStatus() == AuctionStatus.ENDED)
            throw new AuctionClosedException("Auction is already ended.");

        item.setStatus(AuctionStatus.ENDED);
    }
}
