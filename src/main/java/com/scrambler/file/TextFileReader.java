package com.scrambler.file;

import com.scrambler.exception.FileProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Reads text file content for downstream processing stages.
 */
public final class TextFileReader {

    private static final Logger LOGGER = LoggerFactory.getLogger(TextFileReader.class);

    /**
     * Reads a file as UTF-8 text when decodable.
     *
     * @param path absolute path to the text file
     * @return file content, or empty when the bytes are not valid UTF-8 text
     * @throws FileProcessingException when the file cannot be read for reasons other than decoding
     */
    public Optional<String> readUtf8(Path path) throws FileProcessingException {
        if (path == null) {
            throw new IllegalArgumentException("path must not be null");
        }
        try {
            return Optional.of(Files.readString(path, StandardCharsets.UTF_8));
        } catch (CharacterCodingException e) {
            LOGGER.warn("Skipping non-UTF8 or binary file: {}", path);
            return Optional.empty();
        } catch (IOException e) {
            throw new FileProcessingException("Failed to read text file: " + path, e);
        }
    }
}
