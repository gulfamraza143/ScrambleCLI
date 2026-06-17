package com.scrambler.file;

import com.scrambler.exception.FileProcessingException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads text file content for downstream processing stages.
 */
public final class TextFileReader {

    /**
     * Reads a file as UTF-8 text.
     *
     * @param path absolute path to the text file
     * @return file content
     * @throws FileProcessingException when the file cannot be read
     */
    public String readUtf8(Path path) throws FileProcessingException {
        if (path == null) {
            throw new IllegalArgumentException("path must not be null");
        }
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new FileProcessingException("Failed to read text file: " + path, e);
        }
    }
}
