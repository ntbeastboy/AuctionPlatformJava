package com.auction.service;

import com.auction.exception.ProductNotFoundException;
import com.auction.exception.UnauthorizedActionException;
import com.auction.model.Admin;
import com.auction.model.AuctionStatus;
import com.auction.model.Item;
import com.auction.model.Seller;
import com.auction.model.User;
import com.auction.repository.ItemRepository;

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

    public void updateItem(User user, Item item) {
        Item existing = itemRepository.findById(item.getId())
                .orElseThrow(() -> new ProductNotFoundException("Item not found: " + item.getId()));
        requireOwnerOrAdmin(user, existing, "update this item");
        if (existing.getStatus() != AuctionStatus.OPEN)
            throw new IllegalStateException("Only OPEN items can be edited.");
        item.setStatus(existing.getStatus());
        item.setCurrentWinnerId(existing.getCurrentWinnerId());
        item.setVersion(existing.getVersion());
        itemRepository.update(item);
    }

    public void deleteItem(User user, String itemId) {
        Item existing = itemRepository.findById(itemId)
                .orElseThrow(() -> new ProductNotFoundException("Item not found: " + itemId));
        requireOwnerOrAdmin(user, existing, "delete this item");
        if (existing.getStatus() == AuctionStatus.RUNNING)
            throw new IllegalStateException("Cannot delete a RUNNING auction.");
        itemRepository.delete(itemId);
    }

    private void requireOwnerOrAdmin(User user, Item item, String action) {
        boolean isAdmin = user instanceof Admin;
        boolean isOwner = user instanceof Seller && item.getSellerId().equals(user.getId());
        if (!isAdmin && !isOwner)
            throw new UnauthorizedActionException("Only the seller or an admin can " + action + ".");
    }
}
