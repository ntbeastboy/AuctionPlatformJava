package com.auction.controller;

import com.auction.app.AppState;
import com.auction.model.AutoBid;
import com.auction.model.BannableUser;
import com.auction.model.Bid;
import com.auction.model.Item;
import com.auction.model.User;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

public class BiddingController {

  private static final DateTimeFormatter DT_FMT =
      DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

  @FXML private Label lblName;
  @FXML private Label lblDesc;
  @FXML private Label lblType;
  @FXML private Label lblStatus;
  @FXML private Label lblPrice;
  @FXML private Label lblMinBid;
  @FXML private Label lblEndTime;
  @FXML private TextField bidAmountField;
  @FXML private TextField autoMaxBidField;
  @FXML private TextField autoIncrementField;
  @FXML private Label autoBidInfoLabel;
  @FXML private Label statusLabel;
  @FXML private LineChart<String, Number> priceChart;
  @FXML private TableView<Bid> bidTable;
  @FXML private TableColumn<Bid, String> colBidder;
  @FXML private TableColumn<Bid, Double> colPrice;
  @FXML private TableColumn<Bid, Long> colTime;

  private AppState appState;
  private Stage stage;
  private Item item;
  private Consumer<String> updateListener;

  public void init(AppState appState, Stage stage, Item item) {
    this.appState = appState;
    this.stage = stage;
    this.item = item;
    setItem(item.getId());
    populateDetails();
    refreshAutoBidControls();
    registerRealtimeUpdates();
  }

  public void setItem(String itemId) {
    long startMillis =
        item.getBidStartTime() != null
            ? item.getBidStartTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            : System.currentTimeMillis();
    Bid bid = new Bid(item.getSellerId(), item.getId(), item.getStartingPrice(), startMillis);
    List<Bid> bids = new ArrayList<>(appState.bidService.getBidsForItem(itemId));
    bids.add(0, bid);
    loadChart(bids);
    setupTable(bids);
  }

  private void setupTable(List<Bid> bids) {
    colBidder.setCellValueFactory(new PropertyValueFactory<>("bidderId"));
    colPrice.setCellValueFactory(new PropertyValueFactory<>("amount"));
    colTime.setCellValueFactory(new PropertyValueFactory<>("timestamp"));
    colBidder.setCellFactory(
        column ->
            new TableCell<Bid, String>() {
              @Override
              protected void updateItem(String bidderId, boolean empty) {
                super.updateItem(bidderId, empty);
                if (empty || bidderId == null) {
                  setText(null);
                } else {
                  Optional<User> user = appState.userService.findById(bidderId);
                  setText(bidderDisplayName(user));
                }
              }
            });
    colTime.setCellFactory(
        column ->
            new TableCell<Bid, Long>() {
              private final DateTimeFormatter formatter =
                  DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

              @Override
              protected void updateItem(Long timestamp, boolean empty) {
                super.updateItem(timestamp, empty);
                if (empty || timestamp == null) {
                  setText(null);
                } else {
                  LocalDateTime dateTime =
                      LocalDateTime.ofInstant(
                          Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
                  setText(dateTime.format(formatter));
                }
              }
            });

    colBidder.setStyle("-fx-alignment: CENTER;");
    colPrice.setStyle("-fx-alignment: CENTER;");
    colTime.setStyle("-fx-alignment: CENTER;");

    ObservableList<Bid> bidObservableList = FXCollections.observableArrayList(bids);
    bidTable.setItems(bidObservableList);
  }

  private void loadChart(List<Bid> bids) {

    XYChart.Series<String, Number> series = new XYChart.Series<>();

    series.setName("Bid History");

    for (Bid bid : bids) {

      LocalDateTime dateTime =
          LocalDateTime.ofInstant(Instant.ofEpochMilli(bid.getTimestamp()), ZoneId.systemDefault());
      DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
      String label = dateTime.format(formatter);

      series.getData().add(new XYChart.Data<>(label, bid.getAmount()));
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
      } catch (Exception ignored) {
      }
      populateDetails();
      refreshAutoBidControls();
      showStatus("Bid of $" + String.format("%.2f", amount) + " placed!", false);
    } catch (Exception e) {
      showStatus(e.getMessage(), true);
    }
    setItem(item.getId());
  }

