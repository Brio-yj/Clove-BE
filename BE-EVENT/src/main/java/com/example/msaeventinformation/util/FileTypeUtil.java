package com.example.msaeventinformation.util;

import java.util.HashMap;
import java.util.Map;

public class FileTypeUtil {
    private static final Map<String, String> mimeTypeMap = new HashMap<>();

    static {
        mimeTypeMap.put("jpg", "image/jpeg");
        mimeTypeMap.put("jpeg", "image/jpeg");
        mimeTypeMap.put("png", "image/png");
        mimeTypeMap.put("gif", "image/gif");
        mimeTypeMap.put("pdf", "application/pdf");
        mimeTypeMap.put("json", "application/json");

    }

    public static String getMimeType(String fileName) {
        String extension = getFileExtension(fileName);
        return mimeTypeMap.getOrDefault(extension, "application/octet-stream");
    }

    private static String getFileExtension(String fileName) {
        int lastIndexOfDot = fileName.lastIndexOf('.');
        return lastIndexOfDot == -1 ? "" : fileName.substring(lastIndexOfDot + 1).toLowerCase();
    }
}

