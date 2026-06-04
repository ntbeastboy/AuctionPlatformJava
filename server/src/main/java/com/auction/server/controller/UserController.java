package com.auction.server.controller;

import com.auction.exception.UnauthorizedActionException;
import com.auction.exception.UserNotFoundException;
import com.auction.model.Admin;
import com.auction.model.AuctionStatus;
import com.auction.model.AutoBid;
import com.auction.model.BannableUser;
import com.auction.model.Bid;
import com.auction.model.Bidder;
import com.auction.model.Item;
import com.auction.model.Seller;
import com.auction.model.User;
import com.auction.repository.AutoBidRepository;
import com.auction.repository.BidRepository;
import com.auction.repository.ItemRepository;
import com.auction.repository.UserRepository;
import com.auction.security.JwtUtil;
import com.auction.server.events.ItemEventBroadcaster;
import com.auction.server.events.UserBanExpiryScheduler;
import com.auction.service.UserService;
import io.javalin.http.Context;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserController {

  private final UserRepository userRepo;
  private final ItemRepository itemRepo;
  private final AutoBidRepository autoBidRepo;
  private final BidRepository bidRepo;
  private final UserService userService;
  private final ItemEventBroadcaster eventBroadcaster;
  private final UserBanExpiryScheduler banExpiryScheduler;

  public UserController(UserRepository userRepo, UserService userService) {
    this(userRepo, null, null, null, userService, null, null);
  }

  public UserController(
      UserRepository userRepo,
      ItemRepository itemRepo,
      AutoBidRepository autoBidRepo,
      UserService userService) {
    this(userRepo, itemRepo, autoBidRepo, null, userService, null, null);
  }

  public UserController(
      UserRepository userRepo,
      ItemRepository itemRepo,
      AutoBidRepository autoBidRepo,
      UserService userService,
      ItemEventBroadcaster eventBroadcaster) {
    this(userRepo, itemRepo, autoBidRepo, null, userService, eventBroadcaster, null);
  }

  public UserController(
      UserRepository userRepo,
      ItemRepository itemRepo,
      AutoBidRepository autoBidRepo,
      UserService userService,
      ItemEventBroadcaster eventBroadcaster,
      UserBanExpiryScheduler banExpiryScheduler) {
    this(userRepo, itemRepo, autoBidRepo, null, userService, eventBroadcaster, banExpiryScheduler);
  }

  public UserController(
      UserRepository userRepo,
      ItemRepository itemRepo,
      AutoBidRepository autoBidRepo,
      BidRepository bidRepo,
      UserService userService,
      ItemEventBroadcaster eventBroadcaster,
      UserBanExpiryScheduler banExpiryScheduler) {
    this.userRepo = userRepo;
    this.itemRepo = itemRepo;
    this.autoBidRepo = autoBidRepo;
    this.bidRepo = bidRepo;
    this.userService = userService;
    this.eventBroadcaster = eventBroadcaster;
    this.banExpiryScheduler = banExpiryScheduler;
  }

  public void handleLogin(Context ctx) {
    @SuppressWarnings("unchecked")
    Map<String, String> body = ctx.bodyAsClass(Map.class);
    User user = userService.login(body.get("username"), body.get("password"));
    String token = JwtUtil.generateToken(user.getId(), roleOf(user));
    ctx.json(userToMapWithToken(user, token));
  }

  public void handleRegister(Context ctx) {
    @SuppressWarnings("unchecked")
    Map<String, String> body = ctx.bodyAsClass(Map.class);
    String roleStr = body.getOrDefault("role", "BIDDER").toUpperCase();
    UserService.RegisterRole role = UserService.RegisterRole.valueOf(roleStr);
    User user = userService.register(body.get("username"), body.get("password"), role);
    String token = JwtUtil.generateToken(user.getId(), roleOf(user));
    broadcastUsersChanged();
    ctx.status(201).json(userToMapWithToken(user, token));
  }

  public void handleGetUser(Context ctx) {
    String id = ctx.pathParam("id");
    User user =
        userRepo.findById(id).orElseThrow(() -> new UserNotFoundException("User not found: " + id));
    ctx.json(userToMap(user));
  }

  public void handleGetAllUsers(Context ctx) {
    requireAdmin(ctx);
    ctx.json(userRepo.findAll().stream().map(this::userToMap).toList());
  }

  public void handleGetUserByUsername(Context ctx) {
    String username = ctx.pathParam("username");
    User user =
        userRepo
            .findByUsername(username)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + username));
    ctx.json(userToMap(user));
  }

  public void handleAddBalance(Context ctx) {
    String id = ctx.pathParam("id");
    requireSelfOrAdmin(ctx, id, "add balance to this account");
    double amount = Double.parseDouble(ctx.queryParam("amount"));
    User user =
        userRepo.findById(id).orElseThrow(() -> new UserNotFoundException("User not found: " + id));
    if (user instanceof Bidder b) b.addFunds(amount);
    else if (user instanceof Seller s) s.addFunds(amount);
    else throw new IllegalStateException("Cannot add balance to this user type.");
    userRepo.save(user);
    broadcastUserUpdated(id);
    ctx.json(userToMap(user));
  }

  public void handleDeductBalance(Context ctx) {
    String id = ctx.pathParam("id");
    requireSelfOrAdmin(ctx, id, "deduct balance from this account");
    double amount = Double.parseDouble(ctx.queryParam("amount"));
    User user =
        userRepo.findById(id).orElseThrow(() -> new UserNotFoundException("User not found: " + id));
    double remainingBalance = balanceOf(user) - amount;
    double committed = committedAmount(id);
    if (remainingBalance < committed)
      throw new IllegalStateException(
          "Cannot withdraw below active bid and auto-bid commitments ($"
              + String.format("%.2f", committed)
              + " committed).");
    if (user instanceof Bidder b) b.withdraw(amount);
    else if (user instanceof Seller s) s.withdraw(amount);
    else throw new IllegalStateException("Cannot deduct balance from this user type.");
    userRepo.save(user);
    broadcastUserUpdated(id);
    ctx.json(userToMap(user));
  }

  public void handleBanUser(Context ctx) {
    String id = ctx.pathParam("id");
    String durationParam = ctx.queryParam("durationSeconds");
    String permanentParam = ctx.queryParam("permanent");
    boolean permanent = "true".equalsIgnoreCase(permanentParam);
    long durationSeconds = permanent ? 0 : Long.parseLong(durationParam);
    User user = userService.banUser(id, durationSeconds, permanent, getAuthenticatedUser(ctx));
    if (banExpiryScheduler != null) banExpiryScheduler.scheduleIfTemporary(user);
    List<String> affectedItemIds = new ArrayList<>();
    affectedItemIds.addAll(cancelAutoBidsForUser(id));
    affectedItemIds.addAll(revertWinningBidsForUnavailableUser(id));
    broadcastUserBanned(id);
    affectedItemIds.forEach(this::broadcastItemUpdated);
    ctx.json(userToMap(user));
  }

  public void handleUnbanUser(Context ctx) {
    String id = ctx.pathParam("id");
    User user = userService.unbanUser(id, getAuthenticatedUser(ctx));
    broadcastUserUpdated(id);
    ctx.json(userToMap(user));
  }

  public void handleChangeUsername(Context ctx) {
    String id = ctx.pathParam("id");
    @SuppressWarnings("unchecked")
    Map<String, String> body = ctx.bodyAsClass(Map.class);
    User user = userService.changeUsername(id, body.get("username"), getAuthenticatedUser(ctx));
    broadcastUserUpdated(id);
    ctx.json(userToMap(user));
  }

  public void handleChangePassword(Context ctx) {
    String id = ctx.pathParam("id");
    @SuppressWarnings("unchecked")
    Map<String, String> body = ctx.bodyAsClass(Map.class);
    User user = userService.changePassword(id, body.get("password"), getAuthenticatedUser(ctx));
    broadcastUserUpdated(id);
    ctx.json(userToMap(user));
  }

  public void handleDeleteUser(Context ctx) {
    String id = ctx.pathParam("id");
    userService.deleteAccount(id, getAuthenticatedUser(ctx));
    List<String> affectedItemIds = new ArrayList<>();
    affectedItemIds.addAll(cancelAutoBidsForUser(id));
    affectedItemIds.addAll(revertWinningBidsForUnavailableUser(id));
    broadcastUserDeleted(id);
    affectedItemIds.forEach(this::broadcastItemUpdated);
    ctx.json(Map.of("message", "User deleted."));
  }

  private List<String> cancelAutoBidsForUser(String userId) {
    if (autoBidRepo == null) return List.of();

    List<AutoBid> userAutoBids = autoBidRepo.findByUserId(userId);
    List<String> affectedItemIds = new ArrayList<>();
    for (AutoBid autoBid : userAutoBids) {
      autoBidRepo.delete(autoBid.getUserId(), autoBid.getItemId());
      affectedItemIds.add(autoBid.getItemId());
    }
    return affectedItemIds;
  }

  private List<String> revertWinningBidsForUnavailableUser(String userId) {
    if (itemRepo == null || bidRepo == null) return List.of();

    List<String> changedItemIds = new ArrayList<>();
    for (Item item : itemRepo.findAll()) {
      if (!userId.equals(item.getCurrentWinnerId())) continue;
      if (item.getStatus() != AuctionStatus.RUNNING && item.getStatus() != AuctionStatus.FINISHED)
        continue;

      bidRepo.deleteLatestByBidderAndItem(userId, item.getId());

      Bid previousWinner = previousValidBid(item.getId(), userId);
      if (previousWinner == null) {
        item.setCurrentWinnerId(null);
        item.setCurrentPrice(item.getStartingPrice());
      } else {
        item.setCurrentWinnerId(previousWinner.getBidderId());
        item.setCurrentPrice(previousWinner.getAmount());
      }
      itemRepo.update(item);
      changedItemIds.add(item.getId());
    }
    return changedItemIds;
  }

  private Bid previousValidBid(String itemId, String unavailableUserId) {
    List<Bid> bids = bidRepo.findByItemId(itemId);
    for (int i = bids.size() - 1; i >= 0; i--) {
      Bid bid = bids.get(i);
      if (unavailableUserId.equals(bid.getBidderId())) continue;
      if (isEligibleWinner(bid.getBidderId())) return bid;
    }
    return null;
  }

  private boolean isEligibleWinner(String userId) {
    return userRepo
        .findById(userId)
        .filter(user -> !(user instanceof BannableUser bu && bu.isBanned()))
        .isPresent();
  }

  private Map<String, Object> userToMap(User user) {
    Map<String, Object> map = new HashMap<>();
    map.put("id", user.getId());
    map.put("username", user.getUsername());
    map.put("role", roleOf(user));
    if (user instanceof Bidder b) map.put("balance", b.getBalance());
    if (user instanceof Seller s) map.put("balance", s.getBalance());
    if (user instanceof BannableUser bu) {
      map.put("banned", bu.isBanned());
      map.put("banType", bu.getBanType() != null ? bu.getBanType().name() : null);
      map.put("banExpiryUnix", bu.getBanExpiryUnix());
    }
    return map;
  }

  private Map<String, Object> userToMapWithToken(User user, String token) {
    Map<String, Object> map = userToMap(user);
    map.put("token", token);
    return map;
  }

  private void requireSelfOrAdmin(Context ctx, String targetUserId, String action) {
    String requesterId = ctx.attribute("userId");
    String role = ctx.attribute("role");
    if (!targetUserId.equals(requesterId) && !"ADMIN".equals(role)) {
      throw new UnauthorizedActionException("Only admins can " + action + ".");
    }
  }

  private void requireAdmin(Context ctx) {
    if (!"ADMIN".equals(ctx.attribute("role")))
      throw new UnauthorizedActionException("Only admins can manage users.");
  }

  private User getAuthenticatedUser(Context ctx) {
    String userId = ctx.attribute("userId");
    return userRepo
        .findById(userId)
        .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
  }

  private double committedAmount(String userId) {
    if (itemRepo == null) return 0.0;
    Map<String, Double> commitmentsByItem = new HashMap<>();
    for (Item item : itemRepo.findAll()) {
      if ((item.getStatus() == AuctionStatus.RUNNING || item.getStatus() == AuctionStatus.FINISHED)
          && userId.equals(item.getCurrentWinnerId())) {
        commitmentsByItem.merge(item.getId(), item.getCurrentPrice(), Math::max);
      }
    }
    if (autoBidRepo != null) {
      for (AutoBid autoBid : autoBidRepo.findByUserId(userId)) {
        itemRepo
            .findById(autoBid.getItemId())
            .filter(i -> i.getStatus() == AuctionStatus.RUNNING)
            .ifPresent(i -> commitmentsByItem.merge(i.getId(), autoBid.getMaxBid(), Math::max));
      }
    }
    return commitmentsByItem.values().stream().mapToDouble(Double::doubleValue).sum();
  }

  private double balanceOf(User user) {
    if (user instanceof Bidder b) return b.getBalance();
    if (user instanceof Seller s) return s.getBalance();
    return 0.0;
  }

  private String roleOf(User user) {
    if (user instanceof Admin) return "ADMIN";
    if (user instanceof Seller) return "SELLER";
    return "BIDDER";
  }

  private void broadcastUserUpdated(String userId) {
    if (eventBroadcaster != null) eventBroadcaster.broadcastUserUpdated(userId);
  }

  private void broadcastUserBanned(String userId) {
    if (eventBroadcaster != null) eventBroadcaster.broadcastUserBanned(userId);
  }

  private void broadcastUserDeleted(String userId) {
    if (eventBroadcaster != null) eventBroadcaster.broadcastUserDeleted(userId);
  }

  private void broadcastUsersChanged() {
    if (eventBroadcaster != null) eventBroadcaster.broadcastUsersChanged();
  }

  private void broadcastItemUpdated(String itemId) {
    if (eventBroadcaster != null) eventBroadcaster.broadcastItemUpdated(itemId);
  }
}
