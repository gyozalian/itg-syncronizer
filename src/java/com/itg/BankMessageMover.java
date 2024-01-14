package com.itg;

import javafx.application.Platform;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BankMessageMover {
    private Stage stage;

    static Logger logger = Logger.getLogger("BankMessageMover");

    private static final Set<String> ALLOWED_FILE_EXTENSIONS = Set.of("xml", "rje", "prt", "DOS", "csv");

    private static final Set<String> BANK_FOLDERS = new HashSet<>(Arrays.asList("FromODB"));
    private static final String FROM_ODB_FOLDER = "FromODB";
    private static final String BATCH_FOLDER = "batch";
    private static final String MSG_ARCH_FOLDER = "msgArch";
    private long SCAN_INTERVAL_SECONDS;
    private static final long TWO_MINUTES = 2 * 60 * 1000;
    private volatile boolean stopProcessing = false;

    private ScheduledExecutorService scheduler;

    private static final String FILE_SEPARATOR = FileSystems.getDefault().getSeparator();


    private Path archivationDir;
    private Path sboDir;
    private Path bankDir;

    private String printerName;



    public BankMessageMover(Stage stage) {
        this.stage = stage;
    }

    public void setScanInterval(long SCAN_INTERVAL_SECONDS) {
        this.SCAN_INTERVAL_SECONDS = SCAN_INTERVAL_SECONDS;
    }

    public void setArchivationDir(String archivationDir) {
        this.archivationDir =  Paths.get(archivationDir);
    }

    public void setSboDir(String sboDir) {
        this.sboDir =  Paths.get(sboDir);
    }

    public void setBankDir(String bankDir) {
        this.bankDir = Paths.get(bankDir);
    }

    public void setLoggingPath(String loggingPath){
        configureLogger(loggingPath);
    }

    public void setPrinterName(String printerName){
        this.printerName = printerName;
    }


    /**
     * Configures the logger to write to a log file in the specified directories.
     * The log file name is based on the current date.
     */
    private void configureLogger(String loggingPath) {
        FileHandler handler = LoggerConfiguration.configureLogger(loggingPath);
        logger.addHandler(handler);
    }

    /*
     * Starts the file copying and scanning process.
     * The process runs every 2 minutes.
     * The process is started in a separate thread.
     */
    public void start() {
        logger.log(Level.INFO, "Application Started.");

        scheduler = Executors.newScheduledThreadPool(2);
        //scan interval configurable
        scheduler.scheduleAtFixedRate(this::copyAndScanFiles, 0, SCAN_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    public void stop() {
        if(scheduler != null){
            scheduler.shutdown();
            scheduler = null;
            logger.log(Level.INFO, "Application stopped.");
        }
    }

    /**
     * Copies and scans files from the source directory to the destination directory.
     * The files are copied if they are older than 2 minutes.
     */
    private void copyAndScanFiles() {
        CompletableFuture<Void> bankToSBOFuture = CompletableFuture.runAsync(() -> {
            scanAndCopyFiles(true);

        });
        CompletableFuture<Void> sboToBankFuture = CompletableFuture.runAsync(() -> {
            scanAndCopyFiles(false);

        });

        try {
            CompletableFuture<Void> combinedFuture = CompletableFuture.allOf(bankToSBOFuture, sboToBankFuture);
            combinedFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            logger.log(Level.SEVERE, "Error during file copying and scanning.", e);
        }
    }


    /**
     * Scans the source directory and copies files to the destination directory and archives in the Archive directory.
     *
     * @param fromBank If true, the files are copied from the bank directory to the SBO directory.
     */
        void scanAndCopyFiles(boolean fromBank) {
        Path sourceDir = fromBank ? this.bankDir : this.sboDir;
        Path destinationDir = fromBank ? this.sboDir : this.bankDir;

        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            logger.log(Level.WARNING, "Source directory does not exist or is not a directory.");
            return;
        }

        if (!Files.exists(destinationDir) || !Files.isDirectory(destinationDir)) {
            logger.log(Level.WARNING, "Destination directory does not exist or is not a directory.");
            return;
        }

        try {
            Files.walkFileTree(sourceDir, EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if(dir.getFileName() == null){
                        return FileVisitResult.CONTINUE;
                    }
                    String dirName = dir.getFileName().toString();
                    if (dirName.matches("^\\d{4}$")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    //check if not from bank the folder should be or msgArch or batch
                    if (!fromBank) {
                        Path relativePath = sboDir.relativize(dir);
                        if (relativePath.toString().isEmpty()) {
                            return FileVisitResult.CONTINUE;
                        }

                        if (relativePath.startsWith(BATCH_FOLDER + FILE_SEPARATOR + FROM_ODB_FOLDER) &&
                                (!relativePath.startsWith(BATCH_FOLDER) || !relativePath.startsWith(MSG_ARCH_FOLDER))) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }

                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws FileAlreadyExistsException {
                    Path filePath = sourceDir.relativize(file);
                    String fileName = filePath.toString();
                    String fileExtension = fileName.substring(fileName.lastIndexOf(".") + 1);

                    if (!ALLOWED_FILE_EXTENSIONS.contains(fileExtension)) {
                        return FileVisitResult.CONTINUE;
                    }

                    // Define the batch directory structure based on the file path
                    String bankDestinationDir = "";
                    Set<String> fileNames = Set.of(fileName.split("\\\\"));
                    if (fromBank) {
                        Set<String> scannFolders = new HashSet<>(BANK_FOLDERS);
                        Set<String> fileStructure = new HashSet<>(fileNames);

                        Set<String> commanNames = new HashSet<>(scannFolders);
                        commanNames.retainAll(fileStructure);
                        if(commanNames.isEmpty()){
                            return FileVisitResult.CONTINUE;
                        }
                        bankDestinationDir = commanNames.stream().findFirst().get();

                    }

                    if (!fromBank) {
                        Set<String> scannFolders = new HashSet<>(BANK_FOLDERS);
                        Set<String> fileStructure = new HashSet<>(fileNames);

                        Set<String> commanNames = new HashSet<>(scannFolders);
                        commanNames.retainAll(fileStructure);
                        if(!commanNames.isEmpty()){
                            return FileVisitResult.CONTINUE;
                        }
                    }

                    long lastModifiedMillis = attrs.lastModifiedTime().toMillis();
                    long currentTimeMillis = System.currentTimeMillis();
                    Path destinationBatchDir = fromBank ? sboDir.resolve(BATCH_FOLDER + FILE_SEPARATOR + bankDestinationDir) : bankDir;


                    if (currentTimeMillis - lastModifiedMillis >= TWO_MINUTES) {
                        int index = fileName.indexOf(FILE_SEPARATOR);
                        fileName = index != -1 ? fileName.substring(index + 1) : fileName;
                        if (fileName.contains(FROM_ODB_FOLDER + FILE_SEPARATOR)) {
                            fileName = fileName.replace(FROM_ODB_FOLDER + FILE_SEPARATOR, "");
                        }

                        Path destinationFile = destinationBatchDir.resolve(fileName);

                        // Handle the SSBSync directory and folder structure
                        if (!fromBank && filePath.startsWith(MSG_ARCH_FOLDER)) {
                            Path finalArchivationDir = archivationDir.resolve(dateFormattedPath());
                            Path archiveFile = finalArchivationDir.resolve(fileName);
                            copyFile(file, archiveFile);
                        } else {
                            copyFile(file, destinationFile);
                            if (!(printerName == null || printerName.trim().isEmpty())) {
                                PrintUtils.printFile(printerName, destinationFile, logger);
                            }
                        }
                    }

                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error during file scanning and copying.", e);
        }
    }

    private String dateFormattedPath() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd");
        Date currentDate = new Date();
        return dateFormat.format(currentDate);
    }

    /**
     * Copies a file from the source directory to the destination directory.
     * Checks if the copy was successful by comparing the hashes of the source and destination files.
     * If the file is successfully copied and the isArchivation parameter is true, the file is deleted from the source directory.
     * If the file is not successfully copied, it is not deleted from the source directory.
     *
     * @param sourceFile      Source file
     * @param destinationFile Destination file
     */
    void copyFile(Path sourceFile, Path destinationFile) throws FileAlreadyExistsException {
        if (Files.exists(destinationFile)) {
            Platform.runLater(() -> AlertUtils.displayError(stage, "File already exists", "File already exists: " + destinationFile));
            throw new FileAlreadyExistsException("File already exists: " + destinationFile);

        }

        try {
            if (!Files.exists(destinationFile.getParent())) {
                try {
                    Files.createDirectories(destinationFile.getParent());
                } catch (IOException e) {
                    logger.log(Level.WARNING, "Failed to create parent directories: " + destinationFile.getParent(), e);
                    return;  // Exit the method if directories creation fails
                }
            }
            Files.copy(sourceFile, destinationFile, StandardCopyOption.REPLACE_EXISTING);
            //stop process if file already exists

            if (compareFileHashes(sourceFile, destinationFile)) {
                Files.delete(sourceFile);
                logger.log(Level.INFO, "Successfully copied file: " + sourceFile + " to " + destinationFile);
            } else {
                Files.delete(destinationFile);
                logger.log(Level.WARNING, "Hash doesn't match. Will scan again!");
            }

        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to copy file: " + sourceFile, e);
        }
    }

    /**
     * Compares the hashes of two files.
     *
     * @param file1
     * @param file2
     * @return True if the hashes match, false otherwise
     */

    boolean compareFileHashes(Path file1, Path file2) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash1 = md.digest(Files.readAllBytes(file1));
            byte[] hash2 = md.digest(Files.readAllBytes(file2));
            return MessageDigest.isEqual(hash1, hash2);
        } catch (NoSuchAlgorithmException | IOException e) {
            logger.log(Level.WARNING, "Failed to compare file hashes: " + file1 + ", " + file2, e);
            return false;
        }
    }

    public void setBankFolders(List<String> bankFolders) {
        BANK_FOLDERS.addAll(bankFolders);
    }
}