  @FXML
  private void onEnableAutoBid() {
    try {
      double maxBid = Double.parseDouble(autoMaxBidField.getText().trim());
      double increment = Double.parseDouble(autoIncrementField.getText().trim());
      if (increment < item.getPriceStep()) {
        showStatus(
            "Auto-bid increment must be at least $"
                + String.format("%.2f", item.getPriceStep())
                + ".",
            true);
        return;
      }

      appState.bidService.setAutoBid(appState.currentUser, item.getId(), maxBid, increment);
      item = appState.itemRepository.findById(item.getId()).orElse(item);
      try {
        var fresh = appState.restUserService.refresh(appState.currentUser.getId());
        if (fresh != null) appState.currentUser = fresh;
      } catch (Exception ignored) {
      }
      populateDetails();
      refreshAutoBidControls();
      setItem(item.getId());
      showStatus("Auto-bid enabled up to $" + String.format("%.2f", maxBid) + ".", false);
    } catch (NumberFormatException e) {
      showStatus("Auto-bid max and increment must be valid numbers.", true);
    } catch (Exception e) {
      showStatus(e.getMessage(), true);
    }
  }

  @FXML
  private void onCancelAutoBid() {
    try {
      appState.bidService.cancelAutoBid(appState.currentUser, item.getId());
      refreshAutoBidControls();
      showStatus("Auto-bid canceled.", false);
    } catch (Exception e) {
      showStatus(e.getMessage(), true);
    }
  }

  private void refreshAutoBidControls() {
    try {
      var autoBid = appState.bidService.getAutoBid(appState.currentUser, item.getId());
      if (autoBid.isPresent()) {
        AutoBid bid = autoBid.get();
        autoMaxBidField.setText(String.format("%.2f", bid.getMaxBid()));
        autoIncrementField.setText(String.format("%.2f", bid.getIncrement()));
        autoBidInfoLabel.setStyle("-fx-text-fill: #27ae60;");
        autoBidInfoLabel.setText(
            "Auto-bid active: max $"
                + String.format("%.2f", bid.getMaxBid())
                + ", increment $"
                + String.format("%.2f", bid.getIncrement())
                + ".");
      } else {
        if (autoMaxBidField.getText() == null || autoMaxBidField.getText().isBlank())
          autoMaxBidField.setText(
              String.format("%.2f", item.getCurrentPrice() + item.getPriceStep()));
        if (autoIncrementField.getText() == null || autoIncrementField.getText().isBlank())
          autoIncrementField.setText(String.format("%.2f", item.getPriceStep()));
        autoBidInfoLabel.setStyle("-fx-text-fill: #475569;");
        autoBidInfoLabel.setText("Auto-bid is off.");
      }
    } catch (Exception e) {
      autoBidInfoLabel.setStyle("-fx-text-fill: #c0392b;");
      autoBidInfoLabel.setText(e.getMessage());
    }
  }

  @FXML
  private void onBack() {
    try {
      FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/auction-list.fxml"));
      Scene scene = new Scene(loader.load(), 900, 580);
      scene
          .getStylesheets()
          .add(Objects.requireNonNull(getClass().getResource("/css/style.css")).toExternalForm());
      AuctionListController controller = loader.getController();
      controller.init(appState, stage);
      removeRealtimeUpdates();
      stage.setScene(scene);
    } catch (IOException e) {
      showStatus("Navigation error: " + e.getMessage(), true);
    }
  }

  private void showStatus(String msg, boolean isError) {
    statusLabel.setStyle(isError ? "-fx-text-fill: #c0392b;" : "-fx-text-fill: #27ae60;");
    statusLabel.setText(msg);
  }

  private void registerRealtimeUpdates() {
    removeRealtimeUpdates();
    updateListener = message -> Platform.runLater(() -> handleRealtimeUpdate(message));
    appState.httpClient.addUpdateListener(updateListener);
  }

