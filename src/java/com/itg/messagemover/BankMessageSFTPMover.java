package com.itg.messagemover;

import com.itg.FileHashComparator;
import com.itg.LoggerConfiguration;
import com.itg.sftp.SFTPConfig;
import com.itg.sftp.SFTPConnector;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;
import javafx.stage.Stage;
import lombok.SneakyThrows;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.Response;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.sftp.SFTPException;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.TimeUnit;

public class BankMessageSFTPMover implements MessageMover {
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
    private SFTPConnector sftpConnector = new SFTPConnector();

    private ScheduledExecutorService scheduler;

    private static final String FILE_SEPARATOR = FileSystems.getDefault().getSeparator();


    private Path archivationDir;
    private Path bankDir;

    private String printerName;

    private String sftpSboDir;

    public void setSftpConfig(SFTPConfig sftpConfig) {
        this.sftpConfig = sftpConfig;
    }

    private SFTPConfig sftpConfig;

    public BankMessageSFTPMover() {
    }

    public BankMessageSFTPMover(Stage stage) {
        this.stage = stage;
    }

    public void setScanInterval(long SCAN_INTERVAL_SECONDS) {
        this.SCAN_INTERVAL_SECONDS = SCAN_INTERVAL_SECONDS;
    }

    public void setArchivationDir(String archivationDir) {
        this.archivationDir = Paths.get(archivationDir);
    }

    public void setBankDir(String bankDir) {
        this.bankDir = Paths.get(bankDir);
    }

    public void setLoggingPath(String loggingPath) {
        configureLogger(loggingPath);
    }

    public void setPrinterName(String printerName) {
        this.printerName = printerName;
    }

