package com.itg;

import javax.print.*;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PrintUtils {

    public static void printFile(String printerName, Path filePath, Logger logger) {
        try {
            // Locate a print service that can handle the print job
            PrintService[] printServices = PrintServiceLookup.lookupPrintServices(null, null);
            PrintService printService = null;

            for (PrintService service : printServices) {
                if (service.getName().equalsIgnoreCase(printerName)) {
                    printService = service;
                    break;
                }
            }

            if (printService == null) {
                logger.log(Level.WARNING, "Print service not found.");
                return;
            }

            // Prepare the file for printing
            try (InputStream fis = new FileInputStream(filePath.toFile())) {
                DocFlavor flavor = DocFlavor.INPUT_STREAM.TEXT_PLAIN_UTF_8;
                Doc doc = new SimpleDoc(fis, flavor, null);

                // Create a print job from the chosen print service
                DocPrintJob printJob = printService.createPrintJob();
                PrintRequestAttributeSet attributes = new HashPrintRequestAttributeSet();

                // Print the document
                printJob.print(doc, attributes);
                logger.log(Level.INFO, "Sent to printer: " + filePath);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Printing failed: " + e.getMessage(), e);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to access print service: " + e.getMessage(), e);
        }
    }
}
