package com.example.musiclibrary.controller;

import com.example.musiclibrary.session.SessionManager;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;

public class AdminMainController {

    @FXML
    private Label welcomeLabel;

    @FXML
    private TabPane tabPane;

    @FXML
    private void initialize() {
        if (SessionManager.getCurrentUser() != null) {
            welcomeLabel.setText("Welcome, " + SessionManager.getCurrentUser().getUsername());
        }
        // TODO: 在各个 Tab 中嵌入具体的管理/报表界面
    }
}

