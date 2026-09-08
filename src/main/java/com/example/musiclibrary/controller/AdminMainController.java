package com.example.musiclibrary.controller;

import com.example.musiclibrary.MusicLibraryApp;
import com.example.musiclibrary.dao.CustomerDao;
import com.example.musiclibrary.dao.OrderDao;
import com.example.musiclibrary.dao.TrackDao;
import com.example.musiclibrary.db.DBConnectionManager;
import com.example.musiclibrary.model.Customer;
import com.example.musiclibrary.model.Track;
import com.example.musiclibrary.service.ExportService;
import com.example.musiclibrary.session.SessionManager;
import com.example.musiclibrary.util.Async;
import com.example.musiclibrary.util.TrackMediaResolver;
import javafx.application.Platform;
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
import javafx.scene.text.Text;
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
import java.time.LocalDate;
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
    private DatePicker reportStartDatePicker;
    private DatePicker reportEndDatePicker;
    private Label reportRangeLabel;
    private ComboBox<String> reportGenreFilter;
    private TextField reportMinPriceField;
    private TextField reportMaxPriceField;

    private final ObservableList<Track> trackData = FXCollections.observableArrayList();
    private final ObservableList<Customer> customerData = FXCollections.observableArrayList();
    private final ObservableList<String[]> orderData = FXCollections.observableArrayList();

    private final TrackDao trackDao = new TrackDao();
    private final CustomerDao customerDao = new CustomerDao();
    private final OrderDao orderDao = new OrderDao();
    private final ExportService exportService = new ExportService();

    // resource directories for uploaded files
    private static final String IMAGE_DIR = "src/main/resources/images/tracks/";
    private static final String AUDIO_DIR = "src/main/resources/musics/";

    // FIX: flag to prevent re-entrant canvas redraws during layout
    private boolean redrawPending = false;

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

        // FIX: delay refreshReport until after layout is fully complete,
        // so the canvas resize listeners don't fire and clear the chart right after it's drawn
        Platform.runLater(() -> refreshReport("Summary"));
    }

    // track tab
    private VBox buildTracksTabContent() {
        TableView<Track> trackTable = new TableView<>(trackData);
        trackTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN); // let the last column take up remaining space
        trackTable.setPlaceholder(new Label("NO TRACKS // ADD ONE TO GET STARTED"));

        TableColumn<Track, Track> imageCol = new TableColumn<>("Image");
        imageCol.setPrefWidth(110);
        imageCol.setCellValueFactory(param -> new SimpleObjectProperty<>(param.getValue())); // pass the whole Track object to the cell factory so we can load the image based on track ID and filename
        imageCol.setCellFactory(param -> new TableCell<>() {
            private final ImageView imageView = new ImageView();
            @Override
            protected void updateItem(Track item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                imageView.setFitWidth(80); imageView.setFitHeight(80); imageView.setPreserveRatio(true);
                imageView.setImage(TrackMediaResolver.loadTrackImage(item, item.getId() + ".mp3")); // load image based on track ID and common audio extension
                setGraphic(imageView);
            }
        });

        TableColumn<Track, Integer> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(p -> new SimpleObjectProperty<>(p.getValue().getId()));
        idCol.setPrefWidth(50);

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
            return new SimpleStringProperty(price == null ? "" : price.toPlainString()); // show empty string if price is null, otherwise show the price as a string without scientific notation
        });

        TableColumn<Track, String> stockCol = new TableColumn<>("Stock");
        stockCol.setCellValueFactory(p -> new SimpleStringProperty(String.valueOf(p.getValue().getStockQty())));

        trackTable.getColumns().addAll(imageCol, idCol, titleCol, artistCol, albumCol, genreCol, priceCol, stockCol);

        Button addBtn = new Button("➕ ADD TRACK");
        addBtn.getStyleClass().add("neo-button-primary");
        addBtn.setOnAction(e -> showTrackDialog(null));

        Button editBtn = new Button("✏️ EDIT TRACK");
        editBtn.getStyleClass().add("neo-button-secondary");
        editBtn.setOnAction(e -> {
            Track sel = trackTable.getSelectionModel().getSelectedItem();
            if (sel != null){
                showTrackDialog(sel);
            } else{
                showError("Please select a track from the table to edit.");
            }
        });

        Button deleteBtn = new Button("🗑️ DELETE TRACK");
        deleteBtn.getStyleClass().add("neo-button-danger");
        deleteBtn.setOnAction(e -> {
            Track sel = trackTable.getSelectionModel().getSelectedItem();
            if (sel != null){
                deleteTrack(sel);
            } else{
                showError("Please select a track from the table to delete.");
            }
        });

        Button stockBtn = new Button("± STOCK");
        stockBtn.getStyleClass().add("neo-button-outline");
        stockBtn.setOnAction(e -> {
            Track sel = trackTable.getSelectionModel().getSelectedItem();
            if (sel != null) {
                showStockAdjustDialog(sel);
            } else {
                showError("Please select a track to adjust stock.");
            }
        });

        Button exportBtn = new Button("📥 EXPORT LIST");
        exportBtn.getStyleClass().add("neo-button-outline");
        exportBtn.setOnAction(e -> exportService.exportTableToTxt("Tracks", new String[]{"ID", "Title", "Artist", "Album", "Genre", "Price", "Stock"},
                trackData.stream().map(t -> new String[]{
                        String.valueOf(t.getId()), t.getTitle(), t.getArtist(),
                        t.getAlbum(), t.getGenre(),
                        t.getPrice() != null ? t.getPrice().toPlainString() : "",
                        String.valueOf(t.getStockQty())
                }).collect(java.util.stream.Collectors.toList()), tabPane.getScene().getWindow()));

        HBox buttonBox = new HBox(10, addBtn, editBtn, stockBtn, deleteBtn, exportBtn);
        VBox mainBox = new VBox(10, new Label("Track List"), trackTable, buttonBox);
        mainBox.setPadding(new Insets(12));
        VBox.setVgrow(trackTable, Priority.ALWAYS);
        return mainBox;
    }

    private void loadTracks() {
        Async.run("admin-load-tracks", trackDao::findAllActive, // only load active tracks for management
                trackData::setAll,
                ex -> showError("Failed to load tracks from database: " + ex.getMessage()));
    }

    // Add/Edit track dialog — includes image and audio file pickers.
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
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.webp")); // image formats
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

        Label audioPathLabel = new Label("No audio selected"); // audio picker
        audioPathLabel.setStyle("-fx-text-fill: grey;");

        final File[] selectedAudioFile = {null};
        Button browseAudioBtn = new Button("Browse Audio…");
        browseAudioBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Select Audio File");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Audio Files", "*.mp3", "*.wav", "*.aac", "*.flac", "*.ogg", "*.m4a")); // music formats
            File chosen = fc.showOpenDialog(dialog);
            if (chosen != null) {
                selectedAudioFile[0] = chosen;
                audioPathLabel.setText(chosen.getName());
                audioPathLabel.setStyle("-fx-text-fill: black;");
            }
        });

        HBox audioRow = new HBox(8, browseAudioBtn, audioPathLabel);
        audioRow.setAlignment(Pos.CENTER_LEFT);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10); grid.setPadding(new Insets(20)); // form layout
        grid.getStyleClass().add("dialog-form-grid");

        Label titleLabel = new Label("Title:");
        titleLabel.getStyleClass().add("dialog-form-label");
        grid.add(titleLabel, 0, 0);  grid.add(titleField, 1, 0);

        Label artistLabel = new Label("Artist:");
        artistLabel.getStyleClass().add("dialog-form-label");
        grid.add(artistLabel, 0, 1); grid.add(artistField, 1, 1);

        Label albumLabel = new Label("Album:");
        albumLabel.getStyleClass().add("dialog-form-label");
        grid.add(albumLabel, 0, 2);  grid.add(albumField, 1, 2);

        Label genreLabel = new Label("Genre:");
        genreLabel.getStyleClass().add("dialog-form-label");
        grid.add(genreLabel, 0, 3);  grid.add(genreField, 1, 3);

        Label priceLabel = new Label("Price (RM):");
        priceLabel.getStyleClass().add("dialog-form-label");
        grid.add(priceLabel, 0, 4); grid.add(priceField, 1, 4);

        Label stockLabel = new Label("Stock Qty:");
        stockLabel.getStyleClass().add("dialog-form-label");
        grid.add(stockLabel, 0, 5); grid.add(stockField, 1, 5);

        Label coverLabel = new Label("Cover Art:");
        coverLabel.getStyleClass().add("dialog-form-label");
        grid.add(coverLabel, 0, 6); grid.add(imageRow, 1, 6);

        Label audioLabel = new Label("Audio File:");
        audioLabel.getStyleClass().add("dialog-form-label");
        grid.add(audioLabel, 0, 7); grid.add(audioRow, 1, 7);

        Label validationLabel = new Label("");
        validationLabel.setStyle("-fx-text-fill: #FF6B6B; -fx-font-size: 13px; -fx-font-weight: 600; -fx-padding: 8 0 0 0;");
        validationLabel.setWrapText(true);

        Button saveBtn = new Button("💾 SAVE TRACK");
        saveBtn.getStyleClass().add("neo-button-primary");
        saveBtn.setOnAction(e -> {
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
                    validationLabel.setText("Price must be a number greater than 0."); // price must be greater than 0
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
                    validationLabel.setText("Stock must be a positive integer."); // same with stock
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
                t.setAlbum(albumField.getText().trim()); // trim is optional for album and genre since they can be empty, but we still want to remove extra spaces if user entered something
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

                if (selectedImageFile[0] != null) {
                    copyFileToResources(selectedImageFile[0], IMAGE_DIR, savedId + getExtension(selectedImageFile[0])); // copy image file to resources/images/tracks/<id>.ext
                }
                if (selectedAudioFile[0] != null) {
                    copyFileToResources(selectedAudioFile[0], AUDIO_DIR, savedId + getExtension(selectedAudioFile[0])); // Copy audio file to resources/audio/<id>.ext
                }
                loadTracks(); // refresh the track list after saving
                dialog.close();

            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Failed to save track", ex);
                validationLabel.setText("Error saving track: " + ex.getMessage());
            }
        });

        Button cancelBtn = new Button("❌ CANCEL");
        cancelBtn.getStyleClass().add("neo-button-outline");
        cancelBtn.setOnAction(e -> dialog.close());

        HBox buttonLayout = new HBox(10, saveBtn, cancelBtn);
        buttonLayout.setAlignment(Pos.CENTER);

        VBox mainLayout = new VBox(10, grid, validationLabel, buttonLayout);
        mainLayout.setPadding(new Insets(10));

        Scene scene = new Scene(new ScrollPane(mainLayout));
        scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
        dialog.setScene(scene);
        dialog.setWidth(620);
        dialog.setHeight(680);
        dialog.setMinWidth(560);
        dialog.setMinHeight(620);
        dialog.showAndWait();
    }

    // copy file into a resource directory, creating it if needed.
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

    // quick ± stock adjustment without opening the full track editor
    private void showStockAdjustDialog(Track track) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Adjust Stock — Track #" + track.getId());

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        grid.getStyleClass().add("dialog-form-grid");

        Label titleLabel = new Label(track.getTitle() + " — " + track.getArtist());
        titleLabel.setWrapText(true);
        titleLabel.setMaxWidth(320);

        Label currentLabel = new Label("Current stock: " + track.getStockQty());
        currentLabel.getStyleClass().add("dialog-form-label");

        Spinner<Integer> deltaSpinner = new Spinner<>(-1000, 1000, 10);
        deltaSpinner.setEditable(true);
        deltaSpinner.setPrefWidth(110);

        Label previewLabel = new Label();
        previewLabel.getStyleClass().add("body-semibold");
        Runnable updatePreview = () -> {
            int newStock = Math.max(0, track.getStockQty() + deltaSpinner.getValue());
            previewLabel.setText("New stock: " + newStock);
        };
        deltaSpinner.valueProperty().addListener((obs, o, n) -> updatePreview.run());
        updatePreview.run();

        grid.add(new Label("Track:"), 0, 0);
        grid.add(titleLabel, 1, 0);
        grid.add(currentLabel, 0, 1);
        grid.add(deltaSpinner, 1, 1);
        grid.add(new Label("Change by (±):"), 0, 2);
        grid.add(previewLabel, 1, 2);

        Label validationLabel = new Label();
        validationLabel.setStyle("-fx-text-fill: #FF6B6B; -fx-font-weight: 600;");

        Button applyBtn = new Button("APPLY →");
        applyBtn.getStyleClass().add("neo-button-primary");
        applyBtn.setOnAction(e -> {
            int newStock = track.getStockQty() + deltaSpinner.getValue();
            if (newStock < 0) {
                validationLabel.setText("Resulting stock cannot be negative.");
                return;
            }
            int delta = deltaSpinner.getValue();
            applyBtn.setDisable(true);
            Async.run("admin-adjust-stock", () -> {
                        trackDao.adjustStock(track.getId(), delta);
                        return true;
                    },
                    ok -> {
                        applyBtn.setDisable(false);
                        loadTracks();
                        dialog.close();
                    },
                    ex -> {
                        applyBtn.setDisable(false);
                        validationLabel.setText("Failed to adjust stock: " + ex.getMessage());
                    });
        });

        Button cancelBtn = new Button("❌ CANCEL");
        cancelBtn.getStyleClass().add("neo-button-outline");
        cancelBtn.setOnAction(e -> dialog.close());

        HBox buttonLayout = new HBox(10, applyBtn, cancelBtn);
        buttonLayout.setAlignment(Pos.CENTER);

        VBox mainLayout = new VBox(10, grid, validationLabel, buttonLayout);
        mainLayout.setPadding(new Insets(10));
        Scene scene = new Scene(mainLayout);
        scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
        dialog.setScene(scene);
        dialog.setWidth(480);
        dialog.setHeight(320);
        dialog.setMinWidth(440);
        dialog.setMinHeight(280);
        dialog.showAndWait();
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
        customerTable.setPlaceholder(new Label("NO CUSTOMERS YET"));

        TableColumn<Customer, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().getName()));
        TableColumn<Customer, String> emailCol = new TableColumn<>("Email");
        emailCol.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().getEmail()));
        TableColumn<Customer, String> phoneCol = new TableColumn<>("Phone");
        phoneCol.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().getPhone()));
        TableColumn<Customer, String> cityCol = new TableColumn<>("City");
        cityCol.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().getCity()));

        customerTable.getColumns().addAll(nameCol, emailCol, phoneCol, cityCol);

        Button addBtn = new Button("➕ ADD CUSTOMER");
        addBtn.getStyleClass().add("neo-button-primary");
        addBtn.setOnAction(e -> showCustomerDialog(null));

        Button editBtn = new Button("✏️ EDIT CUSTOMER");
        editBtn.getStyleClass().add("neo-button-secondary");
        editBtn.setOnAction(e -> {
            Customer sel = customerTable.getSelectionModel().getSelectedItem();
            if (sel != null){
                showCustomerDialog(sel);
            } else {
                showError("Please select a customer to edit.");
            }
        });

        Button deleteBtn = new Button("🗑️ DELETE CUSTOMER");
        deleteBtn.getStyleClass().add("neo-button-danger");
        deleteBtn.setOnAction(e -> {
            Customer sel = customerTable.getSelectionModel().getSelectedItem();
            if (sel != null){
                deleteCustomer(sel);
            } else{
                showError("Please select a customer to delete.");
            }
        });

        Button exportBtn = new Button("📥 EXPORT LIST");
        exportBtn.getStyleClass().add("neo-button-outline");
        exportBtn.setOnAction(e -> exportService.exportTableToTxt("Customers", new String[]{"ID", "Name", "Email", "Phone", "City"},
                customerData.stream().map(c -> new String[]{
                        String.valueOf(c.getId()), c.getName(), c.getEmail(), c.getPhone(), c.getCity()
                }).collect(java.util.stream.Collectors.toList()), tabPane.getScene().getWindow())); // export customer list to txt file with ID, name, email, phone and city columns

        HBox buttonBox = new HBox(10, addBtn, editBtn, deleteBtn, exportBtn);
        VBox mainBox = new VBox(10, new Label("Customer List"), customerTable, buttonBox);
        mainBox.setPadding(new Insets(12)); // add some padding around the edges
        VBox.setVgrow(customerTable, Priority.ALWAYS);
        return mainBox;
    }

    private void loadCustomers() {
        Async.run("admin-load-customers", customerDao::findAll, // load all customers for management (even those without orders)
                customerData::setAll,
                ex -> showError("Failed to load customers: " + ex.getMessage()));
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
        grid.getStyleClass().add("dialog-form-grid");

        Label nameLabel = new Label("Name:");
        nameLabel.getStyleClass().add("dialog-form-label");
        grid.add(nameLabel, 0, 0);  grid.add(nameField, 1, 0);

        Label emailLabel = new Label("Email:");
        emailLabel.getStyleClass().add("dialog-form-label");
        grid.add(emailLabel, 0, 1); grid.add(emailField, 1, 1);

        Label phoneLabel = new Label("Phone:");
        phoneLabel.getStyleClass().add("dialog-form-label");
        grid.add(phoneLabel, 0, 2); grid.add(phoneField, 1, 2);

        Label cityLabel = new Label("City:");
        cityLabel.getStyleClass().add("dialog-form-label");
        grid.add(cityLabel, 0, 3);  grid.add(cityField, 1, 3);

        Label validationLabel = new Label("");
        validationLabel.setStyle("-fx-text-fill: #FF6B6B; -fx-font-size: 13px; -fx-font-weight: 600; -fx-padding: 8 0 0 0;");
        validationLabel.setWrapText(true);

        Button saveBtn = new Button("💾 SAVE CUSTOMER");
        saveBtn.getStyleClass().add("neo-button-primary");
        saveBtn.setOnAction(e -> {
            String nameVal = nameField.getText().trim();
            String emailVal = emailField.getText().trim();
            String phoneVal = phoneField.getText().trim();

            if (nameVal.isEmpty()) {
                validationLabel.setText("Name is required.");
                return;
            }
            if (!emailVal.isEmpty() && !emailVal.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) { // email format validation
                validationLabel.setText("Invalid email format.");
                return;
            }
            if (!phoneVal.isEmpty() && !phoneVal.matches("^[0-9+\\-() ]{6,20}$")) { // allow digits, spaces, parentheses, plus and hyphens, with length between 6 and 20 characters
                validationLabel.setText("Invalid phone number format.");
                return;
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

        Button cancelBtn = new Button("❌ CANCEL");
        cancelBtn.getStyleClass().add("neo-button-outline");
        cancelBtn.setOnAction(e -> dialog.close());

        HBox buttonLayout = new HBox(10, saveBtn, cancelBtn);
        buttonLayout.setAlignment(Pos.CENTER);
        VBox mainLayout = new VBox(10, grid, validationLabel, buttonLayout);
        mainLayout.setPadding(new Insets(10));
        Scene scene = new Scene(mainLayout);
        scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
        dialog.setScene(scene);
        dialog.setWidth(560);
        dialog.setHeight(420);
        dialog.setMinWidth(520);
        dialog.setMinHeight(360);
        dialog.showAndWait();
    }

    private void deleteCustomer(Customer customer) {
        try {
            customerDao.delete(customer.getId()); // deleting a customer will also delete all their orders in database
            loadCustomers();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to delete customer", e);
            showError("Failed to delete customer: " + e.getMessage());
        }
    }

    // orders tab
    private VBox buildOrdersTabContent() {
        TableView<String[]> orderTable = new TableView<>(orderData);
        orderTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN); // let the last column take up remaining space
        orderTable.setPlaceholder(new Label("NO ORDERS YET"));

        String[] headers = {"Order ID", "Username", "Customer Name", "Order Date", "Status", "Total", "Shipping City"};
        for (int i = 0; i < headers.length; i++) { // use loop to create columns based on the headers array
            final int idx = i;
            TableColumn<String[], String> col = new TableColumn<>(headers[i]);
            col.setCellValueFactory(p -> new SimpleStringProperty(p.getValue()[idx]));
            if (i == 0 || i == 5) { // order ID (index 0) and total (index 5) sort numerically
                col.setComparator((a, b) -> {
                    try {
                        return Double.compare(Double.parseDouble(a), Double.parseDouble(b));
                    }
                    catch (NumberFormatException e) {
                        return a.compareTo(b);
                    }
                });
            }
            orderTable.getColumns().add(col);
        }

        Button editBtn = new Button("✏️ EDIT ORDER");
        editBtn.getStyleClass().add("neo-button-secondary");
        editBtn.setOnAction(e -> {
            String[] row = orderTable.getSelectionModel().getSelectedItem();
            if (row != null){
                showOrderEditDialog(row);
            } else {
                showError("Please select an order to edit.");
            }
        });

        Button exportBtn = new Button("📥 EXPORT LIST");
        exportBtn.getStyleClass().add("neo-button-outline");
        exportBtn.setOnAction(e -> exportService.exportTableToTxt("Orders", new String[]{"Order ID", "Username", "Customer", "Date", "Status", "Total", "City"}, new ArrayList<>(orderData), tabPane.getScene().getWindow())); // export order list to txt

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
        grid.getStyleClass().add("dialog-form-grid");

        Label statusLabel = new Label("Status:");
        statusLabel.getStyleClass().add("dialog-form-label");
        grid.add(statusLabel, 0, 0); grid.add(statusBox, 1, 0);

        Label cityLabel = new Label("Shipping City:");
        cityLabel.getStyleClass().add("dialog-form-label");
        grid.add(cityLabel, 0, 1); grid.add(cityField, 1, 1);

        Label validationLabel = new Label("");
        validationLabel.setStyle("-fx-text-fill: #FF6B6B; -fx-font-size: 13px; -fx-font-weight: 600; -fx-padding: 8 0 0 0;");
        validationLabel.setWrapText(true);

        Button saveBtn = new Button("💾 SAVE ORDER");
        saveBtn.getStyleClass().add("neo-button-primary");
        saveBtn.setOnAction(e -> {
            if (statusBox.getValue() == null) {
                validationLabel.setText("Please select a status.");  // check if status is null before saving
                return;
            }
            int orderId = Integer.parseInt(row[0]);
            String newStatus = statusBox.getValue();
            String newCity = cityField.getText();
            saveBtn.setDisable(true);
            Async.run("admin-update-order", () -> {
                        orderDao.updateStatusAndCity(orderId, newStatus, newCity);
                        return true;
                    },
                    ok -> {
                        saveBtn.setDisable(false);
                        loadOrders();
                        dialog.close();
                    },
                    ex -> {
                        saveBtn.setDisable(false);
                        validationLabel.setText("Failed to update order: " + ex.getMessage());
                    });
        });

        Button cancelBtn = new Button("❌ CANCEL");
        cancelBtn.getStyleClass().add("neo-button-outline");
        cancelBtn.setOnAction(e -> dialog.close());

        HBox buttonLayout = new HBox(10, saveBtn, cancelBtn);
        buttonLayout.setAlignment(Pos.CENTER);

        VBox mainLayout = new VBox(10, grid, validationLabel, buttonLayout);
        mainLayout.setPadding(new Insets(10));
        Scene scene = new Scene(mainLayout);
        scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
        dialog.setScene(scene);
        dialog.setWidth(520);
        dialog.setHeight(360);
        dialog.setMinWidth(500);
        dialog.setMinHeight(320);
        dialog.showAndWait();
    }

    private void loadOrders() {
        Async.run("admin-load-orders", orderDao::findOrderSummaries,
                orderData::setAll,
                ex -> showError("Failed to load orders: " + ex.getMessage()));
    }

    // reports tab
    private final List<String[]> lastPieData = new ArrayList<>();
    private String lastPieTitle = "Sales by Genre";
    private final List<String> lastChartLabels = new ArrayList<>();
    private final List<Double> lastChartValues = new ArrayList<>();
    private String lastChartType = "";

    private VBox buildReportsTabContent() {
        // initialize all components
        Label activeTracksLabel = new Label();
        Label customerCountLabel = new Label();
        Label orderCountLabel = new Label();
        Label totalSalesLabel = new Label();

        TableView<String> reportTable = new TableView<>();
        reportTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        reportTable.setPlaceholder(new Label("RUN A REPORT TO SEE RESULTS"));
        reportTable.setPrefHeight(220);
        reportTable.setMinHeight(150);

        Label avgLabel = new Label(), sumLabel = new Label(), maxLabel = new Label(), minLabel = new Label();

        Canvas pieCanvas = new Canvas(620, 320);

        Pane canvasPane = new Pane(pieCanvas);
        pieCanvas.widthProperty().bind(canvasPane.widthProperty());
        pieCanvas.heightProperty().bind(canvasPane.heightProperty());

        pieCanvas.widthProperty().addListener((obs, oldVal, newVal) -> scheduleRedraw(pieCanvas));
        pieCanvas.heightProperty().addListener((obs, oldVal, newVal) -> scheduleRedraw(pieCanvas));

        ComboBox<String> reportType = new ComboBox<>();
        reportType.getItems().addAll("Summary", "Sales by Genre", "Sales by City", "Sales by Date");
        reportType.setValue("Summary");

        reportStartDatePicker = new DatePicker();
        reportStartDatePicker.setPromptText("Start date");
        reportEndDatePicker = new DatePicker();
        reportEndDatePicker.setPromptText("End date");
        reportRangeLabel = new Label("Showing all dates");
        reportRangeLabel.getStyleClass().add("caption");

        // category (genre) and price range filters; they apply to the "Sales by Genre" report
        reportGenreFilter = new ComboBox<>();
        reportGenreFilter.getItems().add("All Genres");
        reportGenreFilter.setValue("All Genres");
        reportGenreFilter.setPrefWidth(150);
        reportGenreFilter.getItems().addAll(loadAvailableGenres());

        reportMinPriceField = new TextField();
        reportMinPriceField.setPromptText("Min RM");
        reportMinPriceField.setPrefWidth(80);
        reportMinPriceField.setTextFormatter(new TextFormatter<>(change ->
                change.getControlNewText().matches("\\d{0,8}(\\.\\d{0,2})?") ? change : null));

        reportMaxPriceField = new TextField();
        reportMaxPriceField.setPromptText("Max RM");
        reportMaxPriceField.setPrefWidth(80);
        reportMaxPriceField.setTextFormatter(new TextFormatter<>(change ->
                change.getControlNewText().matches("\\d{0,8}(\\.\\d{0,2})?") ? change : null));

        Label genrePriceHint = new Label("Genre & price filters apply to 'Sales by Genre'");
        genrePriceHint.getStyleClass().add("caption");

        reportsTab.setUserData(new Object[]{activeTracksLabel, customerCountLabel, orderCountLabel, totalSalesLabel, reportTable, avgLabel, sumLabel, maxLabel, minLabel, pieCanvas, reportStartDatePicker, reportEndDatePicker, reportRangeLabel, reportGenreFilter, reportMinPriceField, reportMaxPriceField});

        Button refreshBtn = new Button("🔄 REFRESH REPORTS");
        refreshBtn.getStyleClass().add("neo-button-primary");
        refreshBtn.setOnAction(e -> refreshReport(reportType.getValue()));
        reportType.setOnAction(e -> refreshReport(reportType.getValue()));
        reportGenreFilter.setOnAction(e -> {
            if ("Sales by Genre".equals(reportType.getValue())) {
                refreshReport(reportType.getValue());
            }
        });
        reportMinPriceField.setOnAction(e -> {
            if ("Sales by Genre".equals(reportType.getValue())) {
                refreshReport(reportType.getValue());
            }
        });
        reportMaxPriceField.setOnAction(e -> {
            if ("Sales by Genre".equals(reportType.getValue())) {
                refreshReport(reportType.getValue());
            }
        });

        Button exportPieBtn = new Button("📊 EXPORT CHART DATA");
        exportPieBtn.getStyleClass().add("neo-button-secondary");
        exportPieBtn.setOnAction(e -> {
            if (lastPieData.isEmpty()) { showError("No chart data to export. Select a report first."); return; }
            exportService.exportTableToTxt(lastPieTitle, new String[]{"Category", "Value"}, lastPieData, tabPane.getScene().getWindow());
        });

        Button exportUserListBtn = new Button("👥 EXPORT USERS");
        exportUserListBtn.getStyleClass().add("neo-button-outline");
        exportUserListBtn.setOnAction(e -> exportService.exportUserList(tabPane.getScene().getWindow()));

        Button clearRangeBtn = new Button("❌ CLEAR RANGE");
        clearRangeBtn.getStyleClass().add("neo-button-ghost");
        clearRangeBtn.setOnAction(e -> {
            reportStartDatePicker.setValue(null);
            reportEndDatePicker.setValue(null);
            refreshReport(reportType.getValue());
        });

        // left column: flexible report visual area
        VBox leftColumn = new VBox(15);
        leftColumn.setPadding(new Insets(10));
        leftColumn.setMinWidth(520);

        // top left: dropdown menu
        HBox controlsBox = new HBox(10, new Label("Select Report:"), reportType);
        controlsBox.setAlignment(Pos.CENTER_LEFT);
        controlsBox.getStyleClass().add("report-filter-bar");

        HBox rangeBox = new HBox(10, new Label("Start:"), reportStartDatePicker, new Label("End:"), reportEndDatePicker, clearRangeBtn);
        rangeBox.setAlignment(Pos.CENTER_LEFT);
        rangeBox.getStyleClass().add("report-filter-bar");

        HBox genrePriceBox = new HBox(10,
                new Label("Genre:"), reportGenreFilter,
                new Label("Price from"), reportMinPriceField, new Label("to"), reportMaxPriceField, genrePriceHint);
        genrePriceBox.setAlignment(Pos.CENTER_LEFT);
        genrePriceBox.getStyleClass().add("report-filter-bar");

        // middle-top left: chart area
        VBox chartCard = new VBox(10, new Label("📊 Sales Chart"), canvasPane);
        VBox.setVgrow(canvasPane, Priority.ALWAYS);  // Let the canvasPane fill the remaining space of chartCard
        chartCard.getStyleClass().add("report-panel");
        chartCard.getStyleClass().add("report-chart-container");
        chartCard.setAlignment(Pos.CENTER);
        chartCard.setPrefHeight(420);
        chartCard.setMinHeight(320);
        // cap the chart container size to avoid propagating extremely large layout sizes to the Canvas
        chartCard.setMaxWidth(MAX_CANVAS_DIM);
        chartCard.setMaxHeight(MAX_CANVAS_DIM);

        // bottom left: table area
        VBox tableCard = new VBox(10, new Label("📋 Report Table"), reportTable);
        tableCard.getStyleClass().add("report-panel");
        tableCard.getStyleClass().add("report-table-container");
        VBox.setVgrow(reportTable, Priority.ALWAYS);
        VBox.setVgrow(tableCard, Priority.ALWAYS);
        tableCard.setPrefHeight(240);
        tableCard.setMinHeight(190);

        VBox.setVgrow(chartCard, Priority.ALWAYS);

        leftColumn.getChildren().addAll(controlsBox, rangeBox, genrePriceBox, reportRangeLabel, chartCard, tableCard);

        // right column: metrics and report actions
        VBox rightColumn = new VBox(20);
        rightColumn.setPadding(new Insets(10));
        rightColumn.setPrefWidth(280);
        rightColumn.setMinWidth(250);

        // top right: summary card
        VBox summaryCard = new VBox(12,
                new Label("📌 Summary Card"),
                new Separator(),
                activeTracksLabel,
                customerCountLabel,
                orderCountLabel,
                totalSalesLabel
        );
        summaryCard.getStyleClass().add("report-metric-card");

        // middle right: statistics card
        VBox statsCard = new VBox(12, new Label("📈 Statistics"), new Separator(), avgLabel, sumLabel, maxLabel, minLabel);
        statsCard.getStyleClass().add("report-metric-card");

        // bottom right: action buttons
        VBox actionCard = new VBox(10, refreshBtn, exportPieBtn, exportUserListBtn);
        actionCard.setAlignment(Pos.CENTER_LEFT);
        actionCard.getStyleClass().add("report-panel");

        rightColumn.getChildren().addAll(summaryCard, statsCard, actionCard);

        SplitPane splitPane = new SplitPane(leftColumn, rightColumn);
        splitPane.setDividerPositions(0.72);
        splitPane.setPadding(new Insets(15));
        splitPane.setOrientation(javafx.geometry.Orientation.HORIZONTAL);
        splitPane.setPrefHeight(760);

        ScrollPane reportScrollPane = new ScrollPane(splitPane);
        reportScrollPane.setFitToWidth(true);
        reportScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        reportScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        reportScrollPane.getStyleClass().add("report-scroll-pane");

        // return the outermost VBox
        VBox mainBox = new VBox(reportScrollPane);
        VBox.setVgrow(reportScrollPane, Priority.ALWAYS);
        return mainBox;
    }

    // FIX: coalesce rapid resize events into a single redraw on the next JavaFX pulse.
    // Without this, each pixel of resize triggers a separate clearRect + redraw which
    // can race with refreshReport and leave the canvas blank.
    private void scheduleRedraw(Canvas canvas) {
        if (!redrawPending) {
            redrawPending = true;
            Platform.runLater(() -> {
                redrawPending = false;
                redrawCachedPieChart(canvas);
            });
        }
    }

    private void refreshReport(String reportType) {
        Object[] data = (Object[]) reportsTab.getUserData();
        if (data == null || data.length < 16){
            return;
        }

        Label activeTracksLabel = (Label) data[0];
        Label customerCountLabel = (Label) data[1];
        Label orderCountLabel = (Label) data[2];
        Label totalSalesLabel = (Label) data[3];
        @SuppressWarnings("unchecked")
        TableView<String> reportTable = (TableView<String>) data[4];
        Label avgLabel = (Label) data[5], sumLabel = (Label) data[6], maxLabel = (Label) data[7], minLabel = (Label) data[8];
        Canvas pieCanvas = (Canvas) data[9];
        DatePicker startPicker = (DatePicker) data[10];
        DatePicker endPicker = (DatePicker) data[11];
        Label rangeLabel = (Label) data[12];
        @SuppressWarnings("unchecked")
        ComboBox<String> genreFilter = (ComboBox<String>) data[13];
        TextField minPriceField = (TextField) data[14];
        TextField maxPriceField = (TextField) data[15];

        LocalDate startDate = startPicker.getValue();
        LocalDate endDate = endPicker.getValue();

        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            showError("Start date must be on or before end date.");
            return;
        }

        // genre/price filters only make sense for the genre breakdown, so dim them elsewhere
        boolean genreFilterApplicable = "Sales by Genre".equals(reportType);
        genreFilter.setDisable(!genreFilterApplicable);
        minPriceField.setDisable(!genreFilterApplicable);
        maxPriceField.setDisable(!genreFilterApplicable);

        BigDecimal minPrice = parsePriceBound(minPriceField.getText(), "Min price");
        if (minPrice == null && minPriceField.getText() != null && !minPriceField.getText().trim().isEmpty()) {
            return; // invalid input already reported by parsePriceBound
        }
        BigDecimal maxPrice = parsePriceBound(maxPriceField.getText(), "Max price");
        if (maxPrice == null && maxPriceField.getText() != null && !maxPriceField.getText().trim().isEmpty()) {
            return;
        }
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            showError("Min price must be less than or equal to max price.");
            return;
        }

        String selectedGenre = genreFilterApplicable && genreFilter.getValue() != null
                && !"All Genres".equals(genreFilter.getValue()) ? genreFilter.getValue() : null;

        if (startDate != null || endDate != null) {
            rangeLabel.setText("Date range: " + (startDate != null ? startDate : "…") + " to " + (endDate != null ? endDate : "…"));
        } else {
            rangeLabel.setText("Showing all dates");
        }

        String orderDateClause = buildOrderDateClause("o.order_date", startDate, endDate);
        String summarySql = "SELECT "
                + "(SELECT COUNT(*) FROM tracks WHERE is_active = 1) AS active_tracks, "
                + "(SELECT COUNT(*) FROM customers) AS customers, "
                + "(SELECT COUNT(*) FROM orders o WHERE 1=1" + orderDateClause + ") AS orders, "
                + "(SELECT COALESCE(SUM(total_amount), 0) FROM orders o WHERE 1=1" + orderDateClause + ") AS total_sales";

        record ReportSummary(int activeTracks, int customers, int orders, BigDecimal totalSales) {
        }
        Async.run("report-summary", () -> {
            try (Connection conn = DBConnectionManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(summarySql)) {
                int bindIndex = 1;
                bindIndex = bindOrderDateClause(ps, bindIndex, startDate, endDate);
                bindOrderDateClause(ps, bindIndex, startDate, endDate);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next()
                            ? new ReportSummary(rs.getInt("active_tracks"), rs.getInt("customers"),
                            rs.getInt("orders"), rs.getBigDecimal("total_sales"))
                            : null;
                }
            }
        },
                summary -> {
                    if (summary != null) {
                        activeTracksLabel.setText("Active Tracks: " + summary.activeTracks());
                        customerCountLabel.setText("Customers: " + summary.customers());
                        orderCountLabel.setText("Orders: " + summary.orders());
                        totalSalesLabel.setText("Total Sales: " + summary.totalSales());
                    }
                },
                ex -> showError("Failed to load report summary: " + ex.getMessage()));

        loadSalesReport(reportType, reportTable, avgLabel, sumLabel, maxLabel, minLabel, pieCanvas, startDate, endDate, selectedGenre, minPrice, maxPrice);
    }

    // parse a price-range field; returns null for empty input or on error (with a message shown)
    private BigDecimal parsePriceBound(String text, String fieldName) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(text.trim());
            if (value.compareTo(BigDecimal.ZERO) < 0) {
                showError(fieldName + " cannot be negative.");
                return null;
            }
            return value;
        } catch (NumberFormatException e) {
            showError(fieldName + " must be a valid number (e.g. 9.99).");
            return null;
        }
    }

    // distinct genres of active tracks, used to populate the report genre filter
    private List<String> loadAvailableGenres() {
        String sql = "SELECT DISTINCT genre FROM tracks WHERE is_active = 1 AND genre IS NOT NULL AND genre <> '' ORDER BY genre";
        List<String> genres = new ArrayList<>();
        try (Connection conn = DBConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                genres.add(rs.getString("genre"));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to load genres for report filter", e);
        }
        return genres;
    }

    // colours used for the pie chart slices
    private static final Color[] PIE_COLORS = {
            Color.web("#4e79a7"), Color.web("#f28e2b"), Color.web("#e15759"),
            Color.web("#76b7b2"), Color.web("#59a14f"), Color.web("#edc948"),
            Color.web("#b07aa1"), Color.web("#ff9da7"), Color.web("#9c755f")
    };

    // Maximum canvas texture dimension to avoid exceeding GPU limits (some GPUs cap at 16384)
    private static final double MAX_CANVAS_DIM = 16000.0;

    private void loadSalesReport(String reportType, TableView<String> reportTable, Label avgLabel, Label sumLabel, Label maxLabel, Label minLabel, Canvas pieCanvas, LocalDate startDate, LocalDate endDate, String genreFilter, BigDecimal minPrice, BigDecimal maxPrice) {
        String sql;
        boolean bindGenrePrice = false;
        switch (reportType) {
            case "Sales by Genre":
                // optional track-level filters: genre (category) and price range
                sql = "SELECT t.genre, SUM(oi.quantity), SUM(oi.line_total) "
                        + "FROM order_items oi JOIN tracks t ON oi.track_id = t.id "
                        + "JOIN orders o ON oi.order_id = o.id WHERE 1=1" + buildOrderDateClause("o.order_date", startDate, endDate)
                        + (genreFilter != null ? " AND t.genre = ?" : "")
                        + (minPrice != null ? " AND t.price >= ?" : "")
                        + (maxPrice != null ? " AND t.price <= ?" : "")
                        + " GROUP BY t.genre ORDER BY 3 DESC";
                bindGenrePrice = true;
                break;
            case "Sales by City":
                sql = "SELECT o.shipping_city, COUNT(o.id), SUM(o.total_amount) FROM orders o WHERE 1=1"
                        + buildOrderDateClause("o.order_date", startDate, endDate)
                        + " GROUP BY o.shipping_city ORDER BY 3 DESC";
                break;
            case "Sales by Date":
                sql = "SELECT DATE(order_date), COUNT(id), SUM(total_amount) FROM orders o WHERE 1=1"
                        + buildOrderDateClause("o.order_date", startDate, endDate)
                        + " GROUP BY DATE(order_date) ORDER BY 1 DESC";
                break;
            default:
                // FIX: for "Summary" (and any other non-chart report type), clear the table and
                // stats labels but do NOT wipe the canvas — if chart data exists from a previous
                // selection, keep it visible. Only clear the canvas when there is genuinely nothing
                // to show (i.e. lastChartLabels is already empty).
                reportTable.setItems(FXCollections.observableArrayList());
                avgLabel.setText(""); sumLabel.setText(""); maxLabel.setText(""); minLabel.setText("");
                if (lastChartLabels.isEmpty()) {
                    clearPieCanvas(pieCanvas);
                }
                return;
        }

        record SalesData(List<String[]> pieRows, List<String> labels, List<Double> values) {
        }
        final boolean withGenrePrice = bindGenrePrice;
        Async.run("report-sales-" + reportType, () -> {
            List<String[]> pieRows = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            List<Double> values = new ArrayList<>();
            try (Connection conn = DBConnectionManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                int bindIndex = bindOrderDateClause(ps, 1, startDate, endDate);
                if (withGenrePrice) {
                    if (genreFilter != null) {
                        ps.setString(bindIndex++, genreFilter);
                    }
                    if (minPrice != null) {
                        ps.setBigDecimal(bindIndex++, minPrice);
                    }
                    if (maxPrice != null) {
                        ps.setBigDecimal(bindIndex++, maxPrice);
                    }
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        double value = rs.getDouble(3);
                        String label = rs.getString(1);
                        pieRows.add(new String[]{label, String.format("%.2f", value)});
                        labels.add(label);
                        values.add(value);
                    }
                }
            }
            return new SalesData(pieRows, labels, values);
        },
                data -> {
                    lastPieData.clear();
                    lastPieData.addAll(data.pieRows());
                    lastPieTitle = reportType;

                    reportTable.getColumns().clear();
                    TableColumn<String, String> resultCol = new TableColumn<>("Result");
                    resultCol.setCellValueFactory(p -> new SimpleStringProperty(p.getValue())); // single column with the formatted string result
                    reportTable.getColumns().add(resultCol);

                    ObservableList<String> rows = FXCollections.observableArrayList();
                    for (String[] pieRow : data.pieRows()) {
                        rows.add(pieRow[0] + ": " + pieRow[1]);
                    }
                    reportTable.setItems(rows);

                    double sum = 0, max = Double.MIN_VALUE, min = Double.MAX_VALUE;
                    for (double value : data.values()) {
                        sum += value;
                        max = Math.max(max, value);
                        min = Math.min(min, value);
                    }
                    int count = data.values().size();

                    if (count > 0) {
                        avgLabel.setText(String.format("Average: %.2f", sum / count));
                        sumLabel.setText(String.format("Total: %.2f", sum));
                        maxLabel.setText(String.format("Maximum: %.2f", max));
                        minLabel.setText(String.format("Minimum: %.2f", min));
                        lastChartLabels.clear();
                        lastChartLabels.addAll(data.labels());
                        lastChartValues.clear();
                        lastChartValues.addAll(data.values());
                        lastChartType = reportType;
                        if ("Sales by Date".equals(reportType)) {
                            drawLineChart(pieCanvas, data.labels(), data.values(), reportType);
                        } else {
                            drawPieChart(pieCanvas, data.labels(), data.values(), reportType);
                        }
                    } else {
                        // no data returned — clear everything including the canvas
                        avgLabel.setText(""); sumLabel.setText(""); maxLabel.setText(""); minLabel.setText("");
                        lastChartLabels.clear();
                        lastChartValues.clear();
                        lastChartType = "";
                        clearPieCanvas(pieCanvas);
                    }
                },
                ex -> showError("Failed to load sales report: " + ex.getMessage()));
    }

    // drawing pie chart with legend on the given Canvas.
    private void drawPieChart(Canvas canvas, List<String> labels, List<Double> values, String title) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double width = canvas.getWidth();
        double height = canvas.getHeight();
        // Use clamped drawing dimensions to avoid arithmetic or texture allocation issues
        double drawWidth = Math.min(width, MAX_CANVAS_DIM);
        double drawHeight = Math.min(height, MAX_CANVAS_DIM);
        gc.clearRect(0, 0, drawWidth, drawHeight);

        double total = values.stream().mapToDouble(Double::doubleValue).sum();
        if (labels == null || labels.isEmpty() || total <= 0) {
            // draw a clear "no data" message so it's obvious why nothing appears
            try {
                gc.setFill(Color.web("#0A0A0A"));
                gc.fillRect(0, 0, width, height);
                gc.setFill(Color.web("#8A8A8A"));
                gc.setFont(Font.font("System", FontWeight.BOLD, 14));
                gc.setTextAlign(javafx.scene.text.TextAlignment.CENTER);
                gc.setTextBaseline(javafx.geometry.VPos.CENTER);
                gc.fillText("No chart data to display", width / 2.0, height / 2.0);
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Failed to render 'no data' message on pie canvas", ex);
            }
            return;
        }

        boolean legendBelow = width < 760;
        double pieAreaWidth = legendBelow ? width : width * 0.58;
        double legendStartX = legendBelow ? 18 : pieAreaWidth + 14;
        double legendWidth = legendBelow ? width - 36 : width - legendStartX - 16;
        double topMargin = 20;
        // when legend is below on narrow screens, render it as a two-column grid to save vertical space
        int legendColumns = legendBelow ? Math.min(2, Math.max(1, labels.size())) : 1;
        int itemsPerColumn = legendBelow ? (labels.size() + legendColumns - 1) / legendColumns : labels.size();
        double legendRowHeight = 22.0;
        double legendReservedHeight = legendBelow ? Math.min(height * 0.45, Math.max(100, itemsPerColumn * legendRowHeight + 36)) : 0;
        double pieAreaHeight = legendBelow ? height - legendReservedHeight - 16 : height - 20;

        double r = Math.max(55, Math.min(pieAreaWidth * 0.30, pieAreaHeight * 0.34));
        double cx = legendBelow ? width / 2.0 : pieAreaWidth / 2.0;
        double cy = topMargin + pieAreaHeight / 2.0;
        double startAngle = -90;

        // draw slices
        for (int i = 0; i < values.size(); i++) {
            double sweep = (values.get(i) / total) * 360.0;
            gc.setFill(PIE_COLORS[i % PIE_COLORS.length]);
            gc.fillArc(cx - r, cy - r, r * 2, r * 2, startAngle, sweep, javafx.scene.shape.ArcType.ROUND);
            // thin white border between slices
            gc.setStroke(Color.WHITE); gc.setLineWidth(1.5);
            gc.strokeArc(cx - r, cy - r, r * 2, r * 2, startAngle, sweep, javafx.scene.shape.ArcType.ROUND);
            startAngle += sweep;
        }

        // draw legend in a responsive area (right side on wide view, bottom on narrow view)
        Font legendTitleFont = Font.font("System", FontWeight.BOLD, 13);
        Font legendItemFont = Font.font("System", 11);
        gc.setFont(legendTitleFont);
        gc.setFill(Color.WHITE);
        gc.fillText(title, legendStartX, legendBelow ? (pieAreaHeight + 14) : 20);
        gc.setFont(legendItemFont);

        double legendBaseY = legendBelow ? (pieAreaHeight + 34) : 40;

        // draw legend items; when in narrow view, layout in multiple columns (two by default)
        double colWidth = legendWidth / legendColumns;
        for (int i = 0; i < labels.size(); i++) {
            int col = legendBelow ? (i / itemsPerColumn) : 0;
            int row = legendBelow ? (i % itemsPerColumn) : i;
            double ly = legendBaseY + row * legendRowHeight;
            double lx = legendStartX + col * colWidth;

            gc.setFill(PIE_COLORS[i % PIE_COLORS.length]);
            gc.fillRect(lx, ly, 14, 14);
            gc.setFill(Color.WHITE);
            double pct = (values.get(i) / total) * 100;
            String lbl = labels.get(i);
            if (lbl == null || lbl.isEmpty()) lbl = "(None)";
            String legendText = String.format("%s (%.1f%%)", lbl, pct);
            // constrain text to the column width (leave space for color box and padding)
            double maxTextWidth = Math.max(80, colWidth - 26);
            legendText = fitTextToWidth(legendText, legendItemFont, maxTextWidth);
            gc.fillText(legendText, lx + 18, ly + 12);
        }
    }

    private void redrawCachedPieChart(Canvas canvas) {
        if (lastChartLabels.isEmpty() || lastChartValues.isEmpty()) {
            clearPieCanvas(canvas);
            return;
        }
        if ("Sales by Date".equals(lastChartType)) {
            drawLineChart(canvas, lastChartLabels, lastChartValues, lastChartType);
        } else {
            drawPieChart(canvas, lastChartLabels, lastChartValues, lastChartType);
        }
    }

    // simple monochrome line chart for time-series reports (Sales by Date)
    private void drawLineChart(Canvas canvas, List<String> labels, List<Double> values, String title) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double width = canvas.getWidth();
        double height = canvas.getHeight();
        double drawWidth = Math.min(width, MAX_CANVAS_DIM);
        double drawHeight = Math.min(height, MAX_CANVAS_DIM);
        gc.clearRect(0, 0, drawWidth, drawHeight);

        // dates come back DESC from SQL; a time series must run left-to-right
        TreeMap<String, Double> series = new TreeMap<>();
        for (int i = 0; i < labels.size(); i++) {
            String label = labels.get(i) == null || labels.get(i).isEmpty() ? "(None)" : labels.get(i);
            series.merge(label, values.get(i), Double::sum);
        }

        if (series.isEmpty()) {
            drawChartNoData(canvas, gc, width, height);
            return;
        }

        double left = 74, right = 30, top = 34, bottom = 44;
        double plotW = Math.max(40, width - left - right);
        double plotH = Math.max(40, height - top - bottom);
        double maxValue = Math.max(1, series.values().stream().mapToDouble(Double::doubleValue).max().orElse(1));

        // horizontal grid + y-axis labels
        gc.setTextAlign(javafx.scene.text.TextAlignment.RIGHT);
        gc.setTextBaseline(javafx.geometry.VPos.CENTER);
        int yTicks = 4;
        for (int i = 0; i <= yTicks; i++) {
            double frac = i / (double) yTicks;
            double y = top + plotH * (1 - frac);
            gc.setStroke(Color.web("#262626"));
            gc.setLineWidth(1);
            gc.strokeLine(left, y, left + plotW, y);
            gc.setFill(Color.web("#8A8A8A"));
            gc.setFont(Font.font("System", 10));
            gc.fillText(String.format("%.0f", maxValue * frac), left - 8, y);
        }

        // axes
        gc.setStroke(Color.web("#FFFFFF"));
        gc.setLineWidth(1);
        gc.strokeLine(left, top, left, top + plotH);
        gc.strokeLine(left, top + plotH, left + plotW, top + plotH);

        // series line + points
        int n = series.size();
        double[] xs = new double[n];
        double[] ys = new double[n];
        int idx = 0;
        for (Map.Entry<String, Double> entry : series.entrySet()) {
            double x = left + (n == 1 ? plotW / 2.0 : plotW * idx / (double) (n - 1));
            double y = top + plotH * (1 - entry.getValue() / maxValue);
            xs[idx] = x;
            ys[idx] = y;
            idx++;
        }

        gc.setStroke(Color.web("#FFFFFF"));
        gc.setLineWidth(2);
        gc.strokePolyline(xs, ys, n);
        gc.setFill(Color.web("#FFFFFF"));
        for (int i = 0; i < n; i++) {
            gc.fillOval(xs[i] - 3, ys[i] - 3, 6, 6);
        }

        // x-axis labels: skip entries when they would overlap
        gc.setTextAlign(javafx.scene.text.TextAlignment.CENTER);
        gc.setTextBaseline(javafx.geometry.VPos.TOP);
        gc.setFill(Color.web("#8A8A8A"));
        gc.setFont(Font.font("System", 10));
        int step = Math.max(1, (int) Math.ceil(n / 8.0));
        idx = 0;
        for (String key : series.keySet()) {
            if (idx % step == 0) {
                gc.fillText(key, xs[idx], top + plotH + 8);
            }
            idx++;
        }

        gc.setTextAlign(javafx.scene.text.TextAlignment.LEFT);
        gc.setFill(Color.web("#FFFFFF"));
        gc.setFont(Font.font("System", FontWeight.BOLD, 12));
        gc.fillText(title, left, 12);
    }

    private void drawChartNoData(Canvas canvas, GraphicsContext gc, double width, double height) {
        gc.setFill(Color.web("#0A0A0A"));
        gc.fillRect(0, 0, width, height);
        gc.setFill(Color.web("#8A8A8A"));
        gc.setFont(Font.font("System", FontWeight.BOLD, 14));
        gc.setTextAlign(javafx.scene.text.TextAlignment.CENTER);
        gc.setTextBaseline(javafx.geometry.VPos.CENTER);
        gc.fillText("No chart data to display", width / 2.0, height / 2.0);
    }

    private String fitTextToWidth(String text, Font font, double maxWidth) {
        Text helper = new Text(text);
        helper.setFont(font);
        if (helper.getLayoutBounds().getWidth() <= maxWidth) {
            return text;
        }

        String ellipsis = "...";
        int low = 0;
        int high = text.length();
        String best = ellipsis;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            String candidate = text.substring(0, Math.max(0, mid)) + ellipsis;
            helper.setText(candidate);
            if (helper.getLayoutBounds().getWidth() <= maxWidth) {
                best = candidate;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return best;
    }

    private void clearPieCanvas(Canvas canvas) {
        canvas.getGraphicsContext2D().clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
    }

    private String buildOrderDateClause(String column, LocalDate startDate, LocalDate endDate) {
        StringBuilder clause = new StringBuilder();
        if (startDate != null) {
            clause.append(" AND DATE(").append(column).append(") >= ?");
        }
        if (endDate != null) {
            clause.append(" AND DATE(").append(column).append(") <= ?");
        }
        return clause.toString();
    }

    private int bindOrderDateClause(PreparedStatement ps, int index, LocalDate startDate, LocalDate endDate) throws SQLException {
        if (startDate != null) {
            ps.setDate(index++, java.sql.Date.valueOf(startDate));
        }
        if (endDate != null) {
            ps.setDate(index++, java.sql.Date.valueOf(endDate));
        }
        return index;
    }

    // logout
    @FXML
    private void handleLogout() {
        SessionManager.clearCurrentUser();
        Stage stage = (Stage) tabPane.getScene().getWindow();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/LoginView.fxml"));
            Scene loginScene = new Scene(loader.load(), MusicLibraryApp.LOGIN_SCENE_WIDTH, MusicLibraryApp.LOGIN_SCENE_HEIGHT);
            stage.setScene(loginScene); // switch back to login scene
            stage.setMaximized(false);
            stage.setMinWidth(MusicLibraryApp.LOGIN_MIN_WIDTH);
            stage.setMinHeight(MusicLibraryApp.LOGIN_MIN_HEIGHT);
            stage.setResizable(true);
            stage.setWidth(MusicLibraryApp.LOGIN_SCENE_WIDTH);
            stage.setHeight(MusicLibraryApp.LOGIN_SCENE_HEIGHT);
            stage.centerOnScreen();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to return to login screen", e);
            showError("Failed to return to login screen: " + e.getMessage());
        }
    }

    private void showError(String message) {
        showStyledDialog("Error", message, "error");
    }

    private void showSuccess(String message) {
        showStyledDialog("Success", message, "success");
    }

    private void showInfo(String message) {
        showStyledDialog("Information", message, "info");
    }

    private void showStyledDialog(String title, String message, String type) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(title);

        Label titleLabel = new Label(title.toUpperCase());
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: 900; -fx-text-fill: #FFFFFF;");

        Label messageLabel = new Label(message);
        messageLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #EDEDED; -fx-font-weight: 700;");
        messageLabel.setWrapText(true);
        messageLabel.setMaxWidth(400);

        VBox content = new VBox(8);
        content.setPadding(new Insets(16));
        content.getChildren().addAll(titleLabel, new Separator(), messageLabel);

        if ("error".equals(type)) {
            content.setStyle("-fx-background-color: #150A0A; -fx-border-color: #FF6B6B; " +
                    "-fx-border-width: 4; -fx-padding: 16;");
            titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: 900; -fx-text-fill: #FF6B6B;");
        } else if ("success".equals(type)) {
            content.setStyle("-fx-background-color: #0A150F; -fx-border-color: #7BE0AD; " +
                    "-fx-border-width: 4; -fx-padding: 16;");
            titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: 900; -fx-text-fill: #7BE0AD;");
        } else {
            content.setStyle("-fx-background-color: #0A0F16; -fx-border-color: #9AC1FF; " +
                    "-fx-border-width: 4; -fx-padding: 16;");
            titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: 900; -fx-text-fill: #9AC1FF;");
        }

        Button okBtn = new Button("✓ OK");
        okBtn.getStyleClass().add("neo-button-primary");
        okBtn.setOnAction(e -> dialog.close());
        okBtn.setStyle("-fx-min-width: 100;");

        HBox buttonBox = new HBox();
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setPadding(new Insets(12, 16, 16, 16));
        buttonBox.getChildren().add(okBtn);

        VBox mainLayout = new VBox();
        mainLayout.setStyle("-fx-background-color: #0A0A0A;");
        mainLayout.getChildren().addAll(content, buttonBox);

        Scene scene = new Scene(mainLayout);
        scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
        dialog.setScene(scene);
        dialog.setMinWidth(440);
        dialog.setMinHeight(240);
        dialog.sizeToScene();
        dialog.showAndWait();
    }
}