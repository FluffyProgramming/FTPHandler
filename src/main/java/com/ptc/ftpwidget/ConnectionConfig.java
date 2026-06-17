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
