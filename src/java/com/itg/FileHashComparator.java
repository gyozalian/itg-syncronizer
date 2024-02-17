package com.itg;

import net.schmizz.sshj.sftp.OpenMode;
import net.schmizz.sshj.sftp.RemoteFile;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.sftp.SFTPException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumSet;

public class FileHashComparator {

    public static boolean compareFileHashes(Path localFile, String remoteFilePath, SFTPClient sftpClient) {
        return true;
//        try {
//            // Compute hash for local file
//            byte[] localFileHash = calculateFileHash(localFile, "SHA-256");
//
//            // Compute hash for remote file
//            byte[] remoteFileHash = calculateSftpFileHash(remoteFilePath, sftpClient, "SHA-256");
//
//            // Compare hashes
//            return MessageDigest.isEqual(localFileHash, remoteFileHash);
//        } catch (Exception e) {
//            return false;
//        }
    }

}
