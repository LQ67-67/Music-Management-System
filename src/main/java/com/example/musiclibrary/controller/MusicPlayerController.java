package com.example.musiclibrary.controller;

import com.example.musiclibrary.util.TrackMediaResolver;
import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Slider;
import javafx.scene.control.Button;
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
    private Button stopButton;

    @FXML
    private ImageView trackImageView;

    private MediaPlayer mediaPlayer;
    private List<String> playlist;
    private int currentTrackIndex = -1;
    private boolean isPlaying = false;
    private PauseTransition updateTimer;

    @FXML
    private void initialize() {
        playlist = new ArrayList<>();
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

    private void loadMusicFiles() {
        try {
            playlist = new ArrayList<>(TrackMediaResolver.listMusicFiles());
        } catch (Exception e) {
            showError("Failed to load music files: " + e.getMessage());
        }
    }

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

    @FXML
    private void handlePause() {
        if (mediaPlayer != null && isPlaying) {
            mediaPlayer.pause();
            isPlaying = false;
            updatePlayButtonState();
        }
    }

    @FXML
    private void handleStop() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            isPlaying = false;
            currentTrackIndex = -1;
            nowPlayingLabel.setText("Stopped");
            progressSlider.setValue(0);
            timeLabel.setText("00:00 / 00:00");
            updateTrackImage(null);
            updatePlayButtonState();
            if (updateTimer != null) {
                updateTimer.stop();
            }
        }
    }

    private void playTrack(int index) {
        if (index < 0 || index >= playlist.size()) {
            return;
        }

        try {
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

            Media media = new Media(musicUrl.toExternalForm());
            media.setOnError(() -> showError("Audio load failed for " + trackFile + ": " + getMediaErrorMessage(media.getError())));
            mediaPlayer = new MediaPlayer(media);
            mediaPlayer.setOnError(() -> showError("Playback failed for " + trackFile + ": " + getMediaErrorMessage(mediaPlayer.getError())));

            mediaPlayer.setVolume(volumeSlider.getValue() / 100.0);

            mediaPlayer.setOnReady(() -> {
                Duration duration = mediaPlayer.getMedia().getDuration();
                progressSlider.setMax(duration.toSeconds());
                updateTimeLabel();
            });

            mediaPlayer.setOnEndOfMedia(() -> {
                if (currentTrackIndex < playlist.size() - 1) {
                    playTrack(currentTrackIndex + 1);
                } else {
                    handleStop();
                }
            });

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

    private void updateProgressSlider() {
        if (mediaPlayer != null) {
            progressSlider.setValue(mediaPlayer.getCurrentTime().toSeconds());
        }
    }

    private void updateTimeLabel() {
        if (mediaPlayer != null) {
            Duration current = mediaPlayer.getCurrentTime();
            Duration total = mediaPlayer.getMedia().getDuration();
            String timeStr = formatTime(current) + " / " + formatTime(total);
            timeLabel.setText(timeStr);
        }
    }

    private String formatTime(Duration duration) {
        int minutes = (int) duration.toMinutes();
        int seconds = (int) duration.toSeconds() % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private void updatePlayButtonState() {
        if (playButton != null && pauseButton != null && stopButton != null) {
            playButton.setDisable(isPlaying || playlist.isEmpty());
            pauseButton.setDisable(!isPlaying);
            stopButton.setDisable(!isPlaying);
        }
    }

    private void updateTrackImage(String trackFile) {
        if (trackImageView == null) {
            return;
        }

        trackImageView.setImage(TrackMediaResolver.loadTrackImage(null, trackFile));
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private String getMediaErrorMessage(MediaException exception) {
        if (exception == null) {
            return "Unknown media error";
        }
        String detail = exception.getMessage();
        return detail == null || detail.isBlank() ? exception.getType().name() : detail;
    }
}
