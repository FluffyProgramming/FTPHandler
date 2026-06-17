# ThingWorx FTP Extension — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a ThingWorx SDK Java Extension that enables bidirectional FTP/FTPS/SFTP file transfer via a configurable `FTPConnection` Thing Template.

**Architecture:** Stateless connection-per-call pattern. A `FTPConnectionThing` Thing Template reads `ConnectionSettings` from a config table, uses `FTPClientFactory` to select the right handler (`FTPHandler` for FTP/FTPS via Apache Commons Net, `SFTPHandler` for SFTP via JSch), executes the operation, and always disconnects in a `finally` block.

**Tech Stack:** Java 8+, ThingWorx Extension SDK (from local install), Apache Commons Net 3.9.0, JSch 0.1.55, MockFtpServer 2.8.0 (test), Apache MINA SSHD 2.12.0 (test), JUnit 4.13.2, Gradle

---

## File Map

| File | Responsibility |
|------|---------------|
| `build.gradle` | Gradle build: SDK classpath, dependencies, extension zip task |
| `metadata.xml` | ThingWorx extension manifest: JARs, Thing Template registration |
| `src/main/java/com/ptc/ftpwidget/FTPExtension.java` | Extension entry point — minimal, required by SDK |
| `src/main/java/com/ptc/ftpwidget/ConnectionConfig.java` | Immutable POJO holding all connection settings |
| `src/main/java/com/ptc/ftpwidget/FileInfo.java` | Immutable POJO for directory listing entries |
| `src/main/java/com/ptc/ftpwidget/IFTPHandler.java` | Interface: upload, download, listDirectory |
| `src/main/java/com/ptc/ftpwidget/FTPHandler.java` | FTP + FTPS implementation (Apache Commons Net) |
| `src/main/java/com/ptc/ftpwidget/SFTPHandler.java` | SFTP implementation (JSch) |
| `src/main/java/com/ptc/ftpwidget/FTPClientFactory.java` | Creates IFTPHandler based on protocol string |
| `src/main/java/com/ptc/ftpwidget/FTPConnectionThing.java` | ThingWorx Thing Template — services and config table |
| `src/test/java/com/ptc/ftpwidget/FTPHandlerTest.java` | Unit tests for FTPHandler using embedded FTP server |
| `src/test/java/com/ptc/ftpwidget/SFTPHandlerTest.java` | Unit tests for SFTPHandler using embedded SFTP server |
| `src/test/java/com/ptc/ftpwidget/FTPClientFactoryTest.java` | Unit tests for FTPClientFactory |

---

## Task 1: Project Scaffold

**Files:**
- Create: `build.gradle`
- Create: `metadata.xml`
- Create: `src/main/java/com/ptc/ftpwidget/FTPExtension.java`
- Create: `lib/thingworx-sdk/` (directory — user populates with SDK JARs)

- [ ] **Step 1: Create the directory structure**

```
mkdir -p src/main/java/com/ptc/ftpwidget
mkdir -p src/test/java/com/ptc/ftpwidget
mkdir -p lib/thingworx-sdk
```

- [ ] **Step 2: Copy ThingWorx SDK JARs into `lib/thingworx-sdk/`**

Copy these JARs from your ThingWorx server installation at `<ThingWorx-install>/ThingWorx/WEB-INF/lib/`:
- `Thingworx.Foundation-*.jar`
- `log4j-*.jar`
- `slf4j-api-*.jar`

These are `compileOnly` — they will NOT be bundled in the extension zip.

- [ ] **Step 3: Write `build.gradle`**

