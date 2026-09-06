package com.example.musiclibrary.controller;

import com.example.musiclibrary.dao.OrderDao;
import com.example.musiclibrary.dao.OrderItemDao;
import com.example.musiclibrary.dao.TrackDao;
import com.example.musiclibrary.model.Order;
import com.example.musiclibrary.model.OrderItem;
import com.example.musiclibrary.model.Track;
import com.example.musiclibrary.service.OrderService;
import com.example.musiclibrary.session.SessionManager;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Map;

public class OrderManagementController {

    private static final Logger LOGGER = Logger.getLogger(OrderManagementController.class.getName());

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
    @FXML private TableColumn<OrderItem, Void> colItemActions;
    @FXML private TextField shippingCityField;

    private final ObservableList<Order> orders = FXCollections.observableArrayList();
    private final ObservableList<OrderItem> orderItems = FXCollections.observableArrayList();
    private final OrderDao orderDao = new OrderDao();
    private final OrderItemDao orderItemDao = new OrderItemDao();
    private final TrackDao trackDao = new TrackDao();
    private final OrderService orderService = new OrderService();
    private final Map<Integer, String> trackLabelCache = new HashMap<>();

    @FXML
    private void initialize() {
        colOrderId.setCellValueFactory(p -> new SimpleIntegerProperty(p.getValue().getId()));
        colOrderDate.setCellValueFactory(p -> {
            if (p.getValue().getOrderDate() == null){
                return new SimpleStringProperty(""); // handle null order date gracefully
            }
            return new SimpleStringProperty(
                    p.getValue().getOrderDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))); // format the date for good readability
        });
        colStatus.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().getStatus()));
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("status-badge", "status-pending", "status-confirmed", "status-paid", "status-cancelled");
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    getStyleClass().addAll("status-badge", "status-" + item.toLowerCase());
                }
            }
        });
        colTotal.setCellValueFactory(p -> new SimpleObjectProperty<>(p.getValue().getTotalAmount()));
        colTotal.setStyle("-fx-alignment: CENTER-RIGHT;");

        colTrack.setCellValueFactory(p -> new SimpleStringProperty(getTrackLabel(p.getValue().getTrackId())));
        colQuantity.setCellValueFactory(p -> new SimpleIntegerProperty(p.getValue().getQuantity()));
        colQuantity.setStyle("-fx-alignment: CENTER;");
        colUnitPrice.setCellValueFactory(p -> new SimpleObjectProperty<>(p.getValue().getUnitPrice()));
        colUnitPrice.setStyle("-fx-alignment: CENTER-RIGHT;");
        colLineTotal.setCellValueFactory(p -> new SimpleObjectProperty<>(p.getValue().getLineTotal()));
        colLineTotal.setStyle("-fx-alignment: CENTER-RIGHT;");
        setupItemActionColumn();

        orderTable.setItems(orders);
        orderItemTable.setItems(orderItems);

        orderTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadOrderItems(newVal.getId());
                shippingCityField.setText(newVal.getShippingCity() != null ? newVal.getShippingCity() : ""); // handle null shipping city gracefully
            } else {
                orderItems.clear();
                shippingCityField.clear();
            }
            orderItemTable.refresh(); // re-render action buttons so only PENDING orders expose them
        });

        loadOrders(); // load orders when nothing wrong happened with the best cases (>_<)
    }

    private void loadOrders() {
        if (!SessionManager.isLoggedIn()) {
            showError("You must be logged in to view orders."); // tbh this should never happen because the app should redirect to login if not authenticated, but just in case
            return;
        }
        try {
            List<Order> list = orderDao.findByUser(SessionManager.getCurrentUser().getId()); // load orders for the current logged in user only, not all orders in the system
            orders.setAll(list);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to load orders", e); // log the full stack trace  if something wrong
            showError("Failed to load orders: " + e.getMessage());
        }
    }

    private void loadOrderItems(int orderId) {
        try {
            orderItems.setAll(orderItemDao.findByOrder(orderId)); // load order items for the selected order only, not all order items in the system, same with the previous one
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to load order items", e);
            showError("Failed to load order items: " + e.getMessage());
        }
    }

    // inline +/-/remove buttons per item row; only rendered for PENDING orders so paid/cancelled history stays read-only
    private void setupItemActionColumn() {
        colItemActions.setCellValueFactory(p -> new SimpleObjectProperty<>(null));
        colItemActions.setCellFactory(col -> new TableCell<>() {
            private final Button incBtn = new Button("＋");
            private final Button decBtn = new Button("－");
            private final Button removeBtn = new Button("🗑");
            private final HBox actionBox = new HBox(4, decBtn, incBtn, removeBtn);

            {
                actionBox.setAlignment(Pos.CENTER);
                incBtn.getStyleClass().add("qty-btn");
                decBtn.getStyleClass().add("qty-btn");
                removeBtn.getStyleClass().addAll("qty-btn", "qty-btn-danger");
                incBtn.setTooltip(new Tooltip("Increase quantity by 1"));
                decBtn.setTooltip(new Tooltip("Decrease quantity by 1"));
                removeBtn.setTooltip(new Tooltip("Remove this item and return stock"));

                incBtn.setOnAction(e -> changeQuantity(+1));
                decBtn.setOnAction(e -> changeQuantity(-1));
                removeBtn.setOnAction(e -> removeItem());
            }

            private void changeQuantity(int delta) {
                OrderItem item = currentOrderItem();
                Order order = currentOrder();
                if (item == null || order == null) {
                    return;
                }
                int newQty = item.getQuantity() + delta;
                if (newQty < 1) {
                    showError("Quantity cannot go below 1. Use the remove button to delete this item.");
                    return;
                }
                try {
                    orderService.adjustOrderItemQuantity(order.getId(), item.getId(), newQty);
                    reloadAfterItemChange(order);
                } catch (IllegalArgumentException | IllegalStateException ex) {
                    showError(ex.getMessage());
                } catch (SQLException ex) {
                    LOGGER.log(Level.SEVERE, "Failed to adjust item quantity", ex);
                    showError("Failed to adjust quantity: " + ex.getMessage());
                }
            }

            private void removeItem() {
                OrderItem item = currentOrderItem();
                Order order = currentOrder();
                if (item == null || order == null) {
                    return;
                }
                try {
                    orderService.removeOrderItem(order.getId(), item.getId());
                    reloadAfterItemChange(order);
                } catch (IllegalArgumentException | IllegalStateException ex) {
                    showError(ex.getMessage());
                } catch (SQLException ex) {
                    LOGGER.log(Level.SEVERE, "Failed to remove order item", ex);
                    showError("Failed to remove item: " + ex.getMessage());
                }
            }

            private OrderItem currentOrderItem() {
                return getTableRow() == null ? null : getTableRow().getItem();
            }

            private Order currentOrder() {
                return orderTable.getSelectionModel().getSelectedItem();
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                Order order = currentOrder();
                boolean editable = !empty && currentOrderItem() != null
                        && order != null && "PENDING".equals(order.getStatus());
                setGraphic(editable ? actionBox : null);
            }
        });
    }

    // refresh both tables after a successful item edit; keep the same order selected so the user stays in context
    private void reloadAfterItemChange(Order order) {
        loadOrders();
        for (Order o : orders) {
            if (o.getId() == order.getId()) {
                orderTable.getSelectionModel().select(o);
                break;
            }
        }
    }

    @FXML
    private void handleAddOrderItem() {
        Order sel = orderTable.getSelectionModel().getSelectedItem();
        if (sel == null) {
            showError("Please select an order first.");
            return;
        }
        if (!"PENDING".equals(sel.getStatus())) {
            showError("Only PENDING orders can be modified. Current status: " + sel.getStatus());
            return;
        }

        List<Track> availableTracks;
        try {
            availableTracks = trackDao.findAllActive();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to load tracks", e);
            showError("Failed to load tracks: " + e.getMessage());
            return;
        }
        if (availableTracks.isEmpty()) {
            showError("No tracks are available to add.");
            return;
        }

        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(orderTable.getScene().getWindow());
        dialog.setTitle("Add Item to Order #" + sel.getId());

        ComboBox<Track> trackBox = new ComboBox<>(FXCollections.observableArrayList(availableTracks));
        trackBox.setPrefWidth(320);
        trackBox.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Track item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null
                        : String.format("%s - %s (RM %s, stock %d)", item.getTitle(), item.getArtist(), item.getPrice(), item.getStockQty()));
            }
        });
        trackBox.setButtonCell(trackBox.getCellFactory().call(null));
        trackBox.getSelectionModel().selectFirst();

        Spinner<Integer> qtySpinner = new Spinner<>(1, Integer.MAX_VALUE, 1);
        qtySpinner.setPrefWidth(90);
        qtySpinner.setEditable(true);

        Label hintLabel = new Label();
        hintLabel.setStyle("-fx-text-fill: #8A8A8A; -fx-font-size: 12px;");
        Runnable updateHint = () -> {
            Track t = trackBox.getValue();
            if (t != null) {
                hintLabel.setText(String.format("Unit price: RM %s · Available stock: %d", t.getPrice(), t.getStockQty()));
                qtySpinner.getValueFactory().setValue(1);
                // cap the spinner at the current stock so invalid quantities never reach the database
                SpinnerValueFactory.IntegerSpinnerValueFactory factory =
                        (SpinnerValueFactory.IntegerSpinnerValueFactory) qtySpinner.getValueFactory();
                factory.setMax(Math.max(1, t.getStockQty()));
            }
        };
        trackBox.valueProperty().addListener((obs, oldV, newV) -> updateHint.run());
        updateHint.run();

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(20));
        grid.add(new Label("Track:"), 0, 0);
        grid.add(trackBox, 1, 0);
        grid.add(new Label("Quantity:"), 0, 1);
        grid.add(qtySpinner, 1, 1);
        grid.add(hintLabel, 1, 2);

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #FF6B6B; -fx-font-weight: 600;");

        Button addBtn = new Button("➕ Add to Order");
        addBtn.getStyleClass().add("neo-button-primary");
        addBtn.setOnAction(e -> {
            Track track = trackBox.getValue();
            if (track == null) {
                errorLabel.setText("Please select a track.");
                return;
            }
            try {
                orderService.addOrderItem(sel.getId(), track.getId(), qtySpinner.getValue());
                dialog.close();
                reloadAfterItemChange(sel);
            } catch (IllegalArgumentException | IllegalStateException ex) {
                errorLabel.setText(ex.getMessage());
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, "Failed to add item to order", ex);
                errorLabel.setText("Failed to add item: " + ex.getMessage());
            }
        });

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("neo-button-ghost");
        cancelBtn.setOnAction(e -> dialog.close());

        HBox buttons = new HBox(10, addBtn, cancelBtn);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.setPadding(new Insets(0, 20, 16, 20));

        VBox root = new VBox(10, grid, errorLabel, buttons);
        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
        dialog.setScene(scene);
        dialog.setResizable(false);
        dialog.showAndWait();
    }


    @FXML
    private void handleCancelOrder() {
        Order sel = orderTable.getSelectionModel().getSelectedItem();
        if (sel == null) {
            showError("Please select an order first.");
            return;
        }

        if ("PAID".equals(sel.getStatus())) {
            showError("Cannot cancel a PAID order. Please contact support.");
            return;
        }

        try {
            orderService.cancelOrder(sel.getId()); // transactional cancel that also returns reserved stock
            loadOrders();
        } catch (IllegalArgumentException | IllegalStateException e) {
            showError(e.getMessage());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to cancel order", e);
            showError("Failed to cancel order: " + e.getMessage());
        }
    }

    @FXML
    private void handleDeleteOrder() {
        Order sel = orderTable.getSelectionModel().getSelectedItem();
        if (sel == null) { showError("Please select an order first."); return; }
        if ("PAID".equals(sel.getStatus())) {
            showError("PAID orders are kept as financial records and cannot be deleted.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete order #" + sel.getId() + "? Its items will be removed and stock returned.", ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText("Delete Order");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        try {
            orderService.deleteOrder(sel.getId()); // returns stock before deleting
            orderItems.clear();
            shippingCityField.clear();
            loadOrders();
        } catch (IllegalArgumentException | IllegalStateException e) {
            showError(e.getMessage());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to delete order", e);
            showError("Failed to delete order: " + e.getMessage());
        }
    }

    @FXML
    private void handlePayOrder() {
        Order sel = orderTable.getSelectionModel().getSelectedItem();
        if (sel == null) {
            showError("Please select an order to pay.");
            return;
        }

        // check if shipping city is empty
        String city = shippingCityField.getText().trim();
        if (city.isEmpty()) {
            showError("Please enter a Shipping City before proceeding to payment.");
            return;
        }

        // verify the status of the order
        if ("PAID".equals(sel.getStatus())) {
            showError("This order has already been paid.");
            return;
        }
        if ("CANCELLED".equals(sel.getStatus())) {
            showError("Cannot pay a cancelled order.");
            return;
        }

        // save city information to database(make sure info is synchronized before paying)
        try {
            sel.setShippingCity(city);
            orderDao.update(sel);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to save shipping city", e);
            showError("Database error: Could not save shipping city.");
            return;
        }

        // if and only if after the check is passed and saved successfully payment window will pop up
        javafx.application.Platform.runLater(() ->
                showPaymentDialog(sel, orderTable.getScene().getWindow())
        );
    }

    private void showPaymentDialog(Order order, javafx.stage.Window owner) {
        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL); // make the payment dialog modal to the main window, so users can't interact with the main window while the payment dialog is open
        dialog.setTitle("Pay Order #" + order.getId());

        Label titleLabel = new Label("💳  Payment"); // lazy to use images to show, just use emoji for now, at least it looks better than nothing
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        Label amountLabel = new Label(String.format("Amount to Pay: RM %s", order.getTotalAmount()));
        amountLabel.setStyle("-fx-font-size: 14px;");

        GridPane grid = new GridPane();
        grid.setHgap(12); grid.setVgap(12); grid.setPadding(new Insets(20));

        TextField cardNumberField = new TextField();
        cardNumberField.setPromptText("1234 5678 9012 3456"); // prompt text to let user know what kind of format they are going to follow
        cardNumberField.setPrefWidth(260);
        cardNumberField.setTextFormatter(new TextFormatter<>(change -> change.getControlNewText().matches("[0-9 ]{0,19}") ? change : null)); // only allow digits and spaces, and limit the length to 19 (16 digits + 3 spaces)

        cardNumberField.textProperty().addListener((obs, oldText, newText) -> {
            if (oldText != null && newText.length() < oldText.length()) return;
            String digits = newText.replace(" ", "");
            if (!digits.isEmpty() && digits.length() % 4 == 0 && digits.length() < 16) { // add a space after every 4 digits, but not if the user is deleting or if the card number is already complete
                if (!newText.endsWith(" ")) {
                    javafx.application.Platform.runLater(() -> {
                        cardNumberField.setText(newText + " ");
                        cardNumberField.positionCaret(cardNumberField.getText().length()); // move the cursor to the end after adding a space so user can keep typing without interruption
                    });
                }
            }
        });

        TextField cardHolderField = new TextField();
        cardHolderField.setPromptText("Full Name on Card");
        cardHolderField.setPrefWidth(260);

        TextField expiryField = new TextField();
        expiryField.setPromptText("MM/YY");
        expiryField.setPrefWidth(100);
        expiryField.setTextFormatter(new TextFormatter<>(change -> change.getControlNewText().matches("[0-9/]{0,5}") ? change : null)); // only allow digits and slash, and limit the length to 5 (4 digits + 1 slash)

        expiryField.textProperty().addListener((obs, oldText, newText) -> {
            if (oldText != null && newText.length() < oldText.length()) return;
            if (newText.length() == 2 && !newText.contains("/")) { // add slash after the user types 2 digits for the month, but not if the user is deleting or if the slash is already there, same with card number
                javafx.application.Platform.runLater(() -> {
                    expiryField.setText(newText + "/");
                    expiryField.positionCaret(expiryField.getText().length());
                });
            }
        });

        PasswordField cvvField = new PasswordField();
        cvvField.setPromptText("CVV");
        cvvField.setPrefWidth(80);
        cvvField.setTextFormatter(new TextFormatter<>(change -> change.getControlNewText().matches("[0-9]{0,3}") ? change : null)); // only allow digits and limit the length to 3 for CVV

        Label errorLabel = new Label("");
        errorLabel.setStyle("-fx-text-fill: red;");

        grid.add(new Label("Card Number:"), 0, 0); grid.add(cardNumberField, 1, 0);
        grid.add(new Label("Card Holder:"), 0, 1); grid.add(cardHolderField, 1, 1);
        grid.add(new Label("Expiry (MM/YY):"), 0, 2); grid.add(expiryField, 1, 2);
        grid.add(new Label("CVV:"), 0, 3); grid.add(cvvField, 1, 3);
        grid.add(errorLabel, 0, 4, 2, 1);

        Button confirmPayBtn = new Button("Confirm Payment");
        confirmPayBtn.setStyle("-fx-background-color: #FFFFFF; -fx-text-fill: #000000; -fx-font-weight: bold;");

        Button cancelPayBtn = new Button("Cancel");

        Label statusLabel = new Label("");
        statusLabel.setStyle("-fx-font-style: italic;");

        confirmPayBtn.setOnAction(e -> {
            String cardNumber = cardNumberField.getText().replace(" ", "");
            String cardHolder = cardHolderField.getText().trim();
            String expiry = expiryField.getText().trim();
            String cvv = cvvField.getText().trim();

            if (cardNumber.length() != 16 || !cardNumber.matches("\\d{16}")) {
                errorLabel.setText("Please enter a valid 16-digit card number.");
                return;
            }
            if (cardHolder.isEmpty()) {
                errorLabel.setText("Please enter the card holder name.");
                return;
            }

            if (!expiry.matches("^(0[1-9]|1[0-2])/\\d{2}$")) { // format check for MM/YY
                errorLabel.setText("Please enter a valid expiry date (MM/YY).");
                return;
            } else {
                try {
                    String[] parts = expiry.split("/");
                    int expMonth = Integer.parseInt(parts[0]);
                    int expYear = Integer.parseInt(parts[1]) + 2000;

                    java.time.YearMonth currentYearMonth = java.time.YearMonth.now(); // get the current year and month to compare with the expiry date
                    java.time.YearMonth inputYearMonth = java.time.YearMonth.of(expYear, expMonth); // current month

                    if (inputYearMonth.isBefore(currentYearMonth)) {
                        errorLabel.setText("Your card has expired. Please use a valid card.");
                        return;
                    }
                } catch (Exception ex) {
                    errorLabel.setText("Invalid expiry date.");
                    return;
                }
            }

            if (cvv.length() != 3) {
                errorLabel.setText("Please enter a valid 3-digit CVV.");
                return;
            }

            confirmPayBtn.setDisable(true);
            cancelPayBtn.setDisable(true);
            errorLabel.setText("");
            statusLabel.setText("Processing payment... Please wait.");

            javafx.concurrent.Task<Boolean> paymentTask = new javafx.concurrent.Task<Boolean>() {
                @Override
                protected Boolean call() throws Exception {
                    Thread.sleep(2000);
                    return new Random().nextInt(100) < 85;
                }
            };

            paymentTask.setOnSucceeded(event -> {
                boolean success = paymentTask.getValue();
                statusLabel.setText("");

                if (success) {
                    try {
                        order.setStatus("PAID");
                        orderDao.update(order);
                        loadOrders();

                        dialog.close(); // close the payment dialog

                        // use runLater so Mac doesn't render a blank window
                        javafx.application.Platform.runLater(() -> {
                            showPaymentSuccess(order.getId(), order.getTotalAmount());
                        });

                    } catch (SQLException ex) {
                        LOGGER.log(Level.SEVERE, "Failed to mark order as PAID", ex);
                        confirmPayBtn.setDisable(false);
                        cancelPayBtn.setDisable(false);
                        errorLabel.setText("Payment succeeded but status update failed (Check DB limits): " + ex.getMessage());
                    }
                } else {
                    LOGGER.warning("Simulated payment declined for card ending in " + cardNumber.substring(12));
                    confirmPayBtn.setDisable(false);
                    cancelPayBtn.setDisable(false);
                    errorLabel.setText("Payment declined. Please check your card details and try again.");
                }
            });

            paymentTask.setOnFailed(event -> {
                Throwable ex = paymentTask.getException();
                LOGGER.log(Level.SEVERE, "Payment processing error", ex);
                confirmPayBtn.setDisable(false);
                cancelPayBtn.setDisable(false);
                statusLabel.setText("");
                errorLabel.setText("Network error, please try again later.");
            });

            Thread paymentThread = new Thread(paymentTask);
            paymentThread.setDaemon(true);
            paymentThread.start();
        });

        cancelPayBtn.setOnAction(e -> dialog.close());

        HBox buttons = new HBox(12, confirmPayBtn, cancelPayBtn);
        buttons.setAlignment(Pos.CENTER);

        VBox root = new VBox(14, titleLabel, amountLabel, grid, buttons, statusLabel);
        root.setPadding(new Insets(24));
        root.setAlignment(Pos.TOP_CENTER);

        Scene payScene = new Scene(root, 480, 400);
        payScene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
        dialog.setScene(payScene);
        dialog.showAndWait();
    }

    private void showPaymentSuccess(int orderId, BigDecimal amount) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Order Confirmation");

        // icon and main messages
        Label icon = new Label("🎉");
        icon.setStyle("-fx-font-size: 50px;");

        Label msg = new Label("Thank You!");
        msg.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #7BE0AD;");

        Label subMsg = new Label("Your payment was successfully processed.");
        subMsg.setStyle("-fx-font-size: 13px; -fx-text-fill: #8A8A8A;");

        VBox headerBox = new VBox(5, icon, msg, subMsg);
        headerBox.setAlignment(Pos.CENTER);

        javafx.scene.control.Separator separator = new javafx.scene.control.Separator(); // divider Line
        separator.setPadding(new Insets(10, 0, 10, 0));

        javafx.scene.layout.GridPane detailsGrid = new javafx.scene.layout.GridPane(); // receipt details
        detailsGrid.setVgap(12);
        detailsGrid.setHgap(30);
        detailsGrid.setAlignment(Pos.CENTER);

        Label lblOrder = new Label("Order Number:");
        lblOrder.setStyle("-fx-text-fill: #8A8A8A; -fx-font-weight: bold;");
        Label valOrder = new Label("#" + orderId);
        valOrder.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        Label lblDate = new Label("Date:");
        lblDate.setStyle("-fx-text-fill: #8A8A8A; -fx-font-weight: bold;");

        String currentDate = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")); // grab the exact current time for the receipt
        Label valDate = new Label(currentDate);
        valDate.setStyle("-fx-font-size: 13px;");

        Label lblAmount = new Label("Amount Paid:");
        lblAmount.setStyle("-fx-text-fill: #8A8A8A; -fx-font-weight: bold;");
        Label valAmount = new Label(String.format("RM %.2f", amount));
        valAmount.setStyle("-fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: #FFFFFF;");  // highlight final amount

        detailsGrid.add(lblOrder, 0, 0);  detailsGrid.add(valOrder, 1, 0);
        detailsGrid.add(lblDate, 0, 1);   detailsGrid.add(valDate, 1, 1);
        detailsGrid.add(lblAmount, 0, 2); detailsGrid.add(valAmount, 1, 2);

        Button okBtn = new Button("Done"); // bottom Button
        okBtn.setStyle("-fx-background-color: #FFFFFF; -fx-text-fill: #000000; -fx-font-weight: bold; -fx-pref-width: 120px; -fx-padding: 8px; -fx-cursor: hand;");
        okBtn.setOnAction(e -> stage.close());

        VBox box = new VBox(15, headerBox, separator, detailsGrid, new Label(""), okBtn); // assemble everything with a slight off-white/gray background to mimic paper
        box.setAlignment(Pos.TOP_CENTER);
        box.setPadding(new Insets(25, 30, 25, 30));
        box.setStyle("-fx-background-color: #0A0A0A;");

        Scene receiptScene = new Scene(box, 380, 390);
        receiptScene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
        stage.setScene(receiptScene);
        stage.setResizable(false); // prevent the user from resizing the window so receipt layout doesn't break
        stage.showAndWait();
    }

    @FXML
    private void handleSaveShippingCity() {
        Order sel = orderTable.getSelectionModel().getSelectedItem();
        if (sel == null) {
            showError("Please select an order first.");
            return;
        }
        if (!"PENDING".equals(sel.getStatus())) {
            showError("Shipping city can only be changed for PENDING orders.");
            return;
        }
        String city = shippingCityField.getText().trim();
        if (city.isEmpty()) {
            showError("Shipping city cannot be empty.");
            return;
        }
        try {
            sel.setShippingCity(city);
            orderDao.update(sel);
            loadOrders();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to save shipping city", e);
            showError("Failed to save shipping city: " + e.getMessage());
        }
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

        String dateString = sel.getOrderDate() != null ? sel.getOrderDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : "N/A"; // handle null order date

        Label dateLabel   = new Label("Date: " + dateString);
        Label statusLabel = new Label("Status: " + sel.getStatus());
        Label totalLabel  = new Label("Total: " + sel.getTotalAmount());
        totalLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        TableView<OrderItem> itemTable = new TableView<>(orderItems);
        itemTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<OrderItem, String> trackCol = new TableColumn<>("Track");
        trackCol.setCellValueFactory(p -> new SimpleStringProperty(getTrackLabel(p.getValue().getTrackId())));
        TableColumn<OrderItem, Number> qtyCol = new TableColumn<>("Qty");
        qtyCol.setCellValueFactory(p -> new SimpleIntegerProperty(p.getValue().getQuantity()));
        TableColumn<OrderItem, BigDecimal> priceCol = new TableColumn<>("Price");
        priceCol.setCellValueFactory(p -> new SimpleObjectProperty<>(p.getValue().getUnitPrice()));
        TableColumn<OrderItem, BigDecimal> totCol = new TableColumn<>("Total");
        totCol.setCellValueFactory(p -> new SimpleObjectProperty<>(p.getValue().getLineTotal()));

        itemTable.getColumns().add(trackCol);
        itemTable.getColumns().add(qtyCol);
        itemTable.getColumns().add(priceCol);
        itemTable.getColumns().add(totCol);

        Button closeBtn = new Button("Close");
        closeBtn.setOnAction(e -> dialog.close());

        VBox vbox = new VBox(10, orderLabel, dateLabel, statusLabel, totalLabel,
                new Label("Items:"), itemTable, closeBtn);
        vbox.setPadding(new Insets(20));

        Scene invoiceScene = new Scene(vbox, 600, 500);
        invoiceScene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
        dialog.setScene(invoiceScene);
        dialog.showAndWait();
    }

    private String getTrackLabel(int trackId) {
        if (trackLabelCache.containsKey(trackId)) return trackLabelCache.get(trackId);

        String trackName;
        try {
            Track track = trackDao.findById(trackId);
            trackName = track == null ? "Track #" + trackId : track.getTitle() + " - " + track.getArtist(); // if track is not found, show the id instead to avoid showing blank
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