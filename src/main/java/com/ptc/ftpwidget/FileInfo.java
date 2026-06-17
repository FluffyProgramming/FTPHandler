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
