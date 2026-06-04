package com.auction.controller;

import com.auction.app.AppState;
import com.auction.model.Art;
import com.auction.model.Electronics;
import com.auction.model.Item;
import com.auction.model.ItemFactory;
import com.auction.model.Vehicle;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

final class ItemDialogFactory {

  private static final int MAX_ITEM_IMAGES = 8;

  private ItemDialogFactory() {}

  static Dialog<Item> build(AppState appState, Stage stage, Item existing) {
    boolean editing = existing != null;
    Dialog<Item> dialog = new Dialog<>();
    dialog.setTitle(editing ? "Edit Item" : "Create New Item");
    dialog.setHeaderText("Fill in item details");

    ButtonType saveButton =
        new ButtonType(editing ? "Save" : "Create", ButtonBar.ButtonData.OK_DONE);
    dialog.getDialogPane().getButtonTypes().addAll(saveButton, ButtonType.CANCEL);

    TextField nameField = field("Name");
    TextField descriptionField = field("Description");
    TextField startPriceField = field("0");
    TextField priceStepField = field("1");
    TextField durationMinutesField = field("60");

    List<String> imageData = new ArrayList<>(editing ? existing.getImageDataList() : List.of());
    Label imageNameLabel =
        new Label(
            imageData.isEmpty() ? "No images selected" : imageData.size() + " image(s) selected");
    imageNameLabel.setStyle("-fx-text-fill: #475569;");

    Button chooseImageButton = new Button("Choose Images");
    chooseImageButton.setOnAction(event -> chooseImages(stage, imageData, imageNameLabel));

    if (editing) {
      nameField.setText(existing.getName());
      descriptionField.setText(existing.getDescription());
      startPriceField.setText(String.format("%.2f", existing.getStartingPrice()));
      priceStepField.setText(String.format("%.2f", existing.getPriceStep()));
      durationMinutesField.setText(String.valueOf(durationMinutes(existing)));
    }

    ComboBox<String> typeBox =
        new ComboBox<>(FXCollections.observableArrayList("Other", "Art", "Electronics", "Vehicle"));
    typeBox.setValue(editing ? typeName(existing) : "Other");

    TextField artistField = field("Artist");
    TextField styleField = field("Painting style");
    TextField artOriginField = field("Origin");
    TextField wattageField = field("0");
    TextField electronicsOriginField = field("Origin");
    TextField warrantyField = field("12");
    TextField serialField = field("Serial number");
    TextField milesField = field("0");
    TextField manufacturingDateField = field("YYYY-MM-DD");
    TextField brandField = field("Brand");
    TextField vinField = field("VIN");
    CheckBox accidentBox = new CheckBox("Accident history");
    populateExistingFields(
        existing,
        artistField,
        styleField,
        artOriginField,
        wattageField,
        electronicsOriginField,
        warrantyField,
        serialField,
        milesField,
        manufacturingDateField,
        brandField,
        vinField,
        accidentBox);

    GridPane grid = new GridPane();
    grid.setHgap(10);
    grid.setVgap(8);

    int row = 0;
    grid.add(new Label("Type:"), 0, row);
    grid.add(typeBox, 1, row++);
    grid.add(new Label("Name:"), 0, row);
    grid.add(nameField, 1, row++);
    grid.add(new Label("Description:"), 0, row);
    grid.add(descriptionField, 1, row++);
    grid.add(new Label("Starting price:"), 0, row);
    grid.add(startPriceField, 1, row++);
    grid.add(new Label("Price step:"), 0, row);
    grid.add(priceStepField, 1, row++);
    grid.add(new Label("Duration (mins):"), 0, row);
    grid.add(durationMinutesField, 1, row++);
    grid.add(new Label("Image:"), 0, row);
    grid.add(new HBox(8, chooseImageButton, imageNameLabel), 1, row++);

    int dynamicStart = row;
    typeBox.setOnAction(
        event ->
            renderDynamicFields(
                dialog,
                grid,
                dynamicStart,
                typeBox.getValue(),
                artistField,
                styleField,
                artOriginField,
                wattageField,
                electronicsOriginField,
                warrantyField,
                serialField,
                milesField,
                manufacturingDateField,
                brandField,
                vinField,
                accidentBox));

    Label itemErrorLabel = new Label();
    itemErrorLabel.setStyle("-fx-text-fill: #c0392b; -fx-font-size: 11px;");
    itemErrorLabel.setWrapText(true);
    itemErrorLabel.setMaxWidth(400);

    dialog.getDialogPane().setContent(new VBox(10, grid, itemErrorLabel));
    typeBox.getOnAction().handle(null);

    Node saveNode = dialog.getDialogPane().lookupButton(saveButton);
    saveNode.addEventFilter(
        ActionEvent.ACTION,
        event -> {
          String error =
              validateItemFields(
                  typeBox.getValue(),
                  nameField,
                  startPriceField,
                  priceStepField,
                  durationMinutesField,
                  wattageField,
                  warrantyField,
                  milesField,
                  manufacturingDateField);
          if (error != null) {
            itemErrorLabel.setText(error);
            event.consume();
          } else {
            itemErrorLabel.setText("");
          }
        });

    dialog.setResultConverter(
        button ->
            button == saveButton
                ? createItem(
                    appState,
                    existing,
                    typeBox.getValue(),
                    nameField,
                    descriptionField,
                    startPriceField,
                    priceStepField,
                    durationMinutesField,
                    artistField,
                    styleField,
                    artOriginField,
                    wattageField,
                    electronicsOriginField,
                    warrantyField,
                    serialField,
                    milesField,
                    manufacturingDateField,
                    brandField,
                    vinField,
                    accidentBox,
                    imageData)
                : null);

    return dialog;
  }

