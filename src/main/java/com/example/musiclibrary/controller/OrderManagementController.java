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
    @FXML private TableView<Order> orderTable;
    @FXML private TableColumn<Order, Number> colOrderId;
    @FXML private TableColumn<Order, String> colOrderDate;
    @FXML private TableColumn<Order, String> colStatus;
    @FXML private TableColumn<Order, BigDecimal> colTotal;

    @FXML private TableView<OrderItem> orderItemTable;
    @FXML private TableColumn<OrderItem, String> colTrack;
    @FXML private TableColumn<OrderItem, Number> colQuantity;
    @FXML private TableColumn<OrderItem, BigDecimal> colUnitPrice;
    @FXML private TableColumn<OrderItem, BigDecimal> colLineTotal;

    @FXML private TextField shippingCityField;

    private final ObservableList<Order> orders = FXCollections.observableArrayList();
    private final ObservableList<OrderItem> orderItems = FXCollections.observableArrayList();
    private final OrderDao orderDao = new OrderDao();
    private final OrderItemDao orderItemDao = new OrderItemDao();
    private final TrackDao trackDao = new TrackDao();
    private final Map<Integer, String> trackLabelCache = new HashMap<>();

    @FXML
    private void initialize() {
        // order table columns
        colOrderId.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().getId()));
        colOrderDate.setCellValueFactory(d -> {
            if (d.getValue().getOrderDate() == null) return new SimpleStringProperty("");
            return new SimpleStringProperty(d.getValue().getOrderDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        });
        colStatus.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getStatus()));
        colTotal.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().getTotalAmount()));

        // order item table columns
        colTrack.setCellValueFactory(d -> new SimpleStringProperty(getTrackLabel(d.getValue().getTrackId())));
        colQuantity.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().getQuantity()));
        colUnitPrice.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().getUnitPrice()));
        colLineTotal.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().getLineTotal()));

        orderTable.setItems(orders);
        orderItemTable.setItems(orderItems);

        // when an order is selected, show its items and shipping city
        orderTable.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            if (sel != null) {
                loadOrderItems(sel.getId());
                shippingCityField.setText(sel.getShippingCity());
            } else {
                orderItems.clear();
                shippingCityField.clear();
            }
        });

        loadOrders();
    }

    private void loadOrders() {
        if (!SessionManager.isLoggedIn()) { showError("You must be logged in to view orders."); return; }
        try {
            List<Order> list = orderDao.findByUser(SessionManager.getCurrentUser().getId());
            orders.setAll(list);
        } catch (SQLException e) {
            showError("Failed to load orders: " + e.getMessage());
        }
    }

    private void loadOrderItems(int orderId) {
        try { orderItems.setAll(orderItemDao.findByOrder(orderId)); }
        catch (SQLException e) { showError("Failed to load order items: " + e.getMessage()); }
    }

    // set selected order status to CONFIRMED and save shipping city
    @FXML
    private void handleConfirmOrder() {
        Order sel = orderTable.getSelectionModel().getSelectedItem();
        if (sel == null) { showError("Please select an order first."); return; }
        sel.setStatus("CONFIRMED");
        sel.setShippingCity(shippingCityField.getText());
        try { orderDao.update(sel); loadOrders(); }
        catch (SQLException e) { showError("Failed to confirm order: " + e.getMessage()); }
    }

    // set selected order status to CANCELLED
    @FXML
    private void handleCancelOrder() {
        Order sel = orderTable.getSelectionModel().getSelectedItem();
        if (sel == null) { showError("Please select an order first."); return; }
        sel.setStatus("CANCELLED");
        try { orderDao.update(sel); loadOrders(); }
        catch (SQLException e) { showError("Failed to cancel order: " + e.getMessage()); }
    }

    // delete selected order and all its items
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
            showError("Failed to delete order: " + e.getMessage());
        }
    }

    // show popup with a full invoice for the selected order
    @FXML
    private void handleViewInvoice() {
        Order sel = orderTable.getSelectionModel().getSelectedItem();
        if (sel == null) { showError("Please select an order to view invoice."); return; }

        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Invoice - Order #" + sel.getId());

        Label orderLabel = new Label("Order #" + sel.getId());
        orderLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        Label dateLabel   = new Label("Date: " + (sel.getOrderDate() == null ? "N/A" : sel.getOrderDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))));
        Label statusLabel = new Label("Status: " + sel.getStatus());
        Label totalLabel  = new Label("Total: " + sel.getTotalAmount());
        totalLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        TableView<OrderItem> itemTable = new TableView<>(orderItems);
        itemTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<OrderItem, String> trackCol = new TableColumn<>("Track");
        TableColumn<OrderItem, Number> qtyCol = new TableColumn<>("Qty");
        TableColumn<OrderItem, BigDecimal> priceCol = new TableColumn<>("Price");
        TableColumn<OrderItem, BigDecimal> totCol = new TableColumn<>("Total");

        trackCol.setCellValueFactory(d -> new SimpleStringProperty(getTrackLabel(d.getValue().getTrackId())));
        qtyCol.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().getQuantity()));
        priceCol.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().getUnitPrice()));
        totCol.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().getLineTotal()));
        itemTable.getColumns().addAll(trackCol, qtyCol, priceCol, totCol);

        Button closeBtn = new Button("Close");
        closeBtn.setOnAction(e -> dialog.close());

        VBox vbox = new VBox(10, orderLabel, dateLabel, statusLabel, totalLabel, new Label("Items:"), itemTable, closeBtn);
        vbox.setPadding(new Insets(20));
        dialog.setScene(new Scene(vbox, 600, 500));
        dialog.showAndWait();
    }

    // look up a track's display label, caching results to avoid repeated DB calls
    private String getTrackLabel(int trackId) {
        return trackLabelCache.computeIfAbsent(trackId, id -> {
            try {
                Track t = trackDao.findById(id);
                return t == null ? "Track #" + id : t.getTitle() + " - " + t.getArtist();
            } catch (SQLException e) {
                return "Track #" + id;
            }
        });
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}