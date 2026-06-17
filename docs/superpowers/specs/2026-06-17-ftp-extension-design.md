# ThingWorx FTP Extension — Design Spec
**Date:** 2026-06-17
**Status:** Approved

---

## Overview

A ThingWorx SDK Java Extension that enables ThingWorx to transfer files to and from FTP servers. Upload (push) is the primary use case — primarily PDFs generated or stored within ThingWorx — but download and directory listing are also included to make the extension general-purpose.

---

## Architecture

The extension is packaged as a standard ThingWorx extension `.zip`, importable via Composer.

**Approach:** Connection-per-call (stateless). Each service invocation opens a fresh connection, executes the operation, and closes the connection in a `finally` block. No connection pooling — chosen for simplicity, cloud compatibility, and alignment with ThingWorx's stateless service model.

**Protocol support:** FTP, FTPS (FTP over SSL/TLS), and SFTP (SSH File Transfer Protocol), selected per-Thing via configuration.

**Libraries:**
- `Apache Commons Net` — FTP and FTPS
- `JSch` — SFTP
- ThingWorx SDK JARs — provided at build time, not bundled

**Internal routing:** `FTPClientFactory` inspects the configured protocol and instantiates either `FTPHandler` (Commons Net) or `SFTPHandler` (JSch).

---

## Thing Template: `FTPConnection`

Users create a Thing from this template to represent one FTP server connection. All connection configuration is stored in a **Configuration Table** (not Thing properties), following the ThingWorx connector convention.

### Configuration Table: `ConnectionSettings`

| Field             | Type     | Default | Notes                                      |
|-------------------|----------|---------|--------------------------------------------|
| `host`            | String   | —       | FTP server hostname or IP address          |
| `port`            | Integer  | 21 / 22 | 21 for FTP/FTPS, 22 for SFTP              |
| `username`        | String   | —       |                                            |
| `password`        | Password | —       | Masked in Composer UI                      |
| `protocol`        | String   | `FTP`   | One of: `FTP`, `FTPS`, `SFTP`             |
| `passiveMode`     | Boolean  | `true`  | FTP/FTPS only; recommended true for cloud  |
| `connectionTimeout` | Integer | `10000` | Milliseconds                             |

---

## Services

All services are defined on the `FTPConnection` Thing Template. Connection config is read from `ConnectionSettings` at the start of each call.

### `UploadFile`

Uploads a file to the remote FTP server.

**Inputs:**

| Parameter        | Type   | Required | Notes                                                   |
|------------------|--------|----------|---------------------------------------------------------|
| `remotePath`     | String | Yes      | Remote directory path (e.g., `/reports/2026/`)          |
| `fileName`       | String | Yes      | Name to give the file on the remote server              |
| `fileContent`    | BLOB   | No*      | Raw file content (e.g., dynamically generated PDF)      |
| `repositoryName` | String | No*      | ThingWorx File Repository Thing name                    |
| `repositoryPath` | String | No*      | Path within the File Repository                         |

\* Either `fileContent` OR both `repositoryName` + `repositoryPath` must be provided. `fileContent` takes precedence if both are supplied.

**Output:** None. Throws on failure.

---

### `DownloadFile`

Downloads a file from the remote FTP server.

**Inputs:**

| Parameter    | Type   | Required | Notes                        |
|--------------|--------|----------|------------------------------|
| `remotePath` | String | Yes      | Remote directory path        |
| `fileName`   | String | Yes      | Name of the file to download |

**Output:** `BLOB` — the raw file content.

---

### `ListDirectory`

Lists the contents of a remote directory.

**Inputs:**

| Parameter    | Type   | Required | Notes                 |
|--------------|--------|----------|-----------------------|
| `remotePath` | String | Yes      | Remote directory path |

**Output:** `INFOTABLE` with the following columns:

| Column         | Type     |
|----------------|----------|
| `fileName`     | String   |
| `fileSize`     | Long     |
| `lastModified` | DateTime |

---

## Error Handling

All services follow this pattern:

1. Read `ConnectionSettings` from the configuration table
2. Instantiate the appropriate handler via `FTPClientFactory`
3. Connect and execute the operation
4. On any failure:
   - Log a structured `ERROR` entry via ThingWorx `LoggingFunctions` including: protocol, host, operation, remote path, and the underlying exception message
   - Re-throw as a `RuntimeException` with a human-readable message
5. Always disconnect in a `finally` block — no leaked connections

**Example log entry:**
```
[FTPExtension] UPLOAD failed | protocol=FTPS host=ftp.example.com path=/reports/output.pdf | javax.net.ssl.SSLException: Connection reset
```

---

## Project Structure

```
FTPWidget/
├── build.gradle                          # Gradle build with ThingWorx SDK + library deps
├── metadata.xml                          # ThingWorx extension manifest
├── docs/
│   └── superpowers/specs/
│       └── 2026-06-17-ftp-extension-design.md
├── src/main/java/com/ptc/ftpwidget/
│   ├── FTPExtension.java                 # Extension entry point (registers Thing Templates)
│   ├── FTPConnectionThing.java           # Thing Template — all service implementations
│   ├── FTPClientFactory.java             # Selects FTPHandler or SFTPHandler by protocol
│   ├── FTPHandler.java                   # FTP + FTPS operations via Apache Commons Net
│   └── SFTPHandler.java                  # SFTP operations via JSch
└── lib/
    ├── commons-net-*.jar
    └── jsch-*.jar
```

---

## Deployment Considerations

- **Cloud:** Set `passiveMode = true` to avoid firewall/NAT issues with active FTP. SFTP avoids this concern entirely.
- **On-premise:** Both passive and active mode supported; configure per environment.
- **FTPS:** Supports both explicit and implicit TLS. Mode is inferred from port: port `990` → implicit TLS (SSL from connection start); any other port → explicit TLS (STARTTLS after connect). No extra config field required.
- **Extension packaging:** All dependency JARs (`commons-net`, `jsch`) bundled inside the extension `.zip`. ThingWorx SDK JARs are provided at build time only.

---

## Out of Scope

- Connection pooling (can be added later if performance requires it)
- Rename / move / delete remote files
- Recursive directory operations
- ThingWorx file repository write-back on download (download returns BLOB only)
