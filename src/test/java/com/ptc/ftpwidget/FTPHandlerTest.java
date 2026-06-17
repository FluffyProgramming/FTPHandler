package com.ptc.ftpwidget;

import org.junit.*;
import org.mockftpserver.fake.*;
import org.mockftpserver.fake.filesystem.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.*;

public class FTPHandlerTest {

    private FakeFtpServer fakeFtpServer;
    private int port;

    @Before
    public void setup() {
        fakeFtpServer = new FakeFtpServer();
        fakeFtpServer.setServerControlPort(0);

        UnixFakeFileSystem fs = new UnixFakeFileSystem();
        fs.add(new DirectoryEntry("/files"));
        fakeFtpServer.setFileSystem(fs);

        UserAccount account = new UserAccount("user", "password", "/");
        fakeFtpServer.addUserAccount(account);

        fakeFtpServer.start();
        port = fakeFtpServer.getServerControlPort();
    }

    @After
    public void teardown() {
        fakeFtpServer.stop();
    }

    private ConnectionConfig config() {
        return new ConnectionConfig("localhost", port, "user", "password", "FTP", true, 5000);
    }

    private static byte[] readEntry(FileEntry entry) throws IOException {
        try (InputStream in = entry.createInputStream();
             ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
            byte[] tmp = new byte[4096];
            int n;
            while ((n = in.read(tmp)) != -1) buf.write(tmp, 0, n);
            return buf.toByteArray();
        }
    }

    private static FileEntry fileEntryWithBytes(String path, byte[] content) {
        FileEntry e = new FileEntry(path);
        e.setContents(content);
        return e;
    }

    @Test
    public void uploadWritesFileToRemote() throws Exception {
        byte[] content = "Hello FTP".getBytes(StandardCharsets.UTF_8);
        new FTPHandler(config()).upload("/files", "hello.txt", content);

        FileEntry entry = (FileEntry) fakeFtpServer.getFileSystem().getEntry("/files/hello.txt");
        assertNotNull("File should exist on remote server", entry);
        assertArrayEquals(content, readEntry(entry));
    }

    @Test
    public void downloadRetrievesFileContent() throws Exception {
        byte[] expected = "Download me".getBytes(StandardCharsets.UTF_8);
        fakeFtpServer.getFileSystem().add(fileEntryWithBytes("/files/get.txt", expected));

        byte[] result = new FTPHandler(config()).download("/files", "get.txt");

        assertArrayEquals(expected, result);
    }

    @Test
    public void listDirectoryReturnsFileNames() throws Exception {
        fakeFtpServer.getFileSystem().add(fileEntryWithBytes("/files/report.pdf", new byte[512]));
        fakeFtpServer.getFileSystem().add(fileEntryWithBytes("/files/data.csv", new byte[128]));

        List<FileInfo> files = new FTPHandler(config()).listDirectory("/files");

        assertEquals(2, files.size());
        assertTrue(files.stream().anyMatch(f -> f.fileName.equals("report.pdf")));
        assertTrue(files.stream().anyMatch(f -> f.fileName.equals("data.csv")));
    }

    @Test
    public void listDirectoryReturnsFileSizes() throws Exception {
        fakeFtpServer.getFileSystem().add(fileEntryWithBytes("/files/sized.bin", new byte[999]));

        List<FileInfo> files = new FTPHandler(config()).listDirectory("/files");

        assertEquals(1, files.size());
        assertEquals(999L, files.get(0).fileSize);
    }

    @Test(expected = RuntimeException.class)
    public void uploadThrowsOnBadCredentials() throws Exception {
        ConnectionConfig bad = new ConnectionConfig("localhost", port, "user", "wrongpass", "FTP", true, 5000);
        new FTPHandler(bad).upload("/files", "fail.txt", new byte[]{1});
    }

    @Test
    public void listDirectoryOnEmptyDirectoryReturnsEmptyList() throws Exception {
        List<FileInfo> files = new FTPHandler(config()).listDirectory("/files");
        assertNotNull(files);
        assertTrue(files.isEmpty());
    }
}
