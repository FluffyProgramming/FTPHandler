package com.ptc.ftpwidget;

import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.junit.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class SFTPHandlerTest {

    private SshServer sshd;
    private int port;
    private Path tempDir;

    @Before
    public void setup() throws Exception {
        tempDir = Files.createTempDirectory("sftp-test");

        sshd = SshServer.setUpDefaultServer();
        sshd.setPort(0);
        sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(
            tempDir.resolve("hostkey.ser")));
        sshd.setPasswordAuthenticator(
            (username, password, session) -> "user".equals(username) && "password".equals(password));
        sshd.setSubsystemFactories(
            Collections.singletonList(new SftpSubsystemFactory()));
        sshd.setFileSystemFactory(new VirtualFileSystemFactory(tempDir));
        sshd.start();
        port = sshd.getPort();
    }

    @After
    public void teardown() throws Exception {
        sshd.stop();
        deleteDirectory(tempDir);
    }

    private void deleteDirectory(Path dir) throws IOException {
        if (Files.exists(dir)) {
            Files.walk(dir)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> { try { Files.delete(p); } catch (IOException ignored) {} });
        }
    }

    private ConnectionConfig config() {
        return new ConnectionConfig("localhost", port, "user", "password", "SFTP", false, 5000);
    }

    @Test
    public void uploadWritesFileToRemote() throws Exception {
        byte[] content = "Hello SFTP".getBytes(StandardCharsets.UTF_8);
        new SFTPHandler(config()).upload("/", "hello.txt", content);

        Path uploaded = tempDir.resolve("hello.txt");
        assertTrue("File should exist", Files.exists(uploaded));
        assertArrayEquals(content, Files.readAllBytes(uploaded));
    }

    @Test
    public void downloadRetrievesFileContent() throws Exception {
        byte[] expected = "Download SFTP".getBytes(StandardCharsets.UTF_8);
        Files.write(tempDir.resolve("get.txt"), expected);

        byte[] result = new SFTPHandler(config()).download("/", "get.txt");

        assertArrayEquals(expected, result);
    }

    @Test
    public void listDirectoryReturnsFileNames() throws Exception {
        Files.write(tempDir.resolve("a.pdf"), new byte[10]);
        Files.write(tempDir.resolve("b.csv"), new byte[20]);

        List<FileInfo> files = new SFTPHandler(config()).listDirectory("/");

        List<String> names = new java.util.ArrayList<>();
        for (FileInfo f : files) names.add(f.fileName);
        assertTrue(names.contains("a.pdf"));
        assertTrue(names.contains("b.csv"));
    }

    @Test(expected = RuntimeException.class)
    public void uploadThrowsOnBadCredentials() throws Exception {
        ConnectionConfig bad = new ConnectionConfig("localhost", port, "user", "wrongpass", "SFTP", false, 5000);
        new SFTPHandler(bad).upload("/", "fail.txt", new byte[]{1});
    }

    @Test
    public void listDirectoryReturnsFileSizes() throws Exception {
        Files.write(tempDir.resolve("sized.bin"), new byte[256]);

        List<FileInfo> files = new SFTPHandler(config()).listDirectory("/");

        FileInfo found = null;
        for (FileInfo f : files) {
            if ("sized.bin".equals(f.fileName)) {
                found = f;
                break;
            }
        }
        assertNotNull("File sized.bin should be in listing", found);
        assertEquals(256L, found.fileSize);
    }
}
