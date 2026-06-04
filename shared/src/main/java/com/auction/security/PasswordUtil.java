package com.auction.security;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public class PasswordUtil {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final int SALT_LENGTH = 16;

  public static String hash(String plaintext) {
    byte[] salt = new byte[SALT_LENGTH];
    RANDOM.nextBytes(salt);
    byte[] hash = sha256(salt, plaintext);
    return Base64.getEncoder().encodeToString(salt)
        + "$"
        + Base64.getEncoder().encodeToString(hash);
  }

  public static boolean verify(String plaintext, String stored) {
    String[] parts = stored.split("\\$", 2);
    if (parts.length != 2) return false;
    byte[] salt = Base64.getDecoder().decode(parts[0]);
    byte[] expectedHash = Base64.getDecoder().decode(parts[1]);
    byte[] actualHash = sha256(salt, plaintext);
    return MessageDigest.isEqual(expectedHash, actualHash);
  }

  private static byte[] sha256(byte[] salt, String plaintext) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      md.update(salt);
      md.update(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      return md.digest();
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("SHA-256 not available", e);
    }
  }
}
