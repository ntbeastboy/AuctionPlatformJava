package com.auction.app;

import com.auction.controller.LoginController;
import com.auction.model.Admin;
import com.auction.repository.ItemRepository;
import com.auction.repository.UserRepository;
import com.auction.service.AuctionService;
import com.auction.service.BidService;
import com.auction.service.ItemService;
import com.auction.service.UserService;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class MainApplication extends Application {

    private AppState appState;

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
    }

    private AppState buildAppState() {
        UserRepository userRepo = new UserRepository();
        ItemRepository itemRepo = new ItemRepository();
        UserService userService = new UserService(userRepo);
        ItemService itemService = new ItemService(itemRepo);
        BidService bidService = new BidService(itemRepo);
        AuctionService auctionService = new AuctionService(itemRepo, userRepo);

        // Pre-seed admin account (not self-registrable)
        userRepo.save(new Admin("admin-0", "admin", "admin"));

        return new AppState(userRepo, itemRepo, userService, itemService, bidService, auctionService);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
