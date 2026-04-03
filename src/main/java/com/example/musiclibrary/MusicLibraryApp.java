package com.example.musiclibrary;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MusicLibraryApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/LoginView.fxml")
        );
        Scene scene = new Scene(loader.load(), 500, 340);
        primaryStage.setTitle("Music Library Management System");
        primaryStage.setScene(scene);
        primaryStage.getIcons().add(new javafx.scene.image.Image("/images/music.png")); // set the icon of the stage
        primaryStage.setMinWidth(420);
        primaryStage.setMinHeight(280);
        primaryStage.setResizable(true);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}