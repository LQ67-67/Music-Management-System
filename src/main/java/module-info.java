module com.example.musiclibrary {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.media;
    requires java.sql;
    requires mysql.connector.j;
    requires com.zaxxer.hikari;

    exports com.example.musiclibrary;
    exports com.example.musiclibrary.model;

    opens com.example.musiclibrary to javafx.fxml;
    opens com.example.musiclibrary.controller to javafx.fxml;
}
