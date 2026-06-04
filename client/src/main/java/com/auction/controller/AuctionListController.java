package com.auction.controller;

import com.auction.app.AppState;
import com.auction.model.Admin;
import com.auction.model.Art;
import com.auction.model.AuctionStatus;
import com.auction.model.BannableUser;
import com.auction.model.Bidder;
import com.auction.model.Electronics;
import com.auction.model.Item;
import com.auction.model.ItemFactory;
import com.auction.model.Seller;
import com.auction.model.User;
import com.auction.model.Vehicle;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class AuctionListController {

  private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
  private static final DateTimeFormatter BAN_EXPIRY_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
  private static final int MAX_ITEM_IMAGES = 8;

    @FXML private Label userInfoLabel;
    @FXML private Button addFundsBtn;
    @FXML private Button withdrawBtn;
    @FXML private TableView<Item> itemTable;
    @FXML private TableColumn<Item, Item>          colThumbnail;
    @FXML private TableColumn<Item, String>        colName;
    @FXML private TableColumn<Item, String>        colType;
    @FXML private TableColumn<Item, AuctionStatus> colStatus;
    @FXML private TableColumn<Item, String>        colPrice;
    @FXML private TableColumn<Item, String>        colMinBid;
    @FXML private TableColumn<Item, String>        colEndTime;
    @FXML private TableColumn<Item, String>        colWinner;
    @FXML private Button createItemBtn;
    @FXML private Button editItemBtn;
    @FXML private Button startBtn;
    @FXML private Button endEarlyBtn;
    @FXML private Button cancelBtn;
    @FXML private Button deleteBtn;
    @FXML private Button placeBidBtn;
    @FXML private Button paySellerBtn;
    @FXML private TabPane mainTabs;
    @FXML private Tab manageUsersTab;
    @FXML private TableView<User> userTable;
    @FXML private TableColumn<User, String> colUserId;
    @FXML private TableColumn<User, String> colUsername;
    @FXML private TableColumn<User, String> colUserRole;
    @FXML private TableColumn<User, String> colUserBalance;
    @FXML private TableColumn<User, String> colUserBanned;
    @FXML private Label statusLabel;

    private AppState appState;
    private Stage stage;
    private Consumer<String> updateListener;

    public void init(AppState appState, Stage stage) {
        this.appState = appState;
        this.stage = stage;
        setupTable();
        setupUserTable();
        configureRoleButtons();
        refreshTable();
        refreshUsers();
        stage.setTitle("Auction Platform – " + appState.currentUser.getUsername());

        appState.auctionService.setStatusChangeCallback(() -> Platform.runLater(this::refreshTable));
        registerRealtimeUpdates();
    }
    private String formatPrice(double amount) {
    if (amount >= 1_000_000_000)
        return String.format("$%.3gB", amount / 1_000_000_000);
    if (amount >= 1_000_000)
        return String.format("$%.3gM", amount / 1_000_000);
    if (amount >= 1_000)
        return String.format("$%.3gK", amount / 1_000);
    return String.format("$%.2f", amount);
    }
    private void setupTable() {
        itemTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        colThumbnail.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        colThumbnail.setCellFactory(col -> new TableCell<>() {
            private final ImageView view = new ImageView();
            {
                view.setFitWidth(76);
                view.setFitHeight(56);
                view.setPreserveRatio(true);
                view.setSmooth(true);
            }

            @Override
            protected void updateItem(Item item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else if (item.getImageData() == null || item.getImageData().isBlank()) {
                    setGraphic(new Label("No image"));
                } else {
                    view.setImage(ItemImageSupport.toImage(item.getImageData()));
                    setGraphic(view);
                }
                setAlignment(javafx.geometry.Pos.CENTER);
            }
        });
        colName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        centerTextColumn(colName);
        itemTable.setRowFactory(tv -> {
            TableRow<Item> row = new TableRow<>();
            row.setPrefHeight(72);
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty())
                    showItemDetailsCard(row.getItem());
            });
            return row;
        });
        itemTable.getSelectionModel().selectedItemProperty().addListener((obs, oldItem, newItem) -> updatePaySellerButton());
        if (colType != null) colType.setCellValueFactory(c -> new SimpleStringProperty(typeName(c.getValue())));
        if (colStatus != null) colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colPrice.setCellValueFactory(c ->
            new SimpleStringProperty(formatPrice(c.getValue().getCurrentPrice())));
        centerTextColumn(colPrice);

        if (colMinBid != null) colMinBid.setCellValueFactory(c -> {
            Item i = c.getValue();
            return new SimpleStringProperty(formatPrice(i.getCurrentPrice() + i.getPriceStep()));
        });
        colEndTime.setCellValueFactory(c -> {
            Item item = c.getValue();
            if (item.getStatus() == AuctionStatus.OPEN) return new SimpleStringProperty("—");
            LocalDateTime end = item.getBidEndTime();
            return new SimpleStringProperty(end != null ? end.format(DT_FMT) : "—");
        });
        centerTextColumn(colEndTime);
        if (colWinner != null) colWinner.setCellValueFactory(c -> {
            String winnerId = c.getValue().getCurrentWinnerId();
            if (winnerId == null) return new SimpleStringProperty("—");
            String username = appState.userRepository.findById(winnerId)
                    .map(User::getUsername).orElse(winnerId);
            return new SimpleStringProperty(username);
        });
    }

    private void centerTextColumn(TableColumn<Item, String> column) {
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty ? null : value);
                setAlignment(javafx.geometry.Pos.CENTER);
            }
        });
    }

    private void configureRoleButtons() {
        if (appState.currentUser instanceof Admin) {
            show(editItemBtn, startBtn, endEarlyBtn, cancelBtn, deleteBtn);
        } else if (appState.currentUser instanceof Seller) {
            show(addFundsBtn, withdrawBtn, createItemBtn, editItemBtn, deleteBtn, startBtn, placeBidBtn);
            mainTabs.getTabs().remove(manageUsersTab);
        } else if (appState.currentUser instanceof Bidder) {
            show(addFundsBtn, withdrawBtn, placeBidBtn);
            mainTabs.getTabs().remove(manageUsersTab);
        }
        updatePaySellerButton();
    }

    private void setupUserTable() {
        colUserId.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getId()));
        colUsername.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getUsername()));
        colUserRole.setCellValueFactory(c -> new SimpleStringProperty(roleOf(c.getValue())));
        colUserBalance.setCellValueFactory(c -> new SimpleStringProperty(balanceOf(c.getValue())));
        colUserBanned.setCellValueFactory(c -> new SimpleStringProperty(bannedText(c.getValue())));
    }

    private void show(javafx.scene.Node... nodes) {
        for (javafx.scene.Node n : nodes) { n.setVisible(true); n.setManaged(true); }
    }

    @FXML
    private void onLogout() throws IOException {
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
    }

    @FXML
    private void onAddFunds() {
        PaymentDialogFactory.showAddFundsDialog().ifPresent(amount -> {
            try {
                // Persist via the server so the new balance survives across
                // sessions and is visible to every other call (placeBid in
                // particular re-fetches the user inside the bid lock).
                User updated = appState.restUserService.addBalance(appState.currentUser.getId(), amount);
                appState.currentUser = updated;
                refreshUserInfo();
                showStatus("Added $" + String.format("%.2f", amount) + " to your balance.", false);
            } catch (Exception e) {
                showStatus(e.getMessage(), true);
            }
        });
    }

    @FXML
    private void onWithdraw() {
        double committed = appState.itemRepository.findAll().stream()
                .filter(i -> (i.getStatus() == AuctionStatus.RUNNING || i.getStatus() == AuctionStatus.FINISHED)
                          && appState.currentUser.getId().equals(i.getCurrentWinnerId()))
                .mapToDouble(Item::getCurrentPrice)
                .sum();
        if (committed > 0) {
            showStatus("Cannot withdraw while you have active winning bids ($"
                    + String.format("%.2f", committed) + " committed).", true);
            return;
        }
        TextInputDialog dlg = new TextInputDialog();
        dlg.setTitle("Withdraw");
        dlg.setHeaderText("Enter amount to withdraw:");
        dlg.showAndWait().ifPresent(val -> {
            try {
                String trimmed = val.trim();
                if (!trimmed.matches("\\d+(\\.\\d+)?")) {
                    showStatus("Amount must be a plain number (e.g. 100 or 99.99).", true);
                    return;
                }
                double amount = Double.parseDouble(trimmed);
                User updated = appState.restUserService.deductBalance(appState.currentUser.getId(), amount);
                appState.currentUser = updated;
                refreshUserInfo();
                showStatus("Withdrew $" + String.format("%.2f", amount) + " from your balance.", false);
            } catch (Exception e) {
                showStatus(e.getMessage(), true);
            }
        });
    }

    @FXML
    private void onCreateItem() {
        buildItemDialog(null).showAndWait().ifPresent(item -> {
            try {
                appState.itemService.createItem(appState.currentUser, item);
                refreshTable();
                showStatus("Item \"" + item.getName() + "\" created.", false);
            } catch (Exception e) {
                showStatus(e.getMessage(), true);
            }
        });
    }

    @FXML
    private void onEditItem() {
        Item selected = itemTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showStatus("Select an item first.", true); return; }
        if (selected.getStatus() != AuctionStatus.OPEN) {
            showStatus("Only OPEN items can be edited.", true);
            return;
        }
        buildItemDialog(selected).showAndWait().ifPresent(item -> {
            try {
                appState.itemService.updateItem(appState.currentUser, item);
                refreshTable();
                showStatus("Item \"" + item.getName() + "\" updated.", false);
            } catch (Exception e) {
                showStatus(e.getMessage(), true);
            }
        });
    }

    @FXML private void onStart()    { withSelected(item -> { appState.auctionService.startAuction(item.getId(), appState.currentUser); refreshTable(); showStatus("Auction started.", false); }); }
    @FXML private void onEndEarly() { withSelected(item -> { appState.auctionService.endAuctionEarly(item.getId(), appState.currentUser); refreshCurrentUser(); refreshTable(); showStatus("Auction ended early.", false); }); }
    @FXML private void onCancel()   { withSelected(item -> { appState.auctionService.cancelAuction(item.getId(), appState.currentUser);  refreshTable(); showStatus("Auction cancelled.", false); }); }
    @FXML private void onDelete()   { withSelected(item -> { appState.itemService.deleteItem(appState.currentUser, item.getId());         refreshTable(); showStatus("Item deleted.", false); }); }

    @FXML
    private void onPlaceBid() {
        Item selected = itemTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showStatus("Select an item first.", true); return; }
        if (selected.getStatus() != AuctionStatus.RUNNING) { showStatus("Auction is not running.", true); return; }
        switchToBidding(selected);
    }

    @FXML
    private void onPaySeller() {
        Item selected = itemTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showStatus("Select an item first.", true); return; }
        if (selected.getStatus() != AuctionStatus.FINISHED) {
            showStatus("Auction must be FINISHED before payment.", true);
            return;
        }
        if (!appState.currentUser.getId().equals(selected.getCurrentWinnerId())) {
            showStatus("Only the winning bidder can pay the seller.", true);
            return;
        }
        try {
            appState.auctionService.paySeller(selected.getId(), appState.currentUser);
            refreshCurrentUser();
            refreshTable();
            showStatus("Seller paid.", false);
        } catch (Exception e) {
            showStatus(e.getMessage(), true);
        }
    }

    private void switchToBidding(Item item) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/bidding.fxml"));
            Scene scene = new Scene(loader.load(), 900, 800);
            scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
            BiddingController controller = loader.getController();
            controller.init(appState, stage, item);
            removeRealtimeUpdates();
            stage.setScene(scene);
        } catch (IOException e) {
            showStatus("Failed to open bidding screen: " + e.getMessage(), true);
        }
    }

    private void withSelected(java.util.function.Consumer<Item> action) {
        Item selected = itemTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showStatus("Select an item first.", true); return; }
        try { action.accept(selected); }
        catch (Exception e) { showStatus(e.getMessage(), true); }
    }

    private void refreshTable() {
        itemTable.setItems(FXCollections.observableArrayList(appState.itemRepository.findAll()));
        itemTable.refresh();
        refreshUserInfo();
        updatePaySellerButton();
    }

    @FXML
    private void onRefreshUsers() {
        refreshUsers();
        showStatus("User list refreshed.", false);
    }

    @FXML
    private void onBanUser() {
        withSelectedUser(user -> {
            if (user instanceof Admin) {
                showStatus("Admin accounts cannot be banned.", true);
                return;
            }
            TextInputDialog dlg = new TextInputDialog(LocalDateTime.now().plusDays(7).format(BAN_EXPIRY_FMT));
            dlg.setTitle("Ban User");
            dlg.setHeaderText("Enter automatic unban date and time (DD/MM/YYYY HH:MM):");
            dlg.showAndWait().ifPresent(expiryText -> {
                try {
                    LocalDateTime expiry = LocalDateTime.parse(expiryText.trim(), BAN_EXPIRY_FMT);
                    LocalDateTime now = LocalDateTime.now();
                    if (!expiry.isAfter(now)) {
                        showStatus("Unban time must be later than the current date and time.", true);
                        return;
                    }
                    long durationSeconds = Math.max(1, java.time.Duration.between(now, expiry).getSeconds());
                    appState.restUserService.banUser(user.getId(), durationSeconds);
                    refreshUsers();
                    showStatus("User banned until " + expiry.format(BAN_EXPIRY_FMT) + ".", false);
                } catch (DateTimeParseException e) {
                    showStatus("Use DD/MM/YYYY HH:MM, e.g. 25/12/2026 14:30.", true);
                } catch (Exception e) {
                    showStatus(e.getMessage(), true);
                }
            });
        });
    }

    @FXML
    private void onUnbanUser() {
        withSelectedUser(user -> {
            if (user instanceof Admin) {
                showStatus("Admin accounts cannot be unbanned.", true);
                return;
            }
            appState.restUserService.unbanUser(user.getId());
            refreshUsers();
            showStatus("User unbanned.", false);
        });
    }

    @FXML
    private void onChangeUsername() {
        withSelectedUser(user -> {
            TextInputDialog dlg = new TextInputDialog(user.getUsername());
            dlg.setTitle("Change Username");
            dlg.setHeaderText("Enter a new username:");
            dlg.showAndWait().ifPresent(username -> {
                try {
                    User updated = appState.restUserService.changeUsername(user.getId(), username.trim());
                    if (appState.currentUser.getId().equals(updated.getId())) {
                        appState.currentUser = updated;
                        refreshUserInfo();
                    }
                    refreshUsers();
                    showStatus("Username updated.", false);
                } catch (Exception e) {
                    showStatus(e.getMessage(), true);
                }
            });
        });
    }

    @FXML
    private void onChangePassword() {
        withSelectedUser(user -> {
            Dialog<String> dlg = new Dialog<>();
            dlg.setTitle("Change Password");
            dlg.setHeaderText("Enter a new password:");
            PasswordField passwordField = new PasswordField();
            passwordField.setPromptText("New password");
            dlg.getDialogPane().setContent(passwordField);
            ButtonType saveBtn = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
            dlg.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);
            dlg.setResultConverter(btn -> btn == saveBtn ? passwordField.getText() : null);
            dlg.showAndWait().ifPresent(password -> {
                try {
                    appState.restUserService.changePassword(user.getId(), password);
                    refreshUsers();
                    showStatus("Password updated.", false);
                } catch (Exception e) {
                    showStatus(e.getMessage(), true);
                }
            });
        });
    }

    @FXML
    private void onDeleteUser() {
        withSelectedUser(user -> {
            if (user instanceof Admin) {
                showStatus("Admin accounts cannot be deleted.", true);
                return;
            }
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Delete Account");
            confirm.setHeaderText("Delete account \"" + user.getUsername() + "\"?");
            confirm.setContentText("This action cannot be undone.");
            confirm.showAndWait()
                    .filter(btn -> btn == ButtonType.OK)
                    .ifPresent(btn -> {
                        try {
                            appState.restUserService.deleteUser(user.getId());
                            refreshUsers();
                            showStatus("User deleted.", false);
                        } catch (Exception e) {
                            showStatus(e.getMessage(), true);
                        }
                    });
        });
    }

    private void refreshUsers() {
        if (!(appState.currentUser instanceof Admin) || userTable == null) return;
        try {
            userTable.setItems(FXCollections.observableArrayList(appState.restUserService.findAllUsers()));
            userTable.refresh();
        } catch (Exception e) {
            showStatus(e.getMessage(), true);
        }
    }

    private void withSelectedUser(java.util.function.Consumer<User> action) {
        User selected = userTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showStatus("Select a user first.", true); return; }
        try { action.accept(selected); }
        catch (Exception e) { showStatus(e.getMessage(), true); }
    }

    private String roleOf(User user) {
        if (user instanceof Admin) return "ADMIN";
        if (user instanceof Seller) return "SELLER";
        return "BIDDER";
    }

    private String balanceOf(User user) {
        if (user instanceof Admin) return "";
        if (user instanceof Bidder b) return "$" + String.format("%.2f", b.getBalance());
        if (user instanceof Seller s) return "$" + String.format("%.2f", s.getBalance());
        return "â€”";
    }

    private String bannedText(User user) {
        if (user instanceof Admin) return "";
        if (user instanceof BannableUser bu) return bu.isBanned() ? "Yes" : "No";
        return "N/A";
    }

    private void updatePaySellerButton() {
        if (paySellerBtn == null || appState == null || appState.currentUser == null) return;
        Item selected = itemTable.getSelectionModel().getSelectedItem();
        boolean canPay = selected != null
                && selected.getStatus() == AuctionStatus.FINISHED
                && appState.currentUser.getId().equals(selected.getCurrentWinnerId());
        paySellerBtn.setVisible(canPay);
        paySellerBtn.setManaged(canPay);
    }

    private void registerRealtimeUpdates() {
        removeRealtimeUpdates();
        updateListener = message -> Platform.runLater(() -> handleRealtimeUpdate(message));
        appState.httpClient.addUpdateListener(updateListener);
    }

    private void handleRealtimeUpdate(String message) {
        String type = null;
        String userId = null;
        try {
            JsonObject event = JsonParser.parseString(message).getAsJsonObject();
            if (event.has("type") && !event.get("type").isJsonNull()) type = event.get("type").getAsString();
            if (event.has("userId") && !event.get("userId").isJsonNull()) userId = event.get("userId").getAsString();
        } catch (Exception ignored) { }

        if ("ITEM_UPDATED".equals(type) || "ITEMS_CHANGED".equals(type)) {
            refreshCurrentUser();
            refreshTable();
            return;
        }

        if ("USERS_CHANGED".equals(type)) {
            refreshUsers();
            refreshCurrentUserAndLogoutIfUnavailable();
            return;
        }

        if ("USER_DELETED".equals(type)) {
            refreshUsers();
            if (isCurrentUser(userId)) {
                forceLogout("Your account was deleted by an administrator.");
            }
            return;
        }

        if ("USER_BANNED".equals(type)) {
            refreshUsers();
            if (isCurrentUser(userId)) {
                forceLogout("Your account was banned by an administrator.");
            }
            return;
        }

        if ("USER_UPDATED".equals(type)) {
            refreshUsers();
            if (isCurrentUser(userId)) {
                refreshCurrentUserAndLogoutIfUnavailable();
            }
            return;
        }

        refreshCurrentUser();
        refreshTable();
        refreshUsers();
    }

    private void removeRealtimeUpdates() {
        if (updateListener != null && appState != null) {
            appState.httpClient.removeUpdateListener(updateListener);
            updateListener = null;
        }
    }

    /**
     * Re-fetch the current user from the server after a balance-changing
     * action (e.g. winning a bid + auction settlement). Falls back silently if the
     * server can't be reached so we don't blow up the UI.
     */
    private void refreshCurrentUser() {
        try {
            User fresh = appState.restUserService.refresh(appState.currentUser.getId());
            if (fresh != null) appState.currentUser = fresh;
        } catch (Exception ignored) { }
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
                return;
            }
            refreshUserInfo();
        } catch (Exception e) {
            forceLogout("Your account is no longer available.");
        }
    }

    private boolean isCurrentUser(String userId) {
        return userId != null && appState.currentUser != null && userId.equals(appState.currentUser.getId());
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

    private void refreshUserInfo() {
        String role = appState.currentUser.getClass().getSimpleName();
        String balance = "";
        if (appState.currentUser instanceof Bidder b) balance = "  |  Balance: $" + String.format("%.2f", b.getBalance());
        if (appState.currentUser instanceof Seller s) balance = "  |  Balance: $" + String.format("%.2f", s.getBalance());
        userInfoLabel.setText(appState.currentUser.getUsername() + "  (" + role + ")" + balance);
    }

    private void showStatus(String msg, boolean isError) {
        statusLabel.setStyle(isError ? "-fx-text-fill: #c0392b;" : "-fx-text-fill: #27ae60;");
        statusLabel.setText(msg);
    }

    private void showItemDetailsCard(Item item) {
        String winnerId = item.getCurrentWinnerId();
        String winner = winnerId == null ? "No winner yet"
                : appState.userRepository.findById(winnerId).map(User::getUsername).orElse(winnerId);

        List<String> images = item.getImageDataList();
        int[] imageIndex = {0};
        ImageView imageView = new ImageView();
        imageView.setFitWidth(260);
        imageView.setFitHeight(220);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);

        VBox imagePane = new VBox();
        imagePane.setSpacing(14);
        imagePane.setMinWidth(280);
        imagePane.setAlignment(javafx.geometry.Pos.CENTER);
        imagePane.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #e2e8f0; -fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 12;");
        Label imageCounter = new Label();
        Button prevImageBtn = new Button("Previous");
        Button nextImageBtn = new Button("Next");
        HBox imageControls = new HBox(8, prevImageBtn, imageCounter, nextImageBtn);
        imageControls.setAlignment(javafx.geometry.Pos.CENTER);
        imageControls.setPadding(new Insets(10, 0, 0, 0));

        Runnable renderImage = () -> {
            if (images.isEmpty()) return;
            imageView.setImage(ItemImageSupport.toImage(images.get(imageIndex[0])));
            imageCounter.setText((imageIndex[0] + 1) + " / " + images.size());
            prevImageBtn.setDisable(images.size() <= 1);
            nextImageBtn.setDisable(images.size() <= 1);
        };
        prevImageBtn.setOnAction(e -> {
            imageIndex[0] = (imageIndex[0] - 1 + images.size()) % images.size();
            renderImage.run();
        });
        nextImageBtn.setOnAction(e -> {
            imageIndex[0] = (imageIndex[0] + 1) % images.size();
            renderImage.run();
        });

        if (images.isEmpty()) {
            Label noImage = new Label("No image");
            noImage.setStyle("-fx-text-fill: #64748b; -fx-font-size: 16px; -fx-font-weight: 600;");
            imagePane.getChildren().add(noImage);
        } else {
            imageView.setStyle("-fx-cursor: hand;");
            imageView.setOnMouseClicked(e ->
                    ItemImageSupport.openImageViewer(images.get(imageIndex[0]), stylesheetUrl()));
            renderImage.run();
            imagePane.getChildren().addAll(imageView, imageControls);
        }

        Label title = new Label(item.getName());
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: 800; -fx-text-fill: #0f172a;");
        title.setWrapText(true);

        Label price = new Label(formatPrice(item.getCurrentPrice()));
        price.setStyle("-fx-font-size: 20px; -fx-font-weight: 800; -fx-text-fill: #4f46e5;");

        Label description = new Label(item.getDescription() == null || item.getDescription().isBlank()
                ? "No description provided." : item.getDescription());
        description.setWrapText(true);
        description.setStyle("-fx-text-fill: #475569;");

        GridPane auction = detailGrid();
        int r = 0;
        r = addDetail(auction, r, "Type", typeName(item));
        r = addDetail(auction, r, "Status", item.getStatus().toString());
        r = addDetail(auction, r, "Starting price", "$" + String.format("%,.2f", item.getStartingPrice()));
        r = addDetail(auction, r, "Price step", "$" + String.format("%,.2f", item.getPriceStep()));
        r = addDetail(auction, r, "Start time", item.getStatus() != AuctionStatus.OPEN && item.getBidStartTime() != null ? item.getBidStartTime().format(DT_FMT) : "Not started");
        r = addDetail(auction, r, "End bid time", item.getStatus() != AuctionStatus.OPEN && item.getBidEndTime() != null ? item.getBidEndTime().format(DT_FMT) : "Not scheduled");
        addDetail(auction, r, "Winner", winner);

        GridPane specs = detailGrid();
        int s = 0;
        if (item instanceof Art a) {
            s = addDetail(specs, s, "Artist", a.getArtist());
            s = addDetail(specs, s, "Painting style", a.getPaintingStyle());
            addDetail(specs, s, "Origin", a.getOrigin());
        } else if (item instanceof Electronics e) {
            s = addDetail(specs, s, "Wattage", e.getWattage() + " W");
            s = addDetail(specs, s, "Origin", e.getOrigin());
            s = addDetail(specs, s, "Warranty", e.getWarrantyMonths() + " months");
            addDetail(specs, s, "Serial no.", e.getSerialNumber());
        } else if (item instanceof Vehicle v) {
            s = addDetail(specs, s, "Brand", v.getBrand());
            s = addDetail(specs, s, "Miles", String.valueOf(v.getMiles()));
            s = addDetail(specs, s, "Mfg date", v.getManufacturingDate() != null ? v.getManufacturingDate().toString() : "N/A");
            s = addDetail(specs, s, "VIN", v.getVin());
            addDetail(specs, s, "Accident history", v.hasAccidentHistory() ? "Yes" : "No");
        } else {
            addDetail(specs, s, "Category", "General item");
        }

        VBox info = new VBox(12, title, price, description, section("Auction", auction), section("Details", specs));
        HBox.setHgrow(info, Priority.ALWAYS);
        HBox root = new HBox(18, imagePane, info);
        root.setPadding(new Insets(18));
        root.setStyle("-fx-background-color: white;");

        Stage detailStage = new Stage();
        detailStage.setTitle("Item Details");
        Scene scene = new Scene(root, 760, 460);
        scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
        detailStage.setScene(scene);
        detailStage.show();
    }

    private VBox section(String title, GridPane grid) {
        Label label = new Label(title);
        label.setStyle("-fx-font-size: 14px; -fx-font-weight: 800; -fx-text-fill: #0f172a;");
        return new VBox(6, label, grid);
    }

    private GridPane detailGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(6);
        return grid;
    }

    private int addDetail(GridPane grid, int row, String key, String value) {
        Label k = new Label(key);
        k.setStyle("-fx-font-weight: 700; -fx-text-fill: #64748b;");
        Label v = new Label(value == null || value.isBlank() ? "N/A" : value);
        v.setWrapText(true);
        v.setStyle("-fx-text-fill: #0f172a;");
        grid.add(k, 0, row);
        grid.add(v, 1, row);
        return row + 1;
    }

    private String typeName(Item item) {
        return item.getTypeName();
    }

    private String stylesheetUrl() {
        return getClass().getResource("/css/style.css").toExternalForm();
    }

    private String validateItemFields(String type, TextField nameF, TextField startPriceF,
                                      TextField priceStepF, TextField durationMinsF,
                                      TextField wattageF, TextField warrantyF,
                                      TextField milesF, TextField mfgDateF) {
        String startPriceStr = startPriceF.getText().trim();
        String priceStepStr = priceStepF.getText().trim();
        if (nameF.getText().trim().isEmpty())
            return "Name is required.";
        if (startPriceF.getText().trim().isEmpty())
            return "Starting price cannot be empty.";
        if (priceStepF.getText().trim().isEmpty())
            return "Price step cannot be empty.";
        if (!startPriceStr.matches("\\d+(\\.\\d+)?"))
            return "Starting price must be a plain number (e.g. 100 or 99.99)";
        if (!priceStepStr.matches("\\d+(\\.\\d+)?"))
            return "Price step must be a plain number (e.g. 100 or 99.99)";
        try {
            double startPrice = Double.parseDouble(startPriceStr);
            if (startPrice <= 0) return "Start price must be positive.";
        } catch (NumberFormatException e) {
            return "Starting price is not a valid number.";
        }
        try {
            double priceStep = Double.parseDouble(priceStepStr);
            if (priceStep <= 0) return "Price step must be positive.";
        } catch (NumberFormatException e) {
            return "Price step is not a valid number.";
        }
        try { Double.parseDouble(startPriceF.getText().trim()); }
        catch (NumberFormatException e) { return "Starting price must be a number."; }
        try { Double.parseDouble(priceStepF.getText().trim()); }
        catch (NumberFormatException e) { return "Price step must be a number."; }
        try {
            int mins = Integer.parseInt(durationMinsF.getText().trim());
            if (mins <= 0) return "Duration must be a positive number of minutes.";
        } catch (NumberFormatException e) { return "Duration must be a whole number."; }

        switch (type) {
            case "Electronics" -> {
                try { Integer.parseInt(wattageF.getText().trim()); }
                catch (NumberFormatException e) { return "Wattage must be a whole number."; }
                try { Integer.parseInt(warrantyF.getText().trim()); }
                catch (NumberFormatException e) { return "Warranty months must be a whole number."; }
            }
            case "Vehicle" -> {
                try { Integer.parseInt(milesF.getText().trim()); }
                catch (NumberFormatException e) { return "Miles must be a whole number."; }
                try { LocalDate.parse(mfgDateF.getText().trim()); }
                catch (Exception e) { return "Manufacturing date must be YYYY-MM-DD (e.g. 2020-06-15)."; }
            }
        }
        return null;
    }

    // ── Create Item Dialog ────────────────────────────────────────────────────

    private Dialog<Item> buildItemDialog(Item existing) {
        boolean editing = existing != null;
        Dialog<Item> dlg = new Dialog<>();
        dlg.setTitle(editing ? "Edit Item" : "Create New Item");
        dlg.setHeaderText("Fill in item details");

        ButtonType createBtn = new ButtonType(editing ? "Save" : "Create", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(createBtn, ButtonType.CANCEL);

        TextField nameF         = field("Name");
        TextField descF         = field("Description");
        TextField startPriceF   = field("0");
        TextField priceStepF    = field("1");
        TextField durationMinsF = field("60");
        List<String> imageData = new ArrayList<>(editing ? existing.getImageDataList() : List.of());
        Label imageNameLbl = new Label(imageData.isEmpty() ? "No images selected" : imageData.size() + " image(s) selected");
        imageNameLbl.setStyle("-fx-text-fill: #475569;");
        Button chooseImageBtn = new Button("Choose Images");
        chooseImageBtn.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Choose item images");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif"));
            List<File> files = chooser.showOpenMultipleDialog(stage);
            if (files == null || files.isEmpty()) return;
            if (files.size() > MAX_ITEM_IMAGES) {
                imageNameLbl.setText("Choose up to " + MAX_ITEM_IMAGES + " images");
                return;
            }
            try {
                imageData.clear();
                for (File file : files) {
                    imageData.add(ItemImageSupport.imageDataUrl(file));
                }
                imageNameLbl.setText(files.size() + " image(s) selected");
            } catch (IOException ex) {
                imageNameLbl.setText("Could not load images");
            }
        });
        if (editing) {
            nameF.setText(existing.getName());
            descF.setText(existing.getDescription());
            startPriceF.setText(String.format("%.2f", existing.getStartingPrice()));
            priceStepF.setText(String.format("%.2f", existing.getPriceStep()));
            durationMinsF.setText(String.valueOf(durationMinutes(existing)));
        }

        ComboBox<String> typeBox = new ComboBox<>(
                FXCollections.observableArrayList("Other", "Art", "Electronics", "Vehicle"));
        typeBox.setValue(editing ? typeName(existing) : "Other");

        TextField artistF = field("Artist");        TextField styleF = field("Painting style"); TextField artOriginF = field("Origin");
        TextField wattageF = field("0");            TextField elecOriginF = field("Origin");    TextField warrantyF = field("12"); TextField serialF = field("Serial number");
        TextField milesF = field("0");              TextField mfgDateF = field("YYYY-MM-DD");   TextField brandF = field("Brand"); TextField vinF = field("VIN");
        CheckBox accidentBox = new CheckBox("Accident history");
        if (existing instanceof Art a) {
            artistF.setText(a.getArtist());
            styleF.setText(a.getPaintingStyle());
            artOriginF.setText(a.getOrigin());
        } else if (existing instanceof Electronics e) {
            wattageF.setText(String.valueOf(e.getWattage()));
            elecOriginF.setText(e.getOrigin());
            warrantyF.setText(String.valueOf(e.getWarrantyMonths()));
            serialF.setText(e.getSerialNumber());
        } else if (existing instanceof Vehicle v) {
            milesF.setText(String.valueOf(v.getMiles()));
            mfgDateF.setText(v.getManufacturingDate() != null ? v.getManufacturingDate().toString() : "");
            brandF.setText(v.getBrand());
            vinF.setText(v.getVin());
            accidentBox.setSelected(v.hasAccidentHistory());
        }

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(8);

        int r = 0;
        grid.add(new Label("Type:"),             0, r); grid.add(typeBox,       1, r++);
        grid.add(new Label("Name:"),             0, r); grid.add(nameF,         1, r++);
        grid.add(new Label("Description:"),      0, r); grid.add(descF,         1, r++);
        grid.add(new Label("Starting price:"),   0, r); grid.add(startPriceF,   1, r++);
        grid.add(new Label("Price step:"),       0, r); grid.add(priceStepF,    1, r++);
        grid.add(new Label("Duration (mins):"),  0, r); grid.add(durationMinsF, 1, r++);
        grid.add(new Label("Image:"),            0, r);
        grid.add(new HBox(8, chooseImageBtn, imageNameLbl), 1, r++);

        final int DYNAMIC_START = r;

        typeBox.setOnAction(e -> {
            grid.getChildren().removeIf(n -> GridPane.getRowIndex(n) != null && GridPane.getRowIndex(n) >= DYNAMIC_START);
            int dr = DYNAMIC_START;
            switch (typeBox.getValue()) {
                case "Art" -> {
                    grid.add(new Label("Artist:"),         0, dr); grid.add(artistF,     1, dr++);
                    grid.add(new Label("Painting style:"), 0, dr); grid.add(styleF,      1, dr++);
                    grid.add(new Label("Origin:"),         0, dr); grid.add(artOriginF,  1, dr);
                }
                case "Electronics" -> {
                    grid.add(new Label("Wattage:"),        0, dr); grid.add(wattageF,    1, dr++);
                    grid.add(new Label("Origin:"),         0, dr); grid.add(elecOriginF, 1, dr++);
                    grid.add(new Label("Warranty (mo):"),  0, dr); grid.add(warrantyF,   1, dr++);
                    grid.add(new Label("Serial no.:"),     0, dr); grid.add(serialF,     1, dr);
                }
                case "Vehicle" -> {
                    grid.add(new Label("Miles:"),          0, dr); grid.add(milesF,      1, dr++);
                    grid.add(new Label("Mfg date:"),       0, dr); grid.add(mfgDateF,    1, dr++);
                    grid.add(new Label("Brand:"),          0, dr); grid.add(brandF,      1, dr++);
                    grid.add(new Label("VIN:"),            0, dr); grid.add(vinF,        1, dr++);
                    grid.add(accidentBox,                  1, dr);
                }
            }
            if (dlg.getDialogPane().getScene() != null)
                dlg.getDialogPane().getScene().getWindow().sizeToScene();
        });

        Label itemErrorLbl = new Label();
        itemErrorLbl.setStyle("-fx-text-fill: #c0392b; -fx-font-size: 11px;");
        itemErrorLbl.setWrapText(true);
        itemErrorLbl.setMaxWidth(400);

        // Wrap grid + error label in VBox so the label is never inside the grid
        // (and therefore never removed by the dynamic row cleanup)
        javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(10, grid, itemErrorLbl);
        dlg.getDialogPane().setContent(content);
        typeBox.getOnAction().handle(null);

        // Event filter: validate, show error and keep dialog open on failure
        javafx.scene.Node createNode = dlg.getDialogPane().lookupButton(createBtn);
        createNode.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            String error = validateItemFields(typeBox.getValue(), nameF, startPriceF, priceStepF,
                    durationMinsF, wattageF, warrantyF, milesF, mfgDateF);
            if (error != null) {
                itemErrorLbl.setText(error);
                event.consume();
            } else {
                itemErrorLbl.setText("");
            }
        });

        dlg.setResultConverter(btn -> {
            if (btn != createBtn) return null;
            try {
                String id   = editing ? existing.getId() : UUID.randomUUID().toString();
                String name = nameF.getText().trim();
                String desc = descF.getText().trim();
                double sp   = Double.parseDouble(startPriceF.getText().trim());
                double step = Double.parseDouble(priceStepF.getText().trim());
                int    mins = Integer.parseInt(durationMinsF.getText().trim());
                LocalDateTime start = editing && existing.getBidStartTime() != null
                        ? existing.getBidStartTime()
                        : LocalDateTime.now();
                LocalDateTime end = start.plusMinutes(mins);
                String sid = editing ? existing.getSellerId() : appState.currentUser.getId();
                Map<String, Object> attrs = new LinkedHashMap<>();
                attrs.put("artist", artistF.getText().trim());
                attrs.put("paintingStyle", styleF.getText().trim());
                attrs.put("origin", typeBox.getValue().equals("Electronics") ? elecOriginF.getText().trim() : artOriginF.getText().trim());
                attrs.put("wattage", wattageF.getText().trim().isBlank() ? 0 : Integer.parseInt(wattageF.getText().trim()));
                attrs.put("warrantyMonths", warrantyF.getText().trim().isBlank() ? 0 : Integer.parseInt(warrantyF.getText().trim()));
                attrs.put("serialNumber", serialF.getText().trim());
                attrs.put("miles", milesF.getText().trim().isBlank() ? 0 : Integer.parseInt(milesF.getText().trim()));
                attrs.put("manufacturingDate", mfgDateF.getText().trim());
                attrs.put("brand", brandF.getText().trim());
                attrs.put("vin", vinF.getText().trim());
                attrs.put("accidentHistory", accidentBox.isSelected());
                attrs.put("imageDataList", new ArrayList<>(imageData));
                attrs.put("imageData", imageData.isEmpty() ? null : imageData.get(0));

                Item item = ItemFactory.defaultFactory().create(typeBox.getValue(), id, name, desc, sp, step, start, end, sid, attrs);
                if (editing) {
                    item.setStatus(existing.getStatus());
                    item.setCurrentWinnerId(existing.getCurrentWinnerId());
                    item.setVersion(existing.getVersion());
                }
                return item;
            } catch (Exception ex) {
                return null;
            }
        });

        return dlg;
    }

    private int durationMinutes(Item item) {
        if (item.getBidStartTime() == null || item.getBidEndTime() == null) return 60;
        return Math.max(1, (int) java.time.Duration.between(item.getBidStartTime(), item.getBidEndTime()).toMinutes());
    }

    private TextField field(String prompt) {
        TextField tf = new TextField();
        tf.setPromptText(prompt);
        tf.setPrefWidth(200);
        return tf;
    }
}
