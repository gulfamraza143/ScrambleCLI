package com.scrambler.file;

import com.scrambler.exception.FileProcessingException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes text file content for downstream processing stages.
 */
public final class TextFileWriter {

    /**
     * Writes content to a file using UTF-8 encoding.
     *
     * @param path    absolute path to the text file
     * @param content text content to write
     * @throws FileProcessingException when the file cannot be written
     */
    public void writeUtf8(Path path, String content) throws FileProcessingException {
        if (path == null) {
            throw new IllegalArgumentException("path must not be null");
        }
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new FileProcessingException("Failed to write text file: " + path, e);
        }
    }
}
