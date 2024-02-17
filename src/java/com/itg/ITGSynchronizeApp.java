package com.itg;

import com.itg.messagemover.BankMessageMover;
import com.itg.messagemover.BankMessageSFTPMover;
import com.itg.printer.PrintApplication;
import com.itg.sftp.SFTPConfig;
import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Pair;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

public class ITGSynchronizeApp extends Application {

    private static final long DEFAULT_SCAN_INTERVAL = 50L;
    private static final String PREFS_NODE_NAME_LOCAL = "com.itg.TransferApplication.Local";
    private static final String PREFS_NODE_NAME_SFTP = "com.itg.TransferApplication.sftp";

    private volatile boolean stopProcessing = false;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("ITG Synchronize");
        var icon = ITGSynchronizeApp.class.getResourceAsStream("itg.png");
        if (icon != null) {
            primaryStage.getIcons().add(new Image(icon));
        }

        TabPane tabPane = new TabPane();

        // Local Transfer Tab
        Tab localTab = new Tab();
        localTab.setText("Local Transfer");
        localTab.setClosable(false); // Make the tab non-closable
        // Add form components to localTab
        VBox localVBox = createLocalTransferForm(primaryStage);

        // Wrap the VBox in a ScrollPane
        ScrollPane scrollPane = new ScrollPane(localVBox);
        scrollPane.setFitToWidth(true); // Ensure the VBox fits within the width of the ScrollPane

        localTab.setContent(scrollPane);

        // SFTP Transfer Tab
        Tab sftpTab = new Tab();
        sftpTab.setText("SFTP Transfer");
        sftpTab.setClosable(false); // Make the tab non-closable
        // Add form components to sftpTab
        VBox sftpGridPane = createSFTPTransferForm(primaryStage);
        ScrollPane scrollPane2 = new ScrollPane(sftpGridPane);
        scrollPane2.setFitToWidth(true); // Ensure the VBox fits within the width of the ScrollPane

        sftpTab.setContent(scrollPane2);

        tabPane.getTabs().addAll(localTab, sftpTab);

