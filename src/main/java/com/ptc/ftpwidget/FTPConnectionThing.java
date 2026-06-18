package com.ptc.ftpwidget;

import com.thingworx.entities.utils.ThingUtilities;
import com.thingworx.metadata.DataShapeDefinition;
import com.thingworx.metadata.FieldDefinition;
import com.thingworx.metadata.annotations.*;
import com.thingworx.things.Thing;
import com.thingworx.things.repository.FileRepositoryThing;
import com.thingworx.types.BaseTypes;
import com.thingworx.types.InfoTable;
import com.thingworx.types.collections.ValueCollection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

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
                description = "Port (21=FTP/FTPS, 22=SFTP)",
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
                description = "Use passive mode — recommended true for cloud (FTP/FTPS only)",
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
        com.thingworx.types.ConfigurationTable table = getConfigurationTable("ConnectionSettings");
        if (table == null || table.getRowCount() == 0) {
            throw new RuntimeException("[FTPExtension] ConnectionSettings is not configured on this Thing.");
        }
        ValueCollection row = table.getRow(0);
        if (row == null) {
            throw new RuntimeException("[FTPExtension] ConnectionSettings row is null.");
        }
        return new ConnectionConfig(
            row.getStringValue("host"),
            ((Number) row.getValue("port")).intValue(),
            row.getStringValue("username"),
            row.getStringValue("password"),
            row.getStringValue("protocol"),
            (Boolean) row.getValue("passiveMode"),
            ((Number) row.getValue("connectionTimeout")).intValue()
        );
    }

    @ThingworxServiceDefinition(
        name = "UploadFile",
        description = "Upload a file to the FTP server. Provide fileContent (BLOB) OR repositoryName + repositoryPath.")
    @ThingworxServiceResult(name = "result", description = "", baseType = "NOTHING")
    public void UploadFile(
        @ThingworxServiceParameter(name = "remotePath", description = "Remote directory path", baseType = "STRING") String remotePath,
        @ThingworxServiceParameter(name = "fileName", description = "File name on the remote server", baseType = "STRING") String fileName,
        @ThingworxServiceParameter(name = "fileContent", description = "Raw file bytes (BLOB). Takes precedence if provided.", baseType = "BLOB", aspects = {"isRequired:false"}) byte[] fileContent,
        @ThingworxServiceParameter(name = "repositoryName", description = "ThingWorx File Repository Thing name", baseType = "STRING", aspects = {"isRequired:false"}) String repositoryName,
        @ThingworxServiceParameter(name = "repositoryPath", description = "File path within the Repository", baseType = "STRING", aspects = {"isRequired:false"}) String repositoryPath
    ) throws Exception {
        ConnectionConfig config = readConfig();
        try {
            byte[] content = fileContent;
            if (content == null || content.length == 0) {
                if (isEmpty(repositoryName) || isEmpty(repositoryPath)) {
                    throw new RuntimeException(
                        "[FTPExtension] UploadFile requires either fileContent or both repositoryName and repositoryPath.");
                }
                content = readFromRepository(repositoryName, repositoryPath);
            }
            FTPClientFactory.create(config).upload(remotePath, fileName, content);
        } catch (RuntimeException e) {
            logger.error(e.getMessage(), e);
            throw e;
        }
    }

    @ThingworxServiceDefinition(
        name = "DownloadFile",
        description = "Download a file from the FTP server and return its content as BLOB.")
    @ThingworxServiceResult(name = "result", description = "File content", baseType = "BLOB")
    public byte[] DownloadFile(
        @ThingworxServiceParameter(name = "remotePath", description = "Remote directory path", baseType = "STRING") String remotePath,
        @ThingworxServiceParameter(name = "fileName", description = "File name to download", baseType = "STRING") String fileName
    ) throws Exception {
        ConnectionConfig config = readConfig();
        try {
            return FTPClientFactory.create(config).download(remotePath, fileName);
        } catch (RuntimeException e) {
            logger.error(e.getMessage(), e);
            throw e;
        }
    }

    @ThingworxServiceDefinition(
        name = "ListDirectory",
        description = "List files in a remote directory. Returns fileName, fileSize, lastModified.")
    @ThingworxServiceResult(name = "result", description = "Directory listing", baseType = "INFOTABLE")
    public InfoTable ListDirectory(
        @ThingworxServiceParameter(name = "remotePath", description = "Remote directory path", baseType = "STRING") String remotePath
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

    private byte[] readFromRepository(String repositoryName, String repositoryPath) throws Exception {
        Thing repoThing = ThingUtilities.findThing(repositoryName);
        if (repoThing == null) {
            throw new RuntimeException("[FTPExtension] File Repository not found: " + repositoryName);
        }
        if (!(repoThing instanceof FileRepositoryThing)) {
            throw new RuntimeException(
                "[FTPExtension] Thing '" + repositoryName + "' is not a FileRepository.");
        }
        return ((FileRepositoryThing) repoThing).LoadBinary(repositoryPath);
    }

    private static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }
}
