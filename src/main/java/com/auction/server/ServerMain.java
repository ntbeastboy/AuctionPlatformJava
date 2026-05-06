package com.auction.server;

import com.auction.model.Admin;
import com.auction.repository.*;
import com.auction.security.PasswordUtil;
import com.auction.server.controller.AuctionController;
import com.auction.server.controller.BidController;
import com.auction.server.controller.ItemController;
import com.auction.server.controller.UserController;
import com.auction.service.AuctionService;
import com.auction.service.BidService;
import com.auction.service.ItemService;
import com.auction.service.UserService;
import com.auction.service.network.AuctionServer;

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

        // Services
        UserService userService = new UserService(userRepo);
        ItemService itemService = new ItemService(itemRepo);
        BidService bidService = new BidService(itemRepo, bidRepo, userRepo);
        AuctionService auctionService = new AuctionService(itemRepo, userRepo);

        // Seed admin account with hashed password (recreate DB if upgrading from plaintext)
        if (!userRepo.existsByUsername("admin")) {
            userRepo.save(new Admin("admin-0", "admin", PasswordUtil.hash("admin")));
        }

        // Controllers
        UserController userController = new UserController(userRepo, userService);
        ItemController itemController = new ItemController(itemRepo, userRepo, itemService, auctionService);
        BidController bidController = new BidController(bidRepo, userRepo, bidService);
        AuctionController auctionController = new AuctionController(userRepo, auctionService);

        // Server
        AuctionServer server = new AuctionServer(userController, itemController, bidController, auctionController);
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
