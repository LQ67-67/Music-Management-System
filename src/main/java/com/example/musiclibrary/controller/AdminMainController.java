package com.example.musiclibrary.controller;

import com.example.musiclibrary.MusicLibraryApp;
import com.example.musiclibrary.dao.CustomerDao;
import com.example.musiclibrary.dao.TrackDao;
import com.example.musiclibrary.db.DBConnectionManager;
import com.example.musiclibrary.model.Customer;
import com.example.musiclibrary.model.Track;
import com.example.musiclibrary.session.SessionManager;
import com.example.musiclibrary.util.TrackMediaResolver;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class AdminMainController {
    @FXML private Label welcomeLabel;
    @FXML private TabPane tabPane;
    @FXML private Tab tracksTab;
    @FXML private Tab customersTab;
    @FXML private Tab ordersTab;
    @FXML private Tab reportsTab;

    private final ObservableList<Track> trackData = FXCollections.observableArrayList();
    private final ObservableList<Customer> customerData = FXCollections.observableArrayList();
    private final ObservableList<String[]> orderData = FXCollections.observableArrayList();

    private final TrackDao trackDao = new TrackDao();
    private final CustomerDao customerDao = new CustomerDao();

    @FXML
    private void initialize() {
        if (SessionManager.getCurrentUser() != null) {
            welcomeLabel.setText("Welcome Admin, " + SessionManager.getCurrentUser().getUsername());
        }

        tracksTab.setContent(buildTracksTabContent());
        customersTab.setContent(buildCustomersTabContent());
        ordersTab.setContent(buildOrdersTabContent());
        reportsTab.setContent(buildReportsTabContent());

        loadTracks();
        loadCustomers();
        loadOrders();
        refreshReport("Summary");
    }

    // tracks tab
    private VBox buildTracksTabContent() {
        TableView<Track> trackTable = new TableView<>(trackData);
        trackTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        // image column
        TableColumn<Track, Track> imageCol = new TableColumn<>("Image");
        imageCol.setPrefWidth(110);
        imageCol.setCellValueFactory(param -> new SimpleObjectProperty<>(param.getValue()));
        imageCol.setCellFactory(param -> new TableCell<Track, Track>() {
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
        });

        // text columns
        TableColumn<Track, String> titleCol = new TableColumn<>("Title");
        titleCol.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getTitle()));

        TableColumn<Track, String> artistCol = new TableColumn<>("Artist");
        artistCol.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getArtist()));

        TableColumn<Track, String> albumCol = new TableColumn<>("Album");
        albumCol.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getAlbum()));

        TableColumn<Track, String> genreCol = new TableColumn<>("Genre");
        genreCol.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getGenre()));

        TableColumn<Track, String> priceCol = new TableColumn<>("Price");
        priceCol.setCellValueFactory(param -> {
            BigDecimal price = param.getValue().getPrice();
            if (price == null) return new SimpleStringProperty("");
            return new SimpleStringProperty(price.toPlainString());
        });

        TableColumn<Track, String> stockCol = new TableColumn<>("Stock");
        stockCol.setCellValueFactory(param -> new SimpleStringProperty(String.valueOf(param.getValue().getStockQty())));

        trackTable.getColumns().add(imageCol);
        trackTable.getColumns().add(titleCol);
        trackTable.getColumns().add(artistCol);
        trackTable.getColumns().add(albumCol);
        trackTable.getColumns().add(genreCol);
        trackTable.getColumns().add(priceCol);
        trackTable.getColumns().add(stockCol);

        // buttons
        Button addBtn = new Button("Add Track");
        addBtn.setOnAction(event -> showTrackDialog(null));

        Button editBtn = new Button("Edit Track");
        editBtn.setOnAction(event -> {
            Track selectedTrack = trackTable.getSelectionModel().getSelectedItem();
            if (selectedTrack != null) {
                showTrackDialog(selectedTrack);
            } else {
                showError("Please select a track from the table to edit.");
            }
        });

        Button deleteBtn = new Button("Delete Track");
        deleteBtn.setOnAction(event -> {
            Track selectedTrack = trackTable.getSelectionModel().getSelectedItem();
            if (selectedTrack != null) {
                deleteTrack(selectedTrack);
            } else {
                showError("Please select a track from the table to delete.");
            }
        });

        // building the explicit layout
        Label headingLabel = new Label("Track List");
        HBox buttonBox = new HBox(10);
        buttonBox.getChildren().addAll(addBtn, editBtn, deleteBtn);

        VBox mainBox = new VBox(10);
        mainBox.setPadding(new Insets(12));
        VBox.setVgrow(trackTable, Priority.ALWAYS);   // let table fill vertical space
        mainBox.getChildren().addAll(headingLabel, trackTable, buttonBox);

        return mainBox;
    }

    private void loadTracks() {
        try {
            trackData.setAll(trackDao.findAllActive());
        } catch (SQLException e) {
            showError("Failed to load tracks from database: " + e.getMessage());
        }
    }

    private void showTrackDialog(Track track) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);

        if (track == null) {
            dialog.setTitle("Add New Track");
        } else {
            dialog.setTitle("Edit Existing Track");
        }

        TextField titleField = new TextField(track == null ? "" : track.getTitle());
        TextField artistField = new TextField(track == null ? "" : track.getArtist());
        TextField albumField = new TextField(track == null ? "" : track.getAlbum());
        TextField genreField = new TextField(track == null ? "" : track.getGenre());
        TextField priceField = new TextField(track == null ? "" : track.getPrice().toString());
        TextField stockField = new TextField(track == null ? "" : String.valueOf(track.getStockQty()));

        // building explicit layout
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        grid.add(new Label("Title:"), 0, 0); grid.add(titleField, 1, 0);
        grid.add(new Label("Artist:"), 0, 1); grid.add(artistField, 1, 1);
        grid.add(new Label("Album:"), 0, 2); grid.add(albumField, 1, 2);
        grid.add(new Label("Genre:"), 0, 3); grid.add(genreField, 1, 3);
        grid.add(new Label("Price:"), 0, 4); grid.add(priceField, 1, 4);
        grid.add(new Label("Stock:"), 0, 5); grid.add(stockField, 1, 5);

        Button saveBtn = new Button("Save");
        saveBtn.setOnAction(event -> {
            try {
                BigDecimal price = new BigDecimal(priceField.getText());
                int stock = Integer.parseInt(stockField.getText());

                if (price.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("Price must be greater than 0.");
                if (stock <= 0) throw new IllegalArgumentException("Stock must be greater than 0.");

                Track newOrUpdatedTrack = new Track();
                if (track != null) {
                    newOrUpdatedTrack.setId(track.getId());
                }

                newOrUpdatedTrack.setTitle(titleField.getText());
                newOrUpdatedTrack.setArtist(artistField.getText());
                newOrUpdatedTrack.setAlbum(albumField.getText());
                newOrUpdatedTrack.setGenre(genreField.getText());
                newOrUpdatedTrack.setPrice(price);
                newOrUpdatedTrack.setStockQty(stock);

                if (track == null) {
                    trackDao.create(newOrUpdatedTrack);
                } else {
                    trackDao.update(newOrUpdatedTrack);
                }

                loadTracks();
                dialog.close();
            } catch (Exception ex) {
                showError("Invalid input: " + ex.getMessage());
            }
        });

        Button cancelBtn = new Button("Cancel");
        cancelBtn.setOnAction(event -> dialog.close());

        HBox buttonLayout = new HBox(10);
        buttonLayout.setAlignment(Pos.CENTER);
        buttonLayout.getChildren().addAll(saveBtn, cancelBtn);

        VBox mainLayout = new VBox(10);
        mainLayout.getChildren().addAll(grid, buttonLayout);

        dialog.setScene(new Scene(mainLayout));
        dialog.showAndWait();
    }

    private void deleteTrack(Track track) {
        try {
            trackDao.delete(track.getId());
            loadTracks();
        } catch (SQLException e) {
            showError("Failed to delete track: " + e.getMessage());
        }
    }

    // customer tab
    private VBox buildCustomersTabContent() {
        TableView<Customer> customerTable = new TableView<>(customerData);
        customerTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<Customer, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getName()));

        TableColumn<Customer, String> emailCol = new TableColumn<>("Email");
        emailCol.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getEmail()));

        TableColumn<Customer, String> phoneCol = new TableColumn<>("Phone");
        phoneCol.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getPhone()));

        TableColumn<Customer, String> cityCol = new TableColumn<>("City");
        cityCol.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getCity()));

        customerTable.getColumns().add(nameCol);
        customerTable.getColumns().add(emailCol);
        customerTable.getColumns().add(phoneCol);
        customerTable.getColumns().add(cityCol);

        Button addBtn = new Button("Add Customer");
        addBtn.setOnAction(event -> showCustomerDialog(null));

        Button editBtn = new Button("Edit Customer");
        editBtn.setOnAction(event -> {
            Customer selectedCustomer = customerTable.getSelectionModel().getSelectedItem();
            if (selectedCustomer != null) {
                showCustomerDialog(selectedCustomer);
            } else {
                showError("Please select a customer to edit.");
            }
        });

        Button deleteBtn = new Button("Delete Customer");
        deleteBtn.setOnAction(event -> {
            Customer selectedCustomer = customerTable.getSelectionModel().getSelectedItem();
            if (selectedCustomer != null) {
                deleteCustomer(selectedCustomer);
            } else {
                showError("Please select a customer to delete.");
            }
        });

        Label headingLabel = new Label("Customer List");
        HBox buttonBox = new HBox(10);
        buttonBox.getChildren().addAll(addBtn, editBtn, deleteBtn);

        VBox mainBox = new VBox(10);
        mainBox.setPadding(new Insets(12));
        VBox.setVgrow(customerTable, Priority.ALWAYS);
        mainBox.getChildren().addAll(headingLabel, customerTable, buttonBox);

        return mainBox;
    }

    private void loadCustomers() {
        try {
            customerData.setAll(customerDao.findAll());
        } catch (SQLException e) {
            showError("Failed to load customers: " + e.getMessage());
        }
    }

    private void showCustomerDialog(Customer customer) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);

        if (customer == null) {
            dialog.setTitle("Add New Customer");
        } else {
            dialog.setTitle("Edit Customer");
        }

        TextField nameField = new TextField(customer == null ? "" : customer.getName());
        TextField emailField = new TextField(customer == null ? "" : customer.getEmail());
        TextField phoneField = new TextField(customer == null ? "" : customer.getPhone());
        TextField cityField = new TextField(customer == null ? "" : customer.getCity());

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10); grid.setPadding(new Insets(20));

        grid.add(new Label("Name:"), 0, 0); grid.add(nameField, 1, 0);
        grid.add(new Label("Email:"), 0, 1); grid.add(emailField, 1, 1);
        grid.add(new Label("Phone:"), 0, 2); grid.add(phoneField, 1, 2);
        grid.add(new Label("City:"), 0, 3); grid.add(cityField, 1, 3);

        Button saveBtn = new Button("Save");
        saveBtn.setOnAction(event -> {
            try {
                Customer newOrUpdatedCustomer = new Customer();
                if (customer != null) {
                    newOrUpdatedCustomer.setId(customer.getId());
                }

                newOrUpdatedCustomer.setName(nameField.getText());
                newOrUpdatedCustomer.setEmail(emailField.getText());
                newOrUpdatedCustomer.setPhone(phoneField.getText());
                newOrUpdatedCustomer.setCity(cityField.getText());

                if (customer == null) {
                    customerDao.create(newOrUpdatedCustomer);
                } else {
                    customerDao.update(newOrUpdatedCustomer);
                }

                loadCustomers();
                dialog.close();
            } catch (Exception ex) {
                showError("Invalid input: " + ex.getMessage());
            }
        });

        Button cancelBtn = new Button("Cancel");
        cancelBtn.setOnAction(event -> dialog.close());

        HBox buttonLayout = new HBox(10);
        buttonLayout.setAlignment(Pos.CENTER);
        buttonLayout.getChildren().addAll(saveBtn, cancelBtn);

        VBox mainLayout = new VBox(10);
        mainLayout.getChildren().addAll(grid, buttonLayout);

        dialog.setScene(new Scene(mainLayout));
        dialog.showAndWait();
    }

    private void deleteCustomer(Customer customer) {
        try {
            customerDao.delete(customer.getId());
            loadCustomers();
        } catch (SQLException e) {
            showError("Failed to delete customer: " + e.getMessage());
        }
    }

    // orders tab
    private VBox buildOrdersTabContent() {
        TableView<String[]> orderTable = new TableView<>(orderData);
        orderTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        String[] headers = {"Order ID", "Username", "Customer Name", "Order Date", "Status", "Total", "Shipping City"};

        for (int i = 0; i < headers.length; i++) {
            final int index = i;     // needs to be final to use inside the inner class/lambda
            TableColumn<String[], String> column = new TableColumn<>(headers[i]);

            column.setCellValueFactory(param -> new SimpleStringProperty(param.getValue()[index]));

            orderTable.getColumns().add(column);
        }

        Button editBtn = new Button("Edit Order");
        editBtn.setOnAction(event -> {
            String[] selectedRow = orderTable.getSelectionModel().getSelectedItem();
            if (selectedRow != null) {
                showOrderEditDialog(selectedRow);
            } else {
                showError("Please select an order to edit.");
            }
        });

        Label headingLabel = new Label("All Orders");
        HBox buttonBox = new HBox(10);
        buttonBox.getChildren().add(editBtn);

        VBox mainBox = new VBox(10);
        mainBox.setPadding(new Insets(12));
        VBox.setVgrow(orderTable, Priority.ALWAYS);
        mainBox.getChildren().addAll(headingLabel, orderTable, buttonBox);

        return mainBox;
    }

    private void showOrderEditDialog(String[] row) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Edit Order #" + row[0]);

        ComboBox<String> statusBox = new ComboBox<>();
        statusBox.getItems().addAll("PENDING", "CONFIRMED", "CANCELLED");
        statusBox.setValue(row[4]);

        TextField cityField = new TextField(row[6] == null ? "" : row[6]);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10); grid.setPadding(new Insets(20));
        grid.add(new Label("Status:"), 0, 0); grid.add(statusBox, 1, 0);
        grid.add(new Label("Shipping City:"), 0, 1); grid.add(cityField, 1, 1);

        Button saveBtn = new Button("Save");
        saveBtn.setOnAction(event -> {
            String sql = "UPDATE orders SET status = ?, shipping_city = ? WHERE id = ?";
            try (Connection conn = DBConnectionManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setString(1, statusBox.getValue());
                ps.setString(2, cityField.getText());
                ps.setInt(3, Integer.parseInt(row[0]));
                ps.executeUpdate();

                loadOrders();
                dialog.close();
            } catch (SQLException ex) {
                showError("Failed to update order: " + ex.getMessage());
            }
        });

        Button cancelBtn = new Button("Cancel");
        cancelBtn.setOnAction(event -> dialog.close());

        HBox buttonLayout = new HBox(10);
        buttonLayout.setAlignment(Pos.CENTER);
        buttonLayout.getChildren().addAll(saveBtn, cancelBtn);

        VBox mainLayout = new VBox(10);
        mainLayout.getChildren().addAll(grid, buttonLayout);

        dialog.setScene(new Scene(mainLayout));
        dialog.showAndWait();
    }

    private void loadOrders() {
        String sql = "SELECT o.id, u.username, c.name, o.order_date, o.status, o.total_amount, o.shipping_city " + "FROM orders o JOIN users u ON o.user_id = u.id JOIN customers c ON o.customer_id = c.id " + "ORDER BY o.order_date DESC";

        try (Connection conn = DBConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            ObservableList<String[]> rows = FXCollections.observableArrayList();
            while (rs.next()) {
                rows.add(new String[]{
                        String.valueOf(rs.getInt("id")),
                        rs.getString("username"),
                        rs.getString("name"),
                        rs.getTimestamp("order_date").toString(),
                        rs.getString("status"),
                        rs.getString("total_amount"),
                        rs.getString("shipping_city")
                });
            }
            orderData.setAll(rows);

        } catch (SQLException e) {
            showError("Failed to load orders: " + e.getMessage());
        }
    }

    // reports tab
    private VBox buildReportsTabContent() {
        Label activeTracksLabel = new Label();
        Label customerCountLabel = new Label();
        Label orderCountLabel = new Label();
        Label totalSalesLabel = new Label();

        TableView<String> reportTable = new TableView<>();
        reportTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        reportTable.setPrefHeight(200);

        Label avgLabel = new Label();
        Label sumLabel = new Label();
        Label maxLabel = new Label();
        Label minLabel = new Label();

        ComboBox<String> reportType = new ComboBox<>();
        reportType.getItems().addAll("Summary", "Sales by Genre", "Sales by City", "Sales by Date");
        reportType.setValue("Summary");

        reportsTab.setUserData(new Object[]{
                activeTracksLabel, customerCountLabel, orderCountLabel, totalSalesLabel,
                reportTable, avgLabel, sumLabel, maxLabel, minLabel
        });

        Button refreshBtn = new Button("Refresh Reports");
        refreshBtn.setOnAction(event -> refreshReport(reportType.getValue()));

        reportType.setOnAction(event -> refreshReport(reportType.getValue()));

        VBox box = new VBox(10);
        box.setPadding(new Insets(12));
        box.getChildren().addAll(
                new Label("System Summary"),
                activeTracksLabel, customerCountLabel, orderCountLabel, totalSalesLabel,
                new Label("Sales Reports"), reportType, reportTable,
                new Label("Statistics"), avgLabel, sumLabel, maxLabel, minLabel,
                refreshBtn
        );
        return box;
    }

    private void refreshReport(String reportType) {
        Object[] data = (Object[]) reportsTab.getUserData();
        if (data == null || data.length != 9) return;

        Label activeTracksLabel = (Label) data[0];
        Label customerCountLabel = (Label) data[1];
        Label orderCountLabel = (Label) data[2];
        Label totalSalesLabel = (Label) data[3];

        @SuppressWarnings("unchecked")
        TableView<String> reportTable = (TableView<String>) data[4];

        Label avgLabel = (Label) data[5];
        Label sumLabel = (Label) data[6];
        Label maxLabel = (Label) data[7];
        Label minLabel = (Label) data[8];

        String summarySql = "SELECT " + "(SELECT COUNT(*) FROM tracks WHERE is_active = 1) AS active_tracks, " + "(SELECT COUNT(*) FROM customers) AS customers, " + "(SELECT COUNT(*) FROM orders) AS orders, " + "(SELECT COALESCE(SUM(total_amount), 0) FROM orders) AS total_sales";

        try (Connection conn = DBConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(summarySql);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                activeTracksLabel.setText("Active Tracks: " + rs.getInt("active_tracks"));
                customerCountLabel.setText("Customers: " + rs.getInt("customers"));
                orderCountLabel.setText("Orders: " + rs.getInt("orders"));
                totalSalesLabel.setText("Total Sales: " + rs.getBigDecimal("total_sales"));
            }
        } catch (SQLException e) {
            showError("Failed to load report summary: " + e.getMessage());
            return;
        }

        loadSalesReport(reportType, reportTable, avgLabel, sumLabel, maxLabel, minLabel);
    }

    private void loadSalesReport(String reportType, TableView<String> reportTable, Label avgLabel, Label sumLabel, Label maxLabel, Label minLabel) {
        String sql;
        switch (reportType) {
            case "Sales by Genre":
                sql = "SELECT t.genre, SUM(oi.quantity), SUM(oi.line_total) FROM order_items oi JOIN tracks t ON oi.track_id = t.id GROUP BY t.genre ORDER BY 3 DESC";
                break;
            case "Sales by City":
                sql = "SELECT o.shipping_city, COUNT(o.id), SUM(o.total_amount) FROM orders o GROUP BY o.shipping_city ORDER BY 3 DESC";
                break;
            case "Sales by Date":
                sql = "SELECT DATE(order_date), COUNT(id), SUM(total_amount) FROM orders GROUP BY DATE(order_date) ORDER BY 1 DESC";
                break;
            default:
                reportTable.setItems(FXCollections.observableArrayList());
                avgLabel.setText(""); sumLabel.setText(""); maxLabel.setText(""); minLabel.setText("");
                return;
        }

        try (Connection conn = DBConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            ObservableList<String> rows = FXCollections.observableArrayList();
            double sum = 0;
            double max = Double.MIN_VALUE;
            double min = Double.MAX_VALUE;
            int count = 0;

            while (rs.next()) {
                double value = rs.getDouble(3);
                sum += value;
                max = Math.max(max, value);
                min = Math.min(min, value);
                count++;

                rows.add(String.format("%s: %.2f", rs.getString(1), value));
            }

            reportTable.getColumns().clear();
            TableColumn<String, String> resultCol = new TableColumn<>("Result");

            resultCol.setCellValueFactory(param -> new SimpleStringProperty(param.getValue()));

            reportTable.getColumns().add(resultCol);
            reportTable.setItems(rows);

            if (count > 0) {
                avgLabel.setText(String.format("Average: %.2f", sum / count));
                sumLabel.setText(String.format("Total: %.2f", sum));
                maxLabel.setText(String.format("Maximum: %.2f", max));
                minLabel.setText(String.format("Minimum: %.2f", min));
            } else {
                avgLabel.setText(""); sumLabel.setText(""); maxLabel.setText(""); minLabel.setText("");
            }
        } catch (SQLException e) {
            showError("Failed to load sales report: " + e.getMessage());
        }
    }

    @FXML
    private void handleLogout() {
        SessionManager.clearCurrentUser();
        Stage stage = (Stage) tabPane.getScene().getWindow();
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
            showError("Failed to return to login screen: " + e.getMessage());
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.setHeaderText("Admin Error");
        alert.showAndWait();
    }
}