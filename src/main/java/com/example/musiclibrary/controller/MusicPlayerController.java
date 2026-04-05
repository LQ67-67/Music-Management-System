package com.example.musiclibrary.controller;

import com.example.musiclibrary.util.TrackMediaResolver;
import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaException;
import javafx.util.Duration;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class MusicPlayerController {

    @FXML
    private Label nowPlayingLabel;

    @FXML
    private Label timeLabel;

    @FXML
    private Slider progressSlider;

    @FXML
    private Slider volumeSlider;

    @FXML
    private ListView<String> playlistView;

    @FXML
    private Button playButton;

    @FXML
    private Button pauseButton;

    @FXML
    private Button previousButton;

    @FXML
    private Button nextButton;

    @FXML
    private ImageView trackImageView;

    private MediaPlayer mediaPlayer;
    private List<String> playlist;
    private int currentTrackIndex;
    private boolean isPlaying;
    private PauseTransition updateTimer;

    // Initialize method - called when FXML is loaded
    @FXML
    private void initialize() {
        playlist = new ArrayList<>();
        currentTrackIndex = -1;
        isPlaying = false;

        loadMusicFiles();
        updateTrackImage(null);

        if (playlistView != null) {
            playlistView.setItems(javafx.collections.FXCollections.observableArrayList(playlist));
            playlistView.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2) {
                    int selectedIndex = playlistView.getSelectionModel().getSelectedIndex();
                    if (selectedIndex >= 0) {
                        playTrack(selectedIndex);
                    }
                }
            });
        }

        if (progressSlider != null) {
            progressSlider.setOnMousePressed(event -> {
                if (mediaPlayer != null) {
                    mediaPlayer.pause();
                }
            });
            progressSlider.setOnMouseReleased(event -> {
                if (mediaPlayer != null) {
                    mediaPlayer.seek(Duration.seconds(progressSlider.getValue()));
                    if (isPlaying) {
                        mediaPlayer.play();
                    }
                }
            });
        }

        if (volumeSlider != null) {
            volumeSlider.setValue(50);
            volumeSlider.setOnMouseDragged(event -> {
                if (mediaPlayer != null) {
                    mediaPlayer.setVolume(volumeSlider.getValue() / 100.0);
                }
            });
        }

        updatePlayButtonState();
    }

    // Load music files from resources
    private void loadMusicFiles() {
        try {
            playlist = new ArrayList<>(TrackMediaResolver.listMusicFiles());
        } catch (Exception e) {
            showError("Failed to load music files: " + e.getMessage());
        }
    }

    // Handle play button click
    @FXML
    private void handlePlay() {
        if (mediaPlayer != null && !isPlaying) {
            mediaPlayer.play();
            isPlaying = true;
            updatePlayButtonState();
            startUpdateTimer();
        } else if (mediaPlayer == null && !playlist.isEmpty()) {
            playTrack(0);
        } else if (playlist.isEmpty()) {
            showError("No music files found in playlist.");
        }
    }

    // Handle pause button click
    @FXML
    private void handlePause() {
        if (mediaPlayer != null && isPlaying) {
            mediaPlayer.pause();
            isPlaying = false;
            updatePlayButtonState();
        }
    }

    // Handle previous button click
    @FXML
    private void handlePrevious() {
        if (playlist.isEmpty()) {
            return;
        }
        if (currentTrackIndex > 0) {
            playTrack(currentTrackIndex - 1);
        } else if (currentTrackIndex == 0) {
            playTrack(playlist.size() - 1);
        } else {
            playTrack(0);
        }
    }

    // Handle next button click
    @FXML
    private void handleNext() {
        if (playlist.isEmpty()) {
            return;
        }
        if (currentTrackIndex >= 0 && currentTrackIndex < playlist.size() - 1) {
            playTrack(currentTrackIndex + 1);
        } else if (currentTrackIndex == playlist.size() - 1) {
            playTrack(0);
        } else {
            playTrack(0);
        }
    }

    // Play track at specified index
    private void playTrack(int index) {
        if (index < 0 || index >= playlist.size()) {
            return;
        }

        try {
            // Stop current media player if exists
            if (mediaPlayer != null) {
                mediaPlayer.stop();
            }

            currentTrackIndex = index;
            String trackFile = playlist.get(index);
            URL musicUrl = TrackMediaResolver.findMusicUrl(trackFile);

            if (musicUrl == null) {
                showError("Music file not found: " + trackFile);
                return;
            }

            // Create new media and media player
            Media media = new Media(musicUrl.toExternalForm());
            media.setOnError(() -> showError("Audio load failed for " + trackFile + ": " + getMediaErrorMessage(media.getError())));

            mediaPlayer = new MediaPlayer(media);
            mediaPlayer.setOnError(() -> showError("Playback failed for " + trackFile + ": " + getMediaErrorMessage(mediaPlayer.getError())));

            mediaPlayer.setVolume(volumeSlider.getValue() / 100.0);

            // Set on ready - get duration
            mediaPlayer.setOnReady(() -> {
                Duration duration = mediaPlayer.getMedia().getDuration();
                progressSlider.setMax(duration.toSeconds());
                updateTimeLabel();
            });

            // Set on end of media - play next track
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
                    updatePlayButtonState();
                    if (updateTimer != null) {
                        updateTimer.stop();
                    }
                }
            });

            // Start playing
            mediaPlayer.play();
            isPlaying = true;
            nowPlayingLabel.setText("Now Playing: " + trackFile);
            updateTrackImage(trackFile);
            playlistView.getSelectionModel().select(index);
            updatePlayButtonState();
            startUpdateTimer();
        } catch (Exception e) {
            showError("Failed to play track: " + e.getMessage());
        }
    }

    // Start update timer for progress and time
    private void startUpdateTimer() {
        if (updateTimer == null) {
            updateTimer = new PauseTransition(Duration.millis(100));
            updateTimer.setOnFinished(event -> {
                if (isPlaying && mediaPlayer != null) {
                    updateProgressSlider();
                    updateTimeLabel();
                    startUpdateTimer();
                }
            });
        }
        updateTimer.playFromStart();
    }

    // Update progress slider value
    private void updateProgressSlider() {
        if (mediaPlayer != null) {
            progressSlider.setValue(mediaPlayer.getCurrentTime().toSeconds());
        }
    }

    // Update time label
    private void updateTimeLabel() {
        if (mediaPlayer != null) {
            Duration current = mediaPlayer.getCurrentTime();
            Duration total = mediaPlayer.getMedia().getDuration();
            String timeStr = formatTime(current) + " / " + formatTime(total);
            timeLabel.setText(timeStr);
        }
    }

    // Format duration to MM:SS
    private String formatTime(Duration duration) {
        int minutes = (int) duration.toMinutes();
        int seconds = (int) duration.toSeconds() % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    // Update play/pause button states
    private void updatePlayButtonState() {
        boolean hasPlaylist = !playlist.isEmpty();
        boolean isStopped = currentTrackIndex == -1;

        if (playButton != null && pauseButton != null) {
            playButton.setDisable(isPlaying || !hasPlaylist);
            pauseButton.setDisable(!isPlaying);
        }

        if (previousButton != null && nextButton != null) {
            previousButton.setDisable(!hasPlaylist);
            nextButton.setDisable(!hasPlaylist);
        }
    }

    // Update track image
    private void updateTrackImage(String trackFile) {
        if (trackImageView == null) {
            return;
        }
        trackImageView.setImage(TrackMediaResolver.loadTrackImage(null, trackFile));
    }

    // Show error message
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // Get media error message
    private String getMediaErrorMessage(MediaException exception) {
        if (exception == null) {
            return "Unknown media error";
        }
        String detail = exception.getMessage();
        return detail == null || detail.isEmpty() ? exception.getType().name() : detail;
    }
}