```groovy
plugins {
    id 'java'
}

group = 'com.ptc'
version = '1.0.0'
sourceCompatibility = '1.8'
targetCompatibility = '1.8'

repositories {
    mavenCentral()
}

dependencies {
    compileOnly fileTree(dir: 'lib/thingworx-sdk', include: ['*.jar'])
    testImplementation fileTree(dir: 'lib/thingworx-sdk', include: ['*.jar'])

    implementation 'commons-net:commons-net:3.9.0'
    implementation 'com.jcraft:jsch:0.1.55'

    testImplementation 'junit:junit:4.13.2'
    testImplementation 'org.mockftpserver:MockFtpServer:2.8.0'
    testImplementation 'org.apache.sshd:sshd-core:2.12.0'
    testImplementation 'org.apache.sshd:sshd-sftp:2.12.0'
    testImplementation 'org.apache.sshd:sshd-common:2.12.0'
    testImplementation 'org.slf4j:slf4j-simple:1.7.36'
}

task buildExtension(type: Zip, dependsOn: jar) {
    archiveFileName = "FTPWidget-${project.version}.zip"
    destinationDirectory = file("$buildDir/extension")

    from('metadata.xml')

    into('lib') {
        from configurations.runtimeClasspath.filter { file ->
            !file.name.startsWith('slf4j-api') &&
            !file.name.startsWith('slf4j-simple')
        }
        from jar.archiveFile
    }
}
```

- [ ] **Step 4: Write `src/main/java/com/ptc/ftpwidget/FTPExtension.java`**

```java
package com.ptc.ftpwidget;

import com.thingworx.packaging.ExtensionPackage;

public class FTPExtension extends ExtensionPackage {
}
```

- [ ] **Step 5: Write a stub `metadata.xml` at the project root (we'll complete it in Task 7)**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Entities>
  <ExtensionPackages>
    <ExtensionPackage name="FTPWidgetExtension"
                      description="FTP/FTPS/SFTP file transfer extension for ThingWorx"
                      vendor="PTC"
                      packageVersion="1.0.0"
                      minimumThingWorxVersion="8.0.0">
      <JARResources>
        <FileResource type="JAR" file="lib/FTPWidget-1.0.0.jar"/>
        <FileResource type="JAR" file="lib/commons-net-3.9.0.jar"/>
        <FileResource type="JAR" file="lib/jsch-0.1.55.jar"/>
      </JARResources>
    </ExtensionPackage>
  </ExtensionPackages>
</Entities>
```

- [ ] **Step 6: Verify Gradle can resolve dependencies**

```bash
./gradlew dependencies --configuration runtimeClasspath
```

Expected: dependency tree printed, no "Could not resolve" errors.

- [ ] **Step 7: Commit**

```bash
git add build.gradle metadata.xml src/ lib/.gitkeep
git commit -m "feat: scaffold ThingWorx FTP extension project"
```

---

## Task 2: Core Interfaces and Data Classes

**Files:**
- Create: `src/main/java/com/ptc/ftpwidget/ConnectionConfig.java`
- Create: `src/main/java/com/ptc/ftpwidget/FileInfo.java`
- Create: `src/main/java/com/ptc/ftpwidget/IFTPHandler.java`

These have no ThingWorx dependencies — purely plain Java. No tests needed (they're data carriers and an interface).

- [ ] **Step 1: Write `ConnectionConfig.java`**

```java
package com.ptc.ftpwidget;

public final class ConnectionConfig {
    public final String host;
    public final int port;
    public final String username;
    public final String password;
    public final String protocol;
    public final boolean passiveMode;
    public final int connectionTimeout;

    public ConnectionConfig(String host, int port, String username, String password,
                            String protocol, boolean passiveMode, int connectionTimeout) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.protocol = protocol;
        this.passiveMode = passiveMode;
        this.connectionTimeout = connectionTimeout;
    }
}
```

- [ ] **Step 2: Write `FileInfo.java`**

```java
package com.ptc.ftpwidget;

import java.util.Date;

public final class FileInfo {
    public final String fileName;
    public final long fileSize;
    public final Date lastModified;

    public FileInfo(String fileName, long fileSize, Date lastModified) {
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.lastModified = lastModified;
    }
}
```

- [ ] **Step 3: Write `IFTPHandler.java`**

```java
package com.ptc.ftpwidget;

