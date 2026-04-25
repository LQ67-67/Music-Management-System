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

        usernameField.setTextFormatter(new TextFormatter<>(change -> change.getControlNewText().length() <= USERNAME_MAX_LEN ? change : null )); // limit 20 characters can be typed in the username field
        passwordField.setTextFormatter(new TextFormatter<>(change -> change.getControlNewText().length() <= PASSWORD_MAX_LEN ? change : null)); // limit 30 characters
    }

    @FXML
    private void handleLogin() {
        String username = usernameField.getText();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            errorLabel.setText("Please enter username and password."); // if either field is empty, show error
            return;
        }

        try {
            User user = userDao.findByUsername(username);
            if (user == null || !password.equals(user.getPasswordHash())) {
                errorLabel.setText("Wrong username or password."); // if user not found or password doesn't match, show error
                return;
            }

            SessionManager.setCurrentUser(user);
            errorLabel.setText("");
            infoLabel.setText("");

            Stage stage = (Stage) usernameField.getScene().getWindow(); // switch to the main scene directly here without a helper method
            URL resource;

            if (SessionManager.isAdmin()) {
                resource = getClass().getResource("/fxml/AdminMainView.fxml");
                stage.setScene(new Scene(FXMLLoader.load(resource))); // load the admin main view
            } else {
                resource = getClass().getResource("/fxml/UserMainView.fxml");
                stage.setScene(new Scene(FXMLLoader.load(resource)));
            }
            stage.centerOnScreen(); // window will be centered on the screen after loading

            stage.setMinWidth(900);
            stage.setMinHeight(620);
            stage.setResizable(true);
            stage.centerOnScreen();

        } catch (SQLException e) {
            errorLabel.setText("Database error: " + e.getMessage());
        } catch (Exception e) {
            errorLabel.setText("Failed to open main window: " + e.getMessage());
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
        grid.add(new Label("Username:"),0,0); grid.add(newUsernameField,1,0);
        grid.add(new Label("Password:"),0,1); grid.add(newPasswordField,1,1);
        grid.add(new Label("Confirm Password:"),0,2); grid.add(confirmField,1,2);

        Button registerBtn = new Button("Register");
        registerBtn.setOnAction(event -> {
            String u = newUsernameField.getText();
            String p = newPasswordField.getText();

            if (u.isEmpty() || p.isEmpty()) {
                Alert alert = new Alert(Alert.AlertType.ERROR, "Username and password cannot be empty.");
                alert.initOwner(dialog);
                alert.setHeaderText(null);
                alert.showAndWait();
                return;
            }

            if (!p.equals(confirmField.getText())) {
                Alert alert = new Alert(Alert.AlertType.ERROR, "Passwords do not match.");
                alert.initOwner(dialog);
                alert.setHeaderText(null);
                alert.showAndWait();
                return;
            }

            try {
                userDao.create(u, p, "USER");
                dialog.close();

                Alert successAlert = new Alert(Alert.AlertType.INFORMATION, "User '" + u + "' registered!");
                successAlert.showAndWait();
                infoLabel.setText("Registration successful! Please login.");

            } catch (SQLException ex) {
                String errorMsg = ex.getMessage().contains("Duplicate entry") ? "Username already exists." : "Registration failed: " + ex.getMessage(); // if the error message contains "Duplicate entry", it means the username already exists
                Alert alert = new Alert(Alert.AlertType.ERROR, errorMsg);
                alert.initOwner(dialog);
                alert.setHeaderText(null);
                alert.showAndWait();
            }
        });

        Button cancelBtn = new Button("Cancel");
        cancelBtn.setOnAction(event -> dialog.close());

        HBox buttons = new HBox(16);
        buttons.setAlignment(Pos.CENTER);
        buttons.getChildren().addAll(registerBtn, cancelBtn);

        VBox vbox = new VBox(16);
        vbox.setPadding(new Insets(20));
        vbox.getChildren().addAll(grid, buttons);

        dialog.setScene(new Scene(vbox, 500, 300));
        dialog.centerOnScreen();
        dialog.showAndWait();
    }
}