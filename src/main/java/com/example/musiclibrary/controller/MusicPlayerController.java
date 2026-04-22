package com.example.musiclibrary.controller;

import com.example.musiclibrary.util.TrackMediaResolver;
import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class MusicPlayerController {
    @FXML private Label nowPlayingLabel;
    @FXML private Label timeLabel;
    @FXML private Slider progressSlider;
    @FXML private Slider volumeSlider;
    @FXML private ListView<String> playlistView;
    @FXML private Button playButton;
    @FXML private Button pauseButton;
    @FXML private Button previousButton;
    @FXML private Button nextButton;
    @FXML private ImageView trackImageView;

    private MediaPlayer mediaPlayer;
    private List<String> playlist;
    private int currentTrackIndex = -1;
    private boolean isPlaying = false;
    private PauseTransition updateTimer;

    @FXML
    private void initialize() {
        playlist = new ArrayList<>();
        try { playlist = new ArrayList<>(TrackMediaResolver.listMusicFiles()); }
        catch (Exception e) { showError("Failed to load music files: " + e.getMessage()); }

        updateTrackImage(null);

        if (playlistView != null) {
            playlistView.setItems(javafx.collections.FXCollections.observableArrayList(playlist));
            // double-click a track to play it
            playlistView.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2) {
                    int idx = playlistView.getSelectionModel().getSelectedIndex();
                    if (idx >= 0) playTrack(idx);
                }
            });
        }

        if (progressSlider != null) {
            // pause while scrubbing, resume after
            progressSlider.setOnMousePressed(e -> { if (mediaPlayer != null) mediaPlayer.pause(); });
            progressSlider.setOnMouseReleased(e -> {
                if (mediaPlayer != null) {
                    mediaPlayer.seek(Duration.seconds(progressSlider.getValue()));
                    if (isPlaying) mediaPlayer.play();
                }
            });
        }

        if (volumeSlider != null) {
            volumeSlider.setValue(50);
            volumeSlider.setOnMouseDragged(e -> { if (mediaPlayer != null) mediaPlayer.setVolume(volumeSlider.getValue() / 100.0); });
        }

        updateButtonState();
    }

    // resume playback, or start from the first track if nothing is loaded
    @FXML
    private void handlePlay() {
        if (mediaPlayer != null && !isPlaying) {
            mediaPlayer.play();
            isPlaying = true;
            updateButtonState();
            startUpdateTimer();
        } else if (mediaPlayer == null && !playlist.isEmpty()) {
            playTrack(0);
        } else if (playlist.isEmpty()) {
            showError("No music files found in playlist.");
        }
    }

    @FXML
    private void handlePause() {
        if (mediaPlayer != null && isPlaying) {
            mediaPlayer.pause();
            isPlaying = false;
            if (updateTimer != null) updateTimer.stop();
            updateButtonState();
        }
    }

    @FXML
    private void handlePrevious() {
        if (!playlist.isEmpty())
            playTrack(currentTrackIndex > 0 ? currentTrackIndex - 1 : playlist.size() - 1);
    }

    @FXML
    private void handleNext() {
        if (!playlist.isEmpty())
            playTrack(currentTrackIndex < playlist.size() - 1 ? currentTrackIndex + 1 : 0);
    }

    // load and play the track at the given index
    private void playTrack(int index) {
        if (index < 0 || index >= playlist.size()) return;

        try {
            if (mediaPlayer != null) mediaPlayer.stop();

            currentTrackIndex = index;
            String trackFile = playlist.get(index);
            URL url = TrackMediaResolver.findMusicUrl(trackFile);
            if (url == null) { showError("Music file not found: " + trackFile); return; }

            Media media = new Media(url.toExternalForm());
            media.setOnError(() -> showError("Audio load failed: " + getErrorMsg(media.getError())));

            mediaPlayer = new MediaPlayer(media);
            mediaPlayer.setOnError(() -> showError("Playback failed: " + getErrorMsg(mediaPlayer.getError())));
            mediaPlayer.setVolume(volumeSlider.getValue() / 100.0);

            // set the slider max once the duration is known
            mediaPlayer.setOnReady(() -> {
                progressSlider.setMax(mediaPlayer.getMedia().getDuration().toSeconds());
                updateTimeLabel();
            });

            // auto-advance to the next track when the current one ends
            mediaPlayer.setOnEndOfMedia(() -> {
                if (currentTrackIndex < playlist.size() - 1) {
                    playTrack(currentTrackIndex + 1);
                } else {
                    mediaPlayer.pause();
                    isPlaying = false;
                    currentTrackIndex = -1;
                    nowPlayingLabel.setText("Paused");
                    progressSlider.setValue(0);
                    timeLabel.setText("00:00 / 00:00");
                    updateTrackImage(null);
                    updateButtonState();
                    if (updateTimer != null) updateTimer.stop();
                }
            });

            mediaPlayer.play();
            isPlaying = true;
            nowPlayingLabel.setText("Now Playing: " + trackFile);
            updateTrackImage(trackFile);
            playlistView.getSelectionModel().select(index);
            updateButtonState();
            startUpdateTimer();
        } catch (Exception e) {
            showError("Failed to play track: " + e.getMessage());
        }
    }

    // tick every 100ms to update the progress slider and time label
    private void startUpdateTimer() {
        updateTimer = new PauseTransition(Duration.millis(100));
        updateTimer.setOnFinished(e -> {
            if (isPlaying && mediaPlayer != null) {
                progressSlider.setValue(mediaPlayer.getCurrentTime().toSeconds());
                updateTimeLabel();
                startUpdateTimer();
            }
        });
        updateTimer.playFromStart();
    }

    private void updateTimeLabel() {
        if (mediaPlayer != null) {
            timeLabel.setText(formatTime(mediaPlayer.getCurrentTime()) + " / " +
                    formatTime(mediaPlayer.getMedia().getDuration()));
        }
    }

    private String formatTime(Duration d) {
        return String.format("%02d:%02d", (int) d.toMinutes(), (int) d.toSeconds() % 60);
    }

    private void updateButtonState() {
        boolean has = !playlist.isEmpty();
        if (playButton != null)    playButton.setDisable(isPlaying || !has);
        if (pauseButton != null)   pauseButton.setDisable(!isPlaying);
        if (previousButton != null) previousButton.setDisable(!has);
        if (nextButton != null)    nextButton.setDisable(!has);
    }

    private void updateTrackImage(String trackFile) {
        if (trackImageView != null)
            trackImageView.setImage(TrackMediaResolver.loadTrackImage(null, trackFile));
    }

    // Stop playback and release resources when the window is closed
    public void dispose() {
        if (updateTimer != null) updateTimer.stop();
        if (mediaPlayer != null) { mediaPlayer.stop(); mediaPlayer.dispose(); mediaPlayer = null; }
        isPlaying = false;
    }

    private String getErrorMsg(MediaException ex) {
        if (ex == null) return "Unknown media error";
        String msg = ex.getMessage();
        return (msg == null || msg.isEmpty()) ? ex.getType().name() : msg;
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}