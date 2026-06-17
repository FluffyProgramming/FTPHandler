package com.ptc.ftpwidget;

import org.apache.commons.net.ftp.*;
import org.apache.commons.net.ftp.FTPSClient;

import java.io.*;
import java.util.*;
import java.util.Date;

public class FTPHandler implements IFTPHandler {

    private final ConnectionConfig config;

    public FTPHandler(ConnectionConfig config) {
        this.config = config;
    }

    private FTPClient connect() throws Exception {
        FTPClient client;
        if ("FTPS".equals(config.protocol)) {
            boolean implicit = (config.port == 990);
            client = new FTPSClient(implicit);
        } else {
            client = new FTPClient();
        }

        client.setConnectTimeout(config.connectionTimeout);
        client.connect(config.host, config.port);

        if (!FTPReply.isPositiveCompletion(client.getReplyCode())) {
            client.disconnect();
            throw new RuntimeException(
                "[FTPExtension] Connection refused | protocol=" + config.protocol +
                " host=" + config.host + " | reply=" + client.getReplyString().trim());
        }

        if (!client.login(config.username, config.password)) {
            client.disconnect();
            throw new RuntimeException(
                "[FTPExtension] Login failed | protocol=" + config.protocol +
                " host=" + config.host + " user=" + config.username);
        }

        if (config.passiveMode) {
            client.enterLocalPassiveMode();
        }
        client.setFileType(FTP.BINARY_FILE_TYPE);
        return client;
    }

    private void disconnect(FTPClient client) {
        if (client != null && client.isConnected()) {
            try { client.logout(); } catch (Exception ignored) {}
            try { client.disconnect(); } catch (Exception ignored) {}
        }
    }

    @Override
    public void upload(String remotePath, String fileName, byte[] content) throws Exception {
        FTPClient client = null;
        try {
            client = connect();
            String remoteFile = remotePath.endsWith("/")
                ? remotePath + fileName
                : remotePath + "/" + fileName;
            try (InputStream is = new ByteArrayInputStream(content)) {
                if (!client.storeFile(remoteFile, is)) {
                    throw new RuntimeException(
                        "[FTPExtension] UPLOAD failed | protocol=" + config.protocol +
                        " host=" + config.host + " path=" + remoteFile +
                        " | " + client.getReplyString().trim());
                }
            }
        } finally {
            disconnect(client);
        }
    }

    @Override
    public byte[] download(String remotePath, String fileName) throws Exception {
        FTPClient client = null;
        try {
            client = connect();
            String remoteFile = remotePath.endsWith("/")
                ? remotePath + fileName
                : remotePath + "/" + fileName;
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                if (!client.retrieveFile(remoteFile, baos)) {
                    throw new RuntimeException(
                        "[FTPExtension] DOWNLOAD failed | protocol=" + config.protocol +
                        " host=" + config.host + " path=" + remoteFile +
                        " | " + client.getReplyString().trim());
                }
                return baos.toByteArray();
            }
        } finally {
            disconnect(client);
        }
    }

    @Override
    public List<FileInfo> listDirectory(String remotePath) throws Exception {
        FTPClient client = null;
        try {
            client = connect();
            FTPFile[] ftpFiles = client.listFiles(remotePath);
            if (ftpFiles == null) {
                throw new RuntimeException(
                    "[FTPExtension] LIST failed | protocol=" + config.protocol +
                    " host=" + config.host + " path=" + remotePath +
                    " | listFiles returned null (server error or parse failure)");
            }
            List<FileInfo> result = new ArrayList<>();
            for (FTPFile f : ftpFiles) {
                if (f.isFile()) {
                    Date timestamp = f.getTimestamp() != null ? f.getTimestamp().getTime() : null;
                    result.add(new FileInfo(f.getName(), f.getSize(), timestamp));
                }
            }
            return result;
        } finally {
            disconnect(client);
        }
    }
}
