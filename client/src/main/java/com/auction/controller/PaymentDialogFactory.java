package com.auction.controller;

import java.time.YearMonth;
import java.util.Optional;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

final class PaymentDialogFactory {

  private PaymentDialogFactory() {}

  static Optional<Double> showAddFundsDialog() {
    Dialog<Double> dialog = new Dialog<>();
    dialog.setTitle("Add Funds");
    dialog.setHeaderText("Enter payment details");

    ButtonType confirmButton = new ButtonType("Confirm", ButtonBar.ButtonData.OK_DONE);
    dialog.getDialogPane().getButtonTypes().addAll(confirmButton, ButtonType.CANCEL);

    TextField amountField = field("e.g. 100");
    TextField nameField = field("Cardholder name");
    TextField cardField = field("1234 5678 9012 3456");
    TextField expiryField = field("MM/YY");
    TextField cvvField = field("123");

    Label errorLabel = new Label();
    errorLabel.setStyle("-fx-text-fill: #c0392b; -fx-font-size: 11px;");
    errorLabel.setWrapText(true);

    GridPane grid = new GridPane();
    grid.setHgap(10);
    grid.setVgap(8);
    grid.add(new Label("Amount ($):"), 0, 0);
    grid.add(amountField, 1, 0);
    grid.add(new Label("Cardholder name:"), 0, 1);
    grid.add(nameField, 1, 1);
    grid.add(new Label("Card number:"), 0, 2);
    grid.add(cardField, 1, 2);
    grid.add(new Label("Expiry (MM/YY):"), 0, 3);
    grid.add(expiryField, 1, 3);
    grid.add(new Label("CVV:"), 0, 4);
    grid.add(cvvField, 1, 4);
    grid.add(errorLabel, 0, 5, 2, 1);
    dialog.getDialogPane().setContent(grid);

    Node confirmNode = dialog.getDialogPane().lookupButton(confirmButton);
    confirmNode.addEventFilter(
        ActionEvent.ACTION,
        event -> {
          String error = validateCard(amountField, nameField, cardField, expiryField, cvvField);
          if (error != null) {
            errorLabel.setText(error);
            event.consume();
          } else {
            errorLabel.setText("");
          }
        });

    dialog.setResultConverter(
        button ->
            button == confirmButton ? Double.parseDouble(amountField.getText().trim()) : null);

    return dialog.showAndWait();
  }

  private static String validateCard(
      TextField amountField,
      TextField nameField,
      TextField cardField,
      TextField expiryField,
      TextField cvvField) {
    String amount = amountField.getText().trim();
    if (!amount.matches("\\d+(\\.\\d+)?")) {
      return "Amount must be a plain number (e.g. 100 or 99.99)";
    }
    if (Double.parseDouble(amount) <= 0) {
      return "Amount must be positive.";
    }

    if (nameField.getText().trim().isEmpty()) {
      return "Cardholder name is required.";
    }

    String cardNumber = cardField.getText().replaceAll("[\\s\\-]", "");
    if (!cardNumber.matches("\\d{13,19}")) {
      return "Card number must be 13-19 digits.";
    }
    if (!passesLuhn(cardNumber)) {
      return "Invalid card number (failed Luhn check).";
    }

    String expiry = expiryField.getText().trim();
    if (!expiry.matches("(0[1-9]|1[0-2])/\\d{2}")) {
      return "Expiry must be MM/YY (e.g. 08/27).";
    }
    String[] parts = expiry.split("/");
    YearMonth cardExpiry =
        YearMonth.of(2000 + Integer.parseInt(parts[1]), Integer.parseInt(parts[0]));
    if (cardExpiry.isBefore(YearMonth.now())) {
      return "Card has expired.";
    }

    if (!cvvField.getText().trim().matches("\\d{3,4}")) {
      return "CVV must be 3 or 4 digits.";
    }

    return null;
  }

  private static boolean passesLuhn(String number) {
    int sum = 0;
    boolean alternate = false;
    for (int i = number.length() - 1; i >= 0; i--) {
      int digit = Character.getNumericValue(number.charAt(i));
      if (alternate) {
        digit *= 2;
        if (digit > 9) {
          digit -= 9;
        }
      }
      sum += digit;
      alternate = !alternate;
    }
    return sum % 10 == 0;
  }

  private static TextField field(String prompt) {
    TextField field = new TextField();
    field.setPromptText(prompt);
    field.setPrefWidth(200);
    return field;
  }
}
