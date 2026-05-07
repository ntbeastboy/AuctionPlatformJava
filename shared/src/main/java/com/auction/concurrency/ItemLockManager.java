package com.auction.concurrency;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class ItemLockManager {

    private static final ConcurrentHashMap<String, ReentrantLock> LOCKS = new ConcurrentHashMap<>();

    public static ReentrantLock getLock(String itemId) {
        return LOCKS.computeIfAbsent(itemId, k -> new ReentrantLock());
    }
}
