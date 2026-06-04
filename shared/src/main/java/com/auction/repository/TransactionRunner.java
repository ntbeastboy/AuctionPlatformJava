package com.auction.repository;

/**
 * Wraps a unit of work in a storage-engine transaction. The server side binds this to {@code
 * DatabaseManager::inTransaction}; in-memory tests (or any non-transactional repo) can pass {@code
 * null} and the service will fall back to running the action directly.
 */
@FunctionalInterface
public interface TransactionRunner {
  void run(Runnable action);
}
