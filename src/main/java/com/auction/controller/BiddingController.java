package com.auction.controller;

import com.auction.app.AppState;
import com.auction.model.Art;
import com.auction.model.Electronics;
import com.auction.model.Item;
import com.auction.model.Vehicle;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;
import java.time.format.DateTimeFormatter;

public class BiddingController {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");


    @FXML private Label lblName;
    @FXML private Label lblDesc;
    @FXML private Label lblType;
    @FXML private Label lblStatus;
    @FXML private Label lblPrice;
    @FXML private Label lblMinBid;
    @FXML private Label lblEndTime;
    @FXML private TextField bidAmountField;
    @FXML private Label statusLabel;

    private AppState appState;
    private Stage stage;
    private Item item;

    public void init(AppState appState, Stage stage, Item item) {
        this.appState = appState;
        this.stage = stage;
        this.item = item;
        populateDetails();
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
            populateDetails();
            showStatus("Bid of $" + String.format("%.2f", amount) + " placed!", false);
        } catch (Exception e) {
            showStatus(e.getMessage(), true);
        }
    }

    @FXML
    private void onBack() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/auction-list.fxml"));
            Scene scene = new Scene(loader.load(), 900, 580);
            scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
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
