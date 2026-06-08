package secretchat.util;

import java.io.File;

public class FileUtils {

    public static String getFileExtension(File file) {
        if (file == null) return "";
        String name = file.getName();
        int lastIdx = name.lastIndexOf(".");
        if (lastIdx == -1) return "";
        return name.substring(lastIdx + 1);
    }

    public static String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        }
        double kb = size / 1024.0;
        if (kb < 1024) {
            return String.format("%.1f KB", kb);
        }
        double mb = kb / 1024.0;
        return String.format("%.1f MB", mb);
    }
}
