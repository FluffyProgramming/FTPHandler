package com.ptc.ftpwidget;

import com.jcraft.jsch.*;

import java.io.*;
import java.util.*;

public class SFTPHandler implements IFTPHandler {

    private final ConnectionConfig config;

    public SFTPHandler(ConnectionConfig config) {
        this.config = config;
    }

    private Session openSession() throws Exception {
        JSch jsch = new JSch();
        Session session = jsch.getSession(config.username, config.host, config.port);
        session.setPassword(config.password);
        Properties sshConfig = new Properties();
        sshConfig.put("StrictHostKeyChecking", "no");
        session.setConfig(sshConfig);
        session.setTimeout(config.connectionTimeout);
        session.connect(config.connectionTimeout);
        return session;
    }

    private void close(ChannelSftp channel, Session session) {
        if (channel != null && channel.isConnected()) {
            try { channel.disconnect(); } catch (Exception ignored) {}
        }
        if (session != null && session.isConnected()) {
            try { session.disconnect(); } catch (Exception ignored) {}
        }
    }

    @Override
    public void upload(String remotePath, String fileName, byte[] content) throws Exception {
        Session session = null;
        ChannelSftp channel = null;
        try {
            session = openSession();
            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect();
            channel.cd(remotePath);
            try (InputStream is = new ByteArrayInputStream(content)) {
                channel.put(is, fileName);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(
                "[FTPExtension] UPLOAD failed | protocol=SFTP host=" + config.host +
                " path=" + remotePath + "/" + fileName + " | " + e.getMessage(), e);
        } finally {
            close(channel, session);
        }
    }

    @Override
    public byte[] download(String remotePath, String fileName) throws Exception {
        Session session = null;
        ChannelSftp channel = null;
        try {
            session = openSession();
            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect();
            channel.cd(remotePath);
            try (InputStream is = channel.get(fileName);
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    baos.write(buffer, 0, read);
                }
                return baos.toByteArray();
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(
                "[FTPExtension] DOWNLOAD failed | protocol=SFTP host=" + config.host +
                " path=" + remotePath + "/" + fileName + " | " + e.getMessage(), e);
        } finally {
            close(channel, session);
        }
    }

    @Override
    public List<FileInfo> listDirectory(String remotePath) throws Exception {
        Session session = null;
        ChannelSftp channel = null;
        try {
            session = openSession();
            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect();
            @SuppressWarnings("unchecked")
            Vector<ChannelSftp.LsEntry> entries = channel.ls(remotePath);
            List<FileInfo> result = new ArrayList<>();
            for (ChannelSftp.LsEntry entry : entries) {
                if (!entry.getAttrs().isDir() && !entry.getFilename().startsWith(".")) {
                    result.add(new FileInfo(
                        entry.getFilename(),
                        entry.getAttrs().getSize(),
                        new Date(entry.getAttrs().getMTime() * 1000L)
                    ));
                }
            }
            return result;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(
                "[FTPExtension] LIST failed | protocol=SFTP host=" + config.host +
                " path=" + remotePath + " | " + e.getMessage(), e);
        } finally {
            close(channel, session);
        }
    }
}
