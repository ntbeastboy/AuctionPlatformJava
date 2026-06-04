package com.auction.repository;

import com.auction.model.Item;
import java.util.List;
import java.util.Optional;

public interface ItemRepository {
  void save(Item item);

  Optional<Item> findById(String id);

  void delete(String id);

  List<Item> findAll();

  /** Update an existing item's mutable fields in the store. */
  void update(Item item);
}
