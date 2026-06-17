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
