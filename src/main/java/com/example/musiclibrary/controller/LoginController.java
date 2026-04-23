package com.example.musiclibrary.controller;

import com.example.musiclibrary.dao.UserDao;
import com.example.musiclibrary.model.User;
import com.example.musiclibrary.session.SessionManager;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.net.URL;
import java.sql.SQLException;

public class LoginController {
    private static final int USERNAME_MAX_LEN = 20;
    private static final int PASSWORD_MAX_LEN = 30;

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;
    @FXML private Label infoLabel;

    private UserDao userDao;

    @FXML
    private void initialize() {
        userDao = new UserDao();

        // limit how many characters can be typed in each field
        usernameField.setTextFormatter(new TextFormatter<String>(change ->
                change.getControlNewText().length() <= USERNAME_MAX_LEN ? change : null));

        passwordField.setTextFormatter(new TextFormatter<String>(change ->
                change.getControlNewText().length() <= PASSWORD_MAX_LEN ? change : null));
    }

    @FXML
    private void handleLogin() {
        String username = usernameField.getText();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            errorLabel.setText("Please enter username and password.");
            return;
        }

        try {
            User user = userDao.findByUsername(username);

            if (user == null || !password.equals(user.getPasswordHash())) {
                errorLabel.setText("Wrong username or password.");
                return;
            }

            SessionManager.setCurrentUser(user);
            errorLabel.setText("");
            infoLabel.setText("");

            Stage stage = (Stage) usernameField.getScene().getWindow();

            if (SessionManager.isAdmin()) {
                switchToScene(stage, "/fxml/AdminMainView.fxml", 1100, 760);
            } else {
                switchToScene(stage, "/fxml/UserMainView.fxml", 1050, 720);
            }

        } catch (SQLException e) {
            errorLabel.setText("Database error: " + e.getMessage());
        }
    }

    @FXML
    private void handleRegister() {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Register New User");

        TextField newUsernameField = new TextField();
        PasswordField newPasswordField = new PasswordField();
        PasswordField confirmField = new PasswordField();

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
        registerBtn.setOnAction(e -> {
            String u = newUsernameField.getText();
            String p = newPasswordField.getText();

            if (u.isEmpty() || p.isEmpty()) {
                showAlert(dialog, "Username and password cannot be empty.");
                return;
            }

            if (!p.equals(confirmField.getText())) {
                showAlert(dialog, "Passwords do not match.");
                return;
            }

            try {
                userDao.create(u, p, "USER");
                dialog.close();
                new Alert(Alert.AlertType.INFORMATION, "User '" + u + "' registered!").showAndWait();
                infoLabel.setText("Registration successful! Please login.");
            } catch (SQLException ex) {
                String msg = ex.getMessage().contains("Duplicate entry")
                        ? "Username already exists."
                        : "Registration failed: " + ex.getMessage();
                showAlert(dialog, msg);
            }
        });

        Button cancelBtn = new Button("Cancel");
        cancelBtn.setOnAction(e -> dialog.close());

        HBox buttons = new HBox(16, registerBtn, cancelBtn);
        buttons.setAlignment(Pos.CENTER);

        VBox vbox = new VBox(16, grid, buttons);
        vbox.setPadding(new Insets(20));

        dialog.setScene(new Scene(vbox, 450, 250));
        dialog.centerOnScreen();
        dialog.showAndWait();
    }

    private void showAlert(Stage owner, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.initOwner(owner);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private void switchToScene(Stage stage, String fxmlPath, double width, double height) {
        try {
            URL resource = getClass().getResource(fxmlPath);
            if (resource == null) {
                errorLabel.setText("Resource not found: " + fxmlPath);
                return;
            }

            stage.setScene(new Scene(FXMLLoader.load(resource), width, height));
            stage.setMinWidth(900);
            stage.setMinHeight(620);
            stage.setResizable(true);
            stage.centerOnScreen();

        } catch (Exception e) {
            errorLabel.setText("Failed to open main window: " + e.getMessage());
        }
    }
}