package com.scrambler.path;

import com.scrambler.config.CompanyDictionary;
import com.scrambler.config.JiraProjectKeys;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects sensitive identifiers in repository, folder, and file path segments.
 * Reuses company brand, work item, and internal identifier rules adapted for path context.
 */
public final class PathSegmentDetector {

    private static final Set<String> GENERIC_FOLDERS = Set.of(
            "src", "main", "java", "resources", "public", "test", "docs", "config", "node_modules");

    private static final Set<String> GENERIC_FILES = Set.of(
            "application.yml",
            "application.yaml",
            "readme.md",
            "pom.xml",
            "package.json",
            "dockerfile");

    private final Pattern brandPattern;
    private final Pattern workItemPattern;

    /**
     * Creates a detector with default dictionaries.
     */
    public PathSegmentDetector() {
        this(CompanyDictionary.defaults(), JiraProjectKeys.defaults());
    }

    /**
     * Creates a detector with supplied dictionaries.
     *
     * @param companyDictionary company brand dictionary
     * @param jiraProjectKeys   allowlisted JIRA project keys
     */
    public PathSegmentDetector(CompanyDictionary companyDictionary, JiraProjectKeys jiraProjectKeys) {
        this.brandPattern = compilePathBrandPattern(companyDictionary);
        this.workItemPattern = compilePathWorkItemPattern(jiraProjectKeys);
    }

    /**
     * Returns whether a repository root name contains sensitive identifiers.
     *
     * @param repositoryName repository root name without archive extension
     * @return true when the name should be tokenized
     */
    public boolean isSensitiveRepositoryName(String repositoryName) {
        return containsSensitiveSegment(repositoryName);
    }

    /**
     * Returns whether a folder segment contains sensitive identifiers and is not generic.
     *
     * @param folderSegment single path folder name
     * @return true when the folder should be tokenized
     */
    public boolean isSensitiveFolderSegment(String folderSegment) {
        if (isGenericFolder(folderSegment)) {
            return false;
        }
        return containsSensitiveSegment(folderSegment);
    }

    /**
     * Returns whether a file name contains sensitive identifiers and is not generic.
     *
     * @param fileName file name including extension
     * @return true when the file should be tokenized
     */
    public boolean isSensitiveFileName(String fileName) {
        if (isGenericFile(fileName)) {
            return false;
        }
        return containsSensitiveSegment(fileName);
    }

    private boolean containsSensitiveSegment(String segment) {
        if (segment == null || segment.isBlank()) {
            return false;
        }
        if (containsBrand(segment)) {
            return true;
        }
        if (containsWorkItemId(segment)) {
            return true;
        }
        if (containsInternalIdentifier(segment)) {
            return true;
        }
        return containsPathEntityPattern(segment);
    }

    private boolean containsBrand(String segment) {
        Matcher matcher = brandPattern.matcher(segment);
        return matcher.find();
    }

    private boolean containsWorkItemId(String segment) {
        Matcher matcher = workItemPattern.matcher(segment);
        return matcher.find();
    }

    private static boolean containsPathEntityPattern(String segment) {
        return matchesAny(segment, PathEntityPatterns.PAN)
                || matchesAny(segment, PathEntityPatterns.IFSC)
                || matchesAny(segment, PathEntityPatterns.EMAIL)
                || matchesAny(segment, PathEntityPatterns.CIN);
    }

    private static boolean matchesAny(String segment, Pattern pattern) {
        return pattern.matcher(segment).find();
    }

    private static boolean containsInternalIdentifier(String segment) {
        String trimmed = segment.trim();
        if (trimmed.length() < 3) {
            return false;
        }
        if (trimmed.matches("\\d{5,}")) {
            return true;
        }
        return trimmed.matches("[A-Za-z]+\\d{2,}");
    }

    static boolean isGenericFolder(String folderSegment) {
        return GENERIC_FOLDERS.contains(folderSegment.toLowerCase(Locale.ROOT));
    }

    static boolean isGenericFile(String fileName) {
        return GENERIC_FILES.contains(fileName.toLowerCase(Locale.ROOT));
    }

    private static Pattern compilePathBrandPattern(CompanyDictionary companyDictionary) {
        var terms = companyDictionary.getTerms();
        if (terms.isEmpty()) {
            return Pattern.compile("(?!)");
        }
        java.util.LinkedHashSet<String> matchTerms = new java.util.LinkedHashSet<>();
        for (String term : terms) {
            matchTerms.add(term);
            for (String part : term.split("[\\s-]+")) {
                if (part.length() >= 4) {
                    matchTerms.add(part);
                }
            }
        }
        java.util.List<String> longestFirst = new java.util.ArrayList<>(matchTerms);
        longestFirst.sort(java.util.Comparator.comparingInt(String::length).reversed());
        String alternation = String.join("|", longestFirst.stream().map(Pattern::quote).toList());
        return Pattern.compile("(?i)(?:" + alternation + ")");
    }

    private static Pattern compilePathWorkItemPattern(JiraProjectKeys jiraProjectKeys) {
        return Pattern.compile(jiraProjectKeys.compileWorkItemPattern().pattern().replace("\\b", ""));
    }

    /**
     * Path-adapted entity patterns reused from the content detection catalog.
     */
    private static final class PathEntityPatterns {
        private static final Pattern PAN = Pattern.compile("[A-Za-z]{5}\\d{4}[A-Za-z]");
        private static final Pattern IFSC = Pattern.compile("[A-Za-z]{4}0[A-Za-z0-9]{6}", Pattern.CASE_INSENSITIVE);
        private static final Pattern EMAIL = Pattern.compile(
                "(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}");
        private static final Pattern CIN = Pattern.compile("[UL]\\d{5}[A-Za-z]{2}\\d{4}[A-Za-z]{3}\\d{6}", Pattern.CASE_INSENSITIVE);

        private PathEntityPatterns() {
        }
    }
}
