package com.example.musiclibrary.service;

import com.example.musiclibrary.db.DBConnectionManager;
import javafx.scene.control.Alert;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service class for exporting data to text files.
 * Handles file selection, formatting, and writing export files.
 */
public class ExportService {
    private static final Logger LOGGER = Logger.getLogger(ExportService.class.getName());

    /**
     * Export table data to a formatted text file.
     * @param tableName Name of the table being exported
     * @param headers Column headers for the export
     * @param rows Data rows to export
     * @param ownerWindow Parent window for file chooser dialog
     */
    public void exportTableToTxt(String tableName, String[] headers, List<String[]> rows, Window ownerWindow) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Export " + tableName);

        String fileTimestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd_HHmmss"));
        fc.setInitialFileName(tableName.replace(" ", "_") + "_export_" + fileTimestamp + ".txt");

        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files (*.txt)", "*.txt"));
        File file = fc.showSaveDialog(ownerWindow);
        if (file == null) return;

        try {
            writeExportFile(file, tableName, headers, rows);

            Alert alert = new Alert(Alert.AlertType.INFORMATION,
                    tableName + " exported successfully to:\n" + file.getAbsolutePath());
            alert.setHeaderText("Export Complete");
            alert.showAndWait();

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Export failed", e);
            showError("Export failed: " + e.getMessage(), ownerWindow);
        }
    }

    /**
     * Export user list from database to text file.
     * @param ownerWindow Parent window for file chooser dialog
     */
    public void exportUserList(Window ownerWindow) {
        String sql = "SELECT id, username, role FROM users ORDER BY id";
        List<String[]> rows = new ArrayList<>();
        try (Connection conn = DBConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(new String[]{
                        String.valueOf(rs.getInt("id")),
                        rs.getString("username"),
                        rs.getString("role")
                });
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to fetch user list", e);
            showError("Failed to fetch user list: " + e.getMessage(), ownerWindow);
            return;
        }
        exportTableToTxt("Users", new String[]{"ID", "Username", "Role"}, rows, ownerWindow);
    }

    /**
     * Write export data to file with formatted table layout.
     */
    private void writeExportFile(File file, String tableName, String[] headers, List<String[]> rows) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            pw.println("========================================");
            pw.println("  MUSIC LIBRARY – " + tableName.toUpperCase());
            pw.println("  Exported: " + timestamp);
            pw.println("========================================");
            pw.println();

            // compute column widths based on display length
            int[] widths = new int[headers.length];
            for (int i = 0; i < headers.length; i++){
                widths[i] = getDisplayWidth(headers[i]);
            }
            for (String[] row : rows) {
                for (int i = 0; i < Math.min(row.length, headers.length); i++) {
                    if (row[i] != null){
                        widths[i] = Math.max(widths[i], getDisplayWidth(row[i]));
                    }
                }
            }

            String separator = buildSeparator(widths);
            pw.println(separator);
            pw.println(buildRow(headers, widths));
            pw.println(separator);
            for (String[] row : rows){
                pw.println(buildRow(row, widths));
            }
            pw.println(separator);
            pw.println();
            pw.println("Total records: " + rows.size());
        }
    }

    /**
     * Build a separator line for the table.
     */
    private String buildSeparator(int[] widths) {
        StringBuilder sb = new StringBuilder("+");
        for (int w : widths) {
            sb.append("-".repeat(w + 2)).append("+");
        }
        return sb.toString();
    }

    /**
     * Build a formatted row with proper spacing.
     */
    private String buildRow(String[] cells, int[] widths) {
        StringBuilder sb = new StringBuilder("|");
        for (int i = 0; i < widths.length; i++) {
            String cell = (i < cells.length && cells[i] != null) ? cells[i] : "";
            int padding = widths[i] - getDisplayWidth(cell);
            sb.append(" ").append(cell).append(" ".repeat(Math.max(0, padding))).append(" |");
        }
        return sb.toString();
    }

    /**
     * Calculate the visual width of a string (2 for full-width characters, 1 for half-width).
     * This handles CJK (Chinese, Japanese, Korean) characters correctly.
     */
    private int getDisplayWidth(String str) {
        if (str == null) return 0;
        int width = 0;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            // matches most of the Unicode range in Chinese, Japanese, and Korean with full-width punctuation
            if ((c >= 0x2E80 && c <= 0xFE4F) || (c >= 0xFF00 && c <= 0xFFEF)) {
                width += 2;
            } else {
                width += 1;
            }
        }
        return width;
    }

    /**
     * Show error alert dialog.
     */
    private void showError(String message, Window ownerWindow) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.setHeaderText("Export Error");
        if (ownerWindow != null) {
            alert.initOwner(ownerWindow);
        }
        alert.showAndWait();
    }
}

