package com.example.musiclibrary.controller;

import com.example.musiclibrary.dao.CustomerDao;
import com.example.musiclibrary.dao.TrackDao;
import com.example.musiclibrary.db.DBConnectionManager;
import com.example.musiclibrary.model.Customer;
import com.example.musiclibrary.model.Track;
import com.example.musiclibrary.session.SessionManager;
import com.example.musiclibrary.util.TrackMediaResolver;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ComboBox;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class AdminMainController {

    @FXML
    private Label welcomeLabel;

    @FXML
    private TabPane tabPane;

    @FXML
    private Tab tracksTab;

    @FXML
    private Tab customersTab;

    @FXML
    private Tab reportsTab;

    private final ObservableList<Track> trackData = FXCollections.observableArrayList();
    private final ObservableList<Customer> customerData = FXCollections.observableArrayList();

    private final TrackDao trackDao = new TrackDao();
    private final CustomerDao customerDao = new CustomerDao();

    @FXML
    private void initialize() {
        if (SessionManager.getCurrentUser() != null) {
            welcomeLabel.setText("Welcome, " + SessionManager.getCurrentUser().getUsername());
        }

        tracksTab.setContent(buildTracksTabContent());
        customersTab.setContent(buildCustomersTabContent());
        reportsTab.setContent(buildReportsTabContent());

        loadTracks();
        loadCustomers();
        refreshReport("Summary");
    }

    private VBox buildTracksTabContent() {
        TableView<Track> trackTable = new TableView<>(trackData);
        trackTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<Track, String> titleCol = new TableColumn<>("Title");
        titleCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getTitle()));

        TableColumn<Track, Track> imageCol = new TableColumn<>("Image");
        imageCol.setCellValueFactory(data -> new javafx.beans.property.SimpleObjectProperty<>(data.getValue()));
        imageCol.setPrefWidth(110);
        imageCol.setCellFactory(col -> new TableCell<>() {
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

        TableColumn<Track, String> artistCol = new TableColumn<>("Artist");
        artistCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getArtist()));

        TableColumn<Track, String> albumCol = new TableColumn<>("Album");
        albumCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getAlbum()));

        TableColumn<Track, String> genreCol = new TableColumn<>("Genre");
        genreCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getGenre()));

        TableColumn<Track, String> priceCol = new TableColumn<>("Price");
        priceCol.setCellValueFactory(data -> {
            BigDecimal price = data.getValue().getPrice();
            return new SimpleStringProperty(price == null ? "" : price.toPlainString());
        });

        TableColumn<Track, String> stockCol = new TableColumn<>("Stock");
        stockCol.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().getStockQty())));

        trackTable.getColumns().addAll(imageCol, titleCol, artistCol, albumCol, genreCol, priceCol, stockCol);

        Button refreshButton = new Button("Refresh Tracks");
        refreshButton.setOnAction(event -> loadTracks());

        Button addButton = new Button("Add Track");
        addButton.setOnAction(event -> showTrackDialog(null));

        Button editButton = new Button("Edit Track");
        editButton.setOnAction(event -> {
            Track selected = trackTable.getSelectionModel().getSelectedItem();
            if (selected != null) showTrackDialog(selected);
            else showError("Please select a track to edit.");
        });

        Button deleteButton = new Button("Delete Track");
        deleteButton.setOnAction(event -> {
            Track selected = trackTable.getSelectionModel().getSelectedItem();
            if (selected != null) deleteTrack(selected);
            else showError("Please select a track to delete.");
        });

        HBox buttonBox = new HBox(10, addButton, editButton, deleteButton, refreshButton);

        VBox container = new VBox(10, new Label("Track List"), trackTable, buttonBox);
        container.setPadding(new Insets(12));
        return container;
    }

    private VBox buildCustomersTabContent() {
        TableView<Customer> customerTable = new TableView<>(customerData);
        customerTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<Customer, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getName()));

        TableColumn<Customer, String> emailCol = new TableColumn<>("Email");
        emailCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getEmail()));

        TableColumn<Customer, String> phoneCol = new TableColumn<>("Phone");
        phoneCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getPhone()));

        TableColumn<Customer, String> cityCol = new TableColumn<>("City");
        cityCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getCity()));

        customerTable.getColumns().addAll(nameCol, emailCol, phoneCol, cityCol);

        Button refreshButton = new Button("Refresh Customers");
        refreshButton.setOnAction(event -> loadCustomers());

        Button addButton = new Button("Add Customer");
        addButton.setOnAction(event -> showCustomerDialog(null));

        Button editButton = new Button("Edit Customer");
        editButton.setOnAction(event -> {
            Customer selected = customerTable.getSelectionModel().getSelectedItem();
            if (selected != null) showCustomerDialog(selected);
            else showError("Please select a customer to edit.");
        });

        Button deleteButton = new Button("Delete Customer");
        deleteButton.setOnAction(event -> {
            Customer selected = customerTable.getSelectionModel().getSelectedItem();
            if (selected != null) deleteCustomer(selected);
            else showError("Please select a customer to delete.");
        });

        HBox buttonBox = new HBox(10, addButton, editButton, deleteButton, refreshButton);

        VBox container = new VBox(10, new Label("Customer List"), customerTable, buttonBox);
        container.setPadding(new Insets(12));
        return container;
    }

    private VBox buildReportsTabContent() {
        Label activeTracksLabel = new Label();
        Label customerCountLabel = new Label();
        Label orderCountLabel = new Label();
        Label totalSalesLabel = new Label();

        reportsTab.setUserData(new Label[] {
                activeTracksLabel,
                customerCountLabel,
                orderCountLabel,
                totalSalesLabel
        });

        ComboBox<String> reportType = new ComboBox<>();
        reportType.getItems().addAll("Summary", "Sales by Genre", "Sales by City", "Sales by Date");
        reportType.setValue("Summary");

        TableView<String> reportTable = new TableView<>();
        reportTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        reportTable.setPrefHeight(200);

        Label avgLabel = new Label();
        Label sumLabel = new Label();
        Label maxLabel = new Label();
        Label minLabel = new Label();

        reportsTab.setUserData(new Object[] {
                activeTracksLabel, customerCountLabel, orderCountLabel, totalSalesLabel,
                reportTable, avgLabel, sumLabel, maxLabel, minLabel
        });

        Button refreshButton = new Button("Refresh Reports");
        refreshButton.setOnAction(event -> refreshReport(reportType.getValue()));

        reportType.setOnAction(event -> refreshReport(reportType.getValue()));

        VBox container = new VBox(
                10,
                new Label("System Summary"),
                activeTracksLabel,
                customerCountLabel,
                orderCountLabel,
                totalSalesLabel,
                new Label("Sales Reports"),
                reportType,
                reportTable,
                new Label("Statistics"),
                avgLabel,
                sumLabel,
                maxLabel,
                minLabel,
                refreshButton
        );
        container.setPadding(new Insets(12));
        return container;
    }

    private void loadTracks() {
        try {
            trackData.setAll(trackDao.findAllActive());
        } catch (SQLException e) {
            showError("Failed to load tracks: " + e.getMessage());
        }
    }

    private void loadCustomers() {
        try {
            customerData.setAll(customerDao.findAll());
        } catch (SQLException e) {
            showError("Failed to load customers: " + e.getMessage());
        }
    }

    private void refreshReport(String reportType) {
        Object[] data = (Object[]) reportsTab.getUserData();
        if (data == null || data.length != 9) {
            return;
        }

        Label activeTracksLabel = (Label) data[0];
        Label customerCountLabel = (Label) data[1];
        Label orderCountLabel = (Label) data[2];
        Label totalSalesLabel = (Label) data[3];
        TableView<String> reportTable = (TableView<String>) data[4];
        Label avgLabel = (Label) data[5];
        Label sumLabel = (Label) data[6];
        Label maxLabel = (Label) data[7];
        Label minLabel = (Label) data[8];

        String sql = "SELECT " +
                "(SELECT COUNT(*) FROM tracks WHERE is_active = 1) AS active_tracks, " +
                "(SELECT COUNT(*) FROM customers) AS customers, " +
                "(SELECT COUNT(*) FROM orders) AS orders, " +
                "(SELECT COALESCE(SUM(total_amount), 0) FROM orders) AS total_sales";

        try (Connection conn = DBConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
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
        ObservableList<String> data = FXCollections.observableArrayList();
        reportTable.getColumns().clear();

        String sql;
        String categoryColumn;

        switch (reportType) {
            case "Sales by Genre":
                sql = "SELECT t.genre, SUM(oi.quantity) as qty, SUM(oi.line_total) as total " +
                      "FROM order_items oi JOIN tracks t ON oi.track_id = t.id " +
                      "GROUP BY t.genre ORDER BY total DESC";
                categoryColumn = "Genre";
                break;
            case "Sales by City":
                sql = "SELECT o.shipping_city, COUNT(o.id) as orders, SUM(o.total_amount) as total " +
                      "FROM orders o GROUP BY o.shipping_city ORDER BY total DESC";
                categoryColumn = "City";
                break;
            case "Sales by Date":
                sql = "SELECT DATE(order_date) as order_date, COUNT(id) as orders, SUM(total_amount) as total " +
                      "FROM orders GROUP BY DATE(order_date) ORDER BY order_date DESC";
                categoryColumn = "Date";
                break;
            default:
                reportTable.setItems(data);
                avgLabel.setText("");
                sumLabel.setText("");
                maxLabel.setText("");
                minLabel.setText("");
                return;
        }

        try (Connection conn = DBConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            double sum = 0;
            double max = Double.MIN_VALUE;
            double min = Double.MAX_VALUE;
            int count = 0;

            while (rs.next()) {
                String category = rs.getString(1);
                double value = rs.getDouble(3);
                sum += value;
                max = Math.max(max, value);
                min = Math.min(min, value);
                count++;

                String row = String.format("%s: %.2f", category, value);
                data.add(row);
            }

            if (count > 0) {
                double avg = sum / count;
                avgLabel.setText(String.format("Average: %.2f", avg));
                sumLabel.setText(String.format("Total: %.2f", sum));
                maxLabel.setText(String.format("Maximum: %.2f", max));
                minLabel.setText(String.format("Minimum: %.2f", min));
            } else {
                avgLabel.setText("");
                sumLabel.setText("");
                maxLabel.setText("");
                minLabel.setText("");
            }
        } catch (SQLException e) {
            showError("Failed to load sales report: " + e.getMessage());
        }

        reportTable.setItems(data);
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private ImageView createTrackImageView() {
        ImageView imageView = new ImageView();
        imageView.setFitWidth(64);
        imageView.setFitHeight(64);
        imageView.setPreserveRatio(true);
        return imageView;
    }

    private void showTrackDialog(Track track) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(track == null ? "Add Track" : "Edit Track");

        TextField titleField = new TextField(track == null ? "" : track.getTitle());
        TextField artistField = new TextField(track == null ? "" : track.getArtist());
        TextField albumField = new TextField(track == null ? "" : track.getAlbum());
        TextField genreField = new TextField(track == null ? "" : track.getGenre());
        TextField priceField = new TextField(track == null ? "" : track.getPrice().toString());
        TextField stockField = new TextField(track == null ? "" : String.valueOf(track.getStockQty()));

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        grid.add(new Label("Title:"), 0, 0);
        grid.add(titleField, 1, 0);
        grid.add(new Label("Artist:"), 0, 1);
        grid.add(artistField, 1, 1);
        grid.add(new Label("Album:"), 0, 2);
        grid.add(albumField, 1, 2);
        grid.add(new Label("Genre:"), 0, 3);
        grid.add(genreField, 1, 3);
        grid.add(new Label("Price:"), 0, 4);
        grid.add(priceField, 1, 4);
        grid.add(new Label("Stock:"), 0, 5);
        grid.add(stockField, 1, 5);

        Button saveButton = new Button("Save");
        saveButton.setOnAction(event -> {
            try {
                Track newTrack = new Track();
                if (track != null) newTrack.setId(track.getId());
                newTrack.setTitle(titleField.getText());
                newTrack.setArtist(artistField.getText());
                newTrack.setAlbum(albumField.getText());
                newTrack.setGenre(genreField.getText());
                newTrack.setPrice(new BigDecimal(priceField.getText()));
                newTrack.setStockQty(Integer.parseInt(stockField.getText()));

                if (track == null) {
                    trackDao.create(newTrack);
                } else {
                    trackDao.update(newTrack);
                }
                loadTracks();
                dialog.close();
            } catch (Exception e) {
                showError("Invalid input: " + e.getMessage());
            }
        });

        Button cancelButton = new Button("Cancel");
        cancelButton.setOnAction(event -> dialog.close());

        HBox buttonBox = new HBox(10, saveButton, cancelButton);
        buttonBox.setAlignment(Pos.CENTER);

        VBox vbox = new VBox(10, grid, buttonBox);
        Scene scene = new Scene(vbox);
        dialog.setScene(scene);
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

    private void showCustomerDialog(Customer customer) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(customer == null ? "Add Customer" : "Edit Customer");

        TextField nameField = new TextField(customer == null ? "" : customer.getName());
        TextField emailField = new TextField(customer == null ? "" : customer.getEmail());
        TextField phoneField = new TextField(customer == null ? "" : customer.getPhone());
        TextField cityField = new TextField(customer == null ? "" : customer.getCity());

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        grid.add(new Label("Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Email:"), 0, 1);
        grid.add(emailField, 1, 1);
        grid.add(new Label("Phone:"), 0, 2);
        grid.add(phoneField, 1, 2);
        grid.add(new Label("City:"), 0, 3);
        grid.add(cityField, 1, 3);

        Button saveButton = new Button("Save");
        saveButton.setOnAction(event -> {
            try {
                Customer newCustomer = new Customer();
                if (customer != null) newCustomer.setId(customer.getId());
                newCustomer.setName(nameField.getText());
                newCustomer.setEmail(emailField.getText());
                newCustomer.setPhone(phoneField.getText());
                newCustomer.setCity(cityField.getText());

                if (customer == null) {
                    customerDao.create(newCustomer);
                } else {
                    customerDao.update(newCustomer);
                }
                loadCustomers();
                dialog.close();
            } catch (Exception e) {
                showError("Invalid input: " + e.getMessage());
            }
        });

        Button cancelButton = new Button("Cancel");
        cancelButton.setOnAction(event -> dialog.close());

        HBox buttonBox = new HBox(10, saveButton, cancelButton);
        buttonBox.setAlignment(Pos.CENTER);

        VBox vbox = new VBox(10, grid, buttonBox);
        Scene scene = new Scene(vbox);
        dialog.setScene(scene);
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
}