    public void setSftpSboDir(String dir) {
        this.sftpSboDir = dir;
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

    @SneakyThrows
    private void copyAndScanFiles() {
        SFTPClient sftpClient;
        try {
            sftpClient = sftpConnector.connect(sftpConfig).newSFTPClient();


            CompletableFuture<Void> bankToSBOFuture = CompletableFuture.runAsync(() -> {
                scanAndCopyFiles(true, sftpClient);

            });
            CompletableFuture<Void> sboToBankFuture = CompletableFuture.runAsync(() -> {
                scanAndCopyFiles(false, sftpClient);

            });

            CompletableFuture<Void> combinedFuture = CompletableFuture.allOf(bankToSBOFuture, sboToBankFuture);
            combinedFuture.get();
        } catch (InterruptedException | ExecutionException | IOException e) {
            logger.log(Level.SEVERE, "Error during file copying and scanning.", e);
        } finally {
            sftpConnector.disconnect();
        }
    }

    public void stop() {
        if (scheduler != null) {
            scheduler.shutdown();
            scheduler = null;
            logger.log(Level.INFO, "Application stopped.");
        }
    }

    private void scanAndCopyFiles(boolean fromBank, SFTPClient sftpClient) {

        try {
            if (fromBank) {
                // Local to SFTP
                transferLocalToSftp(bankDir, sftpSboDir, sftpClient);
            } else {
                // SFTP to Local
                transferSftpToLocal(sftpClient, sftpSboDir, bankDir);
            }
        } catch (IOException | JSchException | SftpException e) {
            logger.log(Level.SEVERE, "Error during file scanning and copying.", e);
        }
    }

    public void transferLocalToSftp(Path localDir, String remoteDir, SFTPClient sftpClient) throws IOException, JSchException {

        Files.walkFileTree(localDir, EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (dir.getFileName() == null) {
                    return FileVisitResult.CONTINUE;
                }
                String dirName = dir.getFileName().toString();
                if (dirName.matches("^\\d{4}$")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path filePath = localDir.relativize(file);
                String fileName = filePath.toString();
                String fileExtension = getFileExtension(fileName);
                Set<String> fileNames = Set.of(fileName.split("\\\\"));

                if (shouldTransferFile(attrs) && ALLOWED_FILE_EXTENSIONS.contains(fileExtension)) {

                    Set<String> scannFolders = new HashSet<>(BANK_FOLDERS);
                    Set<String> fileStructure = new HashSet<>(fileNames);

                    Set<String> commanNames = new HashSet<>(scannFolders);
                    commanNames.retainAll(fileStructure);
                    if (commanNames.isEmpty()) {
                        return FileVisitResult.CONTINUE;
                    }

                    String remoteFilePath = remoteDir + "/" + localDir.relativize(file).toString().replace("\\", "/");
                    try {
                        ensureDirectoryExists(sftpClient, remoteFilePath);
                        sftpClient.put(file.normalize().toString(), remoteFilePath);
                        logger.log(Level.INFO, "File uploaded successfully: " + file);
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Failed to upload file: " + file, e);
                    }
                    if (FileHashComparator.compareFileHashes(localDir, remoteFilePath, sftpClient)) {
                        Files.delete(file);
                        logger.log(Level.INFO, "Successfully copied file: " + localDir + " to " + remoteFilePath);
                    } else {
                        try {
                            sftpClient.rm(remoteFilePath);
                        } catch (IOException e) {
                            logger.log(Level.WARNING, "Unable to delete file from sftp!");
                        }
                        logger.log(Level.WARNING, "Hash doesn't match. Will scan again!");
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });


    }

    private void transferSftpToLocal(SFTPClient channelSftp, String remoteDir, Path localDir) throws
            IOException, SftpException, JSchException {
        List<RemoteResourceInfo> files = channelSftp.ls(remoteDir);

        for (RemoteResourceInfo entry : files) {
            String fileName = entry.getName();
            Set<String> fileNames = Set.of(fileName.split("\\\\"));

            if (fileName.isEmpty()) {
                continue;
            }


            if (".".equals(fileName) || "..".equals(fileName)) {
                continue; // Skip current and parent directory entries
            }
            if (fileName.startsWith(BATCH_FOLDER + FILE_SEPARATOR + FROM_ODB_FOLDER) &&
                    (!fileName.startsWith(BATCH_FOLDER) || !fileName.startsWith(MSG_ARCH_FOLDER))) {
                continue;
            }

            Set<String> scannFolders = new HashSet<>(BANK_FOLDERS);
            Set<String> fileStructure = new HashSet<>(fileNames);

            Set<String> commanNames = new HashSet<>(scannFolders);
            commanNames.retainAll(fileStructure);
            if (!commanNames.isEmpty()) {
                continue;
            }

            if (entry.isDirectory()) {
                // It's a directory, go inside and call recursively
                transferSftpToLocal(channelSftp, remoteDir + "/" + fileName, localDir);
            } else {
                String fileExtension = getFileExtension(fileName);
                if (shouldTransferFile(entry) && ALLOWED_FILE_EXTENSIONS.contains(fileExtension)) {
                    int index = fileName.indexOf(FILE_SEPARATOR);
                    fileName = index != -1 ? fileName.substring(index + 1) : fileName;
                    if (fileName.contains(FROM_ODB_FOLDER + FILE_SEPARATOR)) {
                        fileName = fileName.replace(FROM_ODB_FOLDER + FILE_SEPARATOR, "");
                    }

                    // Handle the SSBSync directory and folder structure
                    if (fileName.startsWith(MSG_ARCH_FOLDER)) {
                        Path finalArchivationDir = archivationDir.resolve(dateFormattedPath());
                        Path archiveFile = finalArchivationDir.resolve(fileName);
                        channelSftp.get(remoteDir + "/" + fileName, archiveFile.normalize().toString());
                    } else {
                        String localFilePath = localDir.normalize().toString();
                        channelSftp.get(remoteDir + "/" + fileName, localFilePath);
                        logger.log(Level.INFO, "File downloaded successfully: " + localFilePath);
                    }
                }
            }
        }

    }


    private boolean shouldTransferFile(BasicFileAttributes attrs) {
        long lastModifiedMillis = attrs.lastModifiedTime().toMillis();
        long currentTimeMillis = System.currentTimeMillis();
        return currentTimeMillis - lastModifiedMillis >= TWO_MINUTES;
    }

    private boolean shouldTransferFile(RemoteResourceInfo entry) {
        long modTime = entry.getAttributes().getMtime(); // Get modification time in seconds
        long modTimeMillis = modTime * 1000;
        long currentTimeMillis = System.currentTimeMillis();
        return currentTimeMillis - modTimeMillis >= TWO_MINUTES;
    }

    private static String getFileExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf(".");
        return dotIndex == -1 ? "" : fileName.substring(dotIndex + 1);
    }

    private static void ensureDirectoryExists(SFTPClient sftp, String directoryPath) throws IOException {
        try {
            sftp.ls(directoryPath); // Check if directory exists
        } catch (SFTPException e) {
            if (e.getStatusCode() == Response.StatusCode.NO_SUCH_FILE) {
                sftp.mkdirs(directoryPath); // Create directory if it does not exist
            } else {
                throw e; // Re-throw if other exception
            }
        }
    }

    private String dateFormattedPath() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd");
        Date currentDate = new Date();
        return dateFormat.format(currentDate);
    }

    public void setBankFolders(List<String> bankFolders) {
        BANK_FOLDERS.addAll(bankFolders);
    }
}
