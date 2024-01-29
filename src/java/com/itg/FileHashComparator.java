package com.itg;

import com.jcraft.jsch.ChannelSftp;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

public class FileHashComparator {

    public static boolean compareFileHashes(Path localFile, String remoteFilePath, ChannelSftp channelSftp) {
        try {
            // Compute hash for local file
            byte[] localFileHash = calculateFileHash(localFile, "SHA-256");

            // Compute hash for remote file
            byte[] remoteFileHash = calculateSftpFileHash(remoteFilePath, channelSftp, "SHA-256");

            // Compare hashes
            return MessageDigest.isEqual(localFileHash, remoteFileHash);
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] calculateFileHash(Path path, String algorithm) throws Exception {
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        byte[] fileBytes = Files.readAllBytes(path);
        digest.update(fileBytes);
        return digest.digest();
    }

    private static byte[] calculateSftpFileHash(String remoteFilePath, ChannelSftp channelSftp, String algorithm) throws Exception {
        MessageDigest digest = MessageDigest.getInstance(algorithm);

        try (InputStream inputStream = channelSftp.get(remoteFilePath)) {
            byte[] buffer = new byte[1024];
            int numRead;
            while ((numRead = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, numRead);
            }
        }
        return digest.digest();
    }
}
