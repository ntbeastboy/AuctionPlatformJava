package com.auction.app;

import com.auction.controller.LoginController;
import com.auction.model.Admin;
import com.auction.repository.*;
import com.auction.service.AuctionService;
import com.auction.service.BidService;
import com.auction.service.ItemService;
import com.auction.service.UserService;
import com.auction.service.network.AuctionServer;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class MainApplication extends Application {

    private AppState appState;
    private AuctionServer auctionServer;
    private DatabaseManager databaseManager;

    @Override
    public void start(Stage primaryStage) throws IOException {
        appState = buildAppState();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
        Scene scene = new Scene(loader.load(), 420, 340);
        scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());

        LoginController controller = loader.getController();
        controller.init(appState, primaryStage);

        primaryStage.setTitle("Auction Platform");
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.show();
    }

    @Override
    public void stop() {
        if (appState != null) appState.auctionService.shutdown();
        if (auctionServer != null) auctionServer.stop();
        if (databaseManager != null) databaseManager.close();
    }

    private AppState buildAppState() {
        // Initialize SQLite database
        databaseManager = new DatabaseManager("auction_data.db");

        // Use SQLite-backed repositories
        UserRepository userRepo = new SqliteUserRepository(databaseManager);
        ItemRepository itemRepo = new SqliteItemRepository(databaseManager);
        SqliteBidRepository bidRepo = new SqliteBidRepository(databaseManager);

        UserService userService = new UserService(userRepo);
        ItemService itemService = new ItemService(itemRepo);
        BidService bidService = new BidService(itemRepo);
        AuctionService auctionService = new AuctionService(itemRepo, userRepo);

        // Pre-seed admin account if not already in database
        if (!userRepo.existsByUsername("admin")) {
            userRepo.save(new Admin("admin-0", "admin", "admin"));
        }

        // Start embedded HTTP server
        auctionServer = new AuctionServer(userRepo, itemRepo, bidRepo,
                userService, itemService, bidService, auctionService);
        try {
            auctionServer.start(8080);
        } catch (Exception e) {
            System.err.println("Warning: Could not start API server on port 8080: " + e.getMessage());
            System.err.println("The app will still work in offline mode.");
        }

        return new AppState(userRepo, itemRepo, userService, itemService, bidService, auctionService);
    }

    public static void main(String[] args) {
        launch(args);
    }
}