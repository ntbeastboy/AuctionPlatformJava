package com.auction.controller;

import com.auction.app.AppState;
import com.auction.model.User;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

import java.io.IOException;

public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;

    private AppState appState;
    private Stage stage;

    public void init(AppState appState, Stage stage) {
        this.appState = appState;
        this.stage = stage;
    }

    public void showMessage(String msg, boolean isError) {
        errorLabel.setStyle(isError ? "-fx-text-fill: #c0392b;" : "-fx-text-fill: #27ae60;");
        errorLabel.setText(msg);
    }

    @FXML
    private void onLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        try {
            User user = appState.userService.login(username, password);
            appState.currentUser = user;
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/auction-list.fxml"));
            Scene scene = new Scene(loader.load(), 900, 580);
            scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
            AuctionListController controller = loader.getController();
            controller.init(appState, stage);
            stage.setScene(scene);
            stage.setResizable(true);
        } catch (Exception e) {
            showMessage(e.getMessage(), true);
        }
    }

    @FXML
    private void onGoToRegister() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/register.fxml"));
            Scene scene = new Scene(loader.load(), 520, 440);
            scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
            RegisterController controller = loader.getController();
            controller.init(appState, stage);
            stage.setScene(scene);
        } catch (IOException e) {
            errorLabel.setText("Failed to load register page.");
        }
    }

    @FXML
    private void onConfigureServer() {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Server");
        dialog.setHeaderText("Connection settings");

        ButtonType saveButton = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButton, ButtonType.CANCEL);

        TextField serverUrlField = new TextField(appState.httpClient.getBaseUrl());
        serverUrlField.setPrefWidth(320);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.add(new Label("Base URL:"), 0, 0);
        grid.add(serverUrlField, 1, 0);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(button -> button == saveButton ? serverUrlField.getText() : null);
        dialog.showAndWait().ifPresent(url -> {
            try {
                appState.httpClient.setBaseUrl(url);
                showMessage("Server set to " + appState.httpClient.getBaseUrl(), false);
            } catch (IllegalArgumentException e) {
                showMessage(e.getMessage(), true);
            }
        });
    }
}
