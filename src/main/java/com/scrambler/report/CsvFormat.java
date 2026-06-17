package com.scrambler.report;

import java.io.IOException;
import java.io.PushbackReader;
import java.util.ArrayList;
import java.util.List;

/**
 * RFC 4180 CSV escaping and parsing helpers.
 */
final class CsvFormat {

    private CsvFormat() {
    }

    static String escapeField(String value) {
        if (value == null) {
            throw new NullPointerException("value must not be null");
        }
        if (needsQuoting(value)) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    static List<String> readRecord(PushbackReader reader) throws IOException {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean recordStarted = false;

        while (true) {
            int next = reader.read();
            if (next == -1) {
                if (!recordStarted && fields.isEmpty()) {
                    return null;
                }
                fields.add(field.toString());
                return fields;
            }

            char current = (char) next;
            recordStarted = true;

            if (inQuotes) {
                if (current == '"') {
                    int peek = reader.read();
                    if (peek == '"') {
                        field.append('"');
                    } else {
                        inQuotes = false;
                        if (peek != -1) {
                            reader.unread(peek);
                        }
                    }
                } else {
                    field.append(current);
                }
                continue;
            }

            if (current == '"') {
                inQuotes = true;
            } else if (current == ',') {
                fields.add(field.toString());
                field.setLength(0);
            } else if (current == '\r') {
                int peek = reader.read();
                if (peek != '\n' && peek != -1) {
                    reader.unread(peek);
                }
                fields.add(field.toString());
                return fields;
            } else if (current == '\n') {
                fields.add(field.toString());
                return fields;
            } else {
                field.append(current);
            }
        }
    }

    private static boolean needsQuoting(String value) {
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == ',' || current == '"' || current == '\n' || current == '\r') {
                return true;
            }
        }
        return false;
    }
}
