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

    private final UserDao userDao = new UserDao();

    @FXML
    private void handleLogin(ActionEvent event) {
        String username = usernameField.getText();
        String password = passwordField.getText();

        if (username == null || username.isBlank() ||
                password == null || password.isBlank()) {
            errorLabel.setText("Username and password are required.");
            return;
        }

        try {
            User user = userDao.findByUsername(username);
            if (user == null || !password.equals(user.getPasswordHash())) {
                errorLabel.setText("Invalid username or password.");
                return;
            }

            SessionManager.setCurrentUser(user);
            errorLabel.setText("");

            Stage stage = (Stage) usernameField.getScene().getWindow();
            if (SessionManager.isAdmin()) {
                switchScene(stage, "/fxml/AdminMainView.fxml", 1100, 760);
            } else {
                switchScene(stage, "/fxml/UserMainView.fxml", 1050, 720);
            }
        } catch (SQLException e) {
            errorLabel.setText("Database error: " + e.getMessage());
        }
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
