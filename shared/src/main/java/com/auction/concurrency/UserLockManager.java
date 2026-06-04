package com.auction.concurrency;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Per-user ReentrantLock registry. Used to serialize balance / committed-funds checks for a single
 * user across multiple items so concurrent bids on different items by the same bidder cannot
 * over-commit funds.
 */
public class UserLockManager {

  private static final ConcurrentHashMap<String, ReentrantLock> LOCKS = new ConcurrentHashMap<>();

  public static ReentrantLock getLock(String userId) {
    return LOCKS.computeIfAbsent(userId, k -> new ReentrantLock());
  }
}
