package com.example.musiclibrary.controller;

import com.example.musiclibrary.model.Track;
import com.example.musiclibrary.util.TrackMediaResolver;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.util.function.Consumer;

/**
 * Controller for the TrackCard component.
 * Manages the display of individual track information in card format.
 */
public class TrackCardController {
    @FXML private ImageView trackImage;
    @FXML private Label titleLabel;
    @FXML private Label artistLabel;
    @FXML private Label albumLabel;
    @FXML private FlowPane categoryPane;
    @FXML private Label priceLabel;
    @FXML private Button stockButton;
    @FXML private Label quantityLabel;
    @FXML private Button decreaseButton;
    @FXML private Button increaseButton;

    private Track track;
    private VBox rootPane;
    private int quantity = 1;
    private Consumer<Integer> quantityChangeListener;
    private Consumer<TrackCardController> purchaseRequestListener;

    @FXML
    private void initialize() {
        decreaseButton.setOnAction(e -> setQuantity(quantity - 1));
        increaseButton.setOnAction(e -> setQuantity(quantity + 1));
        stockButton.setOnAction(e -> {
            if (purchaseRequestListener != null && track != null && track.isActive() && track.getStockQty() > 0) {
                purchaseRequestListener.accept(this);
            }
        });
    }

    /**
     * Set the track data for this card.
     */
    public void setTrack(Track track) {
        this.track = track;

        if (track != null) {
            titleLabel.setText(track.getTitle() != null ? track.getTitle() : "Unknown");
            artistLabel.setText(track.getArtist() != null ? track.getArtist() : "");
            albumLabel.setText(track.getAlbum() != null && !track.getAlbum().isBlank() ? track.getAlbum() : "");
            buildCategoryChips();

            BigDecimal price = track.getPrice();
            priceLabel.setText(price != null ? String.format("RM %.2f", price) : "RM 0.00");

            // Load track image
            trackImage.setImage(TrackMediaResolver.loadTrackImage(track, track.getId() + ".mp3"));

            // Update stock badge
            updateStockBadge();
            setQuantity(Math.min(quantity, Math.max(1, track.getStockQty())));
        }
    }

    private void buildCategoryChips() {
        categoryPane.getChildren().clear();

        String genre = track.getGenre();
        String album = track.getAlbum();

        addChip(genre == null || genre.isBlank() ? "All" : genre.trim(), true);

        if (album != null && !album.isBlank()) {
            addChip(album.trim(), false);
        }

        if (genre != null && genre.contains("/")) {
            for (String tag : genre.split("[,/&|]")) {
                String clean = tag.trim();
                if (!clean.isEmpty() && categoryPane.getChildren().size() < 4) {
                    addChip(clean, false);
                }
            }
        }
    }

    private void addChip(String text, boolean accent) {
        Label chip = new Label(text);
        chip.getStyleClass().add("category-chip");
        if (accent) {
            chip.getStyleClass().add("accent");
        }
        categoryPane.getChildren().add(chip);
    }

    public String getTrackLabel() {
        return track == null ? "" : (track.getTitle() == null ? "" : track.getTitle());
    }

    /**
     * Update the stock badge based on track availability.
     */
    private void updateStockBadge() {
        if (track != null) {
            int stockQty = track.getStockQty();

            if (!track.isActive()) {
                stockButton.setText("Unavailable");
                stockButton.getStyleClass().removeAll("stock-badge", "out-of-stock", "low-stock");
                stockButton.getStyleClass().addAll("stock-badge", "out-of-stock");
                stockButton.setDisable(true);
            } else if (stockQty <= 0) {
                stockButton.setText("Out of Stock");
                stockButton.getStyleClass().removeAll("stock-badge", "out-of-stock", "low-stock");
                stockButton.getStyleClass().addAll("stock-badge", "out-of-stock");
                stockButton.setDisable(true);
            } else if (stockQty < 5) {
                stockButton.setText("Low Stock (" + stockQty + ")");
                stockButton.getStyleClass().removeAll("stock-badge", "out-of-stock", "low-stock");
                stockButton.getStyleClass().addAll("stock-badge", "low-stock");
                stockButton.setDisable(false);
            } else {
                stockButton.setText("In Stock");
                stockButton.getStyleClass().removeAll("stock-badge", "out-of-stock", "low-stock");
                stockButton.getStyleClass().add("stock-badge");
                stockButton.setDisable(false);
            }
        }
    }

    public void setQuantity(int quantity) {
        int maxQty = track == null ? 1 : Math.max(1, track.getStockQty());
        this.quantity = Math.max(1, Math.min(quantity, maxQty));
        quantityLabel.setText(String.valueOf(this.quantity));

        if (track != null) {
            boolean canIncrease = track.isActive() && this.quantity < maxQty;
            increaseButton.setDisable(!canIncrease);
            decreaseButton.setDisable(this.quantity <= 1);
        }

        if (quantityChangeListener != null) {
            quantityChangeListener.accept(this.quantity);
        }
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantityChangeListener(Consumer<Integer> quantityChangeListener) {
        this.quantityChangeListener = quantityChangeListener;
    }

    public void setPurchaseRequestListener(Consumer<TrackCardController> purchaseRequestListener) {
        this.purchaseRequestListener = purchaseRequestListener;
    }

    /**
     * Mark this card as selected.
     */
    public void setSelected(boolean selected) {
        if (rootPane != null) {
            if (selected) {
                rootPane.getStyleClass().add("selected");
            } else {
                rootPane.getStyleClass().remove("selected");
            }
        }
    }


    public void setRootPane(VBox pane) {
        this.rootPane = pane;
    }

    /**
     * Get the underlying track.
     */
    public Track getTrack() {
        return track;
    }
}

