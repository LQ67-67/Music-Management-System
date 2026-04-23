package com.example.musiclibrary.controller;

import com.example.musiclibrary.MusicLibraryApp;
import com.example.musiclibrary.dao.CustomerDao;
import com.example.musiclibrary.dao.TrackDao;
import com.example.musiclibrary.model.Customer;
import com.example.musiclibrary.model.OrderItem;
import com.example.musiclibrary.model.Track;
import com.example.musiclibrary.model.User;
import com.example.musiclibrary.service.OrderService;
import com.example.musiclibrary.session.SessionManager;
import com.example.musiclibrary.util.TrackMediaResolver;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
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
import javafx.stage.WindowEvent;
import javafx.util.Callback;

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

        // cap spinner max to the selected track's stock quantity using a clear ChangeListener
        trackTable.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<Track>() {
            @Override
            public void changed(ObservableValue<? extends Track> observable, Track oldSelection, Track newSelection) {
                if (newSelection != null) {
                    int maxStock = Math.max(1, newSelection.getStockQty());
                    quantitySpinnerFactory.setMax(maxStock);

                    if (quantitySpinner.getValue() > maxStock) {
                        quantitySpinnerFactory.setValue(maxStock);
                    }
                }
            }
        });

        setupTrackTableColumns();
        trackTable.setItems(trackData);
        loadAllTracks();
    }

    private void setupTrackTableColumns() {
        // cover image column
        colCover.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<Track, Track>, ObservableValue<Track>>() {
            @Override
            public ObservableValue<Track> call(TableColumn.CellDataFeatures<Track, Track> param) {
                return new SimpleObjectProperty<>(param.getValue());
            }
        });

        colCover.setCellFactory(new Callback<TableColumn<Track, Track>, TableCell<Track, Track>>() {
            @Override
            public TableCell<Track, Track> call(TableColumn<Track, Track> param) {
                return new TableCell<Track, Track>() {
                    private final ImageView imageView = new ImageView();

                    @Override
                    protected void updateItem(Track item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty || item == null) {
                            setGraphic(null);
                        } else {
                            imageView.setFitWidth(80);
                            imageView.setFitHeight(80);
                            imageView.setPreserveRatio(true);
                            imageView.setImage(TrackMediaResolver.loadTrackImage(item, item.getId() + ".mp3"));
                            setGraphic(imageView);
                        }
                    }
                };
            }
        });

        // text columns
        colTitle.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<Track, String>, ObservableValue<String>>() {
            @Override
            public ObservableValue<String> call(TableColumn.CellDataFeatures<Track, String> param) {
                return new SimpleStringProperty(param.getValue().getTitle());
            }
        });

        colArtist.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<Track, String>, ObservableValue<String>>() {
            @Override
            public ObservableValue<String> call(TableColumn.CellDataFeatures<Track, String> param) {
                return new SimpleStringProperty(param.getValue().getArtist());
            }
        });

        colGenre.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<Track, String>, ObservableValue<String>>() {
            @Override
            public ObservableValue<String> call(TableColumn.CellDataFeatures<Track, String> param) {
                return new SimpleStringProperty(param.getValue().getGenre());
            }
        });

        colPrice.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<Track, String>, ObservableValue<String>>() {
            @Override
            public ObservableValue<String> call(TableColumn.CellDataFeatures<Track, String> param) {
                BigDecimal price = param.getValue().getPrice();
                if (price == null) {
                    return new SimpleStringProperty("");
                } else {
                    return new SimpleStringProperty(price.toPlainString());
                }
            }
        });

        colStock.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<Track, String>, ObservableValue<String>>() {
            @Override
            public ObservableValue<String> call(TableColumn.CellDataFeatures<Track, String> param) {
                return new SimpleStringProperty(String.valueOf(param.getValue().getStockQty()));
            }
        });
    }

    // edit profile
    @FXML
    public void handleEditProfile() {
        try {
            User currentUser = SessionManager.getCurrentUser();
            if (currentUser == null) {
                showError("Please log in to edit your profile.");
                return;
            }

            // looking for existing customer details in the database
            Customer existingCustomer = null;
            for (Customer customerItem : customerDao.findAll()) {
                if (currentUser.getUsername().equalsIgnoreCase(customerItem.getName())) {
                    existingCustomer = customerItem;
                    break;
                }
            }

            // if this is their first time editing, create a new customer record for them
            if (existingCustomer == null) {
                existingCustomer = new Customer();
                existingCustomer.setName(currentUser.getUsername());
                existingCustomer.setEmail("");
                existingCustomer.setPhone("");
                existingCustomer.setCity("");

                int newId = customerDao.create(existingCustomer);
                existingCustomer.setId(newId);
            }

            // create the popup window (Stage)
            Stage dialog = new Stage();
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.setTitle("Edit My Profile");

            // build the layout explicitly
            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(15);
            grid.setPadding(new Insets(20));

            // setting up text fields with current info
            TextField nameField = new TextField(existingCustomer.getName());
            TextField emailField = new TextField(existingCustomer.getEmail());
            TextField phoneField = new TextField(existingCustomer.getPhone());
            TextField cityField = new TextField(existingCustomer.getCity());

            grid.add(new Label("Username:"),0,0);
            grid.add(nameField, 1, 0);
            grid.add(new Label("Email:"),0,1);
            grid.add(emailField,1, 1);
            grid.add(new Label("Phone:"),0,2);
            grid.add(phoneField,1,2);
            grid.add(new Label("City:"),0,3);
            grid.add(cityField,1,3);

            Button saveButton = new Button("Save Changes");
            Button cancelButton = new Button("Cancel");

            // need this 'final' reference so that we can use it inside our event handler below
            final Customer customerToUpdate = existingCustomer;

            saveButton.setOnAction(new EventHandler<ActionEvent>() {
                @Override
                public void handle(ActionEvent event) {
                    String inputEmail = emailField.getText();
                    String inputPhone = phoneField.getText();

                    String friendlyErrorMessage = "";

                    // email Validation
                    if (inputEmail != null && !inputEmail.trim().isEmpty()) {
                        if (!inputEmail.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
                            friendlyErrorMessage += "• Oops! Your email looks a bit off. Please use a format like user@example.com.\n";
                        }
                    }

                    // phone validation (Allow + - ( ) space numbers, at least 7 digits)
                    if (inputPhone != null && !inputPhone.trim().isEmpty()) {
                        if (!inputPhone.matches("^[0-9+\\-() ]+$")) {
                            friendlyErrorMessage += "• Phone numbers can only contain numbers, spaces, and the characters: + - ( ) \n";
                        } else {
                            // Count actual numbers to make sure there are at least 7
                            int digitCount = 0;
                            for (int i = 0; i < inputPhone.length(); i++) {
                                if (Character.isDigit(inputPhone.charAt(i))) {
                                    digitCount++;
                                }
                            }
                            if (digitCount < 7) {
                                friendlyErrorMessage += "• Your phone number seems too short. It needs at least 7 digits.\n";
                            }
                        }
                    }

                    // if there are errors, intercept and show them nicely!
                    if (!friendlyErrorMessage.isEmpty()) {
                        showError("Let's fix a few things before saving:\n\n" + friendlyErrorMessage);
                        return; // Stop the save process here
                    }

                    // validation passed and save to database
                    try {
                        customerToUpdate.setName(nameField.getText());
                        customerToUpdate.setEmail(inputEmail);
                        customerToUpdate.setPhone(inputPhone);
                        customerToUpdate.setCity(cityField.getText());

                        customerDao.update(customerToUpdate);

                        showInfo("Awesome! Your profile has been successfully updated.");
                        dialog.close();

                    } catch (SQLException ex) {
                        showError("Oh no, we couldn't save your profile to the database: " + ex.getMessage());
                    }
                }
            });

            cancelButton.setOnAction(new EventHandler<ActionEvent>() {
                @Override
                public void handle(ActionEvent event) {
                    dialog.close();
                }
            });

            HBox buttonBox = new HBox(15);
            buttonBox.getChildren().add(saveButton);
            buttonBox.getChildren().add(cancelButton);

            VBox mainLayout = new VBox(20);
            mainLayout.setPadding(new Insets(20));
            mainLayout.getChildren().add(grid);
            mainLayout.getChildren().add(buttonBox);

            Scene scene = new Scene(mainLayout, 400, 320);
            dialog.setScene(scene);
            dialog.showAndWait();

        } catch (SQLException e) {
            showError("We had a problem loading your profile details: " + e.getMessage());
        }
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
        Track selectedTrack = trackTable.getSelectionModel().getSelectedItem();

        if (selectedTrack == null) {
            showError("Please select a track from the table first.");
            return;
        }

        if (selectedTrack.getStockQty() <= 0) {
            showError("Sorry, the selected track is completely out of stock.");
            return;
        }

        int requestedQuantity = quantitySpinner.getValue();

        if (requestedQuantity > selectedTrack.getStockQty()) {
            showError("We don't have enough stock. Available: " + selectedTrack.getStockQty());
            return;
        }

        // check if track is already in the cart to merge quantities
        for (OrderItem existingItem : cartItems) {
            if (existingItem.getTrackId() == selectedTrack.getId()) {
                int newTotalQuantity = existingItem.getQuantity() + requestedQuantity;

                if (newTotalQuantity > selectedTrack.getStockQty()) {
                    showError("You cannot add more than the total stock. You already have " + existingItem.getQuantity() + " in your cart, and we only have " + selectedTrack.getStockQty() + " available.");
                    return;
                }

                existingItem.setQuantity(newTotalQuantity);

                BigDecimal newTotal = selectedTrack.getPrice().multiply(BigDecimal.valueOf(newTotalQuantity));
                existingItem.setLineTotal(newTotal);

                showInfo("Added " + requestedQuantity + " more to your cart. Total for this track is now: " + newTotalQuantity);
                return;
            }
        }

        // if it wasn't in the cart, create a new order item
        OrderItem newItem = new OrderItem();
        newItem.setTrackId(selectedTrack.getId());
        newItem.setQuantity(requestedQuantity);
        newItem.setUnitPrice(selectedTrack.getPrice());

        BigDecimal lineTotal = selectedTrack.getPrice().multiply(BigDecimal.valueOf(requestedQuantity));
        newItem.setLineTotal(lineTotal);

        cartItems.add(newItem);
        showInfo("Successfully added " + requestedQuantity + " of '" + selectedTrack.getTitle() + "' to your cart.");
    }

    @FXML
    private void handleViewCart() {
        if (cartItems.isEmpty()) {
            showInfo("Your cart is currently empty.");
            return;
        }

        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Your Shopping Cart");

        TableView<OrderItem> cartTable = new TableView<>(cartItems);
        cartTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<OrderItem, String> titleCol = new TableColumn<>("Track Title");
        titleCol.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<OrderItem, String>, ObservableValue<String>>() {
            @Override
            public ObservableValue<String> call(TableColumn.CellDataFeatures<OrderItem, String> param) {
                try {
                    Track t = trackDao.findById(param.getValue().getTrackId());
                    if (t != null) {
                        return new SimpleStringProperty(t.getTitle());
                    } else {
                        return new SimpleStringProperty("Unknown Track");
                    }
                } catch (SQLException e) {
                    return new SimpleStringProperty("Error Loading Name");
                }
            }
        });

        TableColumn<OrderItem, Number> qtyCol = new TableColumn<>("Quantity");
        qtyCol.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<OrderItem, Number>, ObservableValue<Number>>() {
            @Override
            public ObservableValue<Number> call(TableColumn.CellDataFeatures<OrderItem, Number> param) {
                return new SimpleIntegerProperty(param.getValue().getQuantity());
            }
        });

        TableColumn<OrderItem, BigDecimal> priceCol = new TableColumn<>("Price Per Unit");
        priceCol.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<OrderItem, BigDecimal>, ObservableValue<BigDecimal>>() {
            @Override
            public ObservableValue<BigDecimal> call(TableColumn.CellDataFeatures<OrderItem, BigDecimal> param) {
                return new SimpleObjectProperty<>(param.getValue().getUnitPrice());
            }
        });

        TableColumn<OrderItem, BigDecimal> totalCol = new TableColumn<>("Total Line Price");
        totalCol.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<OrderItem, BigDecimal>, ObservableValue<BigDecimal>>() {
            @Override
            public ObservableValue<BigDecimal> call(TableColumn.CellDataFeatures<OrderItem, BigDecimal> param) {
                return new SimpleObjectProperty<>(param.getValue().getLineTotal());
            }
        });

        cartTable.getColumns().add(titleCol);
        cartTable.getColumns().add(qtyCol);
        cartTable.getColumns().add(priceCol);
        cartTable.getColumns().add(totalCol);

        Button removeBtn = new Button("Remove Selected Item");
        removeBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                OrderItem selectedItem = cartTable.getSelectionModel().getSelectedItem();
                if (selectedItem != null) {
                    cartItems.remove(selectedItem);
                }
            }
        });

        Button clearBtn = new Button("Clear Entire Cart");
        clearBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                cartItems.clear();
                dialog.close();
            }
        });

        Button checkoutBtn = new Button("Checkout");
        checkoutBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                try {
                    if (!SessionManager.isLoggedIn()) {
                        showError("You must be logged in to checkout.");
                        return;
                    }

                    int userId = SessionManager.getCurrentUser().getId();
                    int customerId = resolveCustomerIdForCurrentUser();

                    orderService.createOrder(customerId, userId, cartItems, null);

                    cartItems.clear();
                    loadAllTracks(); // Refresh main table to show updated stock
                    dialog.close();
                    showInfo("Success! Your order has been placed.");

                } catch (SQLException | IllegalArgumentException ex) {
                    showError("We encountered a problem creating your order: " + ex.getMessage());
                }
            }
        });

        Button closeBtn = new Button("Close Cart Window");
        closeBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                dialog.close();
            }
        });

        HBox buttonBox = new HBox(10);
        buttonBox.getChildren().addAll(removeBtn, clearBtn, checkoutBtn, closeBtn);

        VBox layoutBox = new VBox(15);
        layoutBox.setPadding(new Insets(15));
        layoutBox.getChildren().add(cartTable);
        layoutBox.getChildren().add(buttonBox);

        Scene scene = new Scene(layoutBox, 550, 400);
        dialog.setScene(scene);
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

            Scene scene = new Scene(loader.load(), 920, 640);
            stage.setScene(scene);
            stage.setMinWidth(820);
            stage.setMinHeight(560);
            stage.showAndWait();

        } catch (Exception e) {
            Throwable root = getRootCause(e);
            showError("Failed to open your orders (" + root.getClass().getSimpleName() + "): " + root.getMessage());
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

            Scene scene = new Scene(loader.load(), 600, 500);
            stage.setScene(scene);
            stage.setMinWidth(500);
            stage.setMinHeight(400);

            MusicPlayerController controller = loader.getController();
            stage.setOnCloseRequest(new EventHandler<WindowEvent>() {
                @Override
                public void handle(WindowEvent event) {
                    controller.dispose();
                }
            });
            stage.showAndWait();
        } catch (Exception e) {
            Throwable root = getRootCause(e);
            showError("Failed to open the music player (" + root.getClass().getSimpleName() + "): " + root.getMessage());
        }
    }

    @FXML
    private void handleLogout() {
        SessionManager.clearCurrentUser();
        Stage stage = (Stage) trackTable.getScene().getWindow();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/LoginView.fxml"));
            Scene loginScene = new Scene(loader.load(), MusicLibraryApp.LOGIN_SCENE_WIDTH, MusicLibraryApp.LOGIN_SCENE_HEIGHT);

            stage.setScene(loginScene);
            stage.setMinWidth(MusicLibraryApp.LOGIN_MIN_WIDTH);
            stage.setMinHeight(MusicLibraryApp.LOGIN_MIN_HEIGHT);
            stage.setWidth(MusicLibraryApp.LOGIN_SCENE_WIDTH);
            stage.setHeight(MusicLibraryApp.LOGIN_SCENE_HEIGHT);
            stage.centerOnScreen();
        } catch (Exception e) {
            showError("Failed to return to the login screen: " + e.getMessage());
        }
    }

    private void loadAllTracks() {
        try {
            trackData.setAll(trackDao.findAllActive());
        } catch (SQLException e) {
            showError("We could not load the music tracks: " + e.getMessage());
        }
    }

    private int resolveCustomerIdForCurrentUser() throws SQLException {
        if (SessionManager.getCurrentUser() == null) {
            throw new IllegalArgumentException("There is no logged-in user.");
        }

        String currentUsername = SessionManager.getCurrentUser().getUsername();

        for (Customer customer : customerDao.findAll()) {
            if (currentUsername.equalsIgnoreCase(customer.getName())) {
                return customer.getId();
            }
        }

        Customer newCustomer = new Customer();
        newCustomer.setName(currentUsername);
        newCustomer.setEmail(null);
        newCustomer.setPhone(null);
        newCustomer.setCity(null);

        int generatedId = customerDao.create(newCustomer);
        if (generatedId <= 0) {
            throw new SQLException("Failed to create a customer profile for user: " + currentUsername);
        }
        return generatedId;
    }

    private Throwable getRootCause(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.setHeaderText("Something went wrong");
        alert.showAndWait();
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message);
        alert.setHeaderText("Update Successful");
        alert.showAndWait();
    }
}