package com.auction.service;

import com.auction.exception.InvalidInputException;
import com.auction.exception.UnauthorizedActionException;
import com.auction.exception.UserNotFoundException;
import com.auction.model.Admin;
import com.auction.model.BannableUser;
import com.auction.model.Bidder;
import com.auction.model.Seller;
import com.auction.model.User;
import com.auction.repository.UserRepository;
import com.auction.security.PasswordUtil;
import java.util.Optional;
import java.util.UUID;

public class UserService {

  public enum RegisterRole {
    BIDDER,
    SELLER
  }

  private final UserRepository userRepository;

  public UserService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  public User register(String username, String password, RegisterRole role) {
    if (username == null || username.isBlank())
      throw new InvalidInputException("Username cannot be empty.");
    if (username.contains(" ")) throw new InvalidInputException("Usernames cannot contain spaces.");
    if (password == null || password.isBlank())
      throw new InvalidInputException("Password cannot be empty.");
    if (password.contains(" ")) throw new InvalidInputException("Password cannot contain spaces.");
    if (userRepository.existsByUsername(username))
      throw new InvalidInputException("Username already taken.");

    String id = UUID.randomUUID().toString();
    String hashedPassword = PasswordUtil.hash(password);
    User user =
        switch (role) {
          case BIDDER -> new Bidder(id, username, hashedPassword);
          case SELLER -> new Seller(id, username, hashedPassword);
        };

    userRepository.save(user);
    return user;
  }

  public User login(String username, String password) {
    User user =
        userRepository
            .findByUsername(username)
            .orElseThrow(() -> new UnauthorizedActionException("Invalid username or password."));

    if (!PasswordUtil.verify(password, user.getPassword()))
      throw new UnauthorizedActionException("Invalid username or password.");
    if (user instanceof BannableUser bu && bu.isBanned())
      throw new UnauthorizedActionException("This account is banned.");

    return user;
  }

  public Optional<User> findById(String bidderId) {
    return userRepository.findById(bidderId);
  }

  public User banUser(String targetUserId, long durationSeconds, User adminUser) {
    return banUser(targetUserId, durationSeconds, false, adminUser);
  }

  public User banUser(
      String targetUserId, long durationSeconds, boolean permanent, User adminUser) {
    requireAdmin(adminUser);
    if (!permanent && durationSeconds <= 0)
      throw new InvalidInputException("Ban duration must be positive.");
    User user =
        userRepository
            .findById(targetUserId)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + targetUserId));
    if (user instanceof Admin)
      throw new UnauthorizedActionException("Admin accounts cannot be banned.");
    if (!(user instanceof BannableUser bu))
      throw new IllegalStateException("This user type cannot be banned.");
    if (permanent) {
      bu.banPermanent();
    } else {
      bu.banTemporary(durationSeconds);
    }
    userRepository.save(user);
    return user;
  }

  public User unbanUser(String targetUserId, User adminUser) {
    requireAdmin(adminUser);
    User user =
        userRepository
            .findById(targetUserId)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + targetUserId));
    if (user instanceof Admin)
      throw new UnauthorizedActionException("Admin accounts cannot be unbanned.");
    if (!(user instanceof BannableUser bu))
      throw new IllegalStateException("This user type cannot be unbanned.");
    bu.unban();
    userRepository.save(user);
    return user;
  }

  public User changeUsername(String targetUserId, String username, User adminUser) {
    requireAdmin(adminUser);
    if (username == null || username.isBlank())
      throw new InvalidInputException("Username cannot be empty.");
    if (username.contains(" ")) throw new InvalidInputException("Usernames cannot contain spaces.");

    User user =
        userRepository
            .findById(targetUserId)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + targetUserId));
    userRepository
        .findByUsername(username)
        .ifPresent(
            existing -> {
              if (!existing.getId().equals(targetUserId))
                throw new InvalidInputException("Username already taken.");
            });

    user.setUsername(username);
    userRepository.save(user);
    return user;
  }

  public User changePassword(String targetUserId, String password, User adminUser) {
    requireAdmin(adminUser);
    if (password == null || password.isBlank())
      throw new InvalidInputException("Password cannot be empty.");
    if (password.contains(" ")) throw new InvalidInputException("Password cannot contain spaces.");

    User user =
        userRepository
            .findById(targetUserId)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + targetUserId));
    user.setPassword(PasswordUtil.hash(password));
    userRepository.save(user);
    return user;
  }

  public void deleteAccount(String targetUserId, User adminUser) {
    requireAdmin(adminUser);
    if (adminUser.getId().equals(targetUserId))
      throw new UnauthorizedActionException("Admins cannot delete their own account.");
    User user =
        userRepository
            .findById(targetUserId)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + targetUserId));
    if (user instanceof Admin)
      throw new UnauthorizedActionException("Admin accounts cannot be deleted here.");
    userRepository.delete(targetUserId);
  }

  private void requireAdmin(User user) {
    if (!(user instanceof Admin))
      throw new UnauthorizedActionException("Only admins can manage users.");
  }
}
