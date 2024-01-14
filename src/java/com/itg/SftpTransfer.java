package com.itg;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.SftpClientFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.nio.file.Path;


public class SftpTransfer {
    public void transferFile(String hostname, int port, String username, String password, Path localFilePath, String remoteFilePath) {
        SshClient client = SshClient.setUpDefaultClient();
        client.start();

        try (ClientSession session = client.connect(username, hostname, port).verify(30, TimeUnit.SECONDS).getSession()) {
            session.addPasswordIdentity(password);
            session.auth().verify(30, TimeUnit.SECONDS);

            transferUsingSftp(session, localFilePath, remoteFilePath);

        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            client.stop();
        }
    }

    private void transferUsingSftp(ClientSession session, Path localFilePath, String remoteFilePath) throws IOException {
//        SftpClientFactory factory = SftpClientFactory.instance();
//        try (SftpClient sftp = factory.createSftpClient(session)) {
//            sftp.put(localFilePath, remoteFilePath);
//        }
    }
}