  private static void chooseImages(Stage stage, List<String> imageData, Label imageNameLabel) {
    FileChooser chooser = new FileChooser();
    chooser.setTitle("Choose item images");
    chooser
        .getExtensionFilters()
        .add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif"));
    List<File> files = chooser.showOpenMultipleDialog(stage);
    if (files == null || files.isEmpty()) {
      return;
    }
    if (files.size() > MAX_ITEM_IMAGES) {
      imageNameLabel.setText("Choose up to " + MAX_ITEM_IMAGES + " images");
      return;
    }

    try {
      imageData.clear();
      for (File file : files) {
        imageData.add(ItemImageSupport.imageDataUrl(file));
      }
      imageNameLabel.setText(files.size() + " image(s) selected");
    } catch (IOException e) {
      imageNameLabel.setText("Could not load images");
    }
  }

  private static void populateExistingFields(
      Item existing,
      TextField artistField,
      TextField styleField,
      TextField artOriginField,
      TextField wattageField,
      TextField electronicsOriginField,
      TextField warrantyField,
      TextField serialField,
      TextField milesField,
      TextField manufacturingDateField,
      TextField brandField,
      TextField vinField,
      CheckBox accidentBox) {
    if (existing instanceof Art art) {
      artistField.setText(art.getArtist());
      styleField.setText(art.getPaintingStyle());
      artOriginField.setText(art.getOrigin());
    } else if (existing instanceof Electronics electronics) {
      wattageField.setText(String.valueOf(electronics.getWattage()));
      electronicsOriginField.setText(electronics.getOrigin());
      warrantyField.setText(String.valueOf(electronics.getWarrantyMonths()));
      serialField.setText(electronics.getSerialNumber());
    } else if (existing instanceof Vehicle vehicle) {
      milesField.setText(String.valueOf(vehicle.getMiles()));
      manufacturingDateField.setText(
          vehicle.getManufacturingDate() != null ? vehicle.getManufacturingDate().toString() : "");
      brandField.setText(vehicle.getBrand());
      vinField.setText(vehicle.getVin());
      accidentBox.setSelected(vehicle.hasAccidentHistory());
    }
  }

