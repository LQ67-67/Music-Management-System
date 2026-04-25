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
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
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

        // calculate the total price of everything in the cart
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

        Button checkoutBtn = new Button("Checkout");
        checkoutBtn.setStyle("-fx-background-color: #2e7d32; -fx-text-fill: white; -fx-font-weight: bold;");

        checkoutBtn.setOnAction(e -> {
            try {
                int customerId = resolveCustomerIdForCurrentUser();
                int userId = SessionManager.getCurrentUser().getId();

                Order newOrder = orderService.createOrder(customerId, userId, cartItems, null); // create the order
                newOrder.setStatus("PENDING");
                orderDao.update(newOrder);

                // clear cart and refresh
                cartItems.clear();
                loadAllTracks();
                dialog.close();

                showInfo("Checkout successful!\nPlease go to 'View Orders' to complete your payment.");

            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Checkout failed", ex);
                showError("Checkout failed: " + ex.getMessage());
            }
        });

        Button closeBtn = new Button("Close");
        closeBtn.setOnAction(e -> dialog.close());

        VBox layoutBox = new VBox(12, cartTable, totalLabel,
                new HBox(10, removeBtn, clearBtn, checkoutBtn, closeBtn));
        layoutBox.setPadding(new Insets(15));
        dialog.setScene(new Scene(layoutBox, 600, 430));
        dialog.showAndWait();
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