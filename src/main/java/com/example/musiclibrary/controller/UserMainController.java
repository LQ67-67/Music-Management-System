package com.example.musiclibrary.controller;

import com.example.musiclibrary.MusicLibraryApp;
import com.example.musiclibrary.dao.CustomerDao;
import com.example.musiclibrary.dao.OrderDao;
import com.example.musiclibrary.dao.TrackDao;
import com.example.musiclibrary.dao.UserDao;
import com.example.musiclibrary.model.Customer;
import com.example.musiclibrary.model.Order;
import com.example.musiclibrary.model.OrderItem;
import com.example.musiclibrary.model.Track;
import com.example.musiclibrary.model.User;
import com.example.musiclibrary.service.OrderService;
import com.example.musiclibrary.session.SessionManager;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.StageStyle;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class UserMainController {
    private static final Logger LOGGER = Logger.getLogger(UserMainController.class.getName());

    @FXML private Label welcomeLabel;
    @FXML private TextField searchField;
    @FXML private FlowPane trackGrid;

    private final ObservableList<Track> trackData = FXCollections.observableArrayList();
    private final ObservableList<OrderItem> cartItems = FXCollections.observableArrayList();
    private final CustomerDao customerDao = new CustomerDao();
    private final OrderDao orderDao = new OrderDao();
    private final TrackDao trackDao = new TrackDao();
    private final UserDao userDao = new UserDao();
    private final OrderService orderService = new OrderService();
    private final Map<Integer, Integer> quantitySelections = new HashMap<>();

    private Track selectedTrack = null;
    private VBox selectedCardPane = null;
    private TrackCardController selectedCardController = null;
    private Stage overlayDialog;

    @FXML
    private void initialize() {
        if (SessionManager.getCurrentUser() != null) {
            welcomeLabel.setText("Welcome, " + SessionManager.getCurrentUser().getUsername() + " ☀️");
        }

        // Configure FlowPane
        trackGrid.setHgap(12);
        trackGrid.setVgap(12);
        trackGrid.setPrefWrapLength(1180);
        trackGrid.prefWrapLengthProperty().bind(trackGrid.widthProperty().subtract(24));
        trackGrid.setMaxWidth(Double.MAX_VALUE);

        // Setup the track grid
        setupTrackGrid();
        loadAllTracks();
    }

    private void setupTrackGrid() {
        // Track data change listener
        trackData.addListener((ListChangeListener<Track>) change -> {
            refreshTrackGrid();
        });
    }

    /**
     * Refresh the track grid by reloading all track cards.
     */
    private void refreshTrackGrid() {
        trackGrid.getChildren().clear();
        selectedTrack = null;
        selectedCardPane = null;
        selectedCardController = null;

        for (Track track : trackData) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/TrackCard.fxml"));
                VBox cardPane = loader.load();
                TrackCardController cardController = loader.getController();
                cardController.setTrack(track);
                cardController.setRootPane(cardPane);
                cardController.setQuantity(quantitySelections.getOrDefault(track.getId(), 1));
                cardController.setQuantityChangeListener(qty -> {
                    quantitySelections.put(track.getId(), qty);
                });
                cardController.setPurchaseRequestListener(this::showPurchaseDialog);
                cardController.setSelected(false);

                // Handle card click for selection
                cardPane.setOnMouseClicked(event -> selectTrack(track, cardPane, cardController));

                trackGrid.getChildren().add(cardPane);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to load track card", e);
            }
        }

        syncSelectedCardQuantity();
    }

    /**
     * Select a track by clicking on its card.
     */
    private void selectTrack(Track track, VBox cardPane, TrackCardController cardController) {
        // Deselect previous selection
        if (selectedCardPane != null) {
            selectedCardPane.getStyleClass().remove("selected");
        }

        if (selectedCardController != null) {
            selectedCardController.setSelected(false);
        }

        // Select new track
        selectedTrack = track;
        selectedCardPane = cardPane;
        selectedCardController = cardController;
        selectedCardController.setSelected(true);

        // Update quantity spinner
        syncSelectedCardQuantity();
    }

    /**
     * Keep the selected card quantity within the available stock.
     */
    private void syncSelectedCardQuantity() {
        if (selectedTrack != null) {
            int maxStock = Math.max(1, selectedTrack.getStockQty());
            int desired = Math.max(1, Math.min(quantitySelections.getOrDefault(selectedTrack.getId(), 1), maxStock));
            if (selectedCardController != null) {
                selectedCardController.setQuantity(desired);
            }
        }
    }

    @FXML
    public void handleEditProfile() {
        User currentUser = SessionManager.getCurrentUser();
        if (currentUser == null) {
            showError("Please log in to edit your profile.");
            return;
        }

        try {
            Customer existingCustomer = customerDao.findAll().stream()
                    .filter(c -> currentUser.getUsername().equalsIgnoreCase(c.getName()))
                    .findFirst().orElse(null);

            if (existingCustomer == null) {
                existingCustomer = new Customer();
                existingCustomer.setName(currentUser.getUsername());
                existingCustomer.setEmail("");
                existingCustomer.setPhone("");
                existingCustomer.setCity("");
                existingCustomer.setId(customerDao.create(existingCustomer));
            }

            Customer customerToUpdate = existingCustomer;
            VBox form = new VBox(12);
            form.getStyleClass().add("dialog-surface");
            form.setPadding(new Insets(20));
            form.setMaxWidth(520);

            Label title = new Label("Edit Profile");
            title.getStyleClass().add("display-sub");

            GridPane grid = new GridPane();
            grid.getStyleClass().add("form-grid");

            TextField usernameField = new TextField(currentUser.getUsername());
            PasswordField passwordField = new PasswordField();
            passwordField.setText(currentUser.getPasswordHash());
            TextField emailField = new TextField(customerToUpdate.getEmail() == null ? "" : customerToUpdate.getEmail());
            TextField phoneField = new TextField(customerToUpdate.getPhone() == null ? "" : customerToUpdate.getPhone());
            TextField cityField = new TextField(customerToUpdate.getCity() == null ? "" : customerToUpdate.getCity());

            grid.add(new Label("Name / Username:"), 0, 0); grid.add(usernameField, 1, 0);
            grid.add(new Label("Password:"), 0, 1); grid.add(passwordField, 1, 1);
            grid.add(new Label("Email Address:"), 0, 2); grid.add(emailField, 1, 2);
            grid.add(new Label("Phone:"), 0, 3); grid.add(phoneField, 1, 3);
            grid.add(new Label("City:"), 0, 4); grid.add(cityField, 1, 4);

            Label validation = new Label();
            validation.getStyleClass().add("validation-error");

            Button saveButton = new Button("Save");
            saveButton.getStyleClass().add("wise-pill-primary");
            Button cancelButton = new Button("Cancel");
            cancelButton.getStyleClass().add("wise-pill-secondary");

            saveButton.setOnAction(e -> {
                String username = usernameField.getText().trim();
                String password = passwordField.getText();
                String email = emailField.getText().trim();
                String phone = phoneField.getText().trim();
                String city = cityField.getText().trim();

                if (username.isEmpty() || password.isEmpty()) {
                    validation.setText("Name and password are required.");
                    return;
                }
                if (!email.isEmpty() && !email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
                    validation.setText("Invalid email format.");
                    return;
                }
                if (!phone.isEmpty() && !phone.matches("^[0-9+\\-() ]{6,20}$")) {
                    validation.setText("Invalid phone number format.");
                    return;
                }

                try {
                    User updatedUser = new User(currentUser.getId(), username, password, currentUser.getRole());
                    userDao.update(updatedUser);

                    customerToUpdate.setName(username);
                    customerToUpdate.setEmail(email);
                    customerToUpdate.setPhone(phone);
                    customerToUpdate.setCity(city);
                    customerDao.update(customerToUpdate);

                    SessionManager.setCurrentUser(updatedUser);
                    welcomeLabel.setText("Welcome, " + username + " ☀️");
                    overlayDialog.close();
                } catch (SQLException ex) {
                    LOGGER.log(Level.SEVERE, "Failed to save profile", ex);
                    validation.setText("Failed to save profile: " + ex.getMessage());
                }
            });

            cancelButton.setOnAction(e -> overlayDialog.close());

            HBox actions = new HBox(12, saveButton, cancelButton);
            actions.setAlignment(Pos.CENTER_RIGHT);
            form.getChildren().addAll(title, grid, validation, actions);

            showOverlayDialog(form, 600, 520);

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
        if (selectedTrack == null) {
            showError("Please select a track from the grid.");
            return;
        }
        addTrackToCart(selectedTrack, selectedCardController != null ? selectedCardController.getQuantity() : quantitySelections.getOrDefault(selectedTrack.getId(), 1));
    }

    @FXML
    private void handleViewCart() {
        if (cartItems.isEmpty()) {
            showInfo("Cart is empty.");
            return;
        }

        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Shopping Cart");

        TableView<OrderItem> cartTable = new TableView<>(cartItems);
        cartTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        Label totalLabel = new Label(String.format("Cart Total: RM %.2f", calculateCartTotal()));
        totalLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

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
        qtyCol.setCellFactory(col -> new TableCell<>() {
            private final Spinner<Integer> spinner = new Spinner<>();

            {
                spinner.setEditable(true);
                spinner.valueProperty().addListener((obs, oldVal, newVal) -> {
                    OrderItem item = getTableRow() == null ? null : getTableRow().getItem();
                    if (item == null || newVal == null || newVal.equals(oldVal)) {
                        return;
                    }
                    updateCartItemQuantity(item, newVal);
                    cartTable.refresh();
                    totalLabel.setText(String.format("Cart Total: RM %.2f", calculateCartTotal()));
                });
            }

            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    return;
                }

                OrderItem orderItem = getTableRow().getItem();
                int maxQty = resolveMaxCartQuantity(orderItem);
                spinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, maxQty, orderItem.getQuantity()));
                setGraphic(spinner);
            }
        });

        TableColumn<OrderItem, BigDecimal> priceCol = new TableColumn<>("Price");
        priceCol.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().getUnitPrice()));

        TableColumn<OrderItem, BigDecimal> totalCol = new TableColumn<>("Total");
        totalCol.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().getLineTotal()));

        cartTable.getColumns().addAll(titleCol, qtyCol, priceCol, totalCol);

        Button removeBtn = new Button("Remove Selected");
        removeBtn.setStyle("-fx-background-color: #FF6B6B; -fx-text-fill: white; -fx-padding: 8 16; -fx-font-weight: 600;");
        removeBtn.setOnAction(e -> {
            OrderItem sel = cartTable.getSelectionModel().getSelectedItem();
            if (sel != null) {
                cartItems.remove(sel);
                totalLabel.setText(String.format("Cart Total: RM %.2f", calculateCartTotal()));
            }
        });

        Button clearBtn = new Button("Clear Cart");
        clearBtn.setStyle("-fx-background-color: #FF6B6B; -fx-text-fill: white; -fx-padding: 8 16; -fx-font-weight: 600;");
        clearBtn.setOnAction(e -> {
            cartItems.clear();
            dialog.close();
        });

        Button checkoutBtn = new Button("Checkout");
        checkoutBtn.setStyle("-fx-background-color: #FFFFFF; -fx-text-fill: #000000; -fx-padding: 8 20; -fx-font-weight: 600;");

        checkoutBtn.setOnAction(e -> {
            try {
                int customerId = resolveCustomerIdForCurrentUser();
                int userId = SessionManager.getCurrentUser().getId();

                Order newOrder = orderService.createOrder(customerId, userId, cartItems, null);
                newOrder.setStatus("PENDING");
                orderDao.update(newOrder);

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
        closeBtn.setStyle("-fx-padding: 8 16;");
        closeBtn.setOnAction(e -> dialog.close());

        VBox layoutBox = new VBox(12, cartTable, totalLabel,
                new HBox(10, removeBtn, clearBtn, checkoutBtn, closeBtn));
        layoutBox.setPadding(new Insets(15));
        Scene scene = new Scene(layoutBox, 760, 540);
        applyAppStylesheet(scene);
        dialog.setScene(scene);
        dialog.setMinWidth(700);
        dialog.setMinHeight(500);
        dialog.setResizable(true);
        dialog.showAndWait();
    }

    private void updateCartItemQuantity(OrderItem item, int quantity) {
        item.setQuantity(quantity);
        item.setLineTotal(item.getUnitPrice().multiply(BigDecimal.valueOf(quantity)));
    }

    private BigDecimal calculateCartTotal() {
        return cartItems.stream()
                .map(OrderItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private int resolveMaxCartQuantity(OrderItem item) {
        try {
            Track track = trackDao.findById(item.getTrackId());
            if (track != null && track.getStockQty() > 0) {
                return track.getStockQty();
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to resolve cart quantity limit", e);
        }
        return Math.max(1, item.getQuantity());
    }

    @FXML
    private void handleViewOrders() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/OrderManagementView.fxml"));
            Stage stage = new Stage();
            stage.setTitle("My Order History");
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(trackGrid.getScene().getWindow());
            Scene scene = new Scene(loader.load(), 1120, 780);
            applyAppStylesheet(scene);
            stage.setScene(scene);
            stage.setMinWidth(1000);
            stage.setMinHeight(700);
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
            stage.initOwner(trackGrid.getScene().getWindow());
            Scene scene = new Scene(loader.load(), 1040, 760);
            applyAppStylesheet(scene);
            stage.setScene(scene);
            stage.setMinWidth(920);
            stage.setMinHeight(680);
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
        Stage confirmDialog = new Stage();
        confirmDialog.initOwner(trackGrid.getScene().getWindow());
        confirmDialog.initModality(Modality.WINDOW_MODAL);
        confirmDialog.setTitle("Confirm Logout");

        VBox content = new VBox(14);
        content.getStyleClass().add("report-panel");
        content.setMaxWidth(560);
        content.setPadding(new Insets(20));

        Label title = new Label("CONFIRM LOGOUT");
        title.getStyleClass().add("heading-2");
        Label message = new Label("Are you sure you want to sign out of the Music Library?");
        message.setWrapText(true);
        message.setMaxWidth(460);

        Button confirm = new Button("🚪 LOGOUT");
        confirm.getStyleClass().add("neo-button-danger");
        confirm.setOnAction(e -> {
            SessionManager.clearCurrentUser();
            Stage stage = (Stage) trackGrid.getScene().getWindow();
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/LoginView.fxml"));
                Scene scene = new Scene(loader.load(), MusicLibraryApp.LOGIN_SCENE_WIDTH, MusicLibraryApp.LOGIN_SCENE_HEIGHT);
                applyAppStylesheet(scene);
                stage.setScene(scene);
                stage.setMaximized(false);
                stage.setMinWidth(MusicLibraryApp.LOGIN_MIN_WIDTH);
                stage.setMinHeight(MusicLibraryApp.LOGIN_MIN_HEIGHT);
                stage.setResizable(true);
                stage.setWidth(MusicLibraryApp.LOGIN_SCENE_WIDTH);
                stage.setHeight(MusicLibraryApp.LOGIN_SCENE_HEIGHT);
                stage.centerOnScreen();
                confirmDialog.close();
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Logout failed", ex);
                showError("Logout failed: " + ex.getMessage());
            }
        });

        Button cancel = new Button("❌ CANCEL");
        cancel.getStyleClass().add("neo-button-outline");
        cancel.setOnAction(e -> confirmDialog.close());

        HBox actions = new HBox(10, confirm, cancel);
        actions.setAlignment(Pos.CENTER_RIGHT);
        content.getChildren().addAll(title, message, actions);

        Scene scene = new Scene(content, 520, 260);
        applyAppStylesheet(scene);
        confirmDialog.setScene(scene);
        confirmDialog.setMinWidth(500);
        confirmDialog.setMinHeight(240);
        confirmDialog.showAndWait();
    }

    private void showPurchaseDialog(TrackCardController cardController) {
        Track track = cardController.getTrack();
        if (track == null || !track.isActive() || track.getStockQty() <= 0) {
            showError("This track is currently unavailable.");
            return;
        }

        VBox content = new VBox(14);
        content.getStyleClass().add("dialog-surface");
        content.setMaxWidth(520);
        content.setPadding(new Insets(20));

        Label title = new Label("Add to Cart / Purchase");
        title.getStyleClass().add("display-sub");

        Label songLabel = new Label(track.getTitle());
        songLabel.getStyleClass().add("card-title");
        songLabel.setWrapText(true);
        songLabel.setMaxWidth(460);

        Label albumLabel = new Label(track.getAlbum() == null ? "" : track.getAlbum());
        albumLabel.getStyleClass().add("caption");
        albumLabel.setWrapText(true);
        albumLabel.setMaxWidth(460);

        Label qtyLabel = new Label("Quantity: " + cardController.getQuantity());
        qtyLabel.getStyleClass().add("body-semibold");

        Label amountLabel = new Label(String.format("Estimated total: RM %.2f",
                track.getPrice().multiply(BigDecimal.valueOf(cardController.getQuantity()))));
        amountLabel.getStyleClass().add("caption");

        Button addToCart = new Button("Add to Cart");
        addToCart.getStyleClass().add("wise-pill-primary");
        addToCart.setOnAction(e -> {
            addTrackToCart(track, cardController.getQuantity());
            overlayDialog.close();
        });

        Button buyNow = new Button("Buy Now");
        buyNow.getStyleClass().add("wise-pill-secondary");
        buyNow.setOnAction(e -> {
            addTrackToCart(track, cardController.getQuantity());
            overlayDialog.close();
            handleViewCart();
        });

        Button cancel = new Button("Cancel");
        cancel.getStyleClass().add("wise-pill-secondary");
        cancel.setOnAction(e -> overlayDialog.close());

        HBox actions = new HBox(10, addToCart, buyNow, cancel);
        actions.setAlignment(Pos.CENTER_RIGHT);
        content.getChildren().addAll(title, songLabel, albumLabel, qtyLabel, amountLabel, actions);
        showOverlayDialog(content, 540, 300);
    }

    private void addTrackToCart(Track sel, int qty) {
        if (sel == null) {
            return;
        }
        if (sel.getStockQty() <= 0) {
            showError("Track is out of stock.");
            return;
        }
        int safeQty = Math.max(1, Math.min(qty, sel.getStockQty()));

        for (OrderItem item : cartItems) {
            if (item.getTrackId() == sel.getId()) {
                int newQty = item.getQuantity() + safeQty;
                if (newQty > sel.getStockQty()) {
                    showError("Total quantity exceeds stock.");
                    return;
                }
                item.setQuantity(newQty);
                item.setLineTotal(sel.getPrice().multiply(BigDecimal.valueOf(newQty)));
                showInfo("Added to cart. Total: " + newQty);
                return;
            }
        }

        OrderItem newItem = new OrderItem();
        newItem.setTrackId(sel.getId());
        newItem.setQuantity(safeQty);
        newItem.setUnitPrice(sel.getPrice());
        newItem.setLineTotal(sel.getPrice().multiply(BigDecimal.valueOf(safeQty)));
        cartItems.add(newItem);
        showInfo("Added " + safeQty + " of '" + sel.getTitle() + "' to cart.");
    }

    private void showOverlayDialog(VBox content, double width, double height) {
        if (overlayDialog != null && overlayDialog.isShowing()) {
            overlayDialog.close();
        }

        StackPane overlay = new StackPane(content);
        overlay.setAlignment(Pos.CENTER);
        overlay.setStyle("-fx-background-color: rgba(14,15,12,0.45);");

        Stage dialog = new Stage(StageStyle.TRANSPARENT);
        overlayDialog = dialog;
        dialog.initOwner(trackGrid.getScene().getWindow());
        dialog.initModality(Modality.WINDOW_MODAL);

        double safeWidth = Math.max(width, 480);
        double safeHeight = Math.max(height, 280);
        Scene scene = new Scene(overlay, safeWidth, safeHeight);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        applyAppStylesheet(scene);
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    private void applyAppStylesheet(Scene scene) {
        java.net.URL css = getClass().getResource("/css/app.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
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
        if (SessionManager.getCurrentUser() == null) {
            throw new IllegalArgumentException("No logged-in user.");
        }
        String uname = SessionManager.getCurrentUser().getUsername();
        for (Customer c : customerDao.findAll()) {
            if (uname.equalsIgnoreCase(c.getName())) {
                return c.getId();
            }
        }
        Customer nc = new Customer();
        nc.setName(uname);
        int id = customerDao.create(nc);
        if (id <= 0) {
            throw new SQLException("Failed to create customer.");
        }
        return id;
    }

    private void showError(String msg) {
        Alert alert = createStyledAlert(Alert.AlertType.ERROR, "ERROR", msg, "error-alert");
        alert.showAndWait();
    }

    private void showInfo(String msg) {
        Alert alert = createStyledAlert(Alert.AlertType.INFORMATION, "INFO", msg, "info-alert");
        alert.showAndWait();
    }

    private Alert createStyledAlert(Alert.AlertType type, String header, String message, String styleClass) {
        Alert alert = new Alert(type);
        alert.setTitle(header);
        alert.setHeaderText(header);
        alert.setContentText(message);

        DialogPane pane = alert.getDialogPane();
        pane.getStyleClass().add(styleClass);
        pane.setMinWidth(420);
        pane.setPrefWidth(500);
        pane.setMinHeight(220);

        java.net.URL css = getClass().getResource("/css/app.css");
        if (css != null) {
            pane.getStylesheets().add(css.toExternalForm());
        }

        return alert;
    }
}
