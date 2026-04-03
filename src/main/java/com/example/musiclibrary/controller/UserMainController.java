package com.example.musiclibrary.controller;

import com.example.musiclibrary.dao.TrackDao;
import com.example.musiclibrary.model.OrderItem;
import com.example.musiclibrary.model.Track;
import com.example.musiclibrary.service.OrderService;
import com.example.musiclibrary.session.SessionManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.sql.SQLException;

public class UserMainController {

    @FXML
    private Label welcomeLabel;

    @FXML
    private TextField searchField;

    @FXML
    private TableView<Track> trackTable;

    @FXML
    private TableColumn<Track, String> colTitle;

    @FXML
    private TableColumn<Track, String> colArtist;

    @FXML
    private TableColumn<Track, String> colGenre;

    @FXML
    private TableColumn<Track, String> colPrice;

    @FXML
    private TableColumn<Track, String> colStock;

    private final ObservableList<Track> trackData = FXCollections.observableArrayList();
    private final ObservableList<OrderItem> cartItems = FXCollections.observableArrayList();

    private final TrackDao trackDao = new TrackDao();
    private final OrderService orderService = new OrderService();

    @FXML
    private void initialize() {
        if (SessionManager.getCurrentUser() != null) {
            welcomeLabel.setText("Welcome, " + SessionManager.getCurrentUser().getUsername());
        }

        colTitle.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getTitle()));
        colArtist.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getArtist()));
        colGenre.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getGenre()));
        colPrice.setCellValueFactory(data -> {
            BigDecimal price = data.getValue().getPrice();
            return new javafx.beans.property.SimpleStringProperty(price == null ? "" : price.toPlainString());
        });
        colStock.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(String.valueOf(data.getValue().getStockQty())));

        trackTable.setItems(trackData);
        loadAllTracks();
    }

    @FXML
    private void handleSearch() {
        String keyword = searchField.getText();
        try {
            if (keyword == null || keyword.isBlank()) {
                loadAllTracks();
            } else {
                trackData.setAll(trackDao.searchActiveByKeyword(keyword.trim()));
            }
        } catch (SQLException e) {
            showError("Failed to search tracks: " + e.getMessage());
        }
    }

    @FXML
    private void handleAddToCart() {
        Track selected = trackTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Please select a track first.");
            return;
        }
        if (selected.getStockQty() <= 0) {
            showError("Selected track is out of stock.");
            return;
        }
        OrderItem item = new OrderItem();
        item.setTrackId(selected.getId());
        item.setQuantity(1);
        cartItems.add(item);
        try {
            if (!SessionManager.isLoggedIn()) {
                showError("You must be logged in.");
                return;
            }
            int customerId = 1; // 简化：用固定 customer，实际可让用户选择
            orderService.createOrder(customerId, SessionManager.getCurrentUser().getId(), cartItems, null);
            cartItems.clear();
            loadAllTracks();
            showInfo("Order created successfully.");
        } catch (SQLException | IllegalArgumentException e) {
            showError("Failed to create order: " + e.getMessage());
        }
    }

    @FXML
    private void handleViewOrders() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/OrderManagementView.fxml"));
            Scene scene = new Scene(loader.load(), 920, 640);
            Stage stage = new Stage();
            stage.setTitle("My Orders");
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(trackTable.getScene().getWindow());
            stage.setScene(scene);
            stage.setMinWidth(820);
            stage.setMinHeight(560);
            stage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
            Throwable root = getRootCause(e);
            String msg = root.getMessage();
            showError("Failed to open order window (" + root.getClass().getSimpleName() + "): " + (msg == null ? "" : msg));
        }
    }

    private Throwable getRootCause(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root;
    }

    private void loadAllTracks() {
        try {
            trackData.setAll(trackDao.findAllActive());
        } catch (SQLException e) {
            showError("Failed to load tracks: " + e.getMessage());
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
