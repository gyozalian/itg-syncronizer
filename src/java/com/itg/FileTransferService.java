package com.itg;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface FileTransferService {
    void connect() throws IOException;

    void disconnect() throws IOException;

    void transferFile(Path sourcePath, Path destinationPath) throws IOException;

    byte[] getFile(Path path) throws IOException;

    List<Path> listFiles(Path dir) throws IOException;
}
