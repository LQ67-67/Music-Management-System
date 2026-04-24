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
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AdminMainController {

    private static final Logger LOGGER = Logger.getLogger(AdminMainController.class.getName());

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

    // Resource directories for uploaded files
    private static final String IMAGE_DIR = "src/main/resources/images/tracks/";
    private static final String AUDIO_DIR = "src/main/resources/audio/";

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

    // track tab
    private VBox buildTracksTabContent() {
        TableView<Track> trackTable = new TableView<>(trackData);
        trackTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<Track, Track> imageCol = new TableColumn<>("Image");
        imageCol.setPrefWidth(110);
        imageCol.setCellValueFactory(param -> new SimpleObjectProperty<>(param.getValue()));
        imageCol.setCellFactory(param -> new TableCell<>() {
            private final ImageView imageView = new ImageView();
            @Override
            protected void updateItem(Track item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); return; }
                imageView.setFitWidth(80); imageView.setFitHeight(80); imageView.setPreserveRatio(true);
                imageView.setImage(TrackMediaResolver.loadTrackImage(item, item.getId() + ".mp3"));
                setGraphic(imageView);
            }
        });

        TableColumn<Track, String> titleCol = new TableColumn<>("Title");
        titleCol.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().getTitle()));

        TableColumn<Track, String> artistCol = new TableColumn<>("Artist");
        artistCol.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().getArtist()));

        TableColumn<Track, String> albumCol = new TableColumn<>("Album");
        albumCol.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().getAlbum()));

        TableColumn<Track, String> genreCol = new TableColumn<>("Genre");
        genreCol.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().getGenre()));

        TableColumn<Track, String> priceCol = new TableColumn<>("Price");
        priceCol.setCellValueFactory(p -> {
            BigDecimal price = p.getValue().getPrice();
            return new SimpleStringProperty(price == null ? "" : price.toPlainString());
        });

        TableColumn<Track, String> stockCol = new TableColumn<>("Stock");
        stockCol.setCellValueFactory(p -> new SimpleStringProperty(String.valueOf(p.getValue().getStockQty())));

        trackTable.getColumns().addAll(imageCol, titleCol, artistCol, albumCol, genreCol, priceCol, stockCol);

        Button addBtn = new Button("Add Track");
        addBtn.setOnAction(e -> showTrackDialog(null));

        Button editBtn = new Button("Edit Track");
        editBtn.setOnAction(e -> {
            Track sel = trackTable.getSelectionModel().getSelectedItem();
            if (sel != null) showTrackDialog(sel);
            else showError("Please select a track from the table to edit.");
        });

        Button deleteBtn = new Button("Delete Track");
        deleteBtn.setOnAction(e -> {
            Track sel = trackTable.getSelectionModel().getSelectedItem();
            if (sel != null) deleteTrack(sel);
            else showError("Please select a track from the table to delete.");
        });

        Button exportBtn = new Button("Export Track List (TXT)");
        exportBtn.setOnAction(e -> exportTableToTxt("Tracks", new String[]{"ID", "Title", "Artist", "Album", "Genre", "Price", "Stock"},
                trackData.stream().map(t -> new String[]{
                        String.valueOf(t.getId()), t.getTitle(), t.getArtist(),
                        t.getAlbum(), t.getGenre(),
                        t.getPrice() != null ? t.getPrice().toPlainString() : "",
                        String.valueOf(t.getStockQty())
                }).collect(java.util.stream.Collectors.toList())));

        HBox buttonBox = new HBox(10, addBtn, editBtn, deleteBtn, exportBtn);
        VBox mainBox = new VBox(10, new Label("Track List"), trackTable, buttonBox);
        mainBox.setPadding(new Insets(12));
        VBox.setVgrow(trackTable, Priority.ALWAYS);
        return mainBox;
    }

    private void loadTracks() {
        try {
            trackData.setAll(trackDao.findAllActive());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to load tracks", e);
            showError("Failed to load tracks from database: " + e.getMessage());
        }
    }

     //Add/Edit track dialog — now includes image and audio file pickers.
    private void showTrackDialog(Track track) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(track == null ? "Add New Track" : "Edit Existing Track");

        TextField titleField = new TextField(track == null ? "" : track.getTitle());
        TextField artistField = new TextField(track == null ? "" : track.getArtist());
        TextField albumField = new TextField(track == null ? "" : track.getAlbum());
        TextField genreField = new TextField(track == null ? "" : track.getGenre());
        TextField priceField = new TextField(track == null ? "" : (track.getPrice() != null ? track.getPrice().toString() : ""));
        TextField stockField = new TextField(track == null ? "" : String.valueOf(track.getStockQty()));

        // image picker
        Label imagePathLabel = new Label("No image selected");
        imagePathLabel.setStyle("-fx-text-fill: grey;");
        ImageView previewImageView = new ImageView();
        previewImageView.setFitWidth(80); previewImageView.setFitHeight(80); previewImageView.setPreserveRatio(true);

        final File[] selectedImageFile = {null};
        Button browseImageBtn = new Button("Browse Image…");
        browseImageBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Select Cover Art");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.webp"));
            File chosen = fc.showOpenDialog(dialog);
            if (chosen != null) {
                selectedImageFile[0] = chosen;
                imagePathLabel.setText(chosen.getName());
                imagePathLabel.setStyle("-fx-text-fill: black;");
                try {
                    previewImageView.setImage(new Image(chosen.toURI().toString()));
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "Could not preview image", ex);
                }
            }
        });

        HBox imageRow = new HBox(8, browseImageBtn, imagePathLabel, previewImageView);
        imageRow.setAlignment(Pos.CENTER_LEFT);

        // audio picker
        Label audioPathLabel = new Label("No audio selected");
        audioPathLabel.setStyle("-fx-text-fill: grey;");

        final File[] selectedAudioFile = {null};
        Button browseAudioBtn = new Button("Browse Audio…");
        browseAudioBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Select Audio File");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Audio Files", "*.mp3", "*.wav", "*.aac", "*.flac", "*.ogg", "*.m4a"));
            File chosen = fc.showOpenDialog(dialog);
            if (chosen != null) {
                selectedAudioFile[0] = chosen;
                audioPathLabel.setText(chosen.getName());
                audioPathLabel.setStyle("-fx-text-fill: black;");
            }
        });

        HBox audioRow = new HBox(8, browseAudioBtn, audioPathLabel);
        audioRow.setAlignment(Pos.CENTER_LEFT);

        // form layout
        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10); grid.setPadding(new Insets(20));

        grid.add(new Label("Title:"), 0, 0);  grid.add(titleField, 1, 0);
        grid.add(new Label("Artist:"), 0, 1); grid.add(artistField, 1, 1);
        grid.add(new Label("Album:"), 0, 2);  grid.add(albumField, 1, 2);
        grid.add(new Label("Genre:"), 0, 3);  grid.add(genreField, 1, 3);
        grid.add(new Label("Price (RM):"), 0, 4); grid.add(priceField, 1, 4);
        grid.add(new Label("Stock Qty:"), 0, 5); grid.add(stockField, 1, 5);
        grid.add(new Label("Cover Art:"), 0, 6); grid.add(imageRow, 1, 6);
        grid.add(new Label("Audio File:"), 0, 7); grid.add(audioRow, 1, 7);

        Label validationLabel = new Label("");
        validationLabel.setStyle("-fx-text-fill: red;");

        Button saveBtn = new Button("Save");
        saveBtn.setOnAction(event -> {
            // input validation
            String titleVal = titleField.getText().trim();
            String artistVal = artistField.getText().trim();
            String priceStr = priceField.getText().trim();
            String stockStr = stockField.getText().trim();

            if (titleVal.isEmpty() || artistVal.isEmpty()) {
                validationLabel.setText("Title and Artist are required.");
                return;
            }

            BigDecimal price;
            try {
                price = new BigDecimal(priceStr);
                if (price.compareTo(BigDecimal.ZERO) <= 0) {
                    validationLabel.setText("Price must be a number greater than 0.");
                    return;
                }
            } catch (NumberFormatException ex) {
                validationLabel.setText("Price must be a valid number (e.g. 9.99).");
                return;
            }

            int stock;
            try {
                stock = Integer.parseInt(stockStr);
                if (stock <= 0) {
                    validationLabel.setText("Stock must be a positive integer.");
                    return;
                }
            } catch (NumberFormatException ex) {
                validationLabel.setText("Stock must be a positive integer (no decimals or letters).");
                return;
            }

            // save files if selected
            try {
                Track t = new Track();
                if (track != null) t.setId(track.getId());
                t.setTitle(titleVal);
                t.setArtist(artistVal);
                t.setAlbum(albumField.getText().trim());
                t.setGenre(genreField.getText().trim());
                t.setPrice(price);
                t.setStockQty(stock);

                int savedId;
                if (track == null) {
                    savedId = trackDao.create(t);
                } else {
                    trackDao.update(t);
                    savedId = track.getId();
                }

                // Copy image file to resources/images/tracks/<id>.ext
                if (selectedImageFile[0] != null) {
                    copyFileToResources(selectedImageFile[0], IMAGE_DIR, savedId + getExtension(selectedImageFile[0]));
                }
                // Copy audio file to resources/audio/<id>.ext
                if (selectedAudioFile[0] != null) {
                    copyFileToResources(selectedAudioFile[0], AUDIO_DIR, savedId + getExtension(selectedAudioFile[0]));
                }

                loadTracks();
                dialog.close();

            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Failed to save track", ex);
                validationLabel.setText("Error saving track: " + ex.getMessage());
            }
        });

        Button cancelBtn = new Button("Cancel");
        cancelBtn.setOnAction(e -> dialog.close());

        HBox buttonLayout = new HBox(10, saveBtn, cancelBtn);
        buttonLayout.setAlignment(Pos.CENTER);

        VBox mainLayout = new VBox(10, grid, validationLabel, buttonLayout);
        mainLayout.setPadding(new Insets(10));

        dialog.setScene(new Scene(new ScrollPane(mainLayout)));
        dialog.setWidth(540);
        dialog.setHeight(560);
        dialog.showAndWait();
    }

    // copy a file into a resource directory, creating it if needed.
    private void copyFileToResources(File source, String destDir, String destFilename) throws IOException {
        File dir = new File(destDir);
        if (!dir.exists()) dir.mkdirs();
        File dest = new File(dir, destFilename);
        Files.copy(source.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        LOGGER.info("Saved file: " + dest.getAbsolutePath());
    }

    private String getExtension(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot) : "";
    }

    private void deleteTrack(Track track) {
        try {
            trackDao.delete(track.getId());
            loadTracks();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to delete track", e);
            showError("Failed to delete track: " + e.getMessage());
        }
    }

    // customer tab
    private VBox buildCustomersTabContent() {
        TableView<Customer> customerTable = new TableView<>(customerData);
        customerTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<Customer, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().getName()));
        TableColumn<Customer, String> emailCol = new TableColumn<>("Email");
        emailCol.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().getEmail()));
        TableColumn<Customer, String> phoneCol = new TableColumn<>("Phone");
        phoneCol.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().getPhone()));
        TableColumn<Customer, String> cityCol = new TableColumn<>("City");
        cityCol.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().getCity()));

        customerTable.getColumns().addAll(nameCol, emailCol, phoneCol, cityCol);

        Button addBtn = new Button("Add Customer");
        addBtn.setOnAction(e -> showCustomerDialog(null));

        Button editBtn = new Button("Edit Customer");
        editBtn.setOnAction(e -> {
            Customer sel = customerTable.getSelectionModel().getSelectedItem();
            if (sel != null) showCustomerDialog(sel);
            else showError("Please select a customer to edit.");
        });

        Button deleteBtn = new Button("Delete Customer");
        deleteBtn.setOnAction(e -> {
            Customer sel = customerTable.getSelectionModel().getSelectedItem();
            if (sel != null) deleteCustomer(sel);
            else showError("Please select a customer to delete.");
        });

        Button exportBtn = new Button("Export Customer List (TXT)");
        exportBtn.setOnAction(e -> exportTableToTxt("Customers", new String[]{"ID", "Name", "Email", "Phone", "City"},
                customerData.stream().map(c -> new String[]{
                        String.valueOf(c.getId()), c.getName(),
                        c.getEmail(), c.getPhone(), c.getCity()
                }).collect(java.util.stream.Collectors.toList())));

        HBox buttonBox = new HBox(10, addBtn, editBtn, deleteBtn, exportBtn);
        VBox mainBox = new VBox(10, new Label("Customer List"), customerTable, buttonBox);
        mainBox.setPadding(new Insets(12));
        VBox.setVgrow(customerTable, Priority.ALWAYS);
        return mainBox;
    }

    private void loadCustomers() {
        try {
            customerData.setAll(customerDao.findAll());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to load customers", e);
            showError("Failed to load customers: " + e.getMessage());
        }
    }

    private void showCustomerDialog(Customer customer) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(customer == null ? "Add New Customer" : "Edit Customer");

        TextField nameField = new TextField(customer == null ? "" : customer.getName());
        TextField emailField = new TextField(customer == null ? "" : customer.getEmail());
        TextField phoneField = new TextField(customer == null ? "" : customer.getPhone());
        TextField cityField = new TextField(customer == null ? "" : customer.getCity());

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10); grid.setPadding(new Insets(20));
        grid.add(new Label("Name:"), 0, 0);  grid.add(nameField, 1, 0);
        grid.add(new Label("Email:"), 0, 1); grid.add(emailField, 1, 1);
        grid.add(new Label("Phone:"), 0, 2); grid.add(phoneField, 1, 2);
        grid.add(new Label("City:"), 0, 3);  grid.add(cityField, 1, 3);

        Label validationLabel = new Label("");
        validationLabel.setStyle("-fx-text-fill: red;");

        Button saveBtn = new Button("Save");
        saveBtn.setOnAction(e -> {
            String nameVal = nameField.getText().trim();
            String emailVal = emailField.getText().trim();
            String phoneVal = phoneField.getText().trim();

            if (nameVal.isEmpty()) { validationLabel.setText("Name is required."); return; }
            if (!emailVal.isEmpty() && !emailVal.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
                validationLabel.setText("Invalid email format."); return;
            }
            if (!phoneVal.isEmpty() && !phoneVal.matches("^[0-9+\\-() ]{6,20}$")) {
                validationLabel.setText("Invalid phone number format."); return;
            }

            try {
                Customer c = new Customer();
                if (customer != null) c.setId(customer.getId());
                c.setName(nameVal); c.setEmail(emailVal); c.setPhone(phoneVal); c.setCity(cityField.getText().trim());
                if (customer == null) customerDao.create(c);
                else customerDao.update(c);
                loadCustomers();
                dialog.close();
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Failed to save customer", ex);
                validationLabel.setText("Error: " + ex.getMessage());
            }
        });

        Button cancelBtn = new Button("Cancel");
        cancelBtn.setOnAction(e -> dialog.close());

        HBox buttonLayout = new HBox(10, saveBtn, cancelBtn);
        buttonLayout.setAlignment(Pos.CENTER);
        VBox mainLayout = new VBox(10, grid, validationLabel, buttonLayout);
        dialog.setScene(new Scene(mainLayout));
        dialog.showAndWait();
    }

    private void deleteCustomer(Customer customer) {
        try {
            customerDao.delete(customer.getId());
            loadCustomers();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to delete customer", e);
            showError("Failed to delete customer: " + e.getMessage());
        }
    }

    // orders tab
    private VBox buildOrdersTabContent() {
        TableView<String[]> orderTable = new TableView<>(orderData);
        orderTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        String[] headers = {"Order ID", "Username", "Customer Name", "Order Date", "Status", "Total", "Shipping City"};
        for (int i = 0; i < headers.length; i++) {
            final int idx = i;
            TableColumn<String[], String> col = new TableColumn<>(headers[i]);
            col.setCellValueFactory(p -> new SimpleStringProperty(p.getValue()[idx]));
            orderTable.getColumns().add(col);
        }

        Button editBtn = new Button("Edit Order");
        editBtn.setOnAction(e -> {
            String[] row = orderTable.getSelectionModel().getSelectedItem();
            if (row != null) showOrderEditDialog(row);
            else showError("Please select an order to edit.");
        });

        Button exportBtn = new Button("Export Order List (TXT)");
        exportBtn.setOnAction(e -> exportTableToTxt("Orders",
                new String[]{"Order ID", "Username", "Customer", "Date", "Status", "Total", "City"},
                new ArrayList<>(orderData)));

        HBox buttonBox = new HBox(10, editBtn, exportBtn);
        VBox mainBox = new VBox(10, new Label("All Orders"), orderTable, buttonBox);
        mainBox.setPadding(new Insets(12));
        VBox.setVgrow(orderTable, Priority.ALWAYS);
        return mainBox;
    }

    private void showOrderEditDialog(String[] row) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Edit Order #" + row[0]);

        ComboBox<String> statusBox = new ComboBox<>();
        statusBox.getItems().addAll("PENDING", "CONFIRMED", "PAID", "CANCELLED");
        statusBox.setValue(row[4]);

        TextField cityField = new TextField(row[6] == null ? "" : row[6]);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10); grid.setPadding(new Insets(20));
        grid.add(new Label("Status:"), 0, 0); grid.add(statusBox, 1, 0);
        grid.add(new Label("Shipping City:"), 0, 1); grid.add(cityField, 1, 1);

        Label validationLabel = new Label("");
        validationLabel.setStyle("-fx-text-fill: red;");

        Button saveBtn = new Button("Save");
        saveBtn.setOnAction(e -> {
            if (statusBox.getValue() == null) { validationLabel.setText("Please select a status."); return; }
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
                LOGGER.log(Level.SEVERE, "Failed to update order", ex);
                validationLabel.setText("Failed to update order: " + ex.getMessage());
            }
        });

        Button cancelBtn = new Button("Cancel");
        cancelBtn.setOnAction(e -> dialog.close());

        HBox buttonLayout = new HBox(10, saveBtn, cancelBtn);
        buttonLayout.setAlignment(Pos.CENTER);

        VBox mainLayout = new VBox(10, grid, validationLabel, buttonLayout);
        dialog.setScene(new Scene(mainLayout));
        dialog.showAndWait();
    }

    private void loadOrders() {
        String sql = "SELECT o.id, u.username, c.name, o.order_date, o.status, o.total_amount, o.shipping_city "
                + "FROM orders o JOIN users u ON o.user_id = u.id JOIN customers c ON o.customer_id = c.id "
                + "ORDER BY o.order_date DESC";
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
            LOGGER.log(Level.SEVERE, "Failed to load orders", e);
            showError("Failed to load orders: " + e.getMessage());
        }
    }

    // reports tab
    private final List<String[]> lastPieData = new ArrayList<>();
    private String lastPieTitle = "Sales by Genre";

    private VBox buildReportsTabContent() {
        Label activeTracksLabel  = new Label();
        Label customerCountLabel = new Label();
        Label orderCountLabel    = new Label();
        Label totalSalesLabel    = new Label();

        TableView<String> reportTable = new TableView<>();
        reportTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        reportTable.setPrefHeight(180);

        Label avgLabel = new Label(), sumLabel = new Label(), maxLabel = new Label(), minLabel = new Label();

        // Pie chart canvas
        Canvas pieCanvas = new Canvas(380, 260);

        ComboBox<String> reportType = new ComboBox<>();
        reportType.getItems().addAll("Summary", "Sales by Genre", "Sales by City", "Sales by Date");
        reportType.setValue("Summary");

        reportsTab.setUserData(new Object[]{
                activeTracksLabel, customerCountLabel, orderCountLabel, totalSalesLabel,
                reportTable, avgLabel, sumLabel, maxLabel, minLabel, pieCanvas
        });

        Button refreshBtn = new Button("Refresh Reports");
        refreshBtn.setOnAction(e -> refreshReport(reportType.getValue()));
        reportType.setOnAction(e -> refreshReport(reportType.getValue()));

        Button exportPieBtn = new Button("Export Chart Data (TXT)");
        exportPieBtn.setOnAction(e -> {
            if (lastPieData.isEmpty()) { showError("No chart data to export. Select a report first."); return; }
            exportTableToTxt(lastPieTitle, new String[]{"Category", "Value"}, lastPieData);
        });

        Button exportUserListBtn = new Button("Export User List (TXT)");
        exportUserListBtn.setOnAction(e -> exportUserList());

        HBox exportButtons = new HBox(10, exportPieBtn, exportUserListBtn);

        VBox box = new VBox(10);
        box.setPadding(new Insets(12));
        box.getChildren().addAll(
                new Label("System Summary"),
                activeTracksLabel, customerCountLabel, orderCountLabel, totalSalesLabel,
                new Separator(),
                new Label("Sales Reports"), reportType, reportTable,
                new Label("Statistics"), avgLabel, sumLabel, maxLabel, minLabel,
                new Separator(),
                new Label("Pie Chart"), pieCanvas,
                exportButtons,
                refreshBtn
        );
        return box;
    }

    private void refreshReport(String reportType) {
        Object[] data = (Object[]) reportsTab.getUserData();
        if (data == null || data.length < 10) return;

        Label activeTracksLabel  = (Label) data[0];
        Label customerCountLabel = (Label) data[1];
        Label orderCountLabel = (Label) data[2];
        Label totalSalesLabel = (Label) data[3];
        @SuppressWarnings("unchecked")
        TableView<String> reportTable = (TableView<String>) data[4];
        Label avgLabel = (Label) data[5], sumLabel = (Label) data[6],
                maxLabel = (Label) data[7], minLabel = (Label) data[8];
        Canvas pieCanvas = (Canvas) data[9];

        String summarySql = "SELECT "
                + "(SELECT COUNT(*) FROM tracks WHERE is_active = 1) AS active_tracks, " + "(SELECT COUNT(*) FROM customers) AS customers, "
                + "(SELECT COUNT(*) FROM orders) AS orders, " + "(SELECT COALESCE(SUM(total_amount), 0) FROM orders) AS total_sales";

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
            LOGGER.log(Level.SEVERE, "Failed to load report summary", e);
            showError("Failed to load report summary: " + e.getMessage());
            return;
        }

        loadSalesReport(reportType, reportTable, avgLabel, sumLabel, maxLabel, minLabel, pieCanvas);
    }

    // colours used for the pie chart slices
    private static final Color[] PIE_COLORS = {
            Color.web("#4e79a7"), Color.web("#f28e2b"), Color.web("#e15759"),
            Color.web("#76b7b2"), Color.web("#59a14f"), Color.web("#edc948"),
            Color.web("#b07aa1"), Color.web("#ff9da7"), Color.web("#9c755f")
    };

    private void loadSalesReport(String reportType, TableView<String> reportTable,
                                 Label avgLabel, Label sumLabel, Label maxLabel, Label minLabel,
                                 Canvas pieCanvas) {
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
                clearPieCanvas(pieCanvas);
                return;
        }

        try (Connection conn = DBConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            ObservableList<String> rows = FXCollections.observableArrayList();
            List<String> labels = new ArrayList<>();
            List<Double> values = new ArrayList<>();
            double sum = 0, max = Double.MIN_VALUE, min = Double.MAX_VALUE;
            int count = 0;

            lastPieData.clear();
            lastPieTitle = reportType;

            while (rs.next()) {
                double value = rs.getDouble(3);
                String label = rs.getString(1);
                sum += value; max = Math.max(max, value); min = Math.min(min, value); count++;
                rows.add(String.format("%s: %.2f", label, value));
                labels.add(label);
                values.add(value);
                lastPieData.add(new String[]{label, String.format("%.2f", value)});
            }

            reportTable.getColumns().clear();
            TableColumn<String, String> resultCol = new TableColumn<>("Result");
            resultCol.setCellValueFactory(p -> new SimpleStringProperty(p.getValue()));
            reportTable.getColumns().add(resultCol);
            reportTable.setItems(rows);

            if (count > 0) {
                avgLabel.setText(String.format("Average: %.2f", sum / count));
                sumLabel.setText(String.format("Total: %.2f", sum));
                maxLabel.setText(String.format("Maximum: %.2f", max));
                minLabel.setText(String.format("Minimum: %.2f", min));
                drawPieChart(pieCanvas, labels, values, reportType);
            } else {
                avgLabel.setText(""); sumLabel.setText(""); maxLabel.setText(""); minLabel.setText("");
                clearPieCanvas(pieCanvas);
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to load sales report", e);
            showError("Failed to load sales report: " + e.getMessage());
        }
    }

    // drawing pie chart with legend on the given Canvas.
    private void drawPieChart(Canvas canvas, List<String> labels, List<Double> values, String title) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        double total = values.stream().mapToDouble(Double::doubleValue).sum();
        if (total == 0) return;

        double cx = 120, cy = 130, r = 110;
        double startAngle = -90;

        // draw slices
        for (int i = 0; i < values.size(); i++) {
            double sweep = (values.get(i) / total) * 360.0;
            gc.setFill(PIE_COLORS[i % PIE_COLORS.length]);
            gc.fillArc(cx - r, cy - r, r * 2, r * 2, startAngle, sweep, javafx.scene.shape.ArcType.ROUND);
            // thin white border
            gc.setStroke(Color.WHITE); gc.setLineWidth(1.5);
            gc.strokeArc(cx - r, cy - r, r * 2, r * 2, startAngle, sweep, javafx.scene.shape.ArcType.ROUND);
            startAngle += sweep;
        }

        // draw legend on right side
        gc.setFont(Font.font("System", FontWeight.BOLD, 13));
        gc.setFill(Color.BLACK);
        gc.fillText(title, 250, 20);
        gc.setFont(Font.font("System", 11));

        for (int i = 0; i < labels.size(); i++) {
            double ly = 40 + i * 22;
            gc.setFill(PIE_COLORS[i % PIE_COLORS.length]);
            gc.fillRect(250, ly, 14, 14);
            gc.setFill(Color.BLACK);
            double pct = (values.get(i) / total) * 100;
            String lbl = labels.get(i);
            if (lbl == null || lbl.isEmpty()) lbl = "(None)";
            if (lbl.length() > 14) lbl = lbl.substring(0, 13) + "…";
            gc.fillText(String.format("%s (%.1f%%)", lbl, pct), 268, ly + 12);
        }
    }

    private void clearPieCanvas(Canvas canvas) {
        canvas.getGraphicsContext2D().clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
    }

    // txt reports
    private void exportTableToTxt(String tableName, String[] headers, List<String[]> rows) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Export " + tableName);
        fc.setInitialFileName(tableName.replace(" ", "_") + "_export.txt");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files (*.txt)", "*.txt"));
        File file = fc.showSaveDialog(tabPane.getScene().getWindow());
        if (file == null) return;

        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            pw.println("========================================");
            pw.println("  MUSIC LIBRARY – " + tableName.toUpperCase());
            pw.println("  Exported: " + timestamp);
            pw.println("========================================");
            pw.println();

            // compute column widths
            int[] widths = new int[headers.length];
            for (int i = 0; i < headers.length; i++) widths[i] = headers[i].length();
            for (String[] row : rows) {
                for (int i = 0; i < Math.min(row.length, headers.length); i++) {
                    if (row[i] != null) widths[i] = Math.max(widths[i], row[i].length());
                }
            }

            String separator = buildSeparator(widths);
            pw.println(separator);
            pw.println(buildRow(headers, widths));
            pw.println(separator);
            for (String[] row : rows) pw.println(buildRow(row, widths));
            pw.println(separator);
            pw.println();
            pw.println("Total records: " + rows.size());

            Alert alert = new Alert(Alert.AlertType.INFORMATION,
                    tableName + " exported successfully to:\n" + file.getAbsolutePath());
            alert.setHeaderText("Export Complete");
            alert.showAndWait();

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Export failed", e);
            showError("Export failed: " + e.getMessage());
        }
    }

    private String buildSeparator(int[] widths) {
        StringBuilder sb = new StringBuilder("+");
        for (int w : widths) { sb.append("-".repeat(w + 2)).append("+"); }
        return sb.toString();
    }

    private String buildRow(String[] cells, int[] widths) {
        StringBuilder sb = new StringBuilder("|");
        for (int i = 0; i < widths.length; i++) {
            String cell = (i < cells.length && cells[i] != null) ? cells[i] : "";
            sb.append(" ").append(String.format("%-" + widths[i] + "s", cell)).append(" |");
        }
        return sb.toString();
    }

    // Exports the users table from the database.
    private void exportUserList() {
        String sql = "SELECT id, username, role FROM users ORDER BY id";
        List<String[]> rows = new ArrayList<>();
        try (Connection conn = DBConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(new String[]{
                        String.valueOf(rs.getInt("id")),
                        rs.getString("username"),
                        rs.getString("role")
                });
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to fetch user list", e);
            showError("Failed to fetch user list: " + e.getMessage());
            return;
        }
        exportTableToTxt("Users", new String[]{"ID", "Username", "Role"}, rows);
    }

    // logout
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
            LOGGER.log(Level.SEVERE, "Failed to return to login screen", e);
            showError("Failed to return to login screen: " + e.getMessage());
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.setHeaderText("Admin Error");
        alert.showAndWait();
    }
}