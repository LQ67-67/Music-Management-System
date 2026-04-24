package com.example.musiclibrary.controller;

import com.example.musiclibrary.dao.OrderDao;
import com.example.musiclibrary.dao.OrderItemDao;
import com.example.musiclibrary.dao.TrackDao;
import com.example.musiclibrary.model.Order;
import com.example.musiclibrary.model.OrderItem;
import com.example.musiclibrary.model.Track;
import com.example.musiclibrary.session.SessionManager;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Map;

public class OrderManagementController {

    private static final Logger LOGGER = Logger.getLogger(OrderManagementController.class.getName());

    @FXML private TableView<Order> orderTable;
    @FXML private TableColumn<Order, Number>     colOrderId;
    @FXML private TableColumn<Order, String>     colOrderDate;
    @FXML private TableColumn<Order, String>     colStatus;
    @FXML private TableColumn<Order, BigDecimal> colTotal;
    @FXML private TableView<OrderItem> orderItemTable;
    @FXML private TableColumn<OrderItem, String>     colTrack;
    @FXML private TableColumn<OrderItem, Number>     colQuantity;
    @FXML private TableColumn<OrderItem, BigDecimal> colUnitPrice;
    @FXML private TableColumn<OrderItem, BigDecimal> colLineTotal;
    @FXML private TextField shippingCityField;

    private final ObservableList<Order>     orders     = FXCollections.observableArrayList();
    private final ObservableList<OrderItem> orderItems = FXCollections.observableArrayList();
    private final OrderDao     orderDao     = new OrderDao();
    private final OrderItemDao orderItemDao = new OrderItemDao();
    private final TrackDao     trackDao     = new TrackDao();
    private final Map<Integer, String> trackLabelCache = new HashMap<>();

    @FXML
    private void initialize() {
        colOrderId.setCellValueFactory(p -> new SimpleIntegerProperty(p.getValue().getId()));
        colOrderDate.setCellValueFactory(p -> {
            if (p.getValue().getOrderDate() == null) return new SimpleStringProperty("");
            return new SimpleStringProperty(
                    p.getValue().getOrderDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        });
        colStatus.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().getStatus()));
        colTotal.setCellValueFactory(p -> new SimpleObjectProperty<>(p.getValue().getTotalAmount()));

        colTrack.setCellValueFactory(p -> new SimpleStringProperty(getTrackLabel(p.getValue().getTrackId())));
        colQuantity.setCellValueFactory(p -> new SimpleIntegerProperty(p.getValue().getQuantity()));
        colUnitPrice.setCellValueFactory(p -> new SimpleObjectProperty<>(p.getValue().getUnitPrice()));
        colLineTotal.setCellValueFactory(p -> new SimpleObjectProperty<>(p.getValue().getLineTotal()));

        orderTable.setItems(orders);
        orderItemTable.setItems(orderItems);

        orderTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadOrderItems(newVal.getId());
                shippingCityField.setText(newVal.getShippingCity() != null ? newVal.getShippingCity() : "");
            } else {
                orderItems.clear();
                shippingCityField.clear();
            }
        });

        loadOrders();
    }

    private void loadOrders() {
        if (!SessionManager.isLoggedIn()) {
            showError("You must be logged in to view orders.");
            return;
        }
        try {
            List<Order> list = orderDao.findByUser(SessionManager.getCurrentUser().getId());
            orders.setAll(list);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to load orders", e);
            showError("Failed to load orders: " + e.getMessage());
        }
    }

    private void loadOrderItems(int orderId) {
        try {
            orderItems.setAll(orderItemDao.findByOrder(orderId));
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to load order items", e);
            showError("Failed to load order items: " + e.getMessage());
        }
    }

    @FXML
    private void handleConfirmOrder() {
        Order sel = orderTable.getSelectionModel().getSelectedItem();
        if (sel == null) { showError("Please select an order first."); return; }

        if ("PAID".equals(sel.getStatus()) || "CANCELLED".equals(sel.getStatus())) {
            showError("Cannot confirm an order that is already " + sel.getStatus() + ".");
            return;
        }

        sel.setStatus("CONFIRMED");
        sel.setShippingCity(shippingCityField.getText());
        try {
            orderDao.update(sel);
            loadOrders();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to confirm order", e);
            showError("Failed to confirm order: " + e.getMessage());
        }
    }

    @FXML
    private void handleCancelOrder() {
        Order sel = orderTable.getSelectionModel().getSelectedItem();
        if (sel == null) { showError("Please select an order first."); return; }

        if ("PAID".equals(sel.getStatus())) {
            showError("Cannot cancel a PAID order. Please contact support.");
            return;
        }

        sel.setStatus("CANCELLED");
        try {
            orderDao.update(sel);
            loadOrders();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to cancel order", e);
            showError("Failed to cancel order: " + e.getMessage());
        }
    }

    @FXML
    private void handleDeleteOrder() {
        Order sel = orderTable.getSelectionModel().getSelectedItem();
        if (sel == null) { showError("Please select an order first."); return; }
        try {
            orderDao.delete(sel.getId());
            loadOrders();
            orderItems.clear();
            shippingCityField.clear();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to delete order", e);
            showError("Failed to delete order: " + e.getMessage());
        }
    }

    @FXML
    private void handlePayOrder() {
        Order sel = orderTable.getSelectionModel().getSelectedItem();
        if (sel == null) { showError("Please select an order to pay."); return; }

        if ("PAID".equals(sel.getStatus())) {
            showError("This order has already been paid."); return;
        }
        if ("CANCELLED".equals(sel.getStatus())) {
            showError("Cannot pay a cancelled order."); return;
        }

        javafx.application.Platform.runLater(() ->
                showPaymentDialog(sel, orderTable.getScene().getWindow())
        );
    }

    private void showPaymentDialog(Order order, javafx.stage.Window owner) {
        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle("Pay Order #" + order.getId());

        Label titleLabel = new Label("💳  Payment");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        Label amountLabel = new Label(String.format("Amount to Pay: RM %s", order.getTotalAmount()));
        amountLabel.setStyle("-fx-font-size: 14px;");

        GridPane grid = new GridPane();
        grid.setHgap(12); grid.setVgap(12); grid.setPadding(new Insets(20));

        TextField cardNumberField = new TextField();
        cardNumberField.setPromptText("1234 5678 9012 3456");
        cardNumberField.setPrefWidth(260);
        cardNumberField.setTextFormatter(new TextFormatter<>(change ->
                change.getControlNewText().matches("[0-9 ]{0,19}") ? change : null));

        cardNumberField.textProperty().addListener((obs, oldText, newText) -> {
            if (oldText != null && newText.length() < oldText.length()) return;
            String digits = newText.replace(" ", "");
            if (!digits.isEmpty() && digits.length() % 4 == 0 && digits.length() < 16) {
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
            String cardNumber = cardNumberField.getText().replace(" ", "");
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

            if (!expiry.matches("^(0[1-9]|1[0-2])/\\d{2}$")) {
                errorLabel.setText("Please enter a valid expiry date (MM/YY).");
                return;
            } else {
                try {
                    String[] parts = expiry.split("/");
                    int expMonth = Integer.parseInt(parts[0]);
                    int expYear = Integer.parseInt(parts[1]) + 2000;

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

            confirmPayBtn.setDisable(true);
            cancelPayBtn.setDisable(true);
            errorLabel.setText("");
            statusLabel.setText("Processing payment... Please wait.");

            javafx.concurrent.Task<Boolean> paymentTask = new javafx.concurrent.Task<Boolean>() {
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
                        order.setStatus("PAID");
                        orderDao.update(order);
                        loadOrders();

                        dialog.close(); // Close the payment dialog

                        // FIX: Use runLater so Mac doesn't render a blank window
                        javafx.application.Platform.runLater(() -> {
                            showPaymentSuccess(order.getId(), order.getTotalAmount());
                        });

                    } catch (SQLException ex) {
                        LOGGER.log(Level.SEVERE, "Failed to mark order as PAID", ex);
                        confirmPayBtn.setDisable(false);
                        cancelPayBtn.setDisable(false);
                        errorLabel.setText("Payment succeeded but status update failed (Check DB limits): " + ex.getMessage());
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
        // Updated the window title to be more professional
        stage.setTitle("Order Confirmation");

        // 1. Header: Icon and Main Messages
        Label icon = new Label("🎉");
        icon.setStyle("-fx-font-size: 50px;");

        Label msg = new Label("Thank You!");
        msg.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #2e7d32;");

        Label subMsg = new Label("Your payment was successfully processed.");
        subMsg.setStyle("-fx-font-size: 13px; -fx-text-fill: #555555;");

        VBox headerBox = new VBox(5, icon, msg, subMsg);
        headerBox.setAlignment(Pos.CENTER);

        // 2. Divider Line
        javafx.scene.control.Separator separator = new javafx.scene.control.Separator();
        separator.setPadding(new Insets(10, 0, 10, 0));

        // 3. Receipt Details (Perfectly aligned using a GridPane)
        javafx.scene.layout.GridPane detailsGrid = new javafx.scene.layout.GridPane();
        detailsGrid.setVgap(12);
        detailsGrid.setHgap(30);
        detailsGrid.setAlignment(Pos.CENTER);

        Label lblOrder = new Label("Order Number:");
        lblOrder.setStyle("-fx-text-fill: #666666; -fx-font-weight: bold;");
        Label valOrder = new Label("#" + orderId);
        valOrder.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        Label lblDate = new Label("Date:");
        lblDate.setStyle("-fx-text-fill: #666666; -fx-font-weight: bold;");

        // Dynamically grab the exact current time for the receipt
        String currentDate = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"));
        Label valDate = new Label(currentDate);
        valDate.setStyle("-fx-font-size: 13px;");

        Label lblAmount = new Label("Amount Paid:");
        lblAmount.setStyle("-fx-text-fill: #666666; -fx-font-weight: bold;");
        Label valAmount = new Label(String.format("RM %.2f", amount));
        // Highlight the final amount
        valAmount.setStyle("-fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: #1565c0;");

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
    private void handleViewInvoice() {
        Order sel = orderTable.getSelectionModel().getSelectedItem();
        if (sel == null) { showError("Please select an order to view invoice."); return; }

        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Invoice - Order #" + sel.getId());

        Label orderLabel = new Label("Order #" + sel.getId());
        orderLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        String dateString = sel.getOrderDate() != null
                ? sel.getOrderDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : "N/A";

        Label dateLabel   = new Label("Date: " + dateString);
        Label statusLabel = new Label("Status: " + sel.getStatus());
        Label totalLabel  = new Label("Total: " + sel.getTotalAmount());
        totalLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        TableView<OrderItem> itemTable = new TableView<>(orderItems);
        itemTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<OrderItem, String>     trackCol = new TableColumn<>("Track");
        trackCol.setCellValueFactory(p -> new SimpleStringProperty(getTrackLabel(p.getValue().getTrackId())));
        TableColumn<OrderItem, Number>     qtyCol   = new TableColumn<>("Qty");
        qtyCol.setCellValueFactory(p -> new SimpleIntegerProperty(p.getValue().getQuantity()));
        TableColumn<OrderItem, BigDecimal> priceCol = new TableColumn<>("Price");
        priceCol.setCellValueFactory(p -> new SimpleObjectProperty<>(p.getValue().getUnitPrice()));
        TableColumn<OrderItem, BigDecimal> totCol   = new TableColumn<>("Total");
        totCol.setCellValueFactory(p -> new SimpleObjectProperty<>(p.getValue().getLineTotal()));

        itemTable.getColumns().add(trackCol);
        itemTable.getColumns().add(qtyCol);
        itemTable.getColumns().add(priceCol);
        itemTable.getColumns().add(totCol);

        Button closeBtn = new Button("Close");
        closeBtn.setOnAction(e -> dialog.close());

        VBox vbox = new VBox(10, orderLabel, dateLabel, statusLabel, totalLabel,
                new Label("Items:"), itemTable, closeBtn);
        vbox.setPadding(new Insets(20));

        dialog.setScene(new Scene(vbox, 600, 500));
        dialog.showAndWait();
    }

    private String getTrackLabel(int trackId) {
        if (trackLabelCache.containsKey(trackId)) return trackLabelCache.get(trackId);

        String trackName;
        try {
            Track track = trackDao.findById(trackId);
            trackName = track == null ? "Track #" + trackId : track.getTitle() + " - " + track.getArtist();
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to fetch track label for id " + trackId, e);
            trackName = "Track #" + trackId;
        }
        trackLabelCache.put(trackId, trackName);
        return trackName;
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.setHeaderText("Error");
        alert.showAndWait();
    }
}