package com.itg;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;
import javafx.scene.image.Image;


public class TransferApplication extends Application {

    private static final long DEFAULT_SCAN_INTERVAL = 50L;
    private static final String PREFS_NODE_NAME = "com.itg.TransferApplication";

    private volatile boolean stopProcessing = false;
    private Thread processingThread;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("ITG Synchronize");
        Preferences prefs = Preferences.userRoot().node(PREFS_NODE_NAME);
        var icon = TransferApplication.class.getResourceAsStream("itg.png");
        if(icon != null) {
            primaryStage.getIcons().add(new Image(icon));
        }


        // Create a VBox to hold the content
        VBox content = new VBox(20);
        content.setPadding(new Insets(20, 20, 20, 20));

        // Create a ScrollPane to make the window scrollable
        ScrollPane scrollPane = new ScrollPane(content);

        // Create a GridPane for the input fields
        GridPane grid = new GridPane();
        grid.setVgap(10);
        grid.setHgap(10);

        // Labels for input fields
        Label sboLabel = new Label("SB Path:");
        Label bankLabel = new Label("ODB Path:");
        Label scanIntervalLabel = new Label("Scan interval in seconds:");
        Label archivationPathLabel = new Label("Archivation Path:");
        Label loggingPathLabel = new Label("Logging Path:");
        Label folderLabel = new Label("ODB Extra Scan Folders:");
        Label printerLabel = new Label("Printer name:");


        // TextFields for input
        TextField sboPathField = new TextField(prefs.get("sboPath", ""));
        TextField bankPathField = new TextField(prefs.get("bankPath", ""));
        TextField scanIntervalField = new TextField(prefs.get("scanInterval", String.valueOf(DEFAULT_SCAN_INTERVAL)));
        TextField archivationPathField = new TextField(prefs.get("archivationPath", ""));
        TextField loggingPathField = new TextField(prefs.get("loggingPath", ""));
        TextField printerNameField = new TextField(prefs.get("printerName", ""));
        String folderFieldsValue = prefs.get("folderFields", "");

        String[] folderFieldsArray = folderFieldsValue.split(",");
        List<TextField> folderFields = new ArrayList<>();
        for (String folderText : folderFieldsArray) {
            TextField newFolderField = new TextField(folderText);
            folderFields.add(newFolderField);
            grid.add(newFolderField, 1, grid.getRowCount());
        }
        // Create a dynamic list of folder name input fields
        Button addButton = new Button("+");
        addButton.setOnAction(e -> {
            TextField newFolderField = new TextField();
            folderFields.add(newFolderField);
            grid.add(newFolderField, 1, grid.getRowCount());
        });

        // Add labels and text fields to the grid
        grid.add(sboLabel, 0, 0);
        grid.add(sboPathField, 1, 0);
        grid.add(bankLabel, 0, 1);
        grid.add(bankPathField, 1, 1);
        grid.add(archivationPathLabel, 0, 2);
        grid.add(archivationPathField, 1, 2);
        grid.add(loggingPathLabel, 0, 3);
        grid.add(loggingPathField, 1, 3);
        grid.add(scanIntervalLabel, 0, 4);
        grid.add(scanIntervalField, 1, 4);
        grid.add(folderLabel, 0, 6);
        grid.add(printerLabel, 0, 5);
        grid.add(printerNameField, 1, 5);
        grid.add(addButton, 1, 6);

        // Create a Start button
        Button startButton = new Button("Start");
        Button stopButton = new Button("Stop");
        startButton.setTranslateX(100);
        stopButton.setTranslateX(100);

        BankMessageMover messageMover = new BankMessageMover(primaryStage);
        // Define action for the Start button
        startButton.setOnAction(e -> {
            String sboPath = sboPathField.getText();
            String bankPath = bankPathField.getText();
            String archivationPath = archivationPathField.getText();
            String loggingPath = loggingPathField.getText();
            String printerName = printerNameField.getText();

            long scanInterval = parseScanInterval(scanIntervalField.getText());
            if(!validatePaths(primaryStage, sboPath, bankPath, archivationPath, loggingPath)) {
                return;
            }

            List<String> bankFolders = new ArrayList<>();
            for (TextField folderField : folderFields) {
                bankFolders.add(folderField.getText());
            }

            messageMover.setBankFolders(bankFolders);
            messageMover.setArchivationDir(archivationPath);
            messageMover.setBankDir(bankPath);
            messageMover.setLoggingPath(loggingPath);
            messageMover.setScanInterval(scanInterval);
            messageMover.setSboDir(sboPath);
            messageMover.setBankFolders(bankFolders);
            messageMover.setPrinterName(printerName);

            prefs.put("sboPath", sboPath);
            prefs.put("bankPath", bankPath);
            prefs.put("archivationPath", archivationPathField.getText());
            prefs.put("loggingPath", loggingPathField.getText());
            prefs.put("scanInterval", String.valueOf(scanInterval));
            prefs.put("printerName", printerName);
            String folderPrefs = folderFields.stream().map(TextField::getText).collect(Collectors.joining(","));
            prefs.put("folderFields", folderPrefs);



            messageMover.start();
            content.getChildren().removeAll(grid, startButton);
            content.getChildren().addAll(grid, stopButton);
        });

        // Define action for the Stop button
        stopButton.setOnAction(e -> {
            messageMover.stop();
            content.getChildren().removeAll(grid, stopButton);
            content.getChildren().addAll(grid, startButton);
        });

        content.getChildren().addAll(grid, startButton);

        Scene scene = new Scene(scrollPane, 400, 500); // Adjust the height of the scene
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private long parseScanInterval(String text) {
        try {
            long interval = Long.parseLong(text);
            if (interval < 30L) {
                interval = 30L;
            }
            return interval;
        } catch (NumberFormatException e) {
            return DEFAULT_SCAN_INTERVAL; // Return the default value if parsing fails
        }
    }


    // Function to validate paths
    private boolean validatePaths(Stage primaryStage, String sboPath, String bankPath, String archivationPath, String loggingPath) {
        boolean isValidPath = true;
        if (isInvalidPath(sboPath)) {
            AlertUtils.displayError(primaryStage, "Invalid SB Path", "Please enter a valid SB path.");
            isValidPath = false;
        }

        if (isInvalidPath(bankPath)) {
            AlertUtils.displayError(primaryStage, "Invalid ODB Path", "Please enter a valid ODB Path.");
            isValidPath = false;
        }


        if (isInvalidPath(archivationPath)) {
            AlertUtils.displayError(primaryStage, "Invalid Archive Path", "Please enter a valid Archive Path.");
            isValidPath = false;
        }

        if (isInvalidPath(loggingPath)) {
            AlertUtils.displayError(primaryStage, "Invalid Logging Path", "Please enter a valid Logging Path.");
            isValidPath = false;
        }
        return isValidPath;
    }


    // Function to check if a path is valid
    private boolean isInvalidPath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return true;
        }

        File file = new File(path);
        return !file.exists() || !file.isDirectory();
    }
}