package com.auction.repository;

import com.auction.model.User;
import java.util.List;
import java.util.Optional;

public interface UserRepository {
  void save(User user);

  Optional<User> findByUsername(String username);

  boolean existsByUsername(String username);

  Optional<User> findById(String id);

  List<User> findAll();

  void delete(String id);
}
