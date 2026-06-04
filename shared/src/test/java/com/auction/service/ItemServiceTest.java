package com.auction.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.auction.exception.UnauthorizedActionException;
import com.auction.model.AuctionStatus;
import com.auction.model.Item;
import com.auction.model.Other;
import com.auction.model.Seller;
import com.auction.repository.ItemRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ItemServiceTest {

  @Test
  void sellerCanUpdateOwnOpenItem() {
    InMemoryItemRepository items = new InMemoryItemRepository();
    ItemService service = new ItemService(items);
    Seller seller = new Seller("seller", "seller", "pw");
    Item original = openItem("item", seller.getId(), "Old");
    items.save(original);

    Item updated = openItem("item", seller.getId(), "New");
    service.updateItem(seller, updated);

    assertEquals("New", items.findById("item").orElseThrow().getName());
  }

  @Test
  void sellerCanDeleteOwnItem() {
    InMemoryItemRepository items = new InMemoryItemRepository();
    ItemService service = new ItemService(items);
    Seller seller = new Seller("seller", "seller", "pw");
    items.save(openItem("item", seller.getId(), "Item"));

    service.deleteItem(seller, "item");

    assertFalse(items.findById("item").isPresent());
  }

  @Test
  void sellerCannotDeleteAnotherSellersItem() {
    InMemoryItemRepository items = new InMemoryItemRepository();
    ItemService service = new ItemService(items);
    Seller owner = new Seller("owner", "owner", "pw");
    Seller other = new Seller("other", "other", "pw");
    items.save(openItem("item", owner.getId(), "Item"));

    assertThrows(UnauthorizedActionException.class, () -> service.deleteItem(other, "item"));
  }

  private Item openItem(String id, String sellerId, String name) {
    Item item =
        new Other(
            id,
            name,
            "Desc",
            100.0,
            5.0,
            LocalDateTime.now(),
            LocalDateTime.now().plusMinutes(10),
            sellerId);
    item.setStatus(AuctionStatus.OPEN);
    return item;
  }

  private static class InMemoryItemRepository implements ItemRepository {
    private final Map<String, Item> items = new HashMap<>();

    @Override
    public void save(Item item) {
      items.put(item.getId(), item);
    }

    @Override
    public Optional<Item> findById(String id) {
      return Optional.ofNullable(items.get(id));
    }

    @Override
    public void delete(String id) {
      items.remove(id);
    }

    @Override
    public List<Item> findAll() {
      return new ArrayList<>(items.values());
    }

    @Override
    public void update(Item item) {
      item.setVersion(item.getVersion() + 1);
      items.put(item.getId(), item);
    }
  }
}