import java.util.List;

public interface IFTPHandler {
    void upload(String remotePath, String fileName, byte[] content) throws Exception;
    byte[] download(String remotePath, String fileName) throws Exception;
    List<FileInfo> listDirectory(String remotePath) throws Exception;
}
```

- [ ] **Step 4: Compile to verify no errors**

```bash
./gradlew compileJava
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/ptc/ftpwidget/ConnectionConfig.java \
        src/main/java/com/ptc/ftpwidget/FileInfo.java \
        src/main/java/com/ptc/ftpwidget/IFTPHandler.java
git commit -m "feat: add ConnectionConfig, FileInfo, and IFTPHandler interface"
```

---

## Task 3: FTPHandler (FTP and FTPS)

**Files:**
- Create: `src/main/java/com/ptc/ftpwidget/FTPHandler.java`
- Create: `src/test/java/com/ptc/ftpwidget/FTPHandlerTest.java`

- [ ] **Step 1: Write the failing tests in `FTPHandlerTest.java`**

```java
package com.ptc.ftpwidget;

import org.junit.*;
import org.mockftpserver.fake.*;
import org.mockftpserver.fake.filesystem.*;

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

    @Test
    public void uploadWritesFileToRemote() throws Exception {
        byte[] content = "Hello FTP".getBytes(StandardCharsets.UTF_8);
        new FTPHandler(config()).upload("/files", "hello.txt", content);

        FileEntry entry = (FileEntry) fakeFtpServer.getFileSystem().getEntry("/files/hello.txt");
        assertNotNull("File should exist on remote server", entry);
        assertArrayEquals(content, entry.getContents());
    }

    @Test
    public void downloadRetrievesFileContent() throws Exception {
        byte[] expected = "Download me".getBytes(StandardCharsets.UTF_8);
        fakeFtpServer.getFileSystem().add(new FileEntry("/files/get.txt", expected));

        byte[] result = new FTPHandler(config()).download("/files", "get.txt");

        assertArrayEquals(expected, result);
    }

    @Test
    public void listDirectoryReturnsFileNames() throws Exception {
        fakeFtpServer.getFileSystem().add(new FileEntry("/files/report.pdf", new byte[512]));
        fakeFtpServer.getFileSystem().add(new FileEntry("/files/data.csv", new byte[128]));

        List<FileInfo> files = new FTPHandler(config()).listDirectory("/files");

        assertEquals(2, files.size());
        assertTrue(files.stream().anyMatch(f -> f.fileName.equals("report.pdf")));
        assertTrue(files.stream().anyMatch(f -> f.fileName.equals("data.csv")));
    }

    @Test
    public void listDirectoryReturnsFileSizes() throws Exception {
        fakeFtpServer.getFileSystem().add(new FileEntry("/files/sized.bin", new byte[999]));

        List<FileInfo> files = new FTPHandler(config()).listDirectory("/files");

        assertEquals(1, files.size());
        assertEquals(999L, files.get(0).fileSize);
    }

    @Test(expected = RuntimeException.class)
    public void uploadThrowsOnBadCredentials() throws Exception {
        ConnectionConfig bad = new ConnectionConfig("localhost", port, "user", "wrongpass", "FTP", true, 5000);
        new FTPHandler(bad).upload("/files", "fail.txt", new byte[]{1});
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail (FTPHandler not yet written)**

```bash
./gradlew test --tests "com.ptc.ftpwidget.FTPHandlerTest"
```

Expected: FAIL — `FTPHandler` class not found.

- [ ] **Step 3: Write `FTPHandler.java`**

```java
package com.ptc.ftpwidget;

import org.apache.commons.net.ftp.*;
import org.apache.commons.net.ftp.FTPSClient;

import java.io.*;
import java.util.*;

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
            List<FileInfo> result = new ArrayList<>();
            for (FTPFile f : ftpFiles) {
                if (f.isFile()) {
                    result.add(new FileInfo(
                        f.getName(),
                        f.getSize(),
                        f.getTimestamp().getTime()
                    ));
                }
            }
            return result;
        } finally {
            disconnect(client);
        }
    }
}
```

- [ ] **Step 4: Run tests and confirm they pass**

```bash
./gradlew test --tests "com.ptc.ftpwidget.FTPHandlerTest"
```

Expected: 5 tests pass, BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/ptc/ftpwidget/FTPHandler.java \
        src/test/java/com/ptc/ftpwidget/FTPHandlerTest.java
git commit -m "feat: add FTPHandler with FTP/FTPS support"
```

---

## Task 4: SFTPHandler

**Files:**
- Create: `src/main/java/com/ptc/ftpwidget/SFTPHandler.java`
- Create: `src/test/java/com/ptc/ftpwidget/SFTPHandlerTest.java`

- [ ] **Step 1: Write the failing tests in `SFTPHandlerTest.java`**

```java
package com.ptc.ftpwidget;

import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.apache.sshd.server.auth.password.PasswordAuthenticator;
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
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
./gradlew test --tests "com.ptc.ftpwidget.SFTPHandlerTest"
```

Expected: FAIL — `SFTPHandler` not found.

- [ ] **Step 3: Write `SFTPHandler.java`**

```java
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
            try { channel.exit(); } catch (Exception ignored) {}
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
        } catch (Exception e) {
            throw new RuntimeException(
                "[FTPExtension] LIST failed | protocol=SFTP host=" + config.host +
                " path=" + remotePath + " | " + e.getMessage(), e);
        } finally {
            close(channel, session);
        }
    }
}
```

- [ ] **Step 4: Run tests and confirm they pass**

```bash
./gradlew test --tests "com.ptc.ftpwidget.SFTPHandlerTest"
```

Expected: 4 tests pass, BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/ptc/ftpwidget/SFTPHandler.java \
        src/test/java/com/ptc/ftpwidget/SFTPHandlerTest.java
git commit -m "feat: add SFTPHandler using JSch"
```

