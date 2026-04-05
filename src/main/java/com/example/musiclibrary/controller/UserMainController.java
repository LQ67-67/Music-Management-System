package com.example.musiclibrary.controller;

import com.example.musiclibrary.dao.TrackDao;
import com.example.musiclibrary.model.OrderItem;
import com.example.musiclibrary.model.Track;
import com.example.musiclibrary.service.OrderService;
import com.example.musiclibrary.session.SessionManager;
import com.example.musiclibrary.util.TrackMediaResolver;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
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
    private TableColumn<Track, Track> colCover;

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

        colCover.setCellValueFactory(data -> new javafx.beans.property.SimpleObjectProperty<>(data.getValue()));
        colCover.setCellFactory(col -> new TableCell<>() {
            private final ImageView imageView = createTrackImageView();

            @Override
            protected void updateItem(Track item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                imageView.setImage(TrackMediaResolver.loadTrackImage(item, item.getId() + ".mp3"));
                setGraphic(imageView);
            }
        });
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

        // 检查是否已经在购物车中
        for (OrderItem item : cartItems) {
            if (item.getTrackId() == selected.getId()) {
                item.setQuantity(item.getQuantity() + 1);
                showInfo("Added to cart. Total quantity: " + item.getQuantity());
                return;
            }
        }

        OrderItem item = new OrderItem();
        item.setTrackId(selected.getId());
        item.setQuantity(1);
        item.setUnitPrice(selected.getPrice());
        item.setLineTotal(selected.getPrice());
        cartItems.add(item);
        showInfo("Added to cart.");
    }

    @FXML
    private void handleViewCart() {
        if (cartItems.isEmpty()) {
            showInfo("Your cart is empty.");
            return;
        }

        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Shopping Cart");

        TableView<OrderItem> cartTable = new TableView<>(cartItems);
        cartTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<OrderItem, String> titleCol = new TableColumn<>("Track");
        titleCol.setCellValueFactory(data -> {
            try {
                Track t = trackDao.findById(data.getValue().getTrackId());
                return new javafx.beans.property.SimpleStringProperty(t != null ? t.getTitle() : "Unknown");
            } catch (SQLException e) {
                return new javafx.beans.property.SimpleStringProperty("Error");
            }
        });

        TableColumn<OrderItem, Number> qtyCol = new TableColumn<>("Quantity");
        qtyCol.setCellValueFactory(data -> new javafx.beans.property.SimpleIntegerProperty(data.getValue().getQuantity()));

        TableColumn<OrderItem, BigDecimal> priceCol = new TableColumn<>("Price");
        priceCol.setCellValueFactory(data -> new javafx.beans.property.SimpleObjectProperty<>(data.getValue().getUnitPrice()));

        TableColumn<OrderItem, BigDecimal> totalCol = new TableColumn<>("Total");
        totalCol.setCellValueFactory(data -> new javafx.beans.property.SimpleObjectProperty<>(data.getValue().getLineTotal()));

        cartTable.getColumns().addAll(titleCol, qtyCol, priceCol, totalCol);

        Button removeButton = new Button("Remove Selected");
        removeButton.setOnAction(event -> {
            OrderItem selected = cartTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                cartItems.remove(selected);
            }
        });

        Button clearButton = new Button("Clear Cart");
        clearButton.setOnAction(event -> {
            cartItems.clear();
            dialog.close();
        });

        Button checkoutButton = new Button("Checkout");
        checkoutButton.setOnAction(event -> {
            try {
                if (!SessionManager.isLoggedIn()) {
                    showError("You must be logged in to checkout.");
                    return;
                }
                int customerId = 1;
                orderService.createOrder(customerId, SessionManager.getCurrentUser().getId(), cartItems, null);
                cartItems.clear();
                loadAllTracks();
                dialog.close();
                showInfo("Order created successfully!");
            } catch (SQLException | IllegalArgumentException e) {
                showError("Failed to create order: " + e.getMessage());
            }
        });

        Button closeButton = new Button("Close");
        closeButton.setOnAction(event -> dialog.close());

        HBox buttonBox = new HBox(10, removeButton, clearButton, checkoutButton, closeButton);

        VBox vbox = new VBox(10, cartTable, buttonBox);
        vbox.setPadding(new Insets(10));
        Scene scene = new Scene(vbox, 500, 400);
        dialog.setScene(scene);
        dialog.showAndWait();
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

    @FXML
    private void handleOpenMusicPlayer() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MusicPlayerView.fxml"));
            Scene scene = new Scene(loader.load(), 600, 500);
            Stage stage = new Stage();
            stage.setTitle("Music Player");
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(trackTable.getScene().getWindow());
            stage.setScene(scene);
            stage.setMinWidth(500);
            stage.setMinHeight(400);
            stage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
            Throwable root = getRootCause(e);
            String msg = root.getMessage();
            showError("Failed to open music player (" + root.getClass().getSimpleName() + "): " + (msg == null ? "" : msg));
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

    private ImageView createTrackImageView() {
        ImageView imageView = new ImageView();
        imageView.setFitWidth(56);
        imageView.setFitHeight(56);
        imageView.setPreserveRatio(true);
        return imageView;
    }
}
