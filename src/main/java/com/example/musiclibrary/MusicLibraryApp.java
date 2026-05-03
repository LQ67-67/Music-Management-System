package com.example.musiclibrary;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.InputStream;

public class MusicLibraryApp extends Application {
    public static final double LOGIN_SCENE_WIDTH = 820;
    public static final double LOGIN_SCENE_HEIGHT = 520;
    public static final double LOGIN_MIN_WIDTH = 560;
    public static final double LOGIN_MIN_HEIGHT = 460;

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/LoginView.fxml")
        );
        Scene scene = new Scene(loader.load(), LOGIN_SCENE_WIDTH, LOGIN_SCENE_HEIGHT);
        primaryStage.setTitle("Music Library Management System");
        primaryStage.setScene(scene);
        try (InputStream iconStream = getClass().getResourceAsStream("/images/music.png")) {
            if (iconStream != null) {
                primaryStage.getIcons().add(new Image(iconStream));
            }
        }
        primaryStage.setMinWidth(LOGIN_MIN_WIDTH);
        primaryStage.setMinHeight(LOGIN_MIN_HEIGHT);
        primaryStage.setResizable(true);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
