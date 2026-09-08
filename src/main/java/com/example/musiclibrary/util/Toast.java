package com.example.musiclibrary.util;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import javafx.stage.Popup;
import javafx.util.Duration;

/**
 * Non-blocking notification toast: appears near the bottom of the window
 * and fades out automatically. Use for success confirmations; keep Alerts
 * for errors that genuinely need acknowledgement.
 */
public final class Toast {

    private static final Duration VISIBLE = Duration.seconds(2.2);
    private static final Duration FADE = Duration.millis(250);

    private Toast() {
    }

    public static void show(Scene scene, String message) {
        if (scene == null || scene.getWindow() == null) {
            return;
        }

        Label label = new Label(message);
        label.getStyleClass().add("toast-label");

        Popup popup = new Popup();
        popup.getContent().add(label);
        popup.setAutoFix(true);

        var window = scene.getWindow();
        double x = window.getX() + (window.getWidth() - 360) / 2;
        double y = window.getY() + window.getHeight() - 90;
        popup.show(window, Math.max(window.getX() + 20, x), y);

        FadeTransition fadeIn = new FadeTransition(FADE, label);
        fadeIn.setFromValue(0.0);
        fadeIn.setToValue(1.0);

        PauseTransition hold = new PauseTransition(VISIBLE);

        FadeTransition fadeOut = new FadeTransition(FADE, label);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);
        fadeOut.setOnFinished(e -> popup.hide());

        new SequentialTransition(fadeIn, hold, fadeOut).play();
    }
}
