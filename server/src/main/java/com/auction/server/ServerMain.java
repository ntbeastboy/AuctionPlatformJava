package com.auction.server;

import com.auction.model.Admin;
import com.auction.repository.*;
import com.auction.security.PasswordUtil;
import com.auction.server.controller.AuctionController;
import com.auction.server.controller.BidController;
import com.auction.server.controller.ItemController;
import com.auction.server.controller.UserController;
import com.auction.server.events.ItemEventBroadcaster;
import com.auction.service.AuctionService;
import com.auction.service.BidService;
import com.auction.service.ItemService;
import com.auction.service.UserService;
import com.auction.server.network.AuctionServer;

public class ServerMain {

    public static void main(String[] args) {
        int port = 8080;
        if (args.length > 0) {
            try { port = Integer.parseInt(args[0]); } catch (NumberFormatException ignored) {}
        }

        // Database
        DatabaseManager db = new DatabaseManager("auction_data.db");

        // Repositories
        UserRepository userRepo = new SqliteUserRepository(db);
        ItemRepository itemRepo = new SqliteItemRepository(db);
        BidRepository bidRepo = new SqliteBidRepository(db);
        AutoBidRepository autoBidRepo = new SqliteAutoBidRepository(db);

        // Services
        UserService userService = new UserService(userRepo);
        ItemService itemService = new ItemService(itemRepo);
        TransactionRunner tx = action -> db.inTransaction(action::run);
        BidService bidService = new BidService(itemRepo, bidRepo, userRepo, autoBidRepo, tx);
        AuctionService auctionService = new AuctionService(itemRepo, userRepo, tx);
        ItemEventBroadcaster eventBroadcaster = new ItemEventBroadcaster();
        auctionService.setStatusChangeCallback(eventBroadcaster::broadcastItemsChanged);

        // Recover any RUNNING auctions left over from a previous server run:
        // close those whose end-time has passed, reschedule the rest.
        auctionService.recoverScheduledAuctions();

        // Seed admin account with hashed password (recreate DB if upgrading from plaintext)
        if (!userRepo.existsByUsername("admin")) {
            userRepo.save(new Admin("admin-0", "admin", PasswordUtil.hash("admin")));
        }

        // Controllers
        UserController userController = new UserController(userRepo, itemRepo, autoBidRepo, userService);
        ItemController itemController = new ItemController(itemRepo, userRepo, itemService, auctionService, eventBroadcaster);
        BidController bidController = new BidController(bidRepo, userRepo, bidService, eventBroadcaster);
        AuctionController auctionController = new AuctionController(userRepo, auctionService, eventBroadcaster);

        // Server
        AuctionServer server = new AuctionServer(userController, itemController, bidController, auctionController, eventBroadcaster);
        server.start(port);

        // Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down server...");
            auctionService.shutdown();
            server.stop();
            db.close();
        }));
    }
}
