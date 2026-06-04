package com.auction.server.events;

import com.auction.model.BannableUser;
import com.auction.model.User;
import com.auction.repository.UserRepository;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class UserBanExpiryScheduler {

  private final UserRepository userRepository;
  private final ItemEventBroadcaster eventBroadcaster;
  private final ScheduledExecutorService scheduler =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread t = new Thread(r, "user-ban-expiry");
            t.setDaemon(true);
            return t;
          });

  public UserBanExpiryScheduler(
      UserRepository userRepository, ItemEventBroadcaster eventBroadcaster) {
    this.userRepository = userRepository;
    this.eventBroadcaster = eventBroadcaster;
  }

  public void recoverScheduledBans() {
    for (User user : userRepository.findAll()) {
      scheduleIfTemporary(user);
    }
  }

  public void scheduleIfTemporary(User user) {
    if (!(user instanceof BannableUser bu)) return;
    if (bu.getBanType() != BannableUser.BanType.TEMPORARY) return;
    long expiryUnix = bu.getBanExpiryUnix();
    if (expiryUnix <= 0) return;

    long now = System.currentTimeMillis() / 1000L;
    long delaySeconds = Math.max(0, expiryUnix - now);
    scheduler.schedule(() -> expireBan(user.getId()), delaySeconds, TimeUnit.SECONDS);
  }

  public void shutdown() {
    scheduler.shutdownNow();
  }

  private void expireBan(String userId) {
    userRepository
        .findById(userId)
        .ifPresent(
            user -> {
              if (!(user instanceof BannableUser bu)) return;
              if (bu.getBanType() != BannableUser.BanType.TEMPORARY) return;

              if (bu.isBanned()) {
                scheduleIfTemporary(user);
                return;
              }

              userRepository.save(user);
              eventBroadcaster.broadcastUserUpdated(userId);
            });
  }
}
