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
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.geometry.Insets;

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

    private final ObservableList<Order> orders = FXCollections.observableArrayList();
    private final ObservableList<OrderItem> orderItems = FXCollections.observableArrayList();

    private final OrderDao orderDao = new OrderDao();
    private final OrderItemDao orderItemDao = new OrderItemDao();
    private final TrackDao trackDao = new TrackDao();
    private final Map<Integer, String> trackLabelCache = new HashMap<>();

    @FXML
    private void initialize() {
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

        colQuantity.setCellValueFactory(data -> new javafx.beans.property.SimpleIntegerProperty(data.getValue().getQuantity()));
        colUnitPrice.setCellValueFactory(data -> new javafx.beans.property.SimpleObjectProperty<>(data.getValue().getUnitPrice()));
        colLineTotal.setCellValueFactory(data -> new javafx.beans.property.SimpleObjectProperty<>(data.getValue().getLineTotal()));

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

        orderTable.setItems(orders);
        orderItemTable.setItems(orderItems);

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

    private void loadOrderItems(int orderId) {
        try {
            List<OrderItem> list = orderItemDao.findByOrder(orderId);
            orderItems.setAll(list);
        } catch (SQLException e) {
            showError("Failed to load order items: " + e.getMessage());
        }
    }

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

        Button closeButton = new Button("Close");
        closeButton.setOnAction(event -> dialog.close());

        vbox.getChildren().addAll(orderLabel, dateLabel, statusLabel, totalLabel, new Label("Items:"), itemTable, closeButton);

        Scene scene = new Scene(vbox, 600, 500);
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}

