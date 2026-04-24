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
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

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

    /** Opens the simulated payment dialog for the selected order. */
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

        showPaymentDialog(sel);
    }

    private void showPaymentDialog(Order order) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Pay Order #" + order.getId());

        Label titleLabel = new Label("💳  Payment");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        Label amountLabel = new Label(String.format("Amount to Pay: RM %s", order.getTotalAmount()));
        amountLabel.setStyle("-fx-font-size: 14px;");

        // Card fields
        TextField cardNumberField = new TextField();
        cardNumberField.setPromptText("1234 5678 9012 3456");
        cardNumberField.setTextFormatter(new TextFormatter<>(change -> {
            String t = change.getControlNewText().replaceAll("[^0-9 ]", "");
            return t.length() <= 19 ? change : null;
        }));

        TextField cardHolderField = new TextField();
        cardHolderField.setPromptText("Full Name on Card");

        TextField expiryField = new TextField();
        expiryField.setPromptText("MM/YY");
        expiryField.setTextFormatter(new TextFormatter<>(change -> {
            String t = change.getControlNewText().replaceAll("[^0-9/]", "");
            return t.length() <= 5 ? change : null;
        }));

        PasswordField cvvField = new PasswordField();
        cvvField.setPromptText("CVV");
        cvvField.setTextFormatter(new TextFormatter<>(change -> {
            String t = change.getControlNewText().replaceAll("[^0-9]", "");
            return t.length() <= 3 ? change : null;
        }));

        Label errorLabel = new Label("");
        errorLabel.setStyle("-fx-text-fill: red;");
        Label statusLabel = new Label("");
        statusLabel.setStyle("-fx-font-style: italic;");

        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(12); grid.setVgap(12); grid.setPadding(new Insets(20));
        grid.add(new Label("Card Number:"),      0, 0); grid.add(cardNumberField, 1, 0);
        grid.add(new Label("Card Holder:"),      0, 1); grid.add(cardHolderField, 1, 1);
        grid.add(new Label("Expiry (MM/YY):"),   0, 2); grid.add(expiryField, 1, 2);
        grid.add(new Label("CVV:"),              0, 3); grid.add(cvvField, 1, 3);
        grid.add(errorLabel,                     0, 4, 2, 1);

        Button confirmBtn = new Button("Confirm Payment");
        confirmBtn.setStyle("-fx-background-color: #1565c0; -fx-text-fill: white; -fx-font-weight: bold;");
        Button cancelBtn  = new Button("Cancel");

        confirmBtn.setOnAction(e -> {
            // Validation
            String cardNumber = cardNumberField.getText().replaceAll(" ", "");
            String cardHolder = cardHolderField.getText().trim();
            String expiry     = expiryField.getText().trim();
            String cvv        = cvvField.getText().trim();

            if (cardNumber.length() != 16 || !cardNumber.matches("\\d{16}")) {
                errorLabel.setText("Enter a valid 16-digit card number."); return;
            }
            if (cardHolder.isEmpty()) {
                errorLabel.setText("Enter the card holder name."); return;
            }
            if (!expiry.matches("^(0[1-9]|1[0-2])/\\d{2}$")) {
                errorLabel.setText("Enter a valid expiry date (MM/YY)."); return;
            }
            if (cvv.length() != 3) {
                errorLabel.setText("Enter a valid 3-digit CVV."); return;
            }

            // Loading state
            confirmBtn.setDisable(true);
            cancelBtn.setDisable(true);
            errorLabel.setText("");
            statusLabel.setText("Processing… Please wait.");

            javafx.concurrent.Task<Boolean> task = new javafx.concurrent.Task<>() {
                @Override protected Boolean call() throws Exception {
                    Thread.sleep(2000);
                    return new Random().nextInt(100) < 85; // 85% success
                }
            };

            task.setOnSucceeded(ev -> {
                statusLabel.setText("");
                if (task.getValue()) {
                    // ── Payment succeeded ──
                    try {
                        order.setStatus("PAID");
                        orderDao.update(order);
                        loadOrders();
                        dialog.close();
                        showPaymentSuccess(order.getId(), order.getTotalAmount());
                    } catch (SQLException ex) {
                        LOGGER.log(Level.SEVERE, "Failed to mark order as PAID", ex);
                        confirmBtn.setDisable(false);
                        cancelBtn.setDisable(false);
                        errorLabel.setText("Payment succeeded but status update failed: " + ex.getMessage());
                    }
                } else {
                    // ── Payment declined ──
                    LOGGER.warning("Simulated payment declined for order #" + order.getId());
                    confirmBtn.setDisable(false);
                    cancelBtn.setDisable(false);
                    errorLabel.setText("Payment declined. Please check your card details and try again.");
                }
            });

            task.setOnFailed(ev -> {
                LOGGER.log(Level.SEVERE, "Payment task error", task.getException());
                statusLabel.setText("");
                confirmBtn.setDisable(false);
                cancelBtn.setDisable(false);
                errorLabel.setText("Network error, please try again later.");
            });

            Thread t = new Thread(task);
            t.setDaemon(true);
            t.start();
        });

        cancelBtn.setOnAction(e -> dialog.close());

        HBox buttons = new HBox(12, confirmBtn, cancelBtn);
        buttons.setAlignment(Pos.CENTER);

        VBox root = new VBox(14, titleLabel, amountLabel, grid, buttons, statusLabel);
        root.setPadding(new Insets(24));
        root.setAlignment(Pos.TOP_CENTER);

        dialog.setScene(new Scene(root, 460, 360));
        dialog.showAndWait();
    }

    private void showPaymentSuccess(int orderId, BigDecimal amount) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Payment Successful");

        Label icon     = new Label("✅");  icon.setStyle("-fx-font-size: 48px;");
        Label msg      = new Label("Payment Successful!");
        msg.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #2e7d32;");
        Label orderLbl = new Label("Order #" + orderId + " is now PAID.");
        Label amtLbl   = new Label(String.format("Amount Paid: RM %s", amount));
        Button okBtn   = new Button("OK");
        okBtn.setStyle("-fx-background-color: #2e7d32; -fx-text-fill: white;");
        okBtn.setOnAction(e -> stage.close());

        VBox box = new VBox(14, icon, msg, orderLbl, amtLbl, okBtn);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(30));
        stage.setScene(new Scene(box, 300, 260));
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
        itemTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<OrderItem, String>     trackCol = new TableColumn<>("Track");
        trackCol.setCellValueFactory(p -> new SimpleStringProperty(getTrackLabel(p.getValue().getTrackId())));
        TableColumn<OrderItem, Number>     qtyCol   = new TableColumn<>("Qty");
        qtyCol.setCellValueFactory(p -> new SimpleIntegerProperty(p.getValue().getQuantity()));
        TableColumn<OrderItem, BigDecimal> priceCol = new TableColumn<>("Price");
        priceCol.setCellValueFactory(p -> new SimpleObjectProperty<>(p.getValue().getUnitPrice()));
        TableColumn<OrderItem, BigDecimal> totCol   = new TableColumn<>("Total");
        totCol.setCellValueFactory(p -> new SimpleObjectProperty<>(p.getValue().getLineTotal()));

        itemTable.getColumns().addAll(trackCol, qtyCol, priceCol, totCol);

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