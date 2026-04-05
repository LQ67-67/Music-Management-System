package com.example.musiclibrary.controller;

import com.example.musiclibrary.dao.UserDao;
import com.example.musiclibrary.model.User;
import com.example.musiclibrary.session.SessionManager;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.layout.GridPane;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.net.URL;
import java.sql.SQLException;

public class LoginController {

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Label errorLabel;

    @FXML
    private Label infoLabel;

    private UserDao userDao;

    // Initialize method - called when FXML is loaded
    @FXML
    private void initialize() {
        userDao = new UserDao();
        // Limit username to 20 characters
        limitTextFieldLength(usernameField, 20);
        // Limit password to 30 characters
        limitPasswordFieldLength(passwordField, 30);
    }

    // Helper method to limit text field length
    private void limitTextFieldLength(TextField field, int maxLen) {
        field.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.length() > maxLen) {
                field.setText(oldVal);
            }
        });
    }

    // Helper method to limit password field length
    private void limitPasswordFieldLength(PasswordField field, int maxLen) {
        field.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.length() > maxLen) {
                field.setText(oldVal);
            }
        });
    }

    // Handle login button click
    @FXML
    private void handleLogin(ActionEvent event) {
        String username = usernameField.getText();
        String password = passwordField.getText();

        // Check if username and password are empty
        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            errorLabel.setText("Please enter username and password.");
            infoLabel.setText("");
            return;
        }

        try {
            // Try to find user in database
            User user = userDao.findByUsername(username);

            // Check if user exists and password matches
            if (user == null || !password.equals(user.getPasswordHash())) {
                errorLabel.setText("Wrong username or password.");
                infoLabel.setText("");
                return;
            }

            // Login successful - set current user
            SessionManager.setCurrentUser(user);
            errorLabel.setText("");
            infoLabel.setText("");

            // Get the stage and switch to appropriate view
            Stage stage = (Stage) usernameField.getScene().getWindow();
            if (SessionManager.isAdmin()) {
                switchToScene(stage, "/fxml/AdminMainView.fxml", 1100, 760);
            } else {
                switchToScene(stage, "/fxml/UserMainView.fxml", 1050, 720);
            }
        } catch (SQLException e) {
            errorLabel.setText("Database error: " + e.getMessage());
            infoLabel.setText("");
        }
    }

    // Handle forgot password button click
    @FXML
    private void handleForgotPassword(ActionEvent event) {
        infoLabel.setText("Password reset feature coming soon. Please contact administrator.");
        errorLabel.setText("");
    }

    // Handle register button click - show registration dialog
    @FXML
    private void handleRegister(ActionEvent event) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Register New User");

        TextField newUsernameField = new TextField();
        PasswordField newPasswordField = new PasswordField();
        PasswordField confirmField = new PasswordField();

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        grid.add(new Label("Username:"), 0, 0);
        grid.add(newUsernameField, 1, 0);
        grid.add(new Label("Password:"), 0, 1);
        grid.add(newPasswordField, 1, 1);
        grid.add(new Label("Confirm Password:"), 0, 2);
        grid.add(confirmField, 1, 2);

        Button registerBtn = new Button("Register");
        registerBtn.setOnAction(e -> {
            String newUsername = newUsernameField.getText();
            String newPassword = newPasswordField.getText();
            String confirmPass = confirmField.getText();

            // Validate input
            if (newUsername == null || newUsername.isEmpty() || newPassword == null || newPassword.isEmpty()) {
                showErrorInDialog(dialog, "Username and password cannot be empty.");
                return;
            }

            if (!newPassword.equals(confirmPass)) {
                showErrorInDialog(dialog, "Passwords do not match.");
                return;
            }

            try {
                // Create new user in database
                userDao.create(newUsername, newPassword, "USER");
                dialog.close();
                infoLabel.setText("Registration successful! Please login.");
                errorLabel.setText("");
            } catch (SQLException ex) {
                if (ex.getMessage().contains("Duplicate entry")) {
                    showErrorInDialog(dialog, "Username already exists.");
                } else {
                    showErrorInDialog(dialog, "Registration failed: " + ex.getMessage());
                }
            }
        });

        Button cancelBtn = new Button("Cancel");
        cancelBtn.setOnAction(e -> dialog.close());

        HBox buttonBox = new HBox(10, registerBtn, cancelBtn);
        buttonBox.setAlignment(Pos.CENTER);

        VBox vbox = new VBox(10, grid, buttonBox);
        Scene scene = new Scene(vbox);
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    // Helper method to show error in dialog
    private void showErrorInDialog(Stage dialog, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(dialog);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // Helper method to switch to another scene
    private void switchToScene(Stage stage, String fxmlPath, double width, double height) {
        try {
            URL fxmlResource = getClass().getResource(fxmlPath);
            if (fxmlResource == null) {
                errorLabel.setText("Main window resource not found: " + fxmlPath);
                return;
            }

            FXMLLoader loader = new FXMLLoader(fxmlResource);
            Scene scene = new Scene(loader.load(), width, height);
            stage.setScene(scene);
            stage.setMinWidth(900);
            stage.setMinHeight(620);
            stage.setResizable(true);
            stage.centerOnScreen();
        } catch (Exception e) {
            e.printStackTrace();
            String msg = e.getMessage();
            errorLabel.setText("Failed to open main window: " + (msg == null ? "" : msg));
        }
    }
}