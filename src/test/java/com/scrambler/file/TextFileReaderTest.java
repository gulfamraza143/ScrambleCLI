package com.scrambler.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextFileReaderTest {

    private final TextFileReader textFileReader = new TextFileReader();

    @Test
    void readsValidUtf8Text(@TempDir Path tempDir) throws Exception {
        Path textFile = tempDir.resolve("README.md");
        Files.writeString(textFile, "# docs\n");

        Optional<String> content = textFileReader.readUtf8(textFile);

        assertTrue(content.isPresent());
        assertEquals("# docs\n", content.get());
    }

    @Test
    void skipsBinaryFileWithTextExtension(@TempDir Path tempDir) throws Exception {
        Path binaryYaml = tempDir.resolve("looks-like.yaml");
        Files.write(binaryYaml, new byte[]{(byte) 0xFF, (byte) 0xFE, (byte) 0xFD});

        String logOutput = captureStderr(() -> {
            Optional<String> content = textFileReader.readUtf8(binaryYaml);
            assertTrue(content.isEmpty());
        });

        assertTrue(logOutput.contains("WARN Skipping non-UTF8 or binary file:"));
        assertTrue(logOutput.contains("looks-like.yaml"));
    }

    @Test
    void skipsBinaryFileWithTxtExtension(@TempDir Path tempDir) throws Exception {
        Path binaryTxt = tempDir.resolve("binary.txt");
        Files.write(binaryTxt, new byte[]{(byte) 0xFF, (byte) 0xFE, (byte) 0x00});

        String logOutput = captureStderr(() -> {
            Optional<String> content = textFileReader.readUtf8(binaryTxt);
            assertTrue(content.isEmpty());
        });

        assertTrue(logOutput.contains("WARN Skipping non-UTF8 or binary file:"));
        assertTrue(logOutput.contains("binary.txt"));
    }

    private static String captureStderr(Runnable action) {
        PrintStream originalErr = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            action.run();
            return captured.toString(StandardCharsets.UTF_8);
        } finally {
            System.setErr(originalErr);
        }
    }
}
