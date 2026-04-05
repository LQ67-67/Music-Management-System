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
import java.util.function.UnaryOperator;

public class LoginController {

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Label errorLabel;

    @FXML
    private Label infoLabel;

    private final UserDao userDao = new UserDao();

    @FXML
    private void initialize() {
        // 限制用户名最多20个字符
        setTextFieldMaxLength(usernameField, 20);

        // 限制密码最多30个字符
        setPasswordFieldMaxLength(passwordField, 30);
    }

    private void setTextFieldMaxLength(TextField textField, int maxLength) {
        UnaryOperator<TextFormatter.Change> filter = change -> {
            if (change.getControlNewText().length() <= maxLength) {
                return change;
            }
            return null;
        };
        textField.setTextFormatter(new TextFormatter<>(filter));
    }

    private void setPasswordFieldMaxLength(PasswordField passwordField, int maxLength) {
        UnaryOperator<TextFormatter.Change> filter = change -> {
            if (change.getControlNewText().length() <= maxLength) {
                return change;
            }
            return null;
        };
        passwordField.setTextFormatter(new TextFormatter<>(filter));
    }

    @FXML
    private void handleLogin(ActionEvent event) {
        String username = usernameField.getText();
        String password = passwordField.getText();

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            errorLabel.setText("Username and password are required.");
            infoLabel.setText("");
            return;
        }

        try {
            User user = userDao.findByUsername(username);
            if (user == null || !password.equals(user.getPasswordHash())) {
                errorLabel.setText("Invalid username or password.");
                infoLabel.setText("");
                return;
            }

            SessionManager.setCurrentUser(user);
            errorLabel.setText("");
            infoLabel.setText("");

            Stage stage = (Stage) usernameField.getScene().getWindow();
            if (SessionManager.isAdmin()) {
                switchScene(stage, "/fxml/AdminMainView.fxml", 1100, 760);
            } else {
                switchScene(stage, "/fxml/UserMainView.fxml", 1050, 720);
            }
        } catch (SQLException e) {
            errorLabel.setText("Database error: " + e.getMessage());
            infoLabel.setText("");
        }
    }

    @FXML
    private void handleForgotPassword(ActionEvent event) {
        infoLabel.setText("Password reset feature coming soon. Please contact administrator.");
        errorLabel.setText("");
    }

    @FXML
    private void handleRegister(ActionEvent event) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Register");

        TextField usernameField = new TextField();
        PasswordField passwordField = new PasswordField();
        PasswordField confirmPasswordField = new PasswordField();

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        grid.add(new Label("Username:"), 0, 0);
        grid.add(usernameField, 1, 0);
        grid.add(new Label("Password:"), 0, 1);
        grid.add(passwordField, 1, 1);
        grid.add(new Label("Confirm Password:"), 0, 2);
        grid.add(confirmPasswordField, 1, 2);

        Button registerButton = new Button("Register");
        registerButton.setOnAction(e -> {
            String username = usernameField.getText();
            String password = passwordField.getText();
            String confirmPassword = confirmPasswordField.getText();

            if (username == null || username.isBlank() || password == null || password.isBlank()) {
                showErrorInDialog(dialog, "Username and password are required.");
                return;
            }

            if (!password.equals(confirmPassword)) {
                showErrorInDialog(dialog, "Passwords do not match.");
                return;
            }

            try {
                userDao.create(username, password, "USER");
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

        Button cancelButton = new Button("Cancel");
        cancelButton.setOnAction(e -> dialog.close());

        HBox buttonBox = new HBox(10, registerButton, cancelButton);
        buttonBox.setAlignment(Pos.CENTER);

        VBox vbox = new VBox(10, grid, buttonBox);
        Scene scene = new Scene(vbox);
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    private void showErrorInDialog(Stage dialog, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(dialog);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void switchScene(Stage stage, String fxmlPath, double width, double height) {
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
            errorLabel.setText("Failed to open main window (" + e.getClass().getSimpleName() + "): " + (msg == null ? "" : msg));
        }
    }
}
