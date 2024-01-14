package com.itg;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.SftpClientFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SFTPFileTransferService implements FileTransferService {

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private SshClient client;
    private ClientSession session;

    public SFTPFileTransferService(String host, int port, String username, String password) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
    }

    @Override
    public void connect() throws IOException {
        client = SshClient.setUpDefaultClient();
        client.start();
        session = client.connect(username, host, port).verify().getSession();
        session.addPasswordIdentity(password);
        session.auth().verify();
    }

    @Override
    public void disconnect() throws IOException {
        if (session != null) {
            session.close();
        }
        if (client != null) {
            client.stop();
        }
    }

    @Override
    public void transferFile(Path sourcePath, Path destinationPath) throws IOException {
        SftpClientFactory factory = SftpClientFactory.instance();
        try (SftpClient sftp = factory.createSftpClient(session);
             InputStream localInputStream = Files.newInputStream(sourcePath);
             OutputStream remoteOutputStream = sftp.write(destinationPath.toString())) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = localInputStream.read(buffer)) > 0) {
                remoteOutputStream.write(buffer, 0, length);
            }
        }
    }

    @Override
    public byte[] getFile(Path path) throws IOException {
        SftpClientFactory factory = SftpClientFactory.instance();
        try (SftpClient sftp = factory.createSftpClient(session);
             InputStream remoteInputStream = sftp.read(path.toString());
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = remoteInputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
            return outputStream.toByteArray();
        }
    }

    @Override
    public List<Path> listFiles(Path dir) throws IOException {
        List<Path> files = new ArrayList<>();
        SftpClientFactory factory = SftpClientFactory.instance();
        try (SftpClient sftp = factory.createSftpClient(session)) {
            for (SftpClient.DirEntry entry : sftp.readDir(dir.toString())) {
                if (entry.getAttributes().isRegularFile()) {
                    files.add(dir.resolve(entry.getFilename()));
                }
            }
        }
        return files;
    }
}