        Scene scene = new Scene(tabPane, 400, 600);
        primaryStage.setTitle("ITG Synchronize");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private VBox createLocalTransferForm(Stage primaryStage) {
        Preferences prefs = Preferences.userRoot().node(PREFS_NODE_NAME_LOCAL);

        VBox vBox = new VBox(10);
        vBox.setAlignment(Pos.BASELINE_LEFT);
        vBox.setPadding(new Insets(20, 50, 20, 50));

        // Labels for input fields
        Label sboLabel = new Label("SB Path:");
        Label bankLabel = new Label("ODB Path:");
        Label scanIntervalLabel = new Label("Scan interval in seconds:");
        Label archivationPathLabel = new Label("Archivation Path:");
        Label loggingPathLabel = new Label("Logging Path:");

        // TextFields for input
        TextField sboPathField = new TextField(prefs.get("sboPath", ""));
        TextField bankPathField = new TextField(prefs.get("bankPath", ""));
        TextField scanIntervalField = new TextField(prefs.get("scanInterval", String.valueOf(DEFAULT_SCAN_INTERVAL)));
        TextField archivationPathField = new TextField(prefs.get("archivationPath", ""));
        TextField loggingPathField = new TextField(prefs.get("loggingPath", ""));
        TextField printerNameField = new TextField(prefs.get("printerName", ""));


        Label folderLabel = new Label("ODB Extra Scan Folders:");

        vBox.getChildren().addAll(
                // ... other labels and text fields
                sboLabel,
                sboPathField,
                bankLabel,
                bankPathField,
                scanIntervalLabel,
                scanIntervalField,
                archivationPathLabel,
                archivationPathField,
                loggingPathLabel,
                loggingPathField,
                folderLabel
                // ... any other UI elements you want to include before the start button
        );
        String folderFieldsValue = prefs.get("folderFields", "");
        List<TextField> folderFields = new ArrayList<>();
        if (!folderFieldsValue.isEmpty()) {
            String[] folderFieldsArray = folderFieldsValue.split(",");
            for (String folderText : folderFieldsArray) {
                TextField newFolderField = new TextField(folderText);
                folderFields.add(newFolderField);
                vBox.getChildren().add(newFolderField);
            }
        }

        // Button to add new folder fields
        Button addButton = new Button("+");
        addButton.setOnAction(e -> {
            TextField newFolderField = new TextField();
            folderFields.add(newFolderField);
            vBox.getChildren().add(vBox.getChildren().size() - 1, newFolderField);
        });
        vBox.getChildren().add(addButton);


        // Start button
        Button startButton = new Button("Start");
        startButton.setMaxWidth(Double.MAX_VALUE);


        BankMessageMover messageMover = new BankMessageMover(primaryStage);

        // Set the start button action
        startButton.setOnAction(e -> {
            // Implement your start button logic here
            // This is where you would call your BankMessageMover's start method
            String sboPath = sboPathField.getText();
            String bankPath = bankPathField.getText();
            String archivationPath = archivationPathField.getText();
            String loggingPath = loggingPathField.getText();
            String printerName = printerNameField.getText();

            long scanInterval = parseScanInterval(scanIntervalField.getText());
            if (!validatePaths(primaryStage, sboPath, bankPath, archivationPath, loggingPath)) {
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
        });
        vBox.getChildren().add(startButton);

        return vBox;
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

    private VBox createSFTPTransferForm(Stage primaryStage) {
        Preferences prefs = Preferences.userRoot().node(PREFS_NODE_NAME_SFTP);

        VBox vBox = new VBox(10);
        vBox.setAlignment(Pos.BASELINE_LEFT);
        vBox.setPadding(new Insets(20, 50, 20, 50));

        Label hostLabel = new Label("Host:");
        Label usernameLabel = new Label("Username:");
        Label passwordLabel = new Label("Password:");
        Label sboLabel = new Label("SB Path:");
        Label bankLabel = new Label("ODB Path:");
        Label scanIntervalLabel = new Label("Scan interval in seconds:");
        Label archivationPathLabel = new Label("Archivation Path:");
        Label loggingPathLabel = new Label("Logging Path:");
        Label printerMappingLabel = new Label("Folder and Printer Mapping:");


        // TextFields for input
        TextField hostField = new TextField(prefs.get("host", ""));
        TextField userNameField = new TextField(prefs.get("username", ""));
        TextField passwordField = new TextField(prefs.get("password", ""));
        TextField sboPathField = new TextField(prefs.get("sboPath", ""));
        TextField bankPathField = new TextField(prefs.get("bankPath", ""));
        TextField scanIntervalField = new TextField(prefs.get("scanInterval", String.valueOf(DEFAULT_SCAN_INTERVAL)));
        TextField archivationPathField = new TextField(prefs.get("archivationPath", ""));
        TextField loggingPathField = new TextField(prefs.get("loggingPath", ""));
        TextField printerNameField = new TextField(prefs.get("printerName", ""));


        Label folderLabel = new Label("ODB Extra Scan Folders:");

        vBox.getChildren().addAll(
                hostLabel,
                hostField,
                usernameLabel,
                userNameField,
                passwordLabel,
                passwordField,
                sboLabel,
                sboPathField,
                bankLabel,
                bankPathField,
                scanIntervalLabel,
                scanIntervalField,
                archivationPathLabel,
                archivationPathField,
                loggingPathLabel,
                loggingPathField,
                folderLabel
        );

        String folderFieldsValue = prefs.get("folderFields", "");
        List<TextField> folderFields = new ArrayList<>();
        if (!folderFieldsValue.isEmpty()) {
            String[] folderFieldsArray = folderFieldsValue.split(",");
            for (String folderText : folderFieldsArray) {
                TextField newFolderField = new TextField(folderText);
                folderFields.add(newFolderField);
                vBox.getChildren().add(newFolderField);
            }
        }

        // Button to add new folder fields
        Button addButton = new Button("+");
        addButton.setOnAction(e -> {
            TextField newFolderField = new TextField();
            folderFields.add(newFolderField);
            vBox.getChildren().add(vBox.getChildren().size() - 2, newFolderField);
        });
        //vBox.getChildren().add(addButton);

        // List to keep track of folder and printer fields
        List<Pair<TextField, TextField>> folderPrinterFields = new ArrayList<>();

        // Add button to create new folder-printer pair inputs
        Button addFolderPrinterButton = new Button("Add Folder-Printer Pair");
        addFolderPrinterButton.setOnAction(e -> {
            TextField folderField = new TextField();
            folderField.setPromptText("Folder Path");

            TextField printerField = new TextField();
            printerField.setPromptText("Printer Name");

            folderPrinterFields.add(new Pair<>(folderField, printerField));

            HBox hbox = new HBox(10, folderField, printerField);
            vBox.getChildren().add(vBox.getChildren().size() - 1, hbox);
        });

        vBox.getChildren().add(addFolderPrinterButton);

        // Start button
        Button startButton = new Button("Start");
        startButton.setMaxWidth(Double.MAX_VALUE);


        BankMessageSFTPMover messageMover = new BankMessageSFTPMover(primaryStage);

        // Set the start button action
        startButton.setOnAction(e -> {
            // Implement your start button logic here
            // This is where you would call your BankMessageMover's start method
            String host = hostField.getText();
            String username = userNameField.getText();
            String password = passwordField.getText();
            String sboPath = sboPathField.getText();
            String bankPath = bankPathField.getText();
            String archivationPath = archivationPathField.getText();
            String loggingPath = loggingPathField.getText();
            String printerName = printerNameField.getText();

            long scanInterval = parseScanInterval(scanIntervalField.getText());

            List<String> bankFolders = new ArrayList<>();
            for (TextField folderField : folderFields) {
                bankFolders.add(folderField.getText());
            }
            SFTPConfig config = SFTPConfig.builder()
                    .host(host)
                    .password(password)
                    .userName(username)
                    .build();


            messageMover.setSftpConfig(config);
            messageMover.setSftpSboDir(sboPath);
            messageMover.setBankFolders(bankFolders);
            messageMover.setArchivationDir(archivationPath);
            messageMover.setBankDir(bankPath);
            messageMover.setLoggingPath(loggingPath);
            messageMover.setScanInterval(scanInterval);
            messageMover.setBankFolders(bankFolders);
            messageMover.setPrinterName(printerName);


            prefs.put("host", host);
            prefs.put("username", username);
            prefs.put("password", password);
            prefs.put("sboPath", sboPath);
            prefs.put("bankPath", bankPath);
            prefs.put("archivationPath", archivationPathField.getText());
            prefs.put("loggingPath", loggingPathField.getText());
            prefs.put("scanInterval", String.valueOf(scanInterval));
            prefs.put("printerName", printerName);
            String folderPrefs = folderFields.stream().map(TextField::getText).collect(Collectors.joining(","));
            prefs.put("folderFields", folderPrefs);

            Task<Void> task = new Task<Void>() {
                @Override
                protected Void call() throws Exception {
                    messageMover.start();
                    Map<String, String> printerConfig = new HashMap<>();
                    for (Pair<TextField, TextField> pair : folderPrinterFields) {
                        String folder = pair.getKey().getText();
                        String name = pair.getValue().getText();
                        printerConfig.put(folder, name);
                    }

                    try {
                        PrintApplication printApplication = new PrintApplication(bankPath, printerConfig);
                        printApplication.processEvents();
                    } catch (IOException ignored) {

                    }
                    return null;
                }

            };
            new Thread(task).start();
        });
        vBox.getChildren().add(startButton);
        return vBox;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
