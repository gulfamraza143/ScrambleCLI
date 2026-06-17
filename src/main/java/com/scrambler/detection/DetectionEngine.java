package com.scrambler.detection;

import com.scrambler.config.CompanyDictionary;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rule-driven scanner for sensitive entities in text content.
 */
public final class DetectionEngine {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "(?i)\\b[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}\\b");
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "\\b(?:\\+91[\\s-]?)?[6-9]\\d{9}\\b|\\b\\+?\\d{1,3}[\\s.-]?\\(?\\d{2,4}\\)?[\\s.-]?\\d{3,4}[\\s.-]?\\d{3,9}\\b");
    private static final Pattern PAN_PATTERN = Pattern.compile(
            "\\b[A-Z]{5}[0-9]{4}[A-Z]\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern IFSC_PATTERN = Pattern.compile(
            "\\b[A-Z]{4}0[A-Z0-9]{6}\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern URL_PATTERN = Pattern.compile(
            "https?://[^\\s<>\"']+");
    private static final Pattern IP_ADDRESS_PATTERN = Pattern.compile(
            "\\b(?:(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\b");
    private static final Pattern PASSWORD_ASSIGNMENT_PATTERN = Pattern.compile(
            "(?im)^[ \\t]*(?:[\\w.-]+\\.)*password\\s*[:=]\\s*['\"]?[^\\s#'\"]{4,}['\"]?");
    private static final Pattern API_KEY_ASSIGNMENT_PATTERN = Pattern.compile(
            "(?im)^[ \\t]*(?:[\\w.-]+\\.)*api[._-]key\\s*[:=]\\s*['\"]?[^\\s#'\"]{4,}['\"]?");
    private static final Pattern SECRET_KEY_ASSIGNMENT_PATTERN = Pattern.compile(
            "(?im)^[ \\t]*(?:[\\w.-]+\\.)*secret\\s*[:=]\\s*['\"]?[^\\s#'\"]{3,}['\"]?");
    private static final Pattern JWT_PATTERN = Pattern.compile(
            "\\beyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\b");
    private static final Pattern PRIVATE_KEY_BLOCK_PATTERN = Pattern.compile(
            "-----BEGIN (?:RSA |OPENSSH )?PRIVATE KEY-----[\\s\\S]*?-----END (?:RSA |OPENSSH )?PRIVATE KEY-----");
    private static final Pattern DATABASE_URL_PATTERN = Pattern.compile(
            "\\bjdbc:(?:postgresql|mysql)://[^\\s'\"]+|\\bjdbc:oracle(?::[\\w]+)?:(?:@//|//|@)[^\\s'\"]+");

    private final List<DetectionRule> rules;
    private final Map<EntityType, Integer> priorities;

    /**
     * Creates a detection engine with the default Milestone 3 rule catalog.
     */
    public DetectionEngine() {
        this(CompanyDictionary.defaults());
    }

    /**
     * Creates a detection engine using the provided company dictionary.
     *
     * @param companyDictionary dictionary for company brand matching
     */
    public DetectionEngine(CompanyDictionary companyDictionary) {
        this.rules = buildDefaultRules(companyDictionary);
        this.priorities = indexPriorities(this.rules);
    }

    /**
     * Detects entities in the supplied context.
     *
     * @param context file metadata and text content
     * @return resolved detection result
     */
    public DetectionResult detect(DetectionContext context) {
        if (context == null) {
            throw new NullPointerException("context must not be null");
        }

        List<Entity> candidates = new ArrayList<>();
        String content = context.getContent();

        for (DetectionRule rule : rules) {
            Matcher matcher = rule.getPattern().matcher(content);
            while (matcher.find()) {
                candidates.add(new Entity(
                        rule.getEntityDomain(),
                        rule.getEntityType(),
                        matcher.group(),
                        matcher.start(),
                        matcher.end()));
            }
        }

        List<Entity> resolved = resolveOverlaps(candidates, priorities);
        resolved.sort(Comparator.comparingInt(Entity::getStartOffset));
        return new DetectionResult(context.getFileInfo(), resolved);
    }

    private static Map<EntityType, Integer> indexPriorities(List<DetectionRule> rules) {
        Map<EntityType, Integer> indexed = new EnumMap<>(EntityType.class);
        for (DetectionRule rule : rules) {
            indexed.put(rule.getEntityType(), rule.getPriority());
        }
        return indexed;
    }

    private static List<DetectionRule> buildDefaultRules(CompanyDictionary companyDictionary) {
        List<DetectionRule> catalog = new ArrayList<>();

        catalog.add(new DetectionRule(
                EntityType.COMPANY_BRAND,
                EntityDomain.COMPANY,
                companyDictionary.compilePattern(),
                100));

        catalog.add(new DetectionRule(
                EntityType.PRIVATE_KEY,
                EntityDomain.SECRETS,
                PRIVATE_KEY_BLOCK_PATTERN,
                96));

        catalog.add(new DetectionRule(
                EntityType.IFSC,
                EntityDomain.SPII,
                IFSC_PATTERN,
                90));

        catalog.add(new DetectionRule(
                EntityType.JWT,
                EntityDomain.SECRETS,
                JWT_PATTERN,
                86));

        catalog.add(new DetectionRule(
                EntityType.EMAIL,
                EntityDomain.PII,
                EMAIL_PATTERN,
                80));

        catalog.add(new DetectionRule(
                EntityType.PHONE,
                EntityDomain.PII,
                PHONE_PATTERN,
                70));

        catalog.add(new DetectionRule(
                EntityType.PAN,
                EntityDomain.PII,
                PAN_PATTERN,
                60));

        catalog.add(new DetectionRule(
                EntityType.URL,
                EntityDomain.INFRASTRUCTURE,
                URL_PATTERN,
                50));

        catalog.add(new DetectionRule(
                EntityType.DATABASE_URL,
                EntityDomain.INFRASTRUCTURE,
                DATABASE_URL_PATTERN,
                45));

        catalog.add(new DetectionRule(
                EntityType.IP_ADDRESS,
                EntityDomain.INFRASTRUCTURE,
                IP_ADDRESS_PATTERN,
                40));

        catalog.add(new DetectionRule(
                EntityType.PASSWORD,
                EntityDomain.SECRETS,
                PASSWORD_ASSIGNMENT_PATTERN,
                30));

        catalog.add(new DetectionRule(
                EntityType.API_KEY,
                EntityDomain.SECRETS,
                API_KEY_ASSIGNMENT_PATTERN,
                29));

        catalog.add(new DetectionRule(
                EntityType.SECRET_KEY,
                EntityDomain.SECRETS,
                SECRET_KEY_ASSIGNMENT_PATTERN,
                28));

        // Placeholders for future rules: AADHAAR, UPI_ID, CREDIT_CARD.

        return List.copyOf(catalog);
    }

    private static List<Entity> resolveOverlaps(List<Entity> candidates, Map<EntityType, Integer> priorities) {
        List<Entity> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator
                .comparingInt((Entity entity) -> entity.getEndOffset() - entity.getStartOffset()).reversed()
                .thenComparing(Comparator.comparingInt((Entity entity) -> priorities.getOrDefault(entity.getType(), 0)).reversed())
                .thenComparingInt(Entity::getStartOffset));

        List<Entity> accepted = new ArrayList<>();
        for (Entity candidate : sorted) {
            if (!overlapsAny(candidate, accepted)) {
                accepted.add(candidate);
            }
        }
        return accepted;
    }

    private static boolean overlapsAny(Entity candidate, List<Entity> accepted) {
        for (Entity existing : accepted) {
            if (candidate.getStartOffset() < existing.getEndOffset()
                    && existing.getStartOffset() < candidate.getEndOffset()) {
                return true;
            }
        }
        return false;
    }
}
