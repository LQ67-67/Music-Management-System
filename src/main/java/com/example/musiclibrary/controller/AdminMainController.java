package com.example.musiclibrary.controller;

import com.example.musiclibrary.dao.CustomerDao;
import com.example.musiclibrary.dao.TrackDao;
import com.example.musiclibrary.db.DBConnectionManager;
import com.example.musiclibrary.model.Customer;
import com.example.musiclibrary.model.Track;
import com.example.musiclibrary.session.SessionManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;

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
        refreshReport();
    }

    private VBox buildTracksTabContent() {
        TableView<Track> trackTable = new TableView<>(trackData);
        trackTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<Track, String> titleCol = new TableColumn<>("Title");
        titleCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getTitle()));

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

        trackTable.getColumns().addAll(titleCol, artistCol, albumCol, genreCol, priceCol, stockCol);

        Button refreshButton = new Button("Refresh Tracks");
        refreshButton.setOnAction(event -> loadTracks());

        VBox container = new VBox(10, new Label("Track List"), trackTable, refreshButton);
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

        VBox container = new VBox(10, new Label("Customer List"), customerTable, refreshButton);
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

        Button refreshButton = new Button("Refresh Reports");
        refreshButton.setOnAction(event -> refreshReport());

        VBox container = new VBox(
                10,
                new Label("System Summary"),
                activeTracksLabel,
                customerCountLabel,
                orderCountLabel,
                totalSalesLabel,
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

    private void refreshReport() {
        Label[] labels = (Label[]) reportsTab.getUserData();
        if (labels == null || labels.length != 4) {
            return;
        }

        String sql = "SELECT " +
                "(SELECT COUNT(*) FROM tracks WHERE is_active = 1) AS active_tracks, " +
                "(SELECT COUNT(*) FROM customers) AS customers, " +
                "(SELECT COUNT(*) FROM orders) AS orders, " +
                "(SELECT COALESCE(SUM(total_amount), 0) FROM orders) AS total_sales";

        try (Connection conn = DBConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                labels[0].setText("Active Tracks: " + rs.getInt("active_tracks"));
                labels[1].setText("Customers: " + rs.getInt("customers"));
                labels[2].setText("Orders: " + rs.getInt("orders"));
                labels[3].setText("Total Sales: " + rs.getBigDecimal("total_sales"));
            }
        } catch (SQLException e) {
            showError("Failed to load report summary: " + e.getMessage());
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