---

## Task 5: FTPClientFactory

**Files:**
- Create: `src/main/java/com/ptc/ftpwidget/FTPClientFactory.java`
- Create: `src/test/java/com/ptc/ftpwidget/FTPClientFactoryTest.java`

- [ ] **Step 1: Write the failing tests**

```java
package com.ptc.ftpwidget;

import org.junit.Test;
import static org.junit.Assert.*;

public class FTPClientFactoryTest {

    @Test
    public void createReturnsFTPHandlerForFTP() {
        ConnectionConfig config = new ConnectionConfig("host", 21, "u", "p", "FTP", true, 5000);
        IFTPHandler handler = FTPClientFactory.create(config);
        assertTrue(handler instanceof FTPHandler);
    }

    @Test
    public void createReturnsFTPHandlerForFTPS() {
        ConnectionConfig config = new ConnectionConfig("host", 21, "u", "p", "FTPS", true, 5000);
        IFTPHandler handler = FTPClientFactory.create(config);
        assertTrue(handler instanceof FTPHandler);
    }

    @Test
    public void createReturnsSFTPHandlerForSFTP() {
        ConnectionConfig config = new ConnectionConfig("host", 22, "u", "p", "SFTP", false, 5000);
        IFTPHandler handler = FTPClientFactory.create(config);
        assertTrue(handler instanceof SFTPHandler);
    }

    @Test(expected = IllegalArgumentException.class)
    public void createThrowsOnUnknownProtocol() {
        ConnectionConfig config = new ConnectionConfig("host", 21, "u", "p", "SCP", false, 5000);
        FTPClientFactory.create(config);
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
./gradlew test --tests "com.ptc.ftpwidget.FTPClientFactoryTest"
```

Expected: FAIL — `FTPClientFactory` not found.

- [ ] **Step 3: Write `FTPClientFactory.java`**

