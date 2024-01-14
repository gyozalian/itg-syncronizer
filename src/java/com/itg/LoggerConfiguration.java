package com.itg;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class LoggerConfiguration {

    /**
     * Configures the logger to write to a log file in the specified directories.
     * The log file name is based on the current date.
     *
     * @param bankPath Bank path
     * @return List of FileHandlers
     */

    public static FileHandler configureLogger(String bankPath) {
        String logFilePath = bankPath + "/logs/transfer.log";

        File file = new File(logFilePath);
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }

        FileHandler fileHandler = null;
        try {
            fileHandler = new FileHandler(logFilePath, true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        fileHandler.setFormatter(new SimpleFormatter());

        return fileHandler;
    }
}
