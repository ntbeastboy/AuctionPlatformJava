package com.auction.controller;

import com.auction.app.AppState;
import com.auction.model.*;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class AuctionListController {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    @FXML private Label userInfoLabel;
    @FXML private Button addFundsBtn;
    @FXML private Button withdrawBtn;
    @FXML private TableView<Item> itemTable;
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
    @FXML private Label statusLabel;

    private AppState appState;
    private Stage stage;
    private Consumer<String> updateListener;

    public void init(AppState appState, Stage stage) {
        this.appState = appState;
        this.stage = stage;
        setupTable();
        configureRoleButtons();
        refreshTable();
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
        colName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        itemTable.setRowFactory(tv -> {
            TableRow<Item> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty())
                    showItemDetails(row.getItem());
            });
            return row;
        });
        colType.setCellValueFactory(c -> new SimpleStringProperty(typeName(c.getValue())));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colPrice.setCellValueFactory(c ->
            new SimpleStringProperty(formatPrice(c.getValue().getCurrentPrice())));

        colMinBid.setCellValueFactory(c -> {
            Item i = c.getValue();
            return new SimpleStringProperty(formatPrice(i.getCurrentPrice() + i.getPriceStep()));
        });
        colEndTime.setCellValueFactory(c -> {
            Item item = c.getValue();
            if (item.getStatus() == AuctionStatus.OPEN) return new SimpleStringProperty("—");
            LocalDateTime end = item.getBidEndTime();
            return new SimpleStringProperty(end != null ? end.format(DT_FMT) : "—");
        });
        colWinner.setCellValueFactory(c -> {
            String winnerId = c.getValue().getCurrentWinnerId();
            if (winnerId == null) return new SimpleStringProperty("—");
            String username = appState.userRepository.findById(winnerId)
                    .map(User::getUsername).orElse(winnerId);
            return new SimpleStringProperty(username);
        });
    }

    private void configureRoleButtons() {
        if (appState.currentUser instanceof Admin) {
            show(editItemBtn, startBtn, endEarlyBtn, cancelBtn, deleteBtn);
        } else if (appState.currentUser instanceof Seller) {
            show(addFundsBtn, withdrawBtn, createItemBtn, editItemBtn, deleteBtn, startBtn, placeBidBtn);
        } else if (appState.currentUser instanceof Bidder) {
            show(addFundsBtn, withdrawBtn, placeBidBtn);
        }
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
        showCardDialog().ifPresent(amount -> {
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
                .filter(i -> i.getStatus() == AuctionStatus.RUNNING
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
    }

    private void registerRealtimeUpdates() {
        removeRealtimeUpdates();
        updateListener = message -> Platform.runLater(() -> {
            refreshCurrentUser();
            refreshTable();
        });
        appState.httpClient.addUpdateListener(updateListener);
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

    private void showItemDetails(Item item) {
        String winnerId = item.getCurrentWinnerId();
        String winner = winnerId == null ? "—"
                : appState.userRepository.findById(winnerId).map(User::getUsername).orElse(winnerId);

        GridPane grid = new GridPane();
        grid.setHgap(16); grid.setVgap(6);
        int r = 0;

        // Common fields
        r = addRow(grid, r, "Name",          item.getName());
        r = addRow(grid, r, "Description",   item.getDescription());
        r = addRow(grid, r, "Type",          typeName(item));
        r = addRow(grid, r, "Status",        item.getStatus().toString());
        r = addRow(grid, r, "Starting price","$" + String.format("%,.2f", item.getStartingPrice()));
        r = addRow(grid, r, "Current price", "$" + String.format("%,.2f", item.getCurrentPrice()));
        r = addRow(grid, r, "Price step",    "$" + String.format("%,.2f", item.getPriceStep()));
        r = addRow(grid, r, "Start time",    item.getStatus() != AuctionStatus.OPEN && item.getBidStartTime() != null ? item.getBidStartTime().format(DT_FMT) : "—");
        r = addRow(grid, r, "End time",      item.getStatus() != AuctionStatus.OPEN && item.getBidEndTime()   != null ? item.getBidEndTime().format(DT_FMT)   : "—");
        r = addRow(grid, r, "Winner",        winner);

        // Type-specific fields
        if (item instanceof Art a) {
            r = addRow(grid, r, "Artist",        a.getArtist());
            r = addRow(grid, r, "Painting style",a.getPaintingStyle());
            r = addRow(grid, r, "Origin",        a.getOrigin());
        } else if (item instanceof Electronics e) {
            r = addRow(grid, r, "Wattage",       e.getWattage() + " W");
            r = addRow(grid, r, "Origin",        e.getOrigin());
            r = addRow(grid, r, "Warranty",      e.getWarrantyMonths() + " months");
            r = addRow(grid, r, "Serial no.",    e.getSerialNumber());
        } else if (item instanceof Vehicle v) {
            r = addRow(grid, r, "Brand",         v.getBrand());
            r = addRow(grid, r, "Miles",         String.valueOf(v.getMiles()));
            r = addRow(grid, r, "Mfg date",      v.getManufacturingDate() != null ? v.getManufacturingDate().toString() : "—");
            r = addRow(grid, r, "VIN",           v.getVin());
            r = addRow(grid, r, "Accident history", v.hasAccidentHistory() ? "Yes" : "No");
        }
        Stage detailStage = new Stage();
        detailStage.setTitle("Item Details");
        VBox root = new VBox();
        grid.setPadding(new Insets(13));
        root.getChildren().add(grid);
        Scene scene = new Scene(root, 300, 400);
        detailStage.setScene(scene);
        detailStage.show();
    }

    private int addRow(GridPane grid, int row, String key, String value) {
        Label k = new Label(key + ":");
        k.setStyle("-fx-font-weight: bold; -fx-text-fill: #555;");
        Label v = new Label(value != null ? value : "—");
        v.setWrapText(true); v.setMaxWidth(300);
        grid.add(k, 0, row);
        grid.add(v, 1, row);
        return row + 1;
    }

    private String typeName(Item item) {
        return item.getTypeName();
    }

    // ── Credit Card Dialog ────────────────────────────────────────────────────

    /**
     * Shows a dialog asking for amount and card details.
     * Returns the validated amount, or empty if cancelled or validation failed.
     */
    private java.util.Optional<Double> showCardDialog() {
        Dialog<Double> dlg = new Dialog<>();
        dlg.setTitle("Add Funds");
        dlg.setHeaderText("Enter payment details");

        ButtonType confirmBtn = new ButtonType("Confirm", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(confirmBtn, ButtonType.CANCEL);

        TextField amountF = field("e.g. 100");
        TextField nameF   = field("Cardholder name");
        TextField cardF   = field("1234 5678 9012 3456");
        TextField expiryF = field("MM/YY");
        TextField cvvF    = field("123");

        Label errorLbl = new Label();
        errorLbl.setStyle("-fx-text-fill: #c0392b; -fx-font-size: 11px;");
        errorLbl.setWrapText(true);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(8);
        grid.add(new Label("Amount ($):"),      0, 0); grid.add(amountF, 1, 0);
        grid.add(new Label("Cardholder name:"), 0, 1); grid.add(nameF,   1, 1);
        grid.add(new Label("Card number:"),     0, 2); grid.add(cardF,   1, 2);
        grid.add(new Label("Expiry (MM/YY):"),  0, 3); grid.add(expiryF, 1, 3);
        grid.add(new Label("CVV:"),             0, 4); grid.add(cvvF,    1, 4);
        grid.add(errorLbl,                      0, 5, 2, 1);
        dlg.getDialogPane().setContent(grid);

        // Event filter on the confirm button: validate and consume (keep dialog open) on any error
        javafx.scene.Node confirmNode = dlg.getDialogPane().lookupButton(confirmBtn);
        confirmNode.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            String error = validateCard(amountF, nameF, cardF, expiryF, cvvF);
            if (error != null) {
                errorLbl.setText(error);
                event.consume(); // prevents the dialog from closing
            } else {
                errorLbl.setText("");
            }
        });

        dlg.setResultConverter(btn -> {
            if (btn != confirmBtn) return null;
            // Validation already passed via the event filter above
            try {
                return Double.parseDouble(amountF.getText().trim());
            } catch (NumberFormatException e) {
                return null;
            }
        });

        return dlg.showAndWait();
    }

    private String validateItemFields(String type, TextField nameF, TextField startPriceF,
                                      TextField priceStepF, TextField durationMinsF,
                                      TextField wattageF, TextField warrantyF,
                                      TextField milesF, TextField mfgDateF) {
        if (nameF.getText().trim().isEmpty())
            return "Name is required.";
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

    /** Returns an error message string, or null if everything is valid. */
    private String validateCard(TextField amountF, TextField nameF, TextField cardF,
                                TextField expiryF, TextField cvvF) {
        // Amount — plain decimal only; reject scientific notation (e.g. 1e3).
        String amountStr = amountF.getText().trim();
        if (!amountStr.matches("\\d+(\\.\\d+)?"))
            return "Amount must be a plain number (e.g. 100 or 99.99)";
        try {
            double amount = Double.parseDouble(amountStr);
            if (amount <= 0) return "Amount must be positive.";
        } catch (NumberFormatException e) {
            return "Amount is not a valid number.";
        }

        // Name
        if (nameF.getText().trim().isEmpty())
            return "Cardholder name is required.";

        // Card number
        String cardNum = cardF.getText().replaceAll("[\\s\\-]", "");
        if (!cardNum.matches("\\d{13,19}"))
            return "Card number must be 13–19 digits.";
        if (!passesLuhn(cardNum))
            return "Invalid card number (failed Luhn check).";

        // Expiry
        String expiry = expiryF.getText().trim();
        if (!expiry.matches("(0[1-9]|1[0-2])/\\d{2}"))
            return "Expiry must be MM/YY (e.g. 08/27).";
        String[] parts = expiry.split("/");
        YearMonth cardExpiry = YearMonth.of(2000 + Integer.parseInt(parts[1]), Integer.parseInt(parts[0]));
        if (cardExpiry.isBefore(YearMonth.now()))
            return "Card has expired.";

        // CVV
        if (!cvvF.getText().trim().matches("\\d{3,4}"))
            return "CVV must be 3 or 4 digits.";

        return null; // all valid
    }

    /** Luhn algorithm check. */
    private boolean passesLuhn(String number) {
        int sum = 0;
        boolean alternate = false;
        for (int i = number.length() - 1; i >= 0; i--) {
            int n = Character.getNumericValue(number.charAt(i));
            if (alternate) { n *= 2; if (n > 9) n -= 9; }
            sum += n;
            alternate = !alternate;
        }
        return sum % 10 == 0;
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
