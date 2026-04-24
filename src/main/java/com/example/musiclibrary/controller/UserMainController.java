package com.example.musiclibrary.controller;

import com.example.musiclibrary.MusicLibraryApp;
import com.example.musiclibrary.dao.CustomerDao;
import com.example.musiclibrary.dao.OrderDao;
import com.example.musiclibrary.dao.TrackDao;
import com.example.musiclibrary.model.Customer;
import com.example.musiclibrary.model.Order;
import com.example.musiclibrary.model.OrderItem;
import com.example.musiclibrary.model.Track;
import com.example.musiclibrary.model.User;
import com.example.musiclibrary.service.OrderService;
import com.example.musiclibrary.session.SessionManager;
import com.example.musiclibrary.util.TrackMediaResolver;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

public class UserMainController {

    private static final Logger LOGGER = Logger.getLogger(UserMainController.class.getName());

    @FXML private Label welcomeLabel;
    @FXML private TextField searchField;
    @FXML private TableView<Track> trackTable;
    @FXML private Spinner<Integer> quantitySpinner;
    @FXML private TableColumn<Track, Track> colCover;
    @FXML private TableColumn<Track, String> colTitle;
    @FXML private TableColumn<Track, String> colArtist;
    @FXML private TableColumn<Track, String> colGenre;
    @FXML private TableColumn<Track, String> colPrice;
    @FXML private TableColumn<Track, String> colStock;

    private final ObservableList<Track> trackData = FXCollections.observableArrayList();
    private final ObservableList<OrderItem> cartItems = FXCollections.observableArrayList();
    private final CustomerDao customerDao = new CustomerDao();
    private final OrderDao orderDao = new OrderDao();
    private final TrackDao trackDao = new TrackDao();
    private final OrderService orderService = new OrderService();
    private SpinnerValueFactory.IntegerSpinnerValueFactory quantitySpinnerFactory;

    @FXML
    private void initialize() {
        if (SessionManager.getCurrentUser() != null) {
            welcomeLabel.setText("Welcome, " + SessionManager.getCurrentUser().getUsername());
        }

        quantitySpinnerFactory = new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 99, 1);
        quantitySpinner.setValueFactory(quantitySpinnerFactory);