  private static void renderDynamicFields(
      Dialog<Item> dialog,
      GridPane grid,
      int dynamicStart,
      String type,
      TextField artistField,
      TextField styleField,
      TextField artOriginField,
      TextField wattageField,
      TextField electronicsOriginField,
      TextField warrantyField,
      TextField serialField,
      TextField milesField,
      TextField manufacturingDateField,
      TextField brandField,
      TextField vinField,
      CheckBox accidentBox) {
    grid.getChildren()
        .removeIf(
            node ->
                GridPane.getRowIndex(node) != null && GridPane.getRowIndex(node) >= dynamicStart);
    int row = dynamicStart;
    switch (type) {
      case "Art" -> {
        grid.add(new Label("Artist:"), 0, row);
        grid.add(artistField, 1, row++);
        grid.add(new Label("Painting style:"), 0, row);
        grid.add(styleField, 1, row++);
        grid.add(new Label("Origin:"), 0, row);
        grid.add(artOriginField, 1, row);
      }
      case "Electronics" -> {
        grid.add(new Label("Wattage:"), 0, row);
        grid.add(wattageField, 1, row++);
        grid.add(new Label("Origin:"), 0, row);
        grid.add(electronicsOriginField, 1, row++);
        grid.add(new Label("Warranty (mo):"), 0, row);
        grid.add(warrantyField, 1, row++);
        grid.add(new Label("Serial no.:"), 0, row);
        grid.add(serialField, 1, row);
      }
      case "Vehicle" -> {
        grid.add(new Label("Miles:"), 0, row);
        grid.add(milesField, 1, row++);
        grid.add(new Label("Mfg date:"), 0, row);
        grid.add(manufacturingDateField, 1, row++);
        grid.add(new Label("Brand:"), 0, row);
        grid.add(brandField, 1, row++);
        grid.add(new Label("VIN:"), 0, row);
        grid.add(vinField, 1, row++);
        grid.add(accidentBox, 1, row);
      }
      default -> {}
    }
    if (dialog.getDialogPane().getScene() != null) {
      dialog.getDialogPane().getScene().getWindow().sizeToScene();
    }
  }

  private static String validateItemFields(
      String type,
      TextField nameField,
      TextField startPriceField,
      TextField priceStepField,
      TextField durationMinutesField,
      TextField wattageField,
      TextField warrantyField,
      TextField milesField,
      TextField manufacturingDateField) {
    String startPrice = startPriceField.getText().trim();
    String priceStep = priceStepField.getText().trim();
    if (nameField.getText().trim().isEmpty()) {
      return "Name is required.";
    }
    if (startPrice.isEmpty()) {
      return "Starting price cannot be empty.";
    }
    if (priceStep.isEmpty()) {
      return "Price step cannot be empty.";
    }
    if (!startPrice.matches("\\d+(\\.\\d+)?")) {
      return "Starting price must be a plain number (e.g. 100 or 99.99)";
    }
    if (!priceStep.matches("\\d+(\\.\\d+)?")) {
      return "Price step must be a plain number (e.g. 100 or 99.99)";
    }
    try {
      if (Double.parseDouble(startPrice) <= 0) {
        return "Start price must be positive.";
      }
    } catch (NumberFormatException e) {
      return "Starting price is not a valid number.";
    }
    try {
      if (Double.parseDouble(priceStep) <= 0) {
        return "Price step must be positive.";
      }
    } catch (NumberFormatException e) {
      return "Price step is not a valid number.";
    }
    try {
      int minutes = Integer.parseInt(durationMinutesField.getText().trim());
      if (minutes <= 0) {
        return "Duration must be a positive number of minutes.";
      }
    } catch (NumberFormatException e) {
      return "Duration must be a whole number.";
    }

    switch (type) {
      case "Electronics" -> {
        try {
          Integer.parseInt(wattageField.getText().trim());
        } catch (NumberFormatException e) {
          return "Wattage must be a whole number.";
        }
        try {
          Integer.parseInt(warrantyField.getText().trim());
        } catch (NumberFormatException e) {
          return "Warranty months must be a whole number.";
        }
      }
      case "Vehicle" -> {
        try {
          Integer.parseInt(milesField.getText().trim());
        } catch (NumberFormatException e) {
          return "Miles must be a whole number.";
        }
        try {
          LocalDate.parse(manufacturingDateField.getText().trim());
        } catch (Exception e) {
          return "Manufacturing date must be YYYY-MM-DD (e.g. 2020-06-15).";
        }
      }
      default -> {}
    }
    return null;
  }

