package com.itg.sftp;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import java.io.FileInputStream;
import java.util.Properties;

public class SFTPConnector {

    private static final JSch jsch = new JSch();
    private static Session session = null;
    private static ChannelSftp channelSftp = null;


    public static void init(SFTPConfig config) throws JSchException {
        // Close existing session and channel if they are open
        disconnect();
//        if (session != null) {
//            return;
//        }
        session = jsch.getSession(config.getUserName(), config.getHost(), config.getPort());
        Properties keyConfigs = new Properties();
        session.setConfig("kex", "diffie-hellman-group1-sha1");
//        session.setConfig(keyConfigs);

        session.setTimeout(10000);
        session.setPassword(config.getPassword());
    }

    public static ChannelSftp connect(SFTPConfig config) throws JSchException {
        try {
            init(config);

            // Only connect if the session isn't already connected
            if (!session.isConnected()) {
                session.connect();
            }

            // Only open a new channel if it's not already open
            if (channelSftp == null || !channelSftp.isConnected()) {
                channelSftp = (ChannelSftp) session.openChannel("sftp");
                channelSftp.connect();
            }
            return channelSftp;
        } catch (Exception e) {
            throw e;
        }


    }

    public static void disconnect() {
        if (channelSftp != null && channelSftp.isConnected()) {
            channelSftp.disconnect();
        }
        channelSftp = null;
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
        session = null;
    }
}
