package com.auction.app;

import com.auction.controller.LoginController;
import com.auction.repository.ItemRepository;
import com.auction.repository.UserRepository;
import com.auction.repository.rest.RestItemRepository;
import com.auction.repository.rest.RestUserRepository;
import com.auction.service.AuctionService;
import com.auction.service.BidService;
import com.auction.service.ItemService;
import com.auction.service.UserService;
import com.auction.service.http.HttpClientService;
import com.auction.service.rest.RestAuctionService;
import com.auction.service.rest.RestBidService;
import com.auction.service.rest.RestItemService;
import com.auction.service.rest.RestUserService;
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
        Scene scene = new Scene(loader.load(), 520, 440);
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
        // Single HTTP client shared across all REST-backed services so the
        // JWT bearer token captured at login flows to every other call.
        HttpClientService http = new HttpClientService();

        UserRepository userRepo = new RestUserRepository(http);
        ItemRepository itemRepo = new RestItemRepository(http);

        RestUserService userService = new RestUserService(userRepo, http);
        ItemService itemService = new RestItemService(itemRepo, http);
        BidService bidService = new RestBidService(http);
        AuctionService auctionService = new RestAuctionService(http);

        return new AppState(userRepo, itemRepo, userService, itemService,
                bidService, auctionService, userService);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
