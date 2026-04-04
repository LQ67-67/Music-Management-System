package com.example.musiclibrary;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.InputStream;

public class MusicLibraryApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/LoginView.fxml")
        );
        Scene scene = new Scene(loader.load(), 500, 340);
        primaryStage.setTitle("Music Library Management System");
        primaryStage.setScene(scene);
        try (InputStream iconStream = getClass().getResourceAsStream("/images/music.png")) {
            if (iconStream != null) {
                primaryStage.getIcons().add(new Image(iconStream));
            }
        }
        primaryStage.setMinWidth(420);
        primaryStage.setMinHeight(280);
        primaryStage.setResizable(true);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