  private void removeRealtimeUpdates() {
    if (updateListener != null && appState != null) {
      appState.httpClient.removeUpdateListener(updateListener);
      updateListener = null;
    }
  }

  private void handleRealtimeUpdate(String message) {
    String type = null;
    String userId = null;
    String itemId = null;
    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> event = appState.httpClient.getGson().fromJson(message, Map.class);
      Object typeObj = event.get("type");
      Object userIdObj = event.get("userId");
      Object itemIdObj = event.get("itemId");
      if (typeObj != null) type = String.valueOf(typeObj);
      if (userIdObj != null) userId = String.valueOf(userIdObj);
      if (itemIdObj != null) itemId = String.valueOf(itemIdObj);
    } catch (Exception e) {
      refreshFromServer();
      return;
    }

    if ("USER_BANNED".equals(type)) {
      refreshFromServer();
      if (isCurrentUser(userId)) {
        forceLogout("Your account was banned by an administrator.");
      }
      return;
    }

    if ("USER_DELETED".equals(type)) {
      refreshFromServer();
      if (isCurrentUser(userId)) {
        forceLogout("Your account was deleted by an administrator.");
      }
      return;
    }

    if ("USER_UPDATED".equals(type) || "USERS_CHANGED".equals(type)) {
      refreshFromServer();
      refreshCurrentUserAndLogoutIfUnavailable();
      return;
    }

    if ("ITEM_UPDATED".equals(type)) {
      if (itemId != null && item.getId().equals(itemId)) {
        refreshFromServer();
      }
      return;
    }

    if ("ITEMS_CHANGED".equals(type)) {
      refreshFromServer();
      return;
    }

    refreshFromServer();
  }

  private void refreshFromServer() {
    try {
      item = appState.itemRepository.findById(item.getId()).orElse(item);
      try {
        var fresh = appState.restUserService.refresh(appState.currentUser.getId());
        if (fresh != null) appState.currentUser = fresh;
      } catch (Exception ignored) {
      }
      populateDetails();
      refreshAutoBidControls();
      setItem(item.getId());
    } catch (Exception e) {
      showStatus(e.getMessage(), true);
    }
  }

  private void refreshCurrentUserAndLogoutIfUnavailable() {
    if (appState.currentUser == null) return;
    try {
      User fresh = appState.restUserService.refresh(appState.currentUser.getId());
      if (fresh == null) {
        forceLogout("Your account is no longer available.");
        return;
      }
      appState.currentUser = fresh;
      if (fresh instanceof BannableUser bu && bu.isBanned()) {
        forceLogout("Your account was banned by an administrator.");
      }
    } catch (Exception e) {
      forceLogout("Your account is no longer available.");
    }
  }

  private boolean isCurrentUser(String userId) {
    return userId != null
        && appState.currentUser != null
        && userId.equals(appState.currentUser.getId());
  }

  private void forceLogout(String message) {
    try {
      removeRealtimeUpdates();
      appState.auctionService.setStatusChangeCallback(null);
      appState.restUserService.logout();
      appState.currentUser = null;
      FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
      Scene scene = new Scene(loader.load(), 520, 440);
      scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
      LoginController controller = loader.getController();
      controller.init(appState, stage);
      stage.setScene(scene);
      stage.setResizable(false);
      stage.setTitle("Auction Platform");
      if (message != null && !message.isBlank()) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Logged out");
        alert.setHeaderText(message);
        alert.show();
      }
    } catch (IOException e) {
      showStatus(e.getMessage(), true);
    }
  }

  private String typeName(Item item) {
    return item.getTypeName();
  }

  private String bidderDisplayName(Optional<User> user) {
    if (user.isEmpty()) return "USER_DELETED";
    User bidder = user.orElseThrow();
    String username = bidder.getUsername();
    if (bidder instanceof BannableUser bu && bu.isBanned()) return username + "(BANNED)";
    return username;
  }
}
