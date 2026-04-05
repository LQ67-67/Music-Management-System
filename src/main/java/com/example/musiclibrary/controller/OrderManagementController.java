package com.example.musiclibrary.controller;

import com.example.musiclibrary.dao.OrderDao;
import com.example.musiclibrary.dao.OrderItemDao;
import com.example.musiclibrary.dao.TrackDao;
import com.example.musiclibrary.model.Order;
import com.example.musiclibrary.model.OrderItem;
import com.example.musiclibrary.model.Track;
import com.example.musiclibrary.session.SessionManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OrderManagementController {

    @FXML
    private TableView<Order> orderTable;

    @FXML
    private TableColumn<Order, Number> colOrderId;

    @FXML
    private TableColumn<Order, String> colOrderDate;

    @FXML
    private TableColumn<Order, String> colStatus;

    @FXML
    private TableColumn<Order, BigDecimal> colTotal;

    @FXML
    private TableView<OrderItem> orderItemTable;

    @FXML
    private TableColumn<OrderItem, String> colTrack;

    @FXML
    private TableColumn<OrderItem, Number> colQuantity;

    @FXML
    private TableColumn<OrderItem, BigDecimal> colUnitPrice;

    @FXML
    private TableColumn<OrderItem, BigDecimal> colLineTotal;

    @FXML
    private TextField shippingCityField;

    private ObservableList<Order> orders;
    private ObservableList<OrderItem> orderItems;
    private OrderDao orderDao;
    private OrderItemDao orderItemDao;
    private TrackDao trackDao;
    private Map<Integer, String> trackLabelCache;

    // Initialize method - called when FXML is loaded
    @FXML
    private void initialize() {
        orders = FXCollections.observableArrayList();
        orderItems = FXCollections.observableArrayList();
        orderDao = new OrderDao();
        orderItemDao = new OrderItemDao();
        trackDao = new TrackDao();
        trackLabelCache = new HashMap<>();

        // Setup order table columns
        colOrderId.setCellValueFactory(data -> new javafx.beans.property.SimpleIntegerProperty(data.getValue().getId()));
        colOrderDate.setCellValueFactory(data -> {
            if (data.getValue().getOrderDate() == null) {
                return new javafx.beans.property.SimpleStringProperty("");
            }
            String text = data.getValue().getOrderDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            return new javafx.beans.property.SimpleStringProperty(text);
        });
        colStatus.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getStatus()));
        colTotal.setCellValueFactory(data -> new javafx.beans.property.SimpleObjectProperty<>(data.getValue().getTotalAmount()));

        // Setup order item table columns
        colQuantity.setCellValueFactory(data -> new javafx.beans.property.SimpleIntegerProperty(data.getValue().getQuantity()));
        colUnitPrice.setCellValueFactory(data -> new javafx.beans.property.SimpleObjectProperty<>(data.getValue().getUnitPrice()));
        colLineTotal.setCellValueFactory(data -> new javafx.beans.property.SimpleObjectProperty<>(data.getValue().getLineTotal()));

        // Setup track column with caching
        colTrack.setCellValueFactory(data -> {
            int trackId = data.getValue().getTrackId();
            String label = trackLabelCache.get(trackId);
            if (label == null) {
                try {
                    Track t = trackDao.findById(trackId);
                    label = (t == null) ? ("Track #" + trackId) : (t.getTitle() + " - " + t.getArtist());
                } catch (SQLException e) {
                    label = "Track #" + trackId;
                }
                trackLabelCache.put(trackId, label);
            }
            return new javafx.beans.property.SimpleStringProperty(label);
        });

        // Set table items
        orderTable.setItems(orders);
        orderItemTable.setItems(orderItems);

        // Add selection listener
        orderTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            if (newSel != null) {
                loadOrderItems(newSel.getId());
                shippingCityField.setText(newSel.getShippingCity());
            } else {
                orderItems.clear();
                shippingCityField.clear();
            }
        });

        loadOrders();
    }

    // Load all orders for current user
    private void loadOrders() {
        if (!SessionManager.isLoggedIn()) {
            showError("You must be logged in to view orders.");
            return;
        }
        try {
            List<Order> list = orderDao.findByUser(SessionManager.getCurrentUser().getId());
            orders.setAll(list);
        } catch (SQLException e) {
            showError("Failed to load orders: " + e.getMessage());
        }
    }

    // Load order items for a specific order
    private void loadOrderItems(int orderId) {
        try {
            List<OrderItem> list = orderItemDao.findByOrder(orderId);
            orderItems.setAll(list);
        } catch (SQLException e) {
            showError("Failed to load order items: " + e.getMessage());
        }
    }

    // Handle confirm order button click
    @FXML
    private void handleConfirmOrder() {
        Order selected = orderTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        selected.setStatus("CONFIRMED");
        selected.setShippingCity(shippingCityField.getText());
        try {
            orderDao.update(selected);
            loadOrders();
        } catch (SQLException e) {
            showError("Failed to confirm order: " + e.getMessage());
        }
    }

    // Handle cancel order button click
    @FXML
    private void handleCancelOrder() {
        Order selected = orderTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        selected.setStatus("CANCELLED");
        try {
            orderDao.update(selected);
            loadOrders();
        } catch (SQLException e) {
            showError("Failed to cancel order: " + e.getMessage());
        }
    }

    // Handle delete order button click
    @FXML
    private void handleDeleteOrder() {
        Order selected = orderTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        try {
            orderItemDao.deleteByOrder(selected.getId());
            orderDao.delete(selected.getId());
            loadOrders();
        } catch (SQLException e) {
            showError("Failed to delete order: " + e.getMessage());
        }
    }

    // Handle view invoice button click
    @FXML
    private void handleViewInvoice() {
        Order selected = orderTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Please select an order to view invoice.");
            return;
        }

        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Invoice - Order #" + selected.getId());

        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(20));

        Label orderLabel = new Label("Order #" + selected.getId());
        orderLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        Label dateLabel = new Label("Date: " + selected.getOrderDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        Label statusLabel = new Label("Status: " + selected.getStatus());
        Label totalLabel = new Label("Total: " + selected.getTotalAmount());
        totalLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        // Create item table
        TableView<OrderItem> itemTable = new TableView<>(orderItems);
        itemTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<OrderItem, String> trackCol = new TableColumn<>("Track");
        trackCol.setCellValueFactory(data -> {
            int trackId = data.getValue().getTrackId();
            String label = trackLabelCache.get(trackId);
            if (label == null) {
                try {
                    Track t = trackDao.findById(trackId);
                    label = (t == null) ? ("Track #" + trackId) : (t.getTitle() + " - " + t.getArtist());
                } catch (SQLException e) {
                    label = "Track #" + trackId;
                }
                trackLabelCache.put(trackId, label);
            }
            return new javafx.beans.property.SimpleStringProperty(label);
        });

        TableColumn<OrderItem, Number> qtyCol = new TableColumn<>("Qty");
        qtyCol.setCellValueFactory(data -> new javafx.beans.property.SimpleIntegerProperty(data.getValue().getQuantity()));

        TableColumn<OrderItem, BigDecimal> priceCol = new TableColumn<>("Price");
        priceCol.setCellValueFactory(data -> new javafx.beans.property.SimpleObjectProperty<>(data.getValue().getUnitPrice()));

        TableColumn<OrderItem, BigDecimal> totalCol = new TableColumn<>("Total");
        totalCol.setCellValueFactory(data -> new javafx.beans.property.SimpleObjectProperty<>(data.getValue().getLineTotal()));

        itemTable.getColumns().addAll(trackCol, qtyCol, priceCol, totalCol);

        Button closeBtn = new Button("Close");
        closeBtn.setOnAction(event -> dialog.close());

        vbox.getChildren().addAll(orderLabel, dateLabel, statusLabel, totalLabel, new Label("Items:"), itemTable, closeBtn);

        Scene scene = new Scene(vbox, 600, 500);
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    // Show error message
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}