```java
package com.ptc.ftpwidget;

public class FTPClientFactory {

    public static IFTPHandler create(ConnectionConfig config) {
        switch (config.protocol) {
            case "FTP":
            case "FTPS":
                return new FTPHandler(config);
            case "SFTP":
                return new SFTPHandler(config);
            default:
                throw new IllegalArgumentException(
                    "[FTPExtension] Unknown protocol: " + config.protocol +
                    ". Must be FTP, FTPS, or SFTP.");
        }
    }
}
```

- [ ] **Step 4: Run tests and confirm they pass**

```bash
./gradlew test --tests "com.ptc.ftpwidget.FTPClientFactoryTest"
```

Expected: 4 tests pass, BUILD SUCCESSFUL.

- [ ] **Step 5: Run all tests to verify nothing is broken**

```bash
./gradlew test
```

Expected: All tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/ptc/ftpwidget/FTPClientFactory.java \
        src/test/java/com/ptc/ftpwidget/FTPClientFactoryTest.java
git commit -m "feat: add FTPClientFactory"
```

---

## Task 6: FTPConnectionThing (ThingWorx Thing Template)

**Files:**
- Create: `src/main/java/com/ptc/ftpwidget/FTPConnectionThing.java`

This class depends on the ThingWorx SDK JARs in `lib/thingworx-sdk/`. It cannot be unit-tested without the ThingWorx runtime — functional testing happens in Task 8 (import and manual test in ThingWorx).

- [ ] **Step 1: Write `FTPConnectionThing.java`**

```java
package com.ptc.ftpwidget;

import com.thingworx.metadata.DataShapeDefinition;
import com.thingworx.metadata.FieldDefinition;
import com.thingworx.metadata.annotations.*;
import com.thingworx.types.BaseTypes;
import com.thingworx.things.Thing;
import com.thingworx.types.InfoTable;
import com.thingworx.types.collections.ValueCollection;
import com.thingworx.entities.utils.ThingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.List;

@ThingworxBaseTemplateDefinition(name = "GenericThing")
@ThingworxConfigurationTableDefinitions(tables = {
    @ThingworxConfigurationTableDefinition(
        name = "ConnectionSettings",
        description = "FTP Connection Configuration",
        isMultiRow = false,
        dataShape = @ThingworxDataShapeDefinition(fields = {
            @ThingworxFieldDefinition(
                name = "host",
                description = "FTP server hostname or IP",
                baseType = "STRING",
                aspects = {}),
            @ThingworxFieldDefinition(
                name = "port",
                description = "Port (default: 21 for FTP/FTPS, 22 for SFTP)",
                baseType = "INTEGER",
                aspects = {"defaultValue:21"}),
            @ThingworxFieldDefinition(
                name = "username",
                description = "FTP username",
                baseType = "STRING",
                aspects = {}),
            @ThingworxFieldDefinition(
                name = "password",
                description = "FTP password",
                baseType = "PASSWORD",
                aspects = {}),
            @ThingworxFieldDefinition(
                name = "protocol",
                description = "Transfer protocol: FTP, FTPS, or SFTP",
                baseType = "STRING",
                aspects = {"defaultValue:FTP"}),
            @ThingworxFieldDefinition(
                name = "passiveMode",
                description = "Use passive mode — set true for cloud/NAT environments (FTP/FTPS only)",
                baseType = "BOOLEAN",
                aspects = {"defaultValue:true"}),
            @ThingworxFieldDefinition(
                name = "connectionTimeout",
                description = "Connection timeout in milliseconds",
                baseType = "INTEGER",
                aspects = {"defaultValue:10000"})
        })
    )
})
public class FTPConnectionThing extends Thing {

    private static final Logger logger = LoggerFactory.getLogger(FTPConnectionThing.class);

    private ConnectionConfig readConfig() throws Exception {
        ValueCollection row = getConfigurationTable("ConnectionSettings").getFirstRow();
        if (row == null) {
            throw new RuntimeException("[FTPExtension] ConnectionSettings is not configured on this Thing.");
        }
        return new ConnectionConfig(
            row.getStringValue("host"),
            row.getIntegerValue("port"),
            row.getStringValue("username"),
            row.getStringValue("password"),
            row.getStringValue("protocol"),
            row.getBooleanValue("passiveMode"),
            row.getIntegerValue("connectionTimeout")
        );
    }

    // -------------------------------------------------------------------------
    // UploadFile
    // -------------------------------------------------------------------------

    @ThingworxServiceDefinition(
        name = "UploadFile",
        description = "Upload a file to the FTP server. Provide fileContent (BLOB) OR repositoryName + repositoryPath.")
    @ThingworxServiceResult(name = "result", description = "", baseType = "NOTHING")
    public void UploadFile(
        @ThingworxServiceParameter(
            name = "remotePath",
            description = "Remote directory path, e.g. /reports/2026/",
            baseType = "STRING") String remotePath,
        @ThingworxServiceParameter(
            name = "fileName",
            description = "File name on the remote server, e.g. report.pdf",
            baseType = "STRING") String fileName,
        @ThingworxServiceParameter(
            name = "fileContent",
            description = "Raw file bytes (BLOB). Takes precedence over repository parameters.",
            baseType = "BLOB",
            aspects = {"isRequired:false"}) byte[] fileContent,
        @ThingworxServiceParameter(
            name = "repositoryName",
            description = "ThingWorx File Repository Thing name. Used when fileContent is not provided.",
            baseType = "STRING",
            aspects = {"isRequired:false"}) String repositoryName,
        @ThingworxServiceParameter(
            name = "repositoryPath",
            description = "Full file path within the File Repository, e.g. /pdfs/output.pdf",
            baseType = "STRING",
            aspects = {"isRequired:false"}) String repositoryPath
    ) throws Exception {
        ConnectionConfig config = readConfig();

        byte[] content = fileContent;
        if (content == null || content.length == 0) {
            if (isEmpty(repositoryName) || isEmpty(repositoryPath)) {
                throw new RuntimeException(
                    "[FTPExtension] UploadFile requires either fileContent or both repositoryName and repositoryPath.");
            }
            content = readFromRepository(repositoryName, repositoryPath);
        }

        try {
            FTPClientFactory.create(config).upload(remotePath, fileName, content);
        } catch (RuntimeException e) {
            logger.error(e.getMessage(), e);
            throw e;
        }
    }

    // -------------------------------------------------------------------------
    // DownloadFile
    // -------------------------------------------------------------------------

    @ThingworxServiceDefinition(
        name = "DownloadFile",
        description = "Download a file from the FTP server and return its content as BLOB.")
    @ThingworxServiceResult(name = "result", description = "File content", baseType = "BLOB")
    public byte[] DownloadFile(
        @ThingworxServiceParameter(
            name = "remotePath",
            description = "Remote directory path",
            baseType = "STRING") String remotePath,
        @ThingworxServiceParameter(
            name = "fileName",
            description = "File name to download",
            baseType = "STRING") String fileName
    ) throws Exception {
        ConnectionConfig config = readConfig();
        try {
            return FTPClientFactory.create(config).download(remotePath, fileName);
        } catch (RuntimeException e) {
            logger.error(e.getMessage(), e);
            throw e;
        }
    }

    // -------------------------------------------------------------------------
    // ListDirectory
    // -------------------------------------------------------------------------

