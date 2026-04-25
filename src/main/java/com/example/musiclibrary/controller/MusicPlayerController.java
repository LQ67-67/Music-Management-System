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
        try {
            playlist = new ArrayList<>(TrackMediaResolver.listMusicFiles()); // load music files from resources and add to playlist
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Failed to load music files: " + e.getMessage()); // show error if music files cannot be loaded
            alert.showAndWait();
        }

        if (trackImageView != null) {
            trackImageView.setImage(TrackMediaResolver.loadTrackImage(null, null));
        }

        if (playlistView != null) {
            playlistView.setItems(javafx.collections.FXCollections.observableArrayList(playlist));

            playlistView.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2) {
                    int selectedIndex = playlistView.getSelectionModel().getSelectedIndex(); // double-click a track to play it
                    if (selectedIndex >= 0) {
                        playTrack(selectedIndex);
                    }
                }
            });
        }

        if (progressSlider != null) {
            progressSlider.setOnMousePressed(event -> {
                if (mediaPlayer != null) {
                    mediaPlayer.pause(); // pause while dragging the slider
                }
            });

            progressSlider.setOnMouseReleased(event -> {
                if (mediaPlayer != null) {
                    mediaPlayer.seek(Duration.seconds(progressSlider.getValue()));
                    if (isPlaying) {
                        mediaPlayer.play(); // resume when done dragging
                    }
                }
            });
        }

        if (volumeSlider != null) {
            volumeSlider.setValue(50);
            volumeSlider.setOnMouseDragged(event -> {
                if (mediaPlayer != null) {
                    mediaPlayer.setVolume(volumeSlider.getValue() / 100.0); // adjust volume as slider is dragged
                }
            });
        }
        updateButtonState();
    }

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
            Alert alert = new Alert(Alert.AlertType.ERROR, "No music files found in playlist.");
            alert.showAndWait();
        }
    }

    @FXML
    private void handlePause() {
        if (mediaPlayer != null && isPlaying) {
            mediaPlayer.pause();
            isPlaying = false;
            if (updateTimer != null) {
                updateTimer.stop();
            }
            updateButtonState();
        }
    }

    @FXML
    private void handlePrevious() {
        if (!playlist.isEmpty()) {
            if (currentTrackIndex > 0) {
                playTrack(currentTrackIndex - 1);
            } else {
                playTrack(playlist.size() - 1);
            }
        }
    }

    @FXML
    private void handleNext() {
        if (!playlist.isEmpty()) {
            if (currentTrackIndex < playlist.size() - 1) {
                playTrack(currentTrackIndex + 1); // play next track if not at end of playlist
            } else {
                playTrack(0);
            }
        }
    }

    private void playTrack(int index) {
        if (index < 0 || index >= playlist.size()) return; // invalid index, do nothing

        try {
            if (mediaPlayer != null) {
                mediaPlayer.stop();
            }

            currentTrackIndex = index;
            String trackFile = playlist.get(index);
            URL url = TrackMediaResolver.findMusicUrl(trackFile); // find the URL for the track file in resources

            if (url == null) {
                Alert alert = new Alert(Alert.AlertType.ERROR, "Music file not found: " + trackFile);
                alert.showAndWait();
                return;
            }

            Media media = new Media(url.toExternalForm());
            media.setOnError(() -> {
                String errorMsg = getErrorMsg(media.getError());
                Alert alert = new Alert(Alert.AlertType.ERROR, "Audio load failed: " + errorMsg);
                alert.showAndWait();
            });

            mediaPlayer = new MediaPlayer(media);
            mediaPlayer.setOnError(() -> {
                String errorMsg = getErrorMsg(mediaPlayer.getError());
                Alert alert = new Alert(Alert.AlertType.ERROR, "Playback failed: " + errorMsg);
                alert.showAndWait();
            });

            mediaPlayer.setVolume(volumeSlider.getValue() / 100.0); // set initial volume based on slider position

            mediaPlayer.setOnReady(() -> {
                progressSlider.setMax(mediaPlayer.getMedia().getDuration().toSeconds()); // set the slider max length once the track is fully loaded
                updateTimeLabel();
            });

            mediaPlayer.setOnEndOfMedia(() -> {
                if (currentTrackIndex < playlist.size() - 1) {
                    playTrack(currentTrackIndex + 1); // move to the next track when this one ends
                } else {
                    // end of playlist
                    mediaPlayer.pause();
                    isPlaying = false;
                    currentTrackIndex = -1;
                    nowPlayingLabel.setText("Paused");
                    progressSlider.setValue(0);
                    timeLabel.setText("00:00 / 00:00");
                    if (trackImageView != null) trackImageView.setImage(TrackMediaResolver.loadTrackImage(null, null)); // reset to default image
                    updateButtonState();
                    if (updateTimer != null) updateTimer.stop(); // stop the timer when playback ends
                }
            });

            mediaPlayer.play();
            isPlaying = true;
            nowPlayingLabel.setText("Now Playing: " + trackFile);

            if (trackImageView != null) {
                trackImageView.setImage(TrackMediaResolver.loadTrackImage(null, trackFile)); // load specific image for the track, fallback to default if not found
            }

            playlistView.getSelectionModel().select(index);
            updateButtonState();
            startUpdateTimer();

        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Failed to play track: " + e.getMessage()); // show error if there was an issue starting playback
            alert.showAndWait();
        }
    }

    private void startUpdateTimer() {
        updateTimer = new PauseTransition(Duration.millis(100));
        updateTimer.setOnFinished(event -> {
            if (isPlaying && mediaPlayer != null) {
                progressSlider.setValue(mediaPlayer.getCurrentTime().toSeconds()); // update the slider position to match the current playback time
                updateTimeLabel();
                startUpdateTimer(); // loop the timer
            }
        });
        updateTimer.playFromStart();
    }

    private void updateTimeLabel() {
        if (mediaPlayer != null) {
            int currentMinutes = (int) mediaPlayer.getCurrentTime().toMinutes(); // calculate current minutes and seconds for display
            int currentSeconds = (int) mediaPlayer.getCurrentTime().toSeconds() % 60; // current minutes and seconds

            int totalMinutes = (int) mediaPlayer.getMedia().getDuration().toMinutes(); // total minutes and seconds
            int totalSeconds = (int) mediaPlayer.getMedia().getDuration().toSeconds() % 60; // total minutes and seconds

            String currentTimeStr = String.format("%02d:%02d", currentMinutes, currentSeconds); // format the time as MM:SS
            String totalTimeStr = String.format("%02d:%02d", totalMinutes, totalSeconds);

            timeLabel.setText(currentTimeStr + " / " + totalTimeStr);
        }
    }

    private void updateButtonState() {
        boolean hasMusic = !playlist.isEmpty();
        if (playButton != null) playButton.setDisable(isPlaying || !hasMusic);
        if (pauseButton != null) pauseButton.setDisable(!isPlaying);
        if (previousButton != null) previousButton.setDisable(!hasMusic);
        if (nextButton != null) nextButton.setDisable(!hasMusic);
    }

    public void dispose() {
        if (updateTimer != null) {
            updateTimer.stop();
        }
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
            mediaPlayer = null;
        }
        isPlaying = false;
    }

    private String getErrorMsg(MediaException ex) {
        if (ex == null) return "Unknown media error"; // if the exception is null, return a generic message
        String msg = ex.getMessage();
        if (msg == null || msg.isEmpty()) {
            return ex.getType().name(); // if the message is empty, return type of media error as a fallback
        }
        return msg;
    }
}