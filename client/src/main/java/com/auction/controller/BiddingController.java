package com.auction.controller;

import com.auction.app.AppState;
import com.auction.model.*;
import com.auction.repository.DatabaseManager;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import com.auction.repository.SqliteBidRepository;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

public class BiddingController {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    @FXML private Label lblName;
    @FXML private Label lblDesc;
    @FXML private Label lblType;
    @FXML private Label lblStatus;
    @FXML private Label lblPrice;
    @FXML private Label lblMinBid;
    @FXML private Label lblEndTime;
    @FXML private TextField bidAmountField;
    @FXML private Label statusLabel;
    @FXML private LineChart<String, Number> priceChart;

    private final SqliteBidRepository bidRepo = new SqliteBidRepository(new DatabaseManager("../auction_data.db"));
    private AppState appState;
    private Stage stage;
    private Item item;

    public void init(AppState appState, Stage stage, Item item) {
        this.appState = appState;
        this.stage = stage;
        this.item = item;
        setItem(item.getId());
        populateDetails();
    }

    public void setItem(String itemId) {
        long millis = item.getBidStartTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        Bid bid = new Bid(item.getSellerId(),item.getId(),item.getStartingPrice(),millis/1000L);
        List<Bid> bids = bidRepo.findByItemId(itemId);
        bids.addFirst(bid);
        loadChart(bids);
    }

    private void loadChart(List<Bid> bids) {

        XYChart.Series<String, Number> series =
                new XYChart.Series<>();

        series.setName("Bid History");

        for (Bid bid : bids) {

            long seconds = bid.getTimestamp();
            long milliSeconds = seconds * 1000L;
            LocalDateTime dateTime = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(milliSeconds),
                    ZoneId.systemDefault()
            );
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
            String label = dateTime.format(formatter);

            series.getData().add(
                    new XYChart.Data<>(
                            label,
                            bid.getAmount()
                    )
            );
        }

        priceChart.getData().clear();
        priceChart.getData().add(series);
    }

    private void populateDetails() {
        lblName.setText(item.getName());
        lblDesc.setText(item.getDescription());
        lblType.setText(typeName(item));
        lblStatus.setText(item.getStatus().toString());
        lblPrice.setText("$" + String.format("%.2f", item.getCurrentPrice()));
        lblMinBid.setText("$" + String.format("%.2f", item.getCurrentPrice() + item.getPriceStep()));
        lblEndTime.setText(item.getBidEndTime() != null ? item.getBidEndTime().format(DT_FMT) : "—");
        bidAmountField.setText(String.format("%.2f", item.getCurrentPrice() + item.getPriceStep()));
    }

    @FXML
    private void onPlaceBid() {
        String raw = bidAmountField.getText().trim();
        try {
            double amount = Double.parseDouble(raw);
            appState.bidService.placeBid(appState.currentUser, item.getId(), amount);
            // Refresh item state from repository
            item = appState.itemRepository.findById(item.getId()).orElse(item);
            // Refresh user state too — the server is authoritative for the
            // balance, and we want the next bid attempt to see the latest.
            try {
                var fresh = appState.restUserService.refresh(appState.currentUser.getId());
                if (fresh != null) appState.currentUser = fresh;
            } catch (Exception ignored) { }
            populateDetails();
            showStatus("Bid of $" + String.format("%.2f", amount) + " placed!", false);
        } catch (Exception e) {
            showStatus(e.getMessage(), true);
        }
        setItem(item.getId());
    }

    @FXML
    private void onBack() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/auction-list.fxml"));
            Scene scene = new Scene(loader.load(), 900, 580);
            scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/style.css")).toExternalForm());
            AuctionListController controller = loader.getController();
            controller.init(appState, stage);
            stage.setScene(scene);
        } catch (IOException e) {
            showStatus("Navigation error: " + e.getMessage(), true);
        }
    }

    private void showStatus(String msg, boolean isError) {
        statusLabel.setStyle(isError ? "-fx-text-fill: #c0392b;" : "-fx-text-fill: #27ae60;");
        statusLabel.setText(msg);
    }

    private String typeName(Item item) {
        if (item instanceof Art)         return "Art";
        if (item instanceof Electronics) return "Electronics";
        if (item instanceof Vehicle)     return "Vehicle";
        return "Item";
    }
}
