package AuctionPlatformJava.src.main.java.com.auction.service;

import AuctionPlatformJava.src.main.java.com.auction.exception.AuctionClosedException;
import AuctionPlatformJava.src.main.java.com.auction.exception.InvalidBidException;
import AuctionPlatformJava.src.main.java.com.auction.exception.ProductNotFoundException;
import AuctionPlatformJava.src.main.java.com.auction.exception.UnauthorizedActionException;
import AuctionPlatformJava.src.main.java.com.auction.model.Admin;
import AuctionPlatformJava.src.main.java.com.auction.model.AuctionStatus;
import AuctionPlatformJava.src.main.java.com.auction.model.Bid;
import AuctionPlatformJava.src.main.java.com.auction.model.Bidder;
import AuctionPlatformJava.src.main.java.com.auction.model.Item;
import AuctionPlatformJava.src.main.java.com.auction.model.Seller;
import AuctionPlatformJava.src.main.java.com.auction.model.User;
import AuctionPlatformJava.src.main.java.com.auction.repository.ItemRepository;

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

        if (amount <= 0)
            throw new InvalidBidException("Bid amount must be positive.");

        double minimumBid = item.getCurrentPrice() + item.getPriceStep();
        if (amount < minimumBid)
            throw new InvalidBidException(
                "Bid must be at least " + minimumBid + " (current price " + item.getCurrentPrice() + " + step " + item.getPriceStep() + ")."
            );

        item.setCurrentPrice(amount);
        item.setCurrentWinnerId(user.getId());
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
