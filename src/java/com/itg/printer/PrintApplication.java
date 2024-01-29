package com.itg.printer;

import javax.print.*;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.Map;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;

public class PrintApplication {

    private final Path dir;
    private final WatchService watcher;
    private final Map<String, String> folderPrinterMap;

    public PrintApplication(String dirString, Map<String, String> folderPrinterMap) throws IOException {
        this.dir = Paths.get(dirString);
        this.watcher = FileSystems.getDefault().newWatchService();
        this.folderPrinterMap = folderPrinterMap;
        dir.register(watcher, ENTRY_CREATE);
    }

    public void processEvents() {
        for (; ; ) {
            WatchKey key;
            try {
                key = watcher.take();
            } catch (InterruptedException x) {
                return;
            }

            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();

                if (kind == StandardWatchEventKinds.OVERFLOW) {
                    continue;
                }

                @SuppressWarnings("unchecked")
                WatchEvent<Path> ev = (WatchEvent<Path>) event;
                Path filename = ev.context();

                String folderName = filename.getParent().getFileName().toString();
                String printerName = folderPrinterMap.getOrDefault(folderName, "Default Printer");
                System.out.println("New file detected: " + filename + ", Printer: " + printerName);
                onPrintEvent(dir.resolve(filename).toString(), printerName);
            }

            boolean valid = key.reset();
            if (!valid) {
                break;
            }
        }
    }

    public void onPrintEvent(String filePath, String printerName) {
        DocFlavor flavor = DocFlavor.INPUT_STREAM.AUTOSENSE;
        PrintRequestAttributeSet pras = new HashPrintRequestAttributeSet();

        PrintService[] printServices = PrintServiceLookup.lookupPrintServices(flavor, pras);
        PrintService selectedService = null;

        for (PrintService service : printServices) {
            if (service.getName().equalsIgnoreCase(printerName)) {
                selectedService = service;
                break;
            }
        }

        if (selectedService != null) {
            DocPrintJob job = selectedService.createPrintJob();
            try (FileInputStream fis = new FileInputStream(filePath)) {
                Doc doc = new SimpleDoc(fis, flavor, null);
                job.print(doc, pras);
            } catch (PrintException | IOException e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("Printer not found: " + printerName);
        }
    }
}
