package com.example.musiclibrary.controller;

import com.example.musiclibrary.MusicLibraryApp;
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
            welcomeLabel.setText("Welcome, " + SessionManager.getCurrentUser().getUsername());
        }

        // build and assign content for each tab
        tracksTab.setContent(buildTracksTabContent());
        customersTab.setContent(buildCustomersTabContent());
        ordersTab.setContent(buildOrdersTabContent());
        reportsTab.setContent(buildReportsTabContent());

        loadTracks();
        loadCustomers();
        loadOrders();
        refreshReport("Summary");
    }

    // Tracks Tab
    private VBox buildTracksTabContent() {
        TableView<Track> trackTable = new TableView<>(trackData);
        trackTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        // cover image column
        TableColumn<Track, Track> imageCol = new TableColumn<>("Image");
        imageCol.setCellValueFactory(d ->
                new javafx.beans.property.SimpleObjectProperty<>(d.getValue()));
        imageCol.setPrefWidth(110);
        imageCol.setCellFactory(col -> new TableCell<>() {
            private final ImageView iv = makeImageView();

            @Override
            protected void updateItem(Track item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                iv.setImage(TrackMediaResolver.loadTrackImage(item, item.getId() + ".mp3"));
                setGraphic(iv);
            }
        });

        TableColumn<Track, String> titleCol = makeCol("Title",d -> d.getValue().getTitle());
        TableColumn<Track, String> artistCol = makeCol("Artist",d -> d.getValue().getArtist());
        TableColumn<Track, String> albumCol = makeCol("Album",d -> d.getValue().getAlbum());
        TableColumn<Track, String> genreCol = makeCol("Genre",d -> d.getValue().getGenre());
        TableColumn<Track, String> priceCol = makeCol("Price",d -> {
            BigDecimal p = d.getValue().getPrice();
            return p == null ? "" : p.toPlainString();
        });
        TableColumn<Track, String> stockCol  = makeCol("Stock",  d ->
                String.valueOf(d.getValue().getStockQty()));

        trackTable.getColumns().add(imageCol);
        trackTable.getColumns().add(titleCol);
        trackTable.getColumns().add(artistCol);
        trackTable.getColumns().add(albumCol);
        trackTable.getColumns().add(genreCol);
        trackTable.getColumns().add(priceCol);
        trackTable.getColumns().add(stockCol);

        Button addBtn    = new Button("Add Track");
        Button editBtn   = new Button("Edit Track");
        Button deleteBtn = new Button("Delete Track");

        addBtn.setOnAction(e -> showTrackDialog(null));

        editBtn.setOnAction(e -> {
            Track sel = trackTable.getSelectionModel().getSelectedItem();
            if (sel != null) {
                showTrackDialog(sel);
            } else {
                showError("Please select a track to edit.");
            }
        });

        deleteBtn.setOnAction(e -> {
            Track sel = trackTable.getSelectionModel().getSelectedItem();
            if (sel != null) {
                deleteTrack(sel);
            } else {
                showError("Please select a track to delete.");
            }
        });

        return makeTabBox(new Label("Track List"), trackTable, new HBox(10, addBtn, editBtn, deleteBtn));
    }

    private void loadTracks() {
        try {
            trackData.setAll(trackDao.findAllActive());
        } catch (SQLException e) {
            showError("Failed to load tracks: " + e.getMessage());
        }
    }

    // pass null to open the "Add Track" dialog, or pass an existing track to edit it
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

        GridPane grid = makeGrid();
        grid.add(new Label("Title:"), 0,0);grid.add(titleField,1,0);
        grid.add(new Label("Artist:"),0,1);grid.add(artistField,1,1);
        grid.add(new Label("Album:"),0,2);grid.add(albumField,1,2);
        grid.add(new Label("Genre:"),0,3);grid.add(genreField,1,3);
        grid.add(new Label("Price:"),0,4);grid.add(priceField,1,4);
        grid.add(new Label("Stock:"),0,5);grid.add(stockField,1,5);

        Button saveBtn = new Button("Save");
        saveBtn.setOnAction(e -> {
            try {
                BigDecimal price = new BigDecimal(priceField.getText());
                int stock = Integer.parseInt(stockField.getText());

                if (price.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("Price must be > 0.");
                }
                if (stock <= 0) {
                    throw new IllegalArgumentException("Stock must be > 0.");
                }

                Track t = new Track();
                if (track != null) t.setId(track.getId());
                t.setTitle(titleField.getText());
                t.setArtist(artistField.getText());
                t.setAlbum(albumField.getText());
                t.setGenre(genreField.getText());
                t.setPrice(price);
                t.setStockQty(stock);

                if (track == null) {
                    trackDao.create(t);
                } else {
                    trackDao.update(t);
                }

                loadTracks();
                dialog.close();

            } catch (Exception ex) {
                showError("Invalid input: " + ex.getMessage());
            }
        });

        showDialog(dialog, grid, saveBtn);
    }

    private void deleteTrack(Track track) {
        try {
            trackDao.delete(track.getId());
            loadTracks();
        } catch (SQLException e) {
            showError("Failed to delete track: " + e.getMessage());
        }
    }

    // Customers Tab
    private VBox buildCustomersTabContent() {
        TableView<Customer> customerTable = new TableView<>(customerData);
        customerTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        customerTable.getColumns().add(makeCol("Name",d -> d.getValue().getName()));
        customerTable.getColumns().add(makeCol("Email",d -> d.getValue().getEmail()));
        customerTable.getColumns().add(makeCol("Phone",d -> d.getValue().getPhone()));
        customerTable.getColumns().add(makeCol("City",d -> d.getValue().getCity()));

        Button addBtn = new Button("Add Customer");
        Button editBtn = new Button("Edit Customer");
        Button deleteBtn = new Button("Delete Customer");

        addBtn.setOnAction(e -> showCustomerDialog(null));

        editBtn.setOnAction(e -> {
            Customer sel = customerTable.getSelectionModel().getSelectedItem();
            if (sel != null) {
                showCustomerDialog(sel);
            } else {
                showError("Please select a customer to edit.");
            }
        });

        deleteBtn.setOnAction(e -> {
            Customer sel = customerTable.getSelectionModel().getSelectedItem();
            if (sel != null) {
                deleteCustomer(sel);
            } else {
                showError("Please select a customer to delete.");
            }
        });

        return makeTabBox(new Label("Customer List"), customerTable, new HBox(10, addBtn, editBtn, deleteBtn));
    }

    private void loadCustomers() {
        try {
            customerData.setAll(customerDao.findAll());
        } catch (SQLException e) {
            showError("Failed to load customers: " + e.getMessage());
        }
    }

    // pass null to open the "Add Customer" dialog, or pass an existing customer to edit it
    private void showCustomerDialog(Customer customer) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(customer == null ? "Add Customer" : "Edit Customer");

        TextField nameField = new TextField(customer == null ? "" : customer.getName());
        TextField emailField = new TextField(customer == null ? "" : customer.getEmail());
        TextField phoneField = new TextField(customer == null ? "" : customer.getPhone());
        TextField cityField = new TextField(customer == null ? "" : customer.getCity());

        GridPane grid = makeGrid();
        grid.add(new Label("Name:"),0,0);grid.add(nameField,1,0);
        grid.add(new Label("Email:"),0,1);grid.add(emailField,1,1);
        grid.add(new Label("Phone:"),0,2);grid.add(phoneField,1,2);
        grid.add(new Label("City:"),0,3);grid.add(cityField,1, 3);

        Button saveBtn = new Button("Save");
        saveBtn.setOnAction(e -> {
            try {
                Customer c = new Customer();
                if (customer != null) c.setId(customer.getId());
                c.setName(nameField.getText());
                c.setEmail(emailField.getText());
                c.setPhone(phoneField.getText());
                c.setCity(cityField.getText());

                if (customer == null) {
                    customerDao.create(c);
                } else {
                    customerDao.update(c);
                }

                loadCustomers();
                dialog.close();

            } catch (Exception ex) {
                showError("Invalid input: " + ex.getMessage());
            }
        });

        showDialog(dialog, grid, saveBtn);
    }

    private void deleteCustomer(Customer customer) {
        try {
            customerDao.delete(customer.getId());
            loadCustomers();
        } catch (SQLException e) {
            showError("Failed to delete customer: " + e.getMessage());
        }
    }

    // Orders Tab
    private VBox buildOrdersTabContent() {
        TableView<String[]> orderTable = new TableView<>(orderData);
        orderTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        // each row is a String[7]: id, username, customer name, date, status, total, city
        String[] headers = {"Order ID", "Username", "Customer Name", "Order Date", "Status", "Total", "Shipping City"};

        for (int i = 0; i < headers.length; i++) {
            final int idx = i;
            TableColumn<String[], String> col = new TableColumn<>(headers[i]);
            col.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[idx]));
            orderTable.getColumns().add(col);
        }

        Button editBtn = new Button("Edit Order");
        editBtn.setOnAction(e -> {
            String[] sel = orderTable.getSelectionModel().getSelectedItem();
            if (sel != null) {
                showOrderEditDialog(sel);
            } else {
                showError("Please select an order to edit.");
            }
        });

        return makeTabBox(new Label("All Orders"), orderTable, new HBox(10, editBtn));
    }

    // open dialog to edit the status and shipping city of the selected order
    private void showOrderEditDialog(String[] row) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Edit Order #" + row[0]);

        ComboBox<String> statusBox = new ComboBox<>();
        statusBox.getItems().addAll("PENDING", "CONFIRMED", "CANCELLED");
        statusBox.setValue(row[4]);

        TextField cityField = new TextField(row[6] == null ? "" : row[6]);

        GridPane grid = makeGrid();
        grid.add(new Label("Status:"),0, 0);grid.add(statusBox, 1, 0);
        grid.add(new Label("Shipping City:"),0, 1);grid.add(cityField, 1, 1);

        Button saveBtn = new Button("Save");
        saveBtn.setOnAction(e -> {
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

        showDialog(dialog, grid, saveBtn);
    }

    private void loadOrders() {
        String sql = "SELECT o.id, u.username, c.name, o.order_date, o.status, o.total_amount, o.shipping_city "
                + "FROM orders o " + "JOIN users u ON o.user_id = u.id " + "JOIN customers c ON o.customer_id = c.id " + "ORDER BY o.order_date DESC";

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

    // Reports Tab
    private VBox buildReportsTabContent() {
        Label activeTracksLabel  = new Label();
        Label customerCountLabel = new Label();
        Label orderCountLabel    = new Label();
        Label totalSalesLabel    = new Label();

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

        // store references so refreshReport() can update them later
        reportsTab.setUserData(new Object[]{
                activeTracksLabel, customerCountLabel, orderCountLabel, totalSalesLabel,
                reportTable, avgLabel, sumLabel, maxLabel, minLabel
        });

        Button refreshBtn = new Button("Refresh Reports");
        refreshBtn.setOnAction(e -> refreshReport(reportType.getValue()));
        reportType.setOnAction(e -> refreshReport(reportType.getValue()));

        VBox box = new VBox(10,
                new Label("System Summary"),
                activeTracksLabel, customerCountLabel, orderCountLabel, totalSalesLabel,
                new Label("Sales Reports"), reportType, reportTable,
                new Label("Statistics"), avgLabel, sumLabel, maxLabel, minLabel,
                refreshBtn);

        box.setPadding(new Insets(12));
        return box;
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

        @SuppressWarnings("unchecked")
        TableView<String> reportTable = (TableView<String>) data[4];

        Label avgLabel = (Label) data[5];
        Label sumLabel = (Label) data[6];
        Label maxLabel = (Label) data[7];
        Label minLabel = (Label) data[8];

        String summarySql =
                "SELECT " +
                        "(SELECT COUNT(*) FROM tracks WHERE is_active = 1) AS active_tracks, " +
                        "(SELECT COUNT(*) FROM customers) AS customers, " +
                        "(SELECT COUNT(*) FROM orders) AS orders, " +
                        "(SELECT COALESCE(SUM(total_amount), 0) FROM orders) AS total_sales";

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

    private void loadSalesReport(String reportType, TableView<String> reportTable,
                                 Label avgLabel, Label sumLabel, Label maxLabel, Label minLabel) {
        String sql;

        switch (reportType) {
            case "Sales by Genre":
                sql = "SELECT t.genre, SUM(oi.quantity), SUM(oi.line_total) " + "FROM order_items oi JOIN tracks t ON oi.track_id = t.id " + "GROUP BY t.genre ORDER BY 3 DESC";
                break;
            case "Sales by City":
                sql = "SELECT o.shipping_city, COUNT(o.id), SUM(o.total_amount) " + "FROM orders o GROUP BY o.shipping_city ORDER BY 3 DESC";
                break;
            case "Sales by Date":
                sql = "SELECT DATE(order_date), COUNT(id), SUM(total_amount) " + "FROM orders GROUP BY DATE(order_date) ORDER BY 1 DESC";
                break;
            default:
                reportTable.setItems(FXCollections.observableArrayList());
                avgLabel.setText("");
                sumLabel.setText("");
                maxLabel.setText("");
                minLabel.setText("");
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
            resultCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()));
            reportTable.getColumns().add(resultCol);
            reportTable.setItems(rows);

            if (count > 0) {
                avgLabel.setText(String.format("Average: %.2f", sum / count));
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
    }

    // Logout
    @FXML
    private void handleLogout() {
        SessionManager.clearCurrentUser();
        Stage stage = (Stage) tabPane.getScene().getWindow();

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

    // create a String column with a lambda for the cell value
    private <T> TableColumn<T, String> makeCol(String title,
                                               javafx.util.Callback<TableColumn.CellDataFeatures<T, String>, String> mapper) {
        TableColumn<T, String> col = new TableColumn<>(title);
        col.setCellValueFactory(d -> new SimpleStringProperty(mapper.call(d)));
        return col;
    }

    // standard GridPane used across dialogs
    private GridPane makeGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        return grid;
    }

    // standard 80x80 ImageView for track cover art
    private ImageView makeImageView() {
        ImageView iv = new ImageView();
        iv.setFitWidth(80);
        iv.setFitHeight(80);
        iv.setPreserveRatio(true);
        return iv;
    }

    // standard tab layout: heading on top, scrollable content in middle, buttons at bottom
    private VBox makeTabBox(Label heading, Control content, HBox buttons) {
        VBox.setVgrow(content, Priority.ALWAYS);
        VBox box = new VBox(10, heading, content, buttons);
        box.setPadding(new Insets(12));
        return box;
    }

    // show a dialog with a form grid and a Save/Cancel button pair
    private void showDialog(Stage dialog, GridPane grid, Button saveBtn) {
        Button cancelBtn = new Button("Cancel");
        cancelBtn.setOnAction(e -> dialog.close());

        HBox buttons = new HBox(10, saveBtn, cancelBtn);
        buttons.setAlignment(Pos.CENTER);

        dialog.setScene(new Scene(new VBox(10, grid, buttons)));
        dialog.showAndWait();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}