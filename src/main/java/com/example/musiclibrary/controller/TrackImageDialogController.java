package com.example.musiclibrary.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TrackImageDialogController {

    @FXML
    private ImageView imagePreview;

    @FXML
    private Label selectedFileLabel;

    @FXML
    private Button selectButton;

    @FXML
    private Button confirmButton;

    private String selectedImagePath;
    private String imageCopyPath;

    @FXML
    private void initialize() {
        if (imagePreview != null) {
            imagePreview.setFitWidth(200);
            imagePreview.setFitHeight(200);
            imagePreview.setPreserveRatio(true);
        }
    }

    @FXML
    private void handleSelectImage() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select an Image");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        File selectedFile = fileChooser.showOpenDialog(selectButton.getScene().getWindow());
        if (selectedFile != null) {
            selectedImagePath = selectedFile.getAbsolutePath();
            selectedFileLabel.setText("Selected: " + selectedFile.getName());

            try {
                Image image = new Image("file:" + selectedImagePath);
                imagePreview.setImage(image);

                // 复制图片到项目resources目录
                copyImageToResourcesDir(selectedFile);
            } catch (Exception e) {
                showError("Failed to load image: " + e.getMessage());
            }
        }
    }

    private void copyImageToResourcesDir(File sourceFile) {
        try {
            // 创建images/tracks目录
            Path tracksDir = Paths.get("src/main/resources/images/tracks");
            if (!Files.exists(tracksDir)) {
                Files.createDirectories(tracksDir);
            }

            // 生成新文件名
            String fileName = System.currentTimeMillis() + "_" + sourceFile.getName();
            Path targetPath = tracksDir.resolve(fileName);

            // 复制文件
            Files.copy(sourceFile.toPath(), targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            imageCopyPath = "images/tracks/" + fileName;
        } catch (IOException e) {
            showError("Failed to copy image: " + e.getMessage());
        }
    }

    @FXML
    private void handleConfirm() {
        if (imageCopyPath == null || imageCopyPath.isEmpty()) {
            showError("No image selected.");
            return;
        }

        Stage stage = (Stage) confirmButton.getScene().getWindow();
        stage.close();
    }

    @FXML
    private void handleCancel() {
        imageCopyPath = null;
        Stage stage = (Stage) confirmButton.getScene().getWindow();
        stage.close();
    }

    public String getSelectedImagePath() {
        return imageCopyPath;
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}

