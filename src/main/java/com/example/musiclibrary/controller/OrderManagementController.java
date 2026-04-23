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
        colOrderId.setCellValueFactory(param -> new SimpleIntegerProperty(param.getValue().getId()));

        colOrderDate.setCellValueFactory(param -> {
            if (param.getValue().getOrderDate() == null) {
                return new SimpleStringProperty("");
            }
            String formattedDate = param.getValue().getOrderDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            return new SimpleStringProperty(formattedDate);
        });

        colStatus.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getStatus()));

        colTotal.setCellValueFactory(param -> new SimpleObjectProperty<>(param.getValue().getTotalAmount()));

        // --- Order Item Table Columns ---
        colTrack.setCellValueFactory(param -> new SimpleStringProperty(getTrackLabel(param.getValue().getTrackId())));

        colQuantity.setCellValueFactory(param -> new SimpleIntegerProperty(param.getValue().getQuantity()));

        colUnitPrice.setCellValueFactory(param -> new SimpleObjectProperty<>(param.getValue().getUnitPrice()));

        colLineTotal.setCellValueFactory(param -> new SimpleObjectProperty<>(param.getValue().getLineTotal()));

        orderTable.setItems(orders);
        orderItemTable.setItems(orderItems);

        // when an order is selected, show its items and shipping city
        orderTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                loadOrderItems(newValue.getId());
                shippingCityField.setText(newValue.getShippingCity());
            } else {
                orderItems.clear();
                shippingCityField.clear();
            }
        });

        loadOrders();
    }

    private void loadOrders() {
        if (!SessionManager.isLoggedIn()) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "You must be logged in to view orders.");
            alert.showAndWait();
            return;
        }
        try {
            List<Order> list = orderDao.findByUser(SessionManager.getCurrentUser().getId());
            orders.setAll(list);
        } catch (SQLException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Failed to load orders: " + e.getMessage());
            alert.showAndWait();
        }
    }

    private void loadOrderItems(int orderId) {
        try {
            orderItems.setAll(orderItemDao.findByOrder(orderId));
        } catch (SQLException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Failed to load order items: " + e.getMessage());
            alert.showAndWait();
        }
    }

    @FXML
    private void handleConfirmOrder() {
        Order selectedOrder = orderTable.getSelectionModel().getSelectedItem();
        if (selectedOrder == null) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Please select an order first.");
            alert.showAndWait();
            return;
        }

        selectedOrder.setStatus("CONFIRMED");
        selectedOrder.setShippingCity(shippingCityField.getText());

        try {
            orderDao.update(selectedOrder);
            loadOrders();
        } catch (SQLException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Failed to confirm order: " + e.getMessage());
            alert.showAndWait();
        }
    }

    @FXML
    private void handleCancelOrder() {
        Order selectedOrder = orderTable.getSelectionModel().getSelectedItem();
        if (selectedOrder == null) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Please select an order first.");
            alert.showAndWait();
            return;
        }

        selectedOrder.setStatus("CANCELLED");

        try {
            orderDao.update(selectedOrder);
            loadOrders();
        } catch (SQLException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Failed to cancel order: " + e.getMessage());
            alert.showAndWait();
        }
    }

    @FXML
    private void handleDeleteOrder() {
        Order selectedOrder = orderTable.getSelectionModel().getSelectedItem();
        if (selectedOrder == null) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Please select an order first.");
            alert.showAndWait();
            return;
        }

        try {
            orderDao.delete(selectedOrder.getId());
            loadOrders();
            orderItems.clear();
            shippingCityField.clear();
        } catch (SQLException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Failed to delete order: " + e.getMessage());
            alert.showAndWait();
        }
    }

    @FXML
    private void handleViewInvoice() {
        Order selectedOrder = orderTable.getSelectionModel().getSelectedItem();
        if (selectedOrder == null) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Please select an order to view invoice.");
            alert.showAndWait();
            return;
        }

        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Invoice - Order #" + selectedOrder.getId());

        Label orderLabel = new Label("Order #" + selectedOrder.getId());
        orderLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        String dateString = "N/A";
        if (selectedOrder.getOrderDate() != null) {
            dateString = selectedOrder.getOrderDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        }
        Label dateLabel = new Label("Date: " + dateString);

        Label statusLabel = new Label("Status: " + selectedOrder.getStatus());

        Label totalLabel = new Label("Total: " + selectedOrder.getTotalAmount());
        totalLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        TableView<OrderItem> itemTable = new TableView<>(orderItems);
        itemTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<OrderItem, String> trackCol = new TableColumn<>("Track");
        trackCol.setCellValueFactory(param -> new SimpleStringProperty(getTrackLabel(param.getValue().getTrackId())));

        TableColumn<OrderItem, Number> qtyCol = new TableColumn<>("Qty");
        qtyCol.setCellValueFactory(param -> new SimpleIntegerProperty(param.getValue().getQuantity()));

        TableColumn<OrderItem, BigDecimal> priceCol = new TableColumn<>("Price");
        priceCol.setCellValueFactory(param -> new SimpleObjectProperty<>(param.getValue().getUnitPrice()));

        TableColumn<OrderItem, BigDecimal> totCol = new TableColumn<>("Total");
        totCol.setCellValueFactory(param -> new SimpleObjectProperty<>(param.getValue().getLineTotal()));

        itemTable.getColumns().add(trackCol);
        itemTable.getColumns().add(qtyCol);
        itemTable.getColumns().add(priceCol);
        itemTable.getColumns().add(totCol);

        Button closeBtn = new Button("Close");
        closeBtn.setOnAction(event -> dialog.close());

        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(20));
        vbox.getChildren().addAll(orderLabel, dateLabel, statusLabel, totalLabel, new Label("Items:"), itemTable, closeBtn);

        dialog.setScene(new Scene(vbox, 600, 500));
        dialog.showAndWait();
    }

    private String getTrackLabel(int trackId) {
        // if already looked this up before, return the saved name
        if (trackLabelCache.containsKey(trackId)) {
            return trackLabelCache.get(trackId);
        }

        // if system haven't looked it up yet, fetch it from the database
        String trackName;
        try {
            Track track = trackDao.findById(trackId);
            if (track == null) {
                trackName = "Track #" + trackId;
            } else {
                trackName = track.getTitle() + " - " + track.getArtist();
            }
        } catch (SQLException e) {
            trackName = "Track #" + trackId;
        }

        // save it for next time and return it
        trackLabelCache.put(trackId, trackName);
        return trackName;
    }
}