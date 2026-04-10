package com.auction.controller;

import com.auction.app.AppState;
import com.auction.model.User;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
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
            Scene scene = new Scene(loader.load(), 420, 360);
            scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
            RegisterController controller = loader.getController();
            controller.init(appState, stage);
            stage.setScene(scene);
        } catch (IOException e) {
            showMessage("Failed to load register page.", true);
        }
    }
}