  private static Item createItem(
      AppState appState,
      Item existing,
      String type,
      TextField nameField,
      TextField descriptionField,
      TextField startPriceField,
      TextField priceStepField,
      TextField durationMinutesField,
      TextField artistField,
      TextField styleField,
      TextField artOriginField,
      TextField wattageField,
      TextField electronicsOriginField,
      TextField warrantyField,
      TextField serialField,
      TextField milesField,
      TextField manufacturingDateField,
      TextField brandField,
      TextField vinField,
      CheckBox accidentBox,
      List<String> imageData) {
    boolean editing = existing != null;
    try {
      String id = editing ? existing.getId() : UUID.randomUUID().toString();
      String name = nameField.getText().trim();
      String description = descriptionField.getText().trim();
      double startingPrice = Double.parseDouble(startPriceField.getText().trim());
      double priceStep = Double.parseDouble(priceStepField.getText().trim());
      int minutes = Integer.parseInt(durationMinutesField.getText().trim());
      LocalDateTime start =
          editing && existing.getBidStartTime() != null
              ? existing.getBidStartTime()
              : LocalDateTime.now();
      LocalDateTime end = start.plusMinutes(minutes);
      String sellerId = editing ? existing.getSellerId() : appState.currentUser.getId();
      Map<String, Object> attrs =
          attributes(
              type,
              artistField,
              styleField,
              artOriginField,
              wattageField,
              electronicsOriginField,
              warrantyField,
              serialField,
              milesField,
              manufacturingDateField,
              brandField,
              vinField,
              accidentBox,
              imageData);

      Item item =
          ItemFactory.defaultFactory()
              .create(
                  type,
                  id,
                  name,
                  description,
                  startingPrice,
                  priceStep,
                  start,
                  end,
                  sellerId,
                  attrs);
      if (editing) {
        item.setStatus(existing.getStatus());
        item.setCurrentWinnerId(existing.getCurrentWinnerId());
        item.setVersion(existing.getVersion());
      }
      return item;
    } catch (Exception e) {
      return null;
    }
  }

  private static Map<String, Object> attributes(
      String type,
      TextField artistField,
      TextField styleField,
      TextField artOriginField,
      TextField wattageField,
      TextField electronicsOriginField,
      TextField warrantyField,
      TextField serialField,
      TextField milesField,
      TextField manufacturingDateField,
      TextField brandField,
      TextField vinField,
      CheckBox accidentBox,
      List<String> imageData) {
    Map<String, Object> attrs = new LinkedHashMap<>();
    attrs.put("artist", artistField.getText().trim());
    attrs.put("paintingStyle", styleField.getText().trim());
    attrs.put(
        "origin",
        type.equals("Electronics")
            ? electronicsOriginField.getText().trim()
            : artOriginField.getText().trim());
    attrs.put(
        "wattage",
        wattageField.getText().trim().isBlank()
            ? 0
            : Integer.parseInt(wattageField.getText().trim()));
    attrs.put(
        "warrantyMonths",
        warrantyField.getText().trim().isBlank()
            ? 0
            : Integer.parseInt(warrantyField.getText().trim()));
    attrs.put("serialNumber", serialField.getText().trim());
    attrs.put(
        "miles",
        milesField.getText().trim().isBlank() ? 0 : Integer.parseInt(milesField.getText().trim()));
    attrs.put("manufacturingDate", manufacturingDateField.getText().trim());
    attrs.put("brand", brandField.getText().trim());
    attrs.put("vin", vinField.getText().trim());
    attrs.put("accidentHistory", accidentBox.isSelected());
    attrs.put("imageDataList", new ArrayList<>(imageData));
    attrs.put("imageData", imageData.isEmpty() ? null : imageData.get(0));
    return attrs;
  }

  private static int durationMinutes(Item item) {
    if (item.getBidStartTime() == null || item.getBidEndTime() == null) {
      return 60;
    }
    long minutes = Duration.between(item.getBidStartTime(), item.getBidEndTime()).toMinutes();
    return Math.max(1, (int) minutes);
  }

  private static String typeName(Item item) {
    return item.getTypeName();
  }

  private static TextField field(String prompt) {
    TextField field = new TextField();
    field.setPromptText(prompt);
    field.setPrefWidth(200);
    return field;
  }
}
