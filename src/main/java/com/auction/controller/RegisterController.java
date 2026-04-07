package com.auction.controller;

import com.auction.app.AppState;
import com.auction.service.UserService.RegisterRole;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;

public class RegisterController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private RadioButton bidderRadio;
    @FXML private RadioButton sellerRadio;
    @FXML private Label errorLabel;

    private AppState appState;
    private Stage stage;

    public void init(AppState appState, Stage stage) {
        this.appState = appState;
        this.stage = stage;
        bidderRadio.setSelected(true);
    }

    @FXML
    private void onRegister() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        String confirm  = confirmPasswordField.getText();

        if (!password.equals(confirm)) {
            showError("Passwords do not match.");
            return;
        }

        RegisterRole role = sellerRadio.isSelected() ? RegisterRole.SELLER : RegisterRole.BIDDER;
        try {
            appState.userService.register(username, password, role);
            goToLogin("Account created! You can now log in.");
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    @FXML
    private void onBackToLogin() {
        goToLogin(null);
    }

    private void goToLogin(String successMsg) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            Scene scene = new Scene(loader.load(), 420, 300);
            scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
            LoginController controller = loader.getController();
            controller.init(appState, stage);
            if (successMsg != null) {
                controller.showMessage(successMsg, false);
            }
            stage.setScene(scene);
        } catch (IOException e) {
            showError("Navigation error: " + e.getMessage());
        }
    }

    private void showError(String msg) {
        errorLabel.setStyle("-fx-text-fill: #c0392b;");
        errorLabel.setText(msg);
    }
}
