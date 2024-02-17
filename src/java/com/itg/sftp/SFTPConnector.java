package com.itg.sftp;


import net.schmizz.sshj.SSHClient;

import java.io.IOException;

public class SFTPConnector {

    private SSHClient sshClient;


    public synchronized SSHClient connect(SFTPConfig config) throws IOException {
        if (sshClient == null) {
            sshClient = new SSHClient();
            sshClient.loadKnownHosts();
        }
        if (sshClient.isConnected()) {
            return sshClient;
        }
        sshClient.connect(config.getHost());
        sshClient.authPassword(config.getUserName(), config.getPassword());
        return sshClient;
    }

    public synchronized void disconnect() throws IOException {
        if (sshClient != null) {
            sshClient.disconnect();
        }
        sshClient = null;
    }
}
