package com.example.musiclibrary.controller;

import com.example.musiclibrary.dao.UserDao;
import com.example.musiclibrary.model.User;
import com.example.musiclibrary.session.SessionManager;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
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
import java.util.logging.Level;
import java.util.logging.Logger;

public class LoginController {

    private static final Logger LOGGER = Logger.getLogger(LoginController.class.getName());
    private static final int USERNAME_MAX_LEN = 20;
    private static final int PASSWORD_MAX_LEN = 30;

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
        // Limit username/password length at input time.
        limitUsernameFieldLength(usernameField);
        limitPasswordFieldLength(passwordField);
    }

    // Helper method to limit text field length
    private void limitUsernameFieldLength(TextField field) {
        field.setTextFormatter(new TextFormatter<String>(change ->
                change.getControlNewText().length() <= USERNAME_MAX_LEN ? change : null));
    }

    // Helper method to limit password field length
    private void limitPasswordFieldLength(PasswordField field) {
        field.setTextFormatter(new TextFormatter<String>(change ->
                change.getControlNewText().length() <= PASSWORD_MAX_LEN ? change : null));
    }

    // Handle login button click
    @FXML
    private void handleLogin() {
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

    // Handle register button click - show registration dialog
    @FXML
    private void handleRegister() {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Register New User");
        dialog.setMinWidth(450);
        dialog.setMinHeight(250);
        dialog.setResizable(true);

        TextField newUsernameField = new TextField();
        PasswordField newPasswordField = new PasswordField();
        PasswordField confirmField = new PasswordField();

        // Make input controls wider so the dialog feels roomier.
        newUsernameField.setPrefWidth(300);
        newPasswordField.setPrefWidth(300);
        confirmField.setPrefWidth(300);

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(14);
        grid.setPadding(new Insets(20));
        grid.add(new Label("Username:"), 0, 0);
        grid.add(newUsernameField, 1, 0);
        grid.add(new Label("Password:"), 0, 1);
        grid.add(newPasswordField, 1, 1);
        grid.add(new Label("Confirm Password:"), 0, 2);
        grid.add(confirmField, 1, 2);

        Button registerBtn = new Button("Register");
        registerBtn.setOnAction(actionEvent -> {
            actionEvent.consume();
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
                
                // Show success alert
                Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                successAlert.setTitle("Registration Successful");
                successAlert.setHeaderText(null);
                successAlert.setContentText("User '" + newUsername + "' has been successfully registered!");
                successAlert.showAndWait();
                
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
        cancelBtn.setOnAction(actionEvent -> {
            actionEvent.consume();
            dialog.close();
        });

        HBox buttonBox = new HBox(16, registerBtn, cancelBtn);
        buttonBox.setAlignment(Pos.CENTER);

        VBox vbox = new VBox(16, grid, buttonBox);
        vbox.setPadding(new Insets(20));
        Scene scene = new Scene(vbox, 450, 250);
        dialog.setScene(scene);
        dialog.centerOnScreen();
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
            LOGGER.log(Level.SEVERE, "Failed to open scene: " + fxmlPath, e);
            String msg = e.getMessage();
            errorLabel.setText("Failed to open main window: " + (msg == null ? "" : msg));
        }
    }
}