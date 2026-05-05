package com.auction.service;

import com.auction.exception.ProductNotFoundException;
import com.auction.exception.UnauthorizedActionException;
import com.auction.model.Admin;
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

    public void deleteItem(User user, String itemId) {
        if (!(user instanceof Admin))
            throw new UnauthorizedActionException("Only admins can delete items.");

        if (!itemRepository.findById(itemId).isPresent())
            throw new ProductNotFoundException("Item not found: " + itemId);

        itemRepository.delete(itemId);
    }
}