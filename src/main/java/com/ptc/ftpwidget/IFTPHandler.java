package com.ptc.ftpwidget;

import java.util.List;

public interface IFTPHandler {
    void upload(String remotePath, String fileName, byte[] content) throws Exception;
    byte[] download(String remotePath, String fileName) throws Exception;
    List<FileInfo> listDirectory(String remotePath) throws Exception;
}
