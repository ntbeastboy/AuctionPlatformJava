package com.auction.server;

import com.auction.model.Admin;
import com.auction.repository.AutoBidRepository;
import com.auction.repository.BidRepository;
import com.auction.repository.DatabaseManager;
import com.auction.repository.ItemRepository;
import com.auction.repository.SqliteAutoBidRepository;
import com.auction.repository.SqliteBidRepository;
import com.auction.repository.SqliteItemRepository;
import com.auction.repository.SqliteUserRepository;
import com.auction.repository.TransactionRunner;
import com.auction.repository.UserRepository;
import com.auction.security.PasswordUtil;
import com.auction.server.controller.AuctionController;
import com.auction.server.controller.BidController;
import com.auction.server.controller.ItemController;
import com.auction.server.controller.UserController;
import com.auction.server.events.ItemEventBroadcaster;
import com.auction.server.events.UserBanExpiryScheduler;
import com.auction.server.network.AuctionServer;
import com.auction.service.AuctionService;
import com.auction.service.BidService;
import com.auction.service.ItemService;
import com.auction.service.UserService;

public class ServerMain {

  private static final int DEFAULT_PORT = 8080;
  private static final String DATABASE_FILE = "auction_data.db";

  private static final String DEFAULT_ADMIN_ID = "admin-0";
  private static final String DEFAULT_ADMIN_USERNAME = "admin";
  private static final String DEFAULT_ADMIN_PASSWORD = "admin";

  public static void main(String[] args) {
    int port = parsePort(args);

    DatabaseManager db = DatabaseManager.getInstance(DATABASE_FILE);

    UserRepository userRepo = new SqliteUserRepository(db);
    ItemRepository itemRepo = new SqliteItemRepository(db);
    BidRepository bidRepo = new SqliteBidRepository(db);
    AutoBidRepository autoBidRepo = new SqliteAutoBidRepository(db);

    UserService userService = new UserService(userRepo);
    ItemService itemService = new ItemService(itemRepo);

    TransactionRunner tx = action -> db.inTransaction(action::run);

    BidService bidService = new BidService(itemRepo, bidRepo, userRepo, autoBidRepo, tx);
    AuctionService auctionService = new AuctionService(itemRepo, userRepo, tx);

    ItemEventBroadcaster eventBroadcaster = new ItemEventBroadcaster();
    UserBanExpiryScheduler banExpiryScheduler =
        new UserBanExpiryScheduler(userRepo, eventBroadcaster);

    configureCallbacks(auctionService, bidService, eventBroadcaster);

    auctionService.recoverScheduledAuctions();

    seedAdminIfMissing(userRepo);

    banExpiryScheduler.recoverScheduledBans();

    UserController userController =
        new UserController(
            userRepo,
            itemRepo,
            autoBidRepo,
            bidRepo,
            userService,
            eventBroadcaster,
            banExpiryScheduler);

    ItemController itemController =
        new ItemController(itemRepo, userRepo, itemService, auctionService, eventBroadcaster);

    BidController bidController =
        new BidController(bidRepo, userRepo, bidService, eventBroadcaster);

    AuctionController auctionController =
        new AuctionController(userRepo, auctionService, eventBroadcaster);

    AuctionServer server =
        new AuctionServer(
            userController,
            itemController,
            bidController,
            auctionController,
            eventBroadcaster);

    server.start(port);

    registerShutdownHook(auctionService, bidService, banExpiryScheduler, server, db);
  }

  private static int parsePort(String[] args) {
    if (args.length == 0) {
      return DEFAULT_PORT;
    }

    try {
      return Integer.parseInt(args[0]);
    } catch (NumberFormatException e) {
      return DEFAULT_PORT;
    }
  }

  private static void configureCallbacks(
      AuctionService auctionService,
      BidService bidService,
      ItemEventBroadcaster eventBroadcaster) {

    auctionService.setStatusChangeCallback(eventBroadcaster::broadcastItemsChanged);
    auctionService.setItemStatusChangeCallback(eventBroadcaster::broadcastItemUpdated);
    bidService.setItemUpdateCallback(eventBroadcaster::broadcastItemUpdated);
  }

  private static void seedAdminIfMissing(UserRepository userRepo) {
    if (!userRepo.existsByUsername(DEFAULT_ADMIN_USERNAME)) {
      userRepo.save(
          new Admin(
              DEFAULT_ADMIN_ID,
              DEFAULT_ADMIN_USERNAME,
              PasswordUtil.hash(DEFAULT_ADMIN_PASSWORD)));
    }
  }

  private static void registerShutdownHook(
      AuctionService auctionService,
      BidService bidService,
      UserBanExpiryScheduler banExpiryScheduler,
      AuctionServer server,
      DatabaseManager db) {

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  System.out.println("Shutting down server...");
                  auctionService.shutdown();
                  bidService.shutdown();
                  banExpiryScheduler.shutdown();
                  server.stop();
                  db.close();
                }));
  }
}