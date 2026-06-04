package com.auction.exception;

/**
 * Thrown when an optimistic-locking update fails because the row was modified concurrently (version
 * mismatch) or has been deleted.
 */
public class StaleObjectException extends RuntimeException {
  public StaleObjectException(String message) {
    super(message);
  }
}