        // Dynamically update the max quantity based on the selected track's stock
        trackTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            if (newSel != null) {
                int maxStock = Math.max(1, newSel.getStockQty());
                quantitySpinnerFactory.setMax(maxStock);
                if (quantitySpinner.getValue() > maxStock) quantitySpinnerFactory.setValue(maxStock);
            }
        });

        setupTrackTableColumns();
        trackTable.setItems(trackData);
        loadAllTracks();
    }

    private void setupTrackTableColumns() {
        trackTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN); // make the table automatically expand to fill the width

        colCover.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue()));
        colCover.setCellFactory(col -> new TableCell<>() {
            private final ImageView imageView = new ImageView();
            @Override
            protected void updateItem(Track item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    imageView.setFitWidth(80); imageView.setFitHeight(80); imageView.setPreserveRatio(true);
                    imageView.setImage(TrackMediaResolver.loadTrackImage(item, item.getId() + ".mp3"));
                    setGraphic(imageView);
                }
            }
        });

        colTitle.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTitle()));

        // Custom natural sorting so "Track 2" comes before "Track 10"
        colTitle.setComparator((s1, s2) -> {
            if (s1 == null) return s2 == null ? 0 : -1;
            if (s2 == null) return 1;
            String[] parts1 = s1.split("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)");
            String[] parts2 = s2.split("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)");
            for (int i = 0; i < Math.min(parts1.length, parts2.length); i++) {
                String p1 = parts1[i], p2 = parts2[i];
                if (p1.matches("\\d+") && p2.matches("\\d+")) {
                    int cmp = Long.compare(Long.parseLong(p1), Long.parseLong(p2));
                    if (cmp != 0) return cmp;
                } else {
                    int cmp = p1.compareToIgnoreCase(p2);
                    if (cmp != 0) return cmp;
                }
            }
            return Integer.compare(parts1.length, parts2.length);
        });

        colArtist.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getArtist()));
        colGenre.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getGenre()));
        colPrice.setCellValueFactory(d -> {
            BigDecimal price = d.getValue().getPrice();
            return new SimpleStringProperty(price == null ? "" : price.toPlainString());
        });
        colStock.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().getStockQty())));
    }

    @FXML
    public void handleEditProfile() {
        try {
            User currentUser = SessionManager.getCurrentUser();
            if (currentUser == null) { showError("Please log in to edit your profile."); return; }

            Customer existingCustomer = customerDao.findAll().stream()
                    .filter(c -> currentUser.getUsername().equalsIgnoreCase(c.getName()))
                    .findFirst().orElse(null);

            if (existingCustomer == null) {
                existingCustomer = new Customer();
                existingCustomer.setName(currentUser.getUsername());
                existingCustomer.setEmail(""); existingCustomer.setPhone(""); existingCustomer.setCity("");
                existingCustomer.setId(customerDao.create(existingCustomer));
            }

            Stage dialog = new Stage();
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.setTitle("Edit My Profile");

            GridPane grid = new GridPane();
            grid.setHgap(10); grid.setVgap(15); grid.setPadding(new Insets(20));

            TextField nameField = new TextField(existingCustomer.getName());
            TextField emailField = new TextField(existingCustomer.getEmail());
            TextField phoneField = new TextField(existingCustomer.getPhone());
            TextField cityField = new TextField(existingCustomer.getCity());

            grid.add(new Label("Username:"), 0, 0); grid.add(nameField, 1, 0);
            grid.add(new Label("Email:"), 0, 1); grid.add(emailField, 1, 1);
            grid.add(new Label("Phone:"), 0, 2); grid.add(phoneField, 1, 2);
            grid.add(new Label("City:"), 0, 3); grid.add(cityField, 1, 3);

            Button saveButton = new Button("Save Changes");
            Button cancelButton = new Button("Cancel");

            final Customer customerToUpdate = existingCustomer;

            saveButton.setOnAction(e -> {
                String inputEmail = emailField.getText();
                String inputPhone = phoneField.getText();
                StringBuilder errors = new StringBuilder();

                if (inputEmail != null && !inputEmail.trim().isEmpty()
                        && !inputEmail.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
                    errors.append("Invalid email format.\n");
                }
                if (inputPhone != null && !inputPhone.trim().isEmpty()
                        && !inputPhone.matches("^[0-9+\\-() ]{6,20}$")) {
                    errors.append("Invalid phone number format.\n");
                }
                if (errors.length() > 0) {
                    showError(errors.toString().trim());
                    return;
                }

                customerToUpdate.setName(nameField.getText());
                customerToUpdate.setEmail(inputEmail);
                customerToUpdate.setPhone(inputPhone);
                customerToUpdate.setCity(cityField.getText());
                try {
                    customerDao.update(customerToUpdate);
                    dialog.close();
                } catch (SQLException ex) {
                    LOGGER.log(Level.SEVERE, "Failed to save profile", ex);
                    showError("Failed to save profile: " + ex.getMessage());
                }
            });

            cancelButton.setOnAction(e -> dialog.close());

            HBox buttonBox = new HBox(15, saveButton, cancelButton);
            VBox mainLayout = new VBox(20, grid, buttonBox);
            mainLayout.setPadding(new Insets(20));
            dialog.setScene(new Scene(mainLayout, 400, 320));
            dialog.showAndWait();

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to load profile details", e);
            showError("Failed to load profile details: " + e.getMessage());
        }
    }

    @FXML
    private void handleSearch() {
        String keyword = searchField.getText();
        try {
            trackData.setAll(keyword == null || keyword.isEmpty()
                    ? trackDao.findAllActive()
                    : trackDao.searchActiveByKeyword(keyword.trim()));
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Search failed", e);
            showError("Failed to search tracks: " + e.getMessage());
        }
    }

    @FXML
    private void handleAddToCart() {
        Track sel = trackTable.getSelectionModel().getSelectedItem();
        if (sel == null) { showError("Please select a track."); return; }
        if (sel.getStockQty() <= 0) { showError("Track is out of stock."); return; }

        int qty = quantitySpinner.getValue();

        // Make sure the quantity is a valid positive number
        if (qty <= 0) {
            showError("Please enter a positive integer for quantity.");
            return;
        }
        if (qty > sel.getStockQty()) { showError("Not enough stock."); return; }

        // Check if the item is already in the cart and update it
        for (OrderItem item : cartItems) {
            if (item.getTrackId() == sel.getId()) {
                int newQty = item.getQuantity() + qty;
                if (newQty > sel.getStockQty()) { showError("Total quantity exceeds stock."); return; }
                item.setQuantity(newQty);
                item.setLineTotal(sel.getPrice().multiply(BigDecimal.valueOf(newQty)));
                showInfo("Added to cart. Total: " + newQty);
                return;
            }
        }

        OrderItem newItem = new OrderItem();
        newItem.setTrackId(sel.getId()); newItem.setQuantity(qty);
        newItem.setUnitPrice(sel.getPrice());
        newItem.setLineTotal(sel.getPrice().multiply(BigDecimal.valueOf(qty)));
        cartItems.add(newItem);
        showInfo("Added " + qty + " of '" + sel.getTitle() + "' to cart.");
    }

    @FXML
    private void handleViewCart() {
        if (cartItems.isEmpty()) { showInfo("Cart is empty."); return; }

        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Shopping Cart");

        TableView<OrderItem> cartTable = new TableView<>(cartItems);
        cartTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<OrderItem, String> titleCol = new TableColumn<>("Track Title");
        titleCol.setCellValueFactory(d -> {
            try {
                Track t = trackDao.findById(d.getValue().getTrackId());
                return new SimpleStringProperty(t != null ? t.getTitle() : "Unknown");
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Failed to fetch track title", e);
                return new SimpleStringProperty("Error");
            }
        });

        TableColumn<OrderItem, Number> qtyCol = new TableColumn<>("Quantity");
        qtyCol.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().getQuantity()));

        TableColumn<OrderItem, BigDecimal> priceCol = new TableColumn<>("Price");
        priceCol.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().getUnitPrice()));

        TableColumn<OrderItem, BigDecimal> totalCol = new TableColumn<>("Total");
        totalCol.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().getLineTotal()));

        cartTable.getColumns().addAll(titleCol, qtyCol, priceCol, totalCol);

        // Calculate the total price of everything in the cart
        BigDecimal cartTotal = cartItems.stream()
                .map(OrderItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Label totalLabel = new Label(String.format("Cart Total: RM %.2f", cartTotal));
        totalLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        Button removeBtn = new Button("Remove Selected");
        removeBtn.setOnAction(e -> {
            OrderItem sel = cartTable.getSelectionModel().getSelectedItem();
            if (sel != null) cartItems.remove(sel);

            // Recalculate the total label after removing an item
            BigDecimal newTotal = cartItems.stream()
                    .map(OrderItem::getLineTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            totalLabel.setText(String.format("Cart Total: RM %.2f", newTotal));
        });

        Button clearBtn = new Button("Clear Cart");
        clearBtn.setOnAction(e -> { cartItems.clear(); dialog.close(); });

        Button payBtn = new Button("Proceed to Payment");
        payBtn.setStyle("-fx-background-color: #2e7d32; -fx-text-fill: white; -fx-font-weight: bold;");

        // Use runLater to prevent window focus issues when swapping dialogs
        payBtn.setOnAction(e -> {
            dialog.close();
            javafx.application.Platform.runLater(() ->
                    showPaymentDialog(cartTotal, trackTable.getScene().getWindow())
            );
        });

        Button closeBtn = new Button("Close");
        closeBtn.setOnAction(e -> dialog.close());

        VBox layoutBox = new VBox(12, cartTable, totalLabel,
                new HBox(10, removeBtn, clearBtn, payBtn, closeBtn));
        layoutBox.setPadding(new Insets(15));
        dialog.setScene(new Scene(layoutBox, 600, 430));
        dialog.showAndWait();
    }

    private void showPaymentDialog(BigDecimal amount, javafx.stage.Window owner) {
        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle("Secure Payment");

        Label titleLabel = new Label("💳  Payment");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        Label amountLabel = new Label(String.format("Amount to Pay: RM %.2f", amount));
        amountLabel.setStyle("-fx-font-size: 14px;");

        GridPane grid = new GridPane();
        grid.setHgap(12); grid.setVgap(12); grid.setPadding(new Insets(20));

        // --- 1. Card Number Formatting: Auto-insert spaces every 4 digits ---
        TextField cardNumberField = new TextField();
        cardNumberField.setPromptText("1234 5678 9012 3456");
        cardNumberField.setPrefWidth(260);

        cardNumberField.setTextFormatter(new TextFormatter<>(change ->
                change.getControlNewText().matches("[0-9 ]{0,19}") ? change : null));

        cardNumberField.textProperty().addListener((obs, oldText, newText) -> {
            if (oldText != null && newText.length() < oldText.length()) return;

            String digits = newText.replaceAll(" ", "");
            if (digits.length() > 0 && digits.length() % 4 == 0 && digits.length() < 16) {
                if (!newText.endsWith(" ")) {
                    javafx.application.Platform.runLater(() -> {
                        cardNumberField.setText(newText + " ");
                        cardNumberField.positionCaret(cardNumberField.getText().length());
                    });
                }
            }
        });

        TextField cardHolderField = new TextField();
        cardHolderField.setPromptText("Full Name on Card");
        cardHolderField.setPrefWidth(260);

        // --- 2. Expiry Date Formatting: Auto-insert a slash after the month ---
        TextField expiryField = new TextField();
        expiryField.setPromptText("MM/YY");
        expiryField.setPrefWidth(100);

        expiryField.setTextFormatter(new TextFormatter<>(change ->
                change.getControlNewText().matches("[0-9/]{0,5}") ? change : null));

        expiryField.textProperty().addListener((obs, oldText, newText) -> {
            if (oldText != null && newText.length() < oldText.length()) return;
            if (newText.length() == 2 && !newText.contains("/")) {
                javafx.application.Platform.runLater(() -> {
                    expiryField.setText(newText + "/");
                    expiryField.positionCaret(expiryField.getText().length());
                });
            }
        });

        PasswordField cvvField = new PasswordField();
        cvvField.setPromptText("CVV");
        cvvField.setPrefWidth(80);
        cvvField.setTextFormatter(new TextFormatter<>(change ->
                change.getControlNewText().matches("[0-9]{0,3}") ? change : null));

        Label errorLabel = new Label("");
        errorLabel.setStyle("-fx-text-fill: red;");

        grid.add(new Label("Card Number:"), 0, 0); grid.add(cardNumberField, 1, 0);
        grid.add(new Label("Card Holder:"), 0, 1); grid.add(cardHolderField, 1, 1);
        grid.add(new Label("Expiry (MM/YY):"), 0, 2); grid.add(expiryField, 1, 2);
        grid.add(new Label("CVV:"), 0, 3); grid.add(cvvField, 1, 3);
        grid.add(errorLabel, 0, 4, 2, 1);

        Button confirmPayBtn = new Button("Confirm Payment");
        confirmPayBtn.setStyle("-fx-background-color: #1565c0; -fx-text-fill: white; -fx-font-weight: bold;");

        Button cancelPayBtn = new Button("Cancel");

        Label statusLabel = new Label("");
        statusLabel.setStyle("-fx-font-style: italic;");

        confirmPayBtn.setOnAction(e -> {
            String cardNumber = cardNumberField.getText().replaceAll(" ", "");
            String cardHolder = cardHolderField.getText().trim();
            String expiry = expiryField.getText().trim();
            String cvv = cvvField.getText().trim();

            if (cardNumber.length() != 16 || !cardNumber.matches("\\d{16}")) {
                errorLabel.setText("Please enter a valid 16-digit card number.");
                return;
            }
            if (cardHolder.isEmpty()) {
                errorLabel.setText("Please enter the card holder name.");
                return;
            }

            // strict check to ensure the card hasn't expired
            if (!expiry.matches("^(0[1-9]|1[0-2])/\\d{2}$")) {
                errorLabel.setText("Please enter a valid expiry date (MM/YY).");
                return;
            } else {
                try {
                    String[] parts = expiry.split("/");
                    int expMonth = Integer.parseInt(parts[0]);
                    int expYear = Integer.parseInt(parts[1]) + 2000;

                    // grab the actual current system date
                    java.time.YearMonth currentYearMonth = java.time.YearMonth.now();
                    java.time.YearMonth inputYearMonth = java.time.YearMonth.of(expYear, expMonth);

                    if (inputYearMonth.isBefore(currentYearMonth)) {
                        errorLabel.setText("Your card has expired. Please use a valid card.");
                        return;
                    }
                } catch (Exception ex) {
                    errorLabel.setText("Invalid expiry date.");
                    return;
                }
            }

            if (cvv.length() != 3) {
                errorLabel.setText("Please enter a valid 3-digit CVV.");
                return;
            }

            // lock the UI to prevent double submissions
            confirmPayBtn.setDisable(true);
            cancelPayBtn.setDisable(true);
            errorLabel.setText("");
            statusLabel.setText("Processing payment... Please wait.");

            // fake a payment gateway delay using a background task
            Task<Boolean> paymentTask = new Task<>() {
                @Override
                protected Boolean call() throws Exception {
                    Thread.sleep(2000);
                    return new Random().nextInt(100) < 85;
                }
            };

            paymentTask.setOnSucceeded(event -> {
                boolean success = paymentTask.getValue();
                statusLabel.setText("");

                if (success) {
                    try {
                        int customerId = resolveCustomerIdForCurrentUser();
                        int userId = SessionManager.getCurrentUser().getId();
                        Order newOrder = orderService.createOrder(customerId, userId, cartItems, null);

                        newOrder.setStatus("PAID");
                        orderDao.update(newOrder);

                        cartItems.clear();
                        loadAllTracks();

                        dialog.close(); // Close the payment dialog

                        // FIX: Use runLater so Mac doesn't render a blank window
                        javafx.application.Platform.runLater(() -> {
                            showPaymentSuccess(newOrder.getId(), amount);
                        });

                    } catch (Exception ex) {
                        LOGGER.log(Level.SEVERE, "Order creation after payment failed", ex);
                        confirmPayBtn.setDisable(false);
                        cancelPayBtn.setDisable(false);
                        errorLabel.setText("Payment succeeded but order creation failed: " + ex.getMessage());
                    }
                } else {
                    LOGGER.warning("Simulated payment declined for card ending in " + cardNumber.substring(12));
                    confirmPayBtn.setDisable(false);
                    cancelPayBtn.setDisable(false);
                    errorLabel.setText("Payment declined. Please check your card details and try again.");
                }
            });

            paymentTask.setOnFailed(event -> {
                Throwable ex = paymentTask.getException();
                LOGGER.log(Level.SEVERE, "Payment processing error", ex);
                confirmPayBtn.setDisable(false);
                cancelPayBtn.setDisable(false);
                statusLabel.setText("");
                errorLabel.setText("Network error, please try again later.");
            });

            Thread paymentThread = new Thread(paymentTask);
            paymentThread.setDaemon(true);
            paymentThread.start();
        });

        cancelPayBtn.setOnAction(e -> dialog.close());

        HBox buttons = new HBox(12, confirmPayBtn, cancelPayBtn);
        buttons.setAlignment(Pos.CENTER);

        VBox root = new VBox(14, titleLabel, amountLabel, grid, buttons, statusLabel);
        root.setPadding(new Insets(24));
        root.setAlignment(Pos.TOP_CENTER);

        dialog.setScene(new Scene(root, 480, 370));
        dialog.showAndWait();
    }

    private void showPaymentSuccess(int orderId, BigDecimal amount) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Order Confirmation");  // updated the window title to be more professional

        Label icon = new Label("🎉");  // header celebration emoji
        icon.setStyle("-fx-font-size: 50px;");

        Label msg = new Label("Thank You!");
        msg.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #2e7d32;");

        Label subMsg = new Label("Your payment was successfully processed.");
        subMsg.setStyle("-fx-font-size: 13px; -fx-text-fill: #555555;");

        VBox headerBox = new VBox(5, icon, msg, subMsg);
        headerBox.setAlignment(Pos.CENTER);

        javafx.scene.control.Separator separator = new javafx.scene.control.Separator(); // divider line
        separator.setPadding(new Insets(10, 0, 10, 0));

        javafx.scene.layout.GridPane detailsGrid = new javafx.scene.layout.GridPane(); // receipt details
        detailsGrid.setVgap(12);
        detailsGrid.setHgap(30);
        detailsGrid.setAlignment(Pos.CENTER);

        Label lblOrder = new Label("Order Number:");
        lblOrder.setStyle("-fx-text-fill: #666666; -fx-font-weight: bold;");
        Label valOrder = new Label("#" + orderId);
        valOrder.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        Label lblDate = new Label("Date:");
        lblDate.setStyle("-fx-text-fill: #666666; -fx-font-weight: bold;");

        String currentDate = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")); // dynamically grab the exact current time for the receipt
        Label valDate = new Label(currentDate);
        valDate.setStyle("-fx-font-size: 13px;");

        Label lblAmount = new Label("Amount Paid:");
        lblAmount.setStyle("-fx-text-fill: #666666; -fx-font-weight: bold;");
        Label valAmount = new Label(String.format("RM %.2f", amount));
        valAmount.setStyle("-fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: #1565c0;"); // highlight the final amount

        detailsGrid.add(lblOrder, 0, 0);  detailsGrid.add(valOrder, 1, 0);
        detailsGrid.add(lblDate, 0, 1);   detailsGrid.add(valDate, 1, 1);
        detailsGrid.add(lblAmount, 0, 2); detailsGrid.add(valAmount, 1, 2);

        // 4. Bottom Button
        Button okBtn = new Button("Done");
        okBtn.setStyle("-fx-background-color: #2e7d32; -fx-text-fill: white; -fx-font-weight: bold; -fx-pref-width: 120px; -fx-padding: 8px; -fx-cursor: hand;");
        okBtn.setOnAction(e -> stage.close());

        // Assemble everything with a slight off-white/gray background to mimic paper
        VBox box = new VBox(15, headerBox, separator, detailsGrid, new Label(""), okBtn);
        box.setAlignment(Pos.TOP_CENTER);
        box.setPadding(new Insets(25, 30, 25, 30));
        box.setStyle("-fx-background-color: #f8f9fa;");

        stage.setScene(new Scene(box, 380, 390));
        // Prevent the user from resizing the window so our receipt layout doesn't break
        stage.setResizable(false);
        stage.showAndWait();
    }

    @FXML
    private void handleViewOrders() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/OrderManagementView.fxml"));
            Stage stage = new Stage();
            stage.setTitle("My Order History");
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(trackTable.getScene().getWindow());
            stage.setScene(new Scene(loader.load(), 920, 640));
            stage.setMinWidth(820); stage.setMinHeight(560);
            stage.showAndWait();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to open orders", e);
            showError("Failed to open orders: " + e.getMessage());
        }
    }

    @FXML
    private void handleOpenMusicPlayer() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MusicPlayerView.fxml"));
            Stage stage = new Stage();
            stage.setTitle("Music Player");
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(trackTable.getScene().getWindow());
            stage.setScene(new Scene(loader.load(), 600, 500));
            stage.setMinWidth(500); stage.setMinHeight(400);
            MusicPlayerController controller = loader.getController();
            stage.setOnCloseRequest(ev -> controller.dispose());
            stage.showAndWait();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to open player", e);
            showError("Failed to open player: " + e.getMessage());
        }
    }

    @FXML
    private void handleLogout() {
        SessionManager.clearCurrentUser();
        Stage stage = (Stage) trackTable.getScene().getWindow();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/LoginView.fxml"));
            stage.setScene(new Scene(loader.load(), MusicLibraryApp.LOGIN_SCENE_WIDTH, MusicLibraryApp.LOGIN_SCENE_HEIGHT));
            stage.setMinWidth(MusicLibraryApp.LOGIN_MIN_WIDTH);
            stage.setMinHeight(MusicLibraryApp.LOGIN_MIN_HEIGHT);
            stage.setWidth(MusicLibraryApp.LOGIN_SCENE_WIDTH);
            stage.setHeight(MusicLibraryApp.LOGIN_SCENE_HEIGHT);
            stage.centerOnScreen();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Logout failed", e);
            showError("Logout failed: " + e.getMessage());
        }
    }

    private void loadAllTracks() {
        try {
            trackData.setAll(trackDao.findAllActive());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Load tracks failed", e);
            showError("Load failed: " + e.getMessage());
        }
    }

    private int resolveCustomerIdForCurrentUser() throws SQLException {
        if (SessionManager.getCurrentUser() == null) throw new IllegalArgumentException("No logged-in user.");
        String uname = SessionManager.getCurrentUser().getUsername();
        for (Customer c : customerDao.findAll()) if (uname.equalsIgnoreCase(c.getName())) return c.getId();
        Customer nc = new Customer(); nc.setName(uname);
        int id = customerDao.create(nc);
        if (id <= 0) throw new SQLException("Failed to create customer.");
        return id;
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg);
        alert.setHeaderText("Error");
        alert.showAndWait();
    }

    private void showInfo(String msg) {
        new Alert(Alert.AlertType.INFORMATION, msg).showAndWait();
    }
}