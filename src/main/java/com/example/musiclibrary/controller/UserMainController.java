package com.example.musiclibrary.controller;

import com.example.musiclibrary.MusicLibraryApp;
import com.example.musiclibrary.dao.CustomerDao;
import com.example.musiclibrary.dao.TrackDao;
import com.example.musiclibrary.model.Customer;
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
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.sql.SQLException;

public class UserMainController {

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

        // cap spinner max to the selected track's stock quantity
        trackTable.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected != null) {
                int max = Math.max(1, selected.getStockQty());
                quantitySpinnerFactory.setMax(max);
                if (quantitySpinner.getValue() > max) {
                    quantitySpinnerFactory.setValue(max);
                }
            }
        });

        setupTrackTableColumns();
        trackTable.setItems(trackData);
        loadAllTracks();
    }

    private void setupTrackTableColumns() {
        // cover image column
        colCover.setCellValueFactory(d ->
                new javafx.beans.property.SimpleObjectProperty<>(d.getValue()));

        colCover.setCellFactory(col -> new TableCell<>() {
            private final ImageView iv = new ImageView();

            @Override
            protected void updateItem(Track item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                iv.setFitWidth(80);
                iv.setFitHeight(80);
                iv.setPreserveRatio(true);
                iv.setImage(TrackMediaResolver.loadTrackImage(item, item.getId() + ".mp3"));
                setGraphic(iv);
            }
        });

        colTitle.setCellValueFactory(d ->
                new javafx.beans.property.SimpleStringProperty(d.getValue().getTitle()));

        colArtist.setCellValueFactory(d ->
                new javafx.beans.property.SimpleStringProperty(d.getValue().getArtist()));

        colGenre.setCellValueFactory(d ->
                new javafx.beans.property.SimpleStringProperty(d.getValue().getGenre()));

        colPrice.setCellValueFactory(d -> {
            BigDecimal price = d.getValue().getPrice();
            String text = price == null ? "" : price.toPlainString();
            return new javafx.beans.property.SimpleStringProperty(text);
        });

        colStock.setCellValueFactory(d ->
                new javafx.beans.property.SimpleStringProperty(String.valueOf(d.getValue().getStockQty())));
    }

    @FXML
    private void handleSearch() {
        String keyword = searchField.getText();
        try {
            if (keyword == null || keyword.isEmpty()) {
                trackData.setAll(trackDao.findAllActive());
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

        int qty = quantitySpinner.getValue();

        if (qty > selected.getStockQty()) {
            showError("Not enough stock. Available: " + selected.getStockQty());
            return;
        }

        // if the track is already in the cart, just increase the quantity
        for (OrderItem item : cartItems) {
            if (item.getTrackId() == selected.getId()) {
                int newQty = item.getQuantity() + qty;
                if (newQty > selected.getStockQty()) {
                    showError("Total quantity exceeds stock. Cart: " + item.getQuantity()
                            + ", Available: " + selected.getStockQty());
                    return;
                }
                item.setQuantity(newQty);
                item.setLineTotal(selected.getPrice().multiply(BigDecimal.valueOf(newQty)));
                showInfo("Added " + qty + " to cart. Total: " + newQty);
                return;
            }
        }

        // new item in cart
        OrderItem item = new OrderItem();
        item.setTrackId(selected.getId());
        item.setQuantity(qty);
        item.setUnitPrice(selected.getPrice());
        item.setLineTotal(selected.getPrice().multiply(BigDecimal.valueOf(qty)));
        cartItems.add(item);

        showInfo("Added " + qty + " to cart.");
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
        titleCol.setCellValueFactory(d -> {
            try {
                Track track = trackDao.findById(d.getValue().getTrackId());
                String name = track != null ? track.getTitle() : "Unknown";
                return new javafx.beans.property.SimpleStringProperty(name);
            } catch (SQLException e) {
                return new javafx.beans.property.SimpleStringProperty("Error");
            }
        });

        TableColumn<OrderItem, Number>     qtyCol   = new TableColumn<>("Quantity");
        TableColumn<OrderItem, BigDecimal> priceCol = new TableColumn<>("Price");
        TableColumn<OrderItem, BigDecimal> totalCol = new TableColumn<>("Total");

        qtyCol.setCellValueFactory(d ->
                new javafx.beans.property.SimpleIntegerProperty(d.getValue().getQuantity()));

        priceCol.setCellValueFactory(d ->
                new javafx.beans.property.SimpleObjectProperty<>(d.getValue().getUnitPrice()));

        totalCol.setCellValueFactory(d ->
                new javafx.beans.property.SimpleObjectProperty<>(d.getValue().getLineTotal()));

        cartTable.getColumns().addAll(titleCol, qtyCol, priceCol, totalCol);

        Button removeBtn = new Button("Remove Selected");
        removeBtn.setOnAction(e -> {
            OrderItem selected = cartTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                cartItems.remove(selected);
            }
        });

        Button clearBtn = new Button("Clear Cart");
        clearBtn.setOnAction(e -> {
            cartItems.clear();
            dialog.close();
        });

        Button checkoutBtn = new Button("Checkout");
        checkoutBtn.setOnAction(e -> {
            try {
                if (!SessionManager.isLoggedIn()) {
                    showError("You must be logged in to checkout.");
                    return;
                }
                int userId = SessionManager.getCurrentUser().getId();
                int customerId = resolveCustomerIdForCurrentUser();
                orderService.createOrder(customerId, userId, cartItems, null);
                cartItems.clear();
                loadAllTracks();
                dialog.close();
                showInfo("Order created successfully!");
            } catch (SQLException | IllegalArgumentException ex) {
                showError("Failed to create order: " + ex.getMessage());
            }
        });

        Button closeBtn = new Button("Close");
        closeBtn.setOnAction(e -> dialog.close());

        VBox vbox = new VBox(10, cartTable, new HBox(10, removeBtn, clearBtn, checkoutBtn, closeBtn));
        vbox.setPadding(new Insets(10));

        dialog.setScene(new Scene(vbox, 500, 400));
        dialog.showAndWait();
    }

    @FXML
    private void handleViewOrders() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/OrderManagementView.fxml"));
            Stage stage = new Stage();
            stage.setTitle("My Orders");
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(trackTable.getScene().getWindow());
            stage.setScene(new Scene(loader.load(), 920, 640));
            stage.setMinWidth(820);
            stage.setMinHeight(560);
            stage.showAndWait();
        } catch (Exception e) {
            Throwable root = getRootCause(e);
            showError("Failed to open order window ("
                    + root.getClass().getSimpleName() + "): " + root.getMessage());
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
            stage.setMinWidth(500);
            stage.setMinHeight(400);

            MusicPlayerController controller = loader.getController();
            stage.setOnCloseRequest(e -> controller.dispose());
            stage.showAndWait();

        } catch (Exception e) {
            Throwable root = getRootCause(e);
            showError("Failed to open music player ("
                    + root.getClass().getSimpleName() + "): " + root.getMessage());
        }
    }

    @FXML
    private void handleLogout() {
        SessionManager.clearCurrentUser();
        Stage stage = (Stage) trackTable.getScene().getWindow();

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/LoginView.fxml"));
            stage.setScene(new Scene(loader.load(),
                    MusicLibraryApp.LOGIN_SCENE_WIDTH, MusicLibraryApp.LOGIN_SCENE_HEIGHT));
            stage.setMinWidth(MusicLibraryApp.LOGIN_MIN_WIDTH);
            stage.setMinHeight(MusicLibraryApp.LOGIN_MIN_HEIGHT);
            stage.setWidth(MusicLibraryApp.LOGIN_SCENE_WIDTH);
            stage.setHeight(MusicLibraryApp.LOGIN_SCENE_HEIGHT);
            stage.centerOnScreen();
        } catch (Exception e) {
            showError("Failed to return to login screen: " + e.getMessage());
        }
    }

    private void loadAllTracks() {
        try {
            trackData.setAll(trackDao.findAllActive());
        } catch (SQLException e) {
            showError("Failed to load tracks: " + e.getMessage());
        }
    }

    // find or create a customer record that matches the currently logged-in user
    private int resolveCustomerIdForCurrentUser() throws SQLException {
        if (SessionManager.getCurrentUser() == null) {
            throw new IllegalArgumentException("No logged-in user.");
        }

        String username = SessionManager.getCurrentUser().getUsername();

        for (Customer customer : customerDao.findAll()) {
            if (username.equalsIgnoreCase(customer.getName())) {
                return customer.getId();
            }
        }

        // no matching customer found, create one
        Customer customer = new Customer();
        customer.setName(username);
        customer.setEmail(null);
        customer.setPhone(null);
        customer.setCity(null);

        int newId = customerDao.create(customer);
        if (newId <= 0) {
            throw new SQLException("Failed to create customer profile for user: " + username);
        }

        return newId;
    }

    private Throwable getRootCause(Throwable t) {
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t;
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}