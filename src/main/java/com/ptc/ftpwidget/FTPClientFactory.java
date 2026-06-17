package com.ptc.ftpwidget;

public class FTPClientFactory {

    private FTPClientFactory() {}

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
