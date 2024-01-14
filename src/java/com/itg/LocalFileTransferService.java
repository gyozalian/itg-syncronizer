package com.itg;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LocalFileTransferService implements FileTransferService{

    @Override
    public void connect() {
        // No action needed for local connections
    }

    @Override
    public void disconnect() {
        // No action needed for local connections
    }

    @Override
    public void transferFile(Path sourcePath, Path destinationPath) throws IOException {
        Files.copy(sourcePath, destinationPath, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public byte[] getFile(Path path) throws IOException {
        return Files.readAllBytes(path);
    }

    @Override
    public List<Path> listFiles(Path dir) throws IOException {
        try (Stream<Path> paths = Files.walk(dir)) {
            return paths.filter(Files::isRegularFile).collect(Collectors.toList());
        }
    }
}
