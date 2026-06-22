package com.scrambler.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Allowlisted JIRA project keys used by {@code WORK_ITEM_ID} detection.
 */
public final class JiraProjectKeys {

    private static final String DEFAULT_RESOURCE = "/jira-project-keys.txt";
    private static final String SERVICENOW_WORK_ITEM_PATTERN = "(?:INC|CHG|REQ|RITM|PRB|TASK)\\d{7,}";

    private final List<String> keys;

    private JiraProjectKeys(List<String> keys) {
        this.keys = List.copyOf(keys);
    }

    /**
     * Returns the default allowlist loaded from the bundled resource file.
     *
     * @return default project keys
     */
    public static JiraProjectKeys defaults() {
        return loadFromResource(DEFAULT_RESOURCE);
    }

    /**
     * Loads allowlisted project keys from a classpath resource.
     *
     * @param resourcePath classpath resource path
     * @return loaded project keys
     */
    public static JiraProjectKeys loadFromResource(String resourcePath) {
        try (InputStream inputStream = JiraProjectKeys.class.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IllegalStateException("JIRA project keys resource not found: " + resourcePath);
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                List<String> loadedKeys = reader.lines()
                        .map(String::trim)
                        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                        .toList();
                return new JiraProjectKeys(loadedKeys);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load JIRA project keys: " + resourcePath, e);
        }
    }

    /**
     * Returns immutable allowlisted project keys.
     *
     * @return project keys
     */
    public List<String> getKeys() {
        return keys;
    }

    /**
     * Compiles a pattern for ServiceNow and allowlisted JIRA work item identifiers.
     *
     * @return compiled work item pattern
     */
    public Pattern compileWorkItemPattern() {
        if (keys.isEmpty()) {
            return Pattern.compile("\\b" + SERVICENOW_WORK_ITEM_PATTERN + "\\b");
        }

        List<String> longestFirst = new ArrayList<>(keys);
        longestFirst.sort(Comparator.comparingInt(String::length).reversed());
        String jiraAlternation = String.join("|", longestFirst.stream().map(Pattern::quote).toList());
        return Pattern.compile("\\b(?:" + SERVICENOW_WORK_ITEM_PATTERN + "|(?:" + jiraAlternation + ")-\\d+)\\b");
    }
}
