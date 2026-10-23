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
        boolean implicitTLS = (config.port == 990);
        if ("FTPS".equals(config.protocol)) {
            client = new FTPSClient(implicitTLS);
        } else {
            client = new FTPClient();
        }

        client.setConnectTimeout(config.connectionTimeout);
        client.connect(config.host, config.port);

        if (!FTPReply.isPositiveCompletion(client.getReplyCode())) {
            String reply = client.getReplyString();
            client.disconnect();
            throw new RuntimeException(
                "[FTPExtension] Connection refused | protocol=" + config.protocol +
                " host=" + config.host + " | reply=" + (reply != null ? reply.trim() : "(no reply)"));
        }

        if (client instanceof FTPSClient && !implicitTLS) {
            try {
                ((FTPSClient) client).execAUTH("TLS");
            } catch (Exception e) {
                String reply = client.getReplyString();
                client.disconnect();
                throw new RuntimeException(
                    "[FTPExtension] AUTH TLS failed | protocol=" + config.protocol +
                    " host=" + config.host +
                    " | " + (reply != null ? reply.trim() : e.getMessage()));
            }
        }

        if (!client.login(config.username, config.password)) {
            String reply = client.getReplyString();
            client.disconnect();
            throw new RuntimeException(
                "[FTPExtension] Login failed | protocol=" + config.protocol +
                " host=" + config.host + " user=" + config.username +
                " | " + (reply != null ? reply.trim() : "(no reply)"));
        }

        if (client instanceof FTPSClient) {
            FTPSClient ftps = (FTPSClient) client;
            ftps.execPBSZ(0);
            ftps.execPROT("P");
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
