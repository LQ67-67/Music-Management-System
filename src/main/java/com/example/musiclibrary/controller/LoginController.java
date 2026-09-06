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
    @FXML private TextField passwordShowField;
    @FXML private Button togglePasswordBtn;
    @FXML private Label errorLabel;
    @FXML private Label infoLabel;
    @FXML private Label passwordHintLabel;
    @FXML private Label requirementsLabel;

    private UserDao userDao;
    private boolean passwordVisible = false;

    @FXML
    private void initialize() {
        userDao = new UserDao();

        usernameField.setTextFormatter(new TextFormatter<>(change ->
            change.getControlNewText().length() <= USERNAME_MAX_LEN ? change : null));
        passwordField.setTextFormatter(new TextFormatter<>(change ->
            change.getControlNewText().length() <= PASSWORD_MAX_LEN ? change : null));

        passwordShowField.setVisible(false);
        passwordShowField.setManaged(false);

        // Setup password field listener for real-time validation
        passwordField.textProperty().addListener((obs, oldVal, newVal) -> {
            validatePasswordRequirements(newVal);
            if (passwordVisible) {
                passwordShowField.setText(newVal);
            }
        });

        passwordShowField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (passwordVisible) {
                passwordField.setText(newVal);
            }
        });

        // Press-and-hold reveal: show while pressing, hide when released.
        togglePasswordBtn.setOnMousePressed(e -> showPasswordTemporarily());
        togglePasswordBtn.setOnMouseReleased(e -> hidePassword());
        togglePasswordBtn.setOnMouseExited(e -> hidePassword());
    }

    /**
     * Validate password requirements in real-time (display hints only)
     */
    private void validatePasswordRequirements(String password) {
        if (password == null || password.isEmpty()) {
            requirementsLabel.setText("");
            passwordHintLabel.setText("");
            return;
        }

        boolean hasUppercase = password.matches(".*[A-Z].*");
        boolean hasLowercase = password.matches(".*[a-z].*");
        boolean hasDigit = password.matches(".*\\d.*");

        StringBuilder requirements = new StringBuilder();

        if (!hasUppercase) requirements.append("✗ Uppercase (A-Z)  ");
        else requirements.append("✓ Uppercase  ");

        if (!hasLowercase) requirements.append("✗ Lowercase (a-z)  ");
        else requirements.append("✓ Lowercase  ");

        if (!hasDigit) requirements.append("✗ Digit (0-9)");
        else requirements.append("✓ Digit");

        requirementsLabel.setText(requirements.toString());

        if (!hasUppercase || !hasLowercase || !hasDigit) {
            requirementsLabel.setStyle("-fx-text-fill: #d03238;");
            passwordHintLabel.setText("Password could be stronger");
            passwordHintLabel.setStyle("-fx-text-fill: #868685; -fx-font-weight: 400;");
        } else {
            requirementsLabel.setStyle("-fx-text-fill: #054d28;");
            passwordHintLabel.setText("✓ Password strength: strong");
            passwordHintLabel.setStyle("-fx-text-fill: #054d28; -fx-font-weight: 600;");
        }
    }

    private void showPasswordTemporarily() {
        if (passwordVisible) {
            return;
        }
        passwordVisible = true;
        passwordShowField.setText(passwordField.getText());
        passwordField.setVisible(false);
        passwordField.setManaged(false);
        passwordShowField.setVisible(true);
        passwordShowField.setManaged(true);
        passwordShowField.positionCaret(passwordShowField.getText().length());
        togglePasswordBtn.setText("🙈");
    }

    private void hidePassword() {
        if (!passwordVisible) {
            return;
        }
        passwordField.setText(passwordShowField.getText());
        passwordShowField.setVisible(false);
        passwordShowField.setManaged(false);
        passwordField.setVisible(true);
        passwordField.setManaged(true);
        passwordField.positionCaret(passwordField.getText().length());
        togglePasswordBtn.setText("👁️");
        passwordVisible = false;
    }

    @FXML
    private void handleLogin() {
        String username = usernameField.getText();
        String password = passwordVisible ? passwordShowField.getText() : passwordField.getText();

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
            URL resource;

            if (SessionManager.isAdmin()) {
                resource = getClass().getResource("/fxml/AdminMainView.fxml");
                stage.setScene(new Scene(FXMLLoader.load(resource)));
            } else {
                resource = getClass().getResource("/fxml/UserMainView.fxml");
                stage.setScene(new Scene(FXMLLoader.load(resource)));
            }
            stage.setMinWidth(900);
            stage.setMinHeight(620);
            stage.setResizable(true);
            stage.setWidth(1280);
            stage.setHeight(864);
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

        // placeholders tell users the expected format before they type
        newUsernameField.setPromptText("4-20 characters: letters, numbers or _");
        newUsernameField.setTextFormatter(new TextFormatter<>(change ->
            change.getControlNewText().length() <= USERNAME_MAX_LEN ? change : null));
        newPasswordField.setPromptText("Min 6 chars with A-Z, a-z and 0-9");
        newPasswordField.setTextFormatter(new TextFormatter<>(change ->
            change.getControlNewText().length() <= PASSWORD_MAX_LEN ? change : null));
        confirmField.setPromptText("Re-enter the same password");

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(6);
        grid.setPadding(new Insets(20));
        grid.add(new Label("Username:"), 0, 0);
        grid.add(newUsernameField, 1, 0);
        grid.add(new Label("Password:"), 0, 1);
        grid.add(newPasswordField, 1, 1);
        grid.add(new Label("Confirm:"), 0, 2);
        grid.add(confirmField, 1, 2);

        // live hints, one per field, so mistakes are caught while typing instead of on submit
        Label usernameHint = new Label("");
        Label passwordHint = new Label("");
        Label confirmHint = new Label("");
        usernameHint.getStyleClass().add("caption");
        passwordHint.getStyleClass().add("caption");
        confirmHint.getStyleClass().add("caption");
        usernameHint.setWrapText(true);
        passwordHint.setWrapText(true);
        confirmHint.setWrapText(true);

        grid.add(usernameHint, 1, 3);
        grid.add(passwordHint, 1, 4);
        grid.add(confirmHint, 1, 5);

        Label validationLabel = new Label("");
        validationLabel.setStyle("-fx-text-fill: #d03238; -fx-font-weight: 600;");
        validationLabel.setWrapText(true);

        Runnable validateInput = () -> {
            String username = newUsernameField.getText();
            String password = newPasswordField.getText();
            String confirm = confirmField.getText();

            if (username.isEmpty()) {
                usernameHint.setText("");
            } else if (username.length() < 4 || !username.matches("[A-Za-z0-9_]+")) {
                usernameHint.setText("✗ Username must be 4-20 characters using letters, numbers or _");
                usernameHint.setStyle("-fx-text-fill: #d03238;");
            } else {
                usernameHint.setText("✓ Username looks good");
                usernameHint.setStyle("-fx-text-fill: #054d28;");
            }

            if (password.isEmpty()) {
                passwordHint.setText("");
            } else {
                boolean hasUppercase = password.matches(".*[A-Z].*");
                boolean hasLowercase = password.matches(".*[a-z].*");
                boolean hasDigit = password.matches(".*\\d.*");
                if (password.length() < 6 || !hasUppercase || !hasLowercase || !hasDigit) {
                    passwordHint.setText(String.format("✗ Needs: %s%s%s%s",
                            password.length() < 6 ? "6+ chars, " : "",
                            !hasUppercase ? "uppercase (A-Z), " : "",
                            !hasLowercase ? "lowercase (a-z), " : "",
                            !hasDigit ? "digit (0-9)" : "").replaceAll(", $", ""));
                    passwordHint.setStyle("-fx-text-fill: #d03238;");
                } else {
                    passwordHint.setText("✓ Password strength: strong");
                    passwordHint.setStyle("-fx-text-fill: #054d28;");
                }
            }

            if (confirm.isEmpty()) {
                confirmHint.setText("");
            } else if (!confirm.equals(password)) {
                confirmHint.setText("✗ Passwords do not match");
                confirmHint.setStyle("-fx-text-fill: #d03238;");
            } else {
                confirmHint.setText("✓ Passwords match");
                confirmHint.setStyle("-fx-text-fill: #054d28;");
            }
        };

        newUsernameField.textProperty().addListener((obs, o, n) -> validateInput.run());
        newPasswordField.textProperty().addListener((obs, o, n) -> validateInput.run());
        confirmField.textProperty().addListener((obs, o, n) -> validateInput.run());

        Button registerBtn = new Button("Register");
        registerBtn.setStyle("-fx-background-color: #9fe870; -fx-text-fill: #163300; -fx-padding: 8 20; -fx-border-radius: 9999; -fx-background-radius: 9999; -fx-font-weight: 600;");
        registerBtn.setOnAction(event -> {
            String u = newUsernameField.getText().trim();
            String p = newPasswordField.getText();
            String confirm = confirmField.getText();

            if (u.isEmpty() || p.isEmpty()) {
                validationLabel.setText("Username and password cannot be empty.");
                return;
            }

            if (u.length() < 4 || !u.matches("[A-Za-z0-9_]+")) {
                validationLabel.setText("Username must be 4-20 characters using letters, numbers or _.");
                return;
            }

            if (p.length() < 6 || !p.matches(".*[A-Z].*") || !p.matches(".*[a-z].*") || !p.matches(".*\\d.*")) {
                validationLabel.setText("Password needs at least 6 characters with uppercase, lowercase and a digit.");
                return;
            }

            if (p.length() > PASSWORD_MAX_LEN) {
                validationLabel.setText("Password cannot exceed " + PASSWORD_MAX_LEN + " characters.");
                return;
            }

            if (!p.equals(confirm)) {
                validationLabel.setText("Passwords do not match.");
                return;
            }

            try {
                hidePassword();
                userDao.create(u, p, "USER");
                dialog.close();

                Alert successAlert = new Alert(Alert.AlertType.INFORMATION, "User '" + u + "' registered successfully!");
                successAlert.setHeaderText("Success");
                successAlert.showAndWait();
                infoLabel.setText("Registration successful! Please log in.");

            } catch (SQLException ex) {
                String errorMsg = ex.getMessage() != null && ex.getMessage().contains("Duplicate entry")
                    ? "Username already exists."
                    : "Registration failed: " + ex.getMessage();
                validationLabel.setText(errorMsg);
            }
        });

        Button cancelBtn = new Button("Cancel");
        cancelBtn.setStyle("-fx-padding: 8 20;");
        cancelBtn.setOnAction(event -> dialog.close());

        HBox buttons = new HBox(16);
        buttons.setAlignment(Pos.CENTER);
        buttons.getChildren().addAll(registerBtn, cancelBtn);

        VBox vbox = new VBox(12);
        vbox.setPadding(new Insets(16));
        vbox.getChildren().addAll(grid, validationLabel, buttons);

        Scene scene = new Scene(vbox, 520, 400);
        scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
        dialog.setScene(scene);
        dialog.centerOnScreen();
        dialog.setResizable(false);
        dialog.showAndWait();
    }
}

