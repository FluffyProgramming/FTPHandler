# FTPHandler — ThingWorx FTP/FTPS/SFTP Extension

A ThingWorx extension that enables FTP, FTPS, and SFTP file transfers directly from ThingWorx services. Upload files, download files, and list remote directories using a simple configuration table — no scripting of connection logic required.

---

## Features

- **FTP** — standard file transfer protocol
- **FTPS** — FTP over TLS/SSL (explicit)
- **SFTP** — SSH file transfer protocol
- Upload from raw BLOB or directly from a ThingWorx File Repository
- Download files as BLOB
- List remote directory contents with file name, size, and last modified date

---

## Requirements

- ThingWorx 8.0 or later (tested on 10.1.1)
- Java 8+ on the ThingWorx server

---

## Installation

1. Download `FTPHandler-1.1.0.zip` from the [Releases](../../releases) page
2. In ThingWorx Composer go to **Import/Export → Import → Extension**
3. Select the zip and click **Import**
4. After import, restart ThingWorx (required for the extension to fully initialize)

---

## Usage

### 1. Create a Thing

In Composer, create a new Thing and set its **Thing Template** to `FTPConnectionTemplate`.

### 2. Configure the Connection

Open the Thing → **Configuration** tab → **ConnectionSettings** table and fill in:

| Field | Type | Description | Default |
|---|---|---|---|
| `host` | STRING | FTP/SFTP server hostname or IP | |
| `port` | INTEGER | Server port | `21` |
| `username` | STRING | Login username | |
| `password` | PASSWORD | Login password | |
| `protocol` | STRING | `FTP`, `FTPS`, or `SFTP` | `FTP` |
| `passiveMode` | BOOLEAN | Use passive mode (FTP/FTPS only — recommended for cloud) | `true` |
| `connectionTimeout` | INTEGER | Connection timeout in milliseconds | `10000` |

### 3. Call Services

All services are available on any Thing based on `FTPConnectionTemplate`.

---

## Services

### `ListDirectory`

List files in a remote directory.

**Input:**

| Parameter | Type | Description |
|---|---|---|
| `remotePath` | STRING | Remote directory path (e.g. `/files`) |

**Output:** `INFOTABLE` with fields `fileName` (STRING), `fileSize` (LONG), `lastModified` (DATETIME)

---

### `DownloadFile`

Download a file from the FTP server and return its contents as a BLOB.

**Input:**

| Parameter | Type | Description |
|---|---|---|
| `remotePath` | STRING | Remote directory path |
| `fileName` | STRING | File name to download |

**Output:** `BLOB` — raw file bytes

---

### `UploadFile`

Upload a file to the FTP server. Provide either `fileContent` (BLOB) or a ThingWorx File Repository source.

**Input:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `remotePath` | STRING | Yes | Remote directory path |
| `fileName` | STRING | Yes | File name on the remote server |
| `fileContent` | BLOB | One of these | Raw file bytes — takes precedence if provided |
| `repositoryName` | STRING | One of these | ThingWorx File Repository Thing name |
| `repositoryPath` | STRING | One of these | File path within the Repository |

**Output:** Nothing

---

## Building from Source

Requirements: JDK 8+, Gradle

1. Place ThingWorx Extension SDK JARs in `lib/thingworx-sdk/`
2. Run:
   ```
   gradlew buildExtension
   ```
3. Output zip will be at `build/extension/FTPHandler-<version>.zip`

---

## Libraries Used

| Library | Version | License |
|---|---|---|
| Apache Commons Net | 3.9.0 | Apache 2.0 |
| JSch | 0.1.55 | BSD |

---

## Author

Derrick Swint — independently developed and supported, not affiliated with PTC.