    @ThingworxServiceDefinition(
        name = "ListDirectory",
        description = "List files in a remote directory. Returns fileName, fileSize, lastModified.")
    @ThingworxServiceResult(name = "result", description = "Directory listing", baseType = "INFOTABLE")
    public InfoTable ListDirectory(
        @ThingworxServiceParameter(
            name = "remotePath",
            description = "Remote directory path",
            baseType = "STRING") String remotePath
    ) throws Exception {
        ConnectionConfig config = readConfig();
        try {
            List<FileInfo> files = FTPClientFactory.create(config).listDirectory(remotePath);

            DataShapeDefinition dsd = new DataShapeDefinition();
            dsd.addFieldDefinition(new FieldDefinition("fileName", BaseTypes.STRING));
            dsd.addFieldDefinition(new FieldDefinition("fileSize", BaseTypes.LONG));
            dsd.addFieldDefinition(new FieldDefinition("lastModified", BaseTypes.DATETIME));
            InfoTable table = new InfoTable(dsd);

            for (FileInfo file : files) {
                ValueCollection row = new ValueCollection();
                row.SetStringValue("fileName", file.fileName);
                row.SetLongValue("fileSize", file.fileSize);
                row.SetDateTimeValue("lastModified", file.lastModified);
                table.addRow(row);
            }
            return table;
        } catch (RuntimeException e) {
            logger.error(e.getMessage(), e);
            throw e;
        }
    }

    // -------------------------------------------------------------------------
    // File Repository helper
    // -------------------------------------------------------------------------

    private byte[] readFromRepository(String repositoryName, String repositoryPath) throws Exception {
        Thing repoThing = ThingUtilities.findThing(repositoryName);
        if (repoThing == null) {
            throw new RuntimeException("[FTPExtension] File Repository not found: " + repositoryName);
        }
        // Cast to FileRepository to access the file stream API
        // Note: import com.thingworx.things.repository.FileRepository if available in your SDK version
        if (!(repoThing instanceof com.thingworx.things.repository.FileRepository)) {
            throw new RuntimeException(
                "[FTPExtension] Thing '" + repositoryName + "' is not a FileRepository.");
        }
        com.thingworx.things.repository.FileRepository repo =
            (com.thingworx.things.repository.FileRepository) repoThing;

        try (InputStream is = repo.getStream(repositoryPath);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
            }
            return baos.toByteArray();
        }
    }

    private static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }
}
```

> **Note on `readFromRepository`:** The `FileRepository.getStream(path)` method name and `InfoTableInstanceFactory.createDataShape` signatures can vary between ThingWorx SDK versions (8.x vs 9.x). If compilation fails on these, check your SDK's `com.thingworx.things.repository.FileRepository` and `com.thingworx.data.util.InfoTableInstanceFactory` JavaDocs for the correct method names. An alternative is to call `GetFile` via `repoThing.processServiceRequest(...)` and cast the result.

- [ ] **Step 2: Compile and fix any SDK-version-specific issues**

```bash
./gradlew compileJava 2>&1 | grep -E "error:|warning:"
```

Expected: No errors. If `FileRepository.getStream` or `InfoTableInstanceFactory.createDataShape` fails, consult your SDK JavaDocs and adjust those two calls.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/ptc/ftpwidget/FTPConnectionThing.java
git commit -m "feat: add FTPConnectionThing ThingWorx Thing Template"
```

---

## Task 7: Complete metadata.xml

**Files:**
- Modify: `metadata.xml`

ThingWorx reads this file when importing the extension zip. The Thing Template entry tells ThingWorx which Java class to use; the annotations on the class define config tables and services.

- [ ] **Step 1: Replace `metadata.xml` with the complete version**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Entities>
  <ExtensionPackages>
    <ExtensionPackage name="FTPWidgetExtension"
                      description="FTP/FTPS/SFTP file transfer extension for ThingWorx"
                      vendor="PTC"
                      packageVersion="1.0.0"
                      minimumThingWorxVersion="8.0.0">
      <JARResources>
        <FileResource type="JAR" file="lib/FTPWidget-1.0.0.jar"/>
        <FileResource type="JAR" file="lib/commons-net-3.9.0.jar"/>
        <FileResource type="JAR" file="lib/jsch-0.1.55.jar"/>
      </JARResources>
    </ExtensionPackage>
  </ExtensionPackages>

  <ThingTemplates>
    <ThingTemplate name="FTPConnection"
                   description="FTP/FTPS/SFTP connection configuration and file transfer services"
                   baseThingTemplate="GenericThing"
                   className="com.ptc.ftpwidget.FTPConnectionThing"
                   documentationContent="">
    </ThingTemplate>
  </ThingTemplates>
