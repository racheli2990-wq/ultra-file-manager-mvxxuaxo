package com.generated.app;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FileItem {

    public static final int TYPE_FOLDER = 0;
    public static final int TYPE_IMAGE = 1;
    public static final int TYPE_VIDEO = 2;
    public static final int TYPE_AUDIO = 3;
    public static final int TYPE_DOC = 4;
    public static final int TYPE_OTHER = 5;

    private final File file;
    private final String name;
    private final long size;
    private final long lastModified;
    private final boolean isDirectory;
    private final int type;

    public FileItem(File file) {
        this.file = file;
        this.name = file.getName();
        this.size = file.length();
        this.lastModified = file.lastModified();
        this.isDirectory = file.isDirectory();
        this.type = determineType(file);
    }

    private int determineType(File f) {
        if (f.isDirectory()) {
            return TYPE_FOLDER;
        }
        String n = f.getName().toLowerCase(Locale.ROOT);
        if (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".gif") || n.endsWith(".webp") || n.endsWith(".bmp")) {
            return TYPE_IMAGE;
        } else if (n.endsWith(".mp4") || n.endsWith(".mkv") || n.endsWith(".webm") || n.endsWith(".3gp") || n.endsWith(".avi")) {
            return TYPE_VIDEO;
        } else if (n.endsWith(".mp3") || n.endsWith(".wav") || n.endsWith(".ogg") || n.endsWith(".m4a") || n.endsWith(".flac")) {
            return TYPE_AUDIO;
        } else if (n.endsWith(".pdf") || n.endsWith(".txt") || n.endsWith(".doc") || n.endsWith(".docx") || n.endsWith(".json") || n.endsWith(".xml") || n.endsWith(".html") || n.endsWith(".java") || n.endsWith(".py")) {
            return TYPE_DOC;
        }
        return TYPE_OTHER;
    }

    public File getFile() {
        return file;
    }

    public String getName() {
        return name;
    }

    public long getSize() {
        return size;
    }

    public long getLastModified() {
        return lastModified;
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    public int getType() {
        return type;
    }

    public String getFormattedSize() {
        if (isDirectory) {
            File[] children = file.listFiles();
            int count = children != null ? children.length : 0;
            return count + " פריטים";
        }
        if (size < 1024) return size + " B";
        int z = (63 - Long.numberOfLeadingZeros(size)) / 10;
        return String.format(Locale.getDefault(), "%.1f %cB", (double) size / (1L << (z * 10)), " KMGTPE".charAt(z));
    }

    public String getFormattedDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
        return sdf.format(new Date(lastModified));
    }

    public int getIconResId() {
        switch (type) {
            case TYPE_FOLDER:
                return R.drawable.ic_folder;
            case TYPE_IMAGE:
                return R.drawable.ic_image;
            case TYPE_VIDEO:
                return R.drawable.ic_video;
            case TYPE_AUDIO:
                return R.drawable.ic_audio;
            case TYPE_DOC:
                return R.drawable.ic_document;
            default:
                return R.drawable.ic_file;
        }
    }
}