</Entities>
```

- [ ] **Step 2: Commit**

```bash
git add metadata.xml
git commit -m "feat: complete metadata.xml with FTPConnection Thing Template registration"
```

---

## Task 8: Build, Package, and Smoke Test

**Files:**
- No new files — runs Gradle tasks and imports into ThingWorx

- [ ] **Step 1: Run all tests one final time**

```bash
./gradlew test
```

Expected: All tests pass, BUILD SUCCESSFUL.

- [ ] **Step 2: Build the extension zip**

```bash
./gradlew buildExtension
```

Expected: `build/extension/FTPWidget-1.0.0.zip` created, BUILD SUCCESSFUL.

- [ ] **Step 3: Verify zip contents**

```bash
unzip -l build/extension/FTPWidget-1.0.0.zip
```

Expected output should include:
```
metadata.xml
lib/FTPWidget-1.0.0.jar
lib/commons-net-3.9.0.jar
lib/jsch-0.1.55.jar
```

If any JAR is missing or SDK JARs are accidentally bundled, fix the `buildExtension` task's `runtimeClasspath` filter in `build.gradle`.

- [ ] **Step 4: Import into ThingWorx**

1. Open ThingWorx Composer
2. Navigate to **Import/Export → Import**
3. Select `build/extension/FTPWidget-1.0.0.zip`
4. Click **Import**

Expected: No errors, "FTPWidgetExtension" appears under **Subsystems → Extension Manager**.

- [ ] **Step 5: Create a test FTPConnection Thing**

1. In Composer, create a new Thing
2. Set **Base Thing Template** to `FTPConnection`
3. Name it `TestFTPConnection`
4. Open the **Configuration** tab
5. Fill in `ConnectionSettings` with your FTP server credentials
6. Save

- [ ] **Step 6: Test UploadFile service**

In the **Services** tab of `TestFTPConnection`, execute `UploadFile` with:
- `remotePath`: `/` (or a valid remote directory)
- `fileName`: `test-upload.txt`
- `fileContent`: any small byte array (use the ThingWorx base64 field)

Expected: Service completes without error; verify the file appears on the FTP server.

- [ ] **Step 7: Test ListDirectory service**

Execute `ListDirectory` with `remotePath`: `/`

Expected: Returns an INFOTABLE with `fileName`, `fileSize`, `lastModified` columns showing the uploaded file.

- [ ] **Step 8: Test DownloadFile service**

Execute `DownloadFile` with `remotePath`: `/` and `fileName`: `test-upload.txt`

Expected: Returns a BLOB with the file content.

- [ ] **Step 9: Final commit**

```bash
git add .
git commit -m "feat: ThingWorx FTP extension v1.0.0 complete"
```

---

## Known Caveats

- **`FileRepository.getStream(path)`** — method name may differ in your SDK version. If it doesn't compile, check the `com.thingworx.things.repository.FileRepository` class in your SDK's JavaDoc.
- **`ValueCollection.SetDateTimeValue`** — takes a `java.util.Date` in most SDK versions; if your SDK differs, replace with `SetValue("lastModified", new DatetimePrimitive(file.lastModified.getTime()))` using `com.thingworx.types.primitives.DatetimePrimitive`.
- **FTPS implicit mode** — tested against port 990. If your FTPS server uses a non-standard port with implicit TLS, update the `FTPHandler.connect()` logic accordingly.
- **Host key verification (SFTP)** — `StrictHostKeyChecking=no` is set for compatibility. For production hardening, configure JSch with a known-hosts file.
