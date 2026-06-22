package com.scrambler.detection;

import com.scrambler.config.CompanyDictionary;
import com.scrambler.config.JiraProjectKeys;

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
            "(?im)(?:^[ \\t]*(?:[\\w.-]+\\.)*password\\s*[:=]\\s*['\"]?|[\"']password[\"']\\s*:\\s*[\"'])([^\\s#\"']{4,})");
    private static final Pattern API_KEY_ASSIGNMENT_PATTERN = Pattern.compile(
            "(?im)(?:^[ \\t]*(?:[\\w.-]+\\.)*api[._-]key\\s*[:=]\\s*['\"]?|[\"']api[._-]key[\"']\\s*:\\s*[\"'])([^\\s#\"']{4,})");
    private static final Pattern SECRET_KEY_ASSIGNMENT_PATTERN = Pattern.compile(
            "(?im)(?:^[ \\t]*(?:[\\w.-]+\\.)*secret\\s*[:=]\\s*['\"]?|[\"']secret[\"']\\s*:\\s*[\"'])([^\\s#\"']{3,})");
    private static final String INTERNAL_IDENTIFIER_LABELS =
            "(?:employeeId|employee_id|empId|emp_id|staffId|staff_id|staffNumber|staff_number|"
                    + "associateId|associate_id|banId|ban_id|ldapId|ldap_id|adId|ad_id|"
                    + "contractorId|contractor_id|internalUserId|internal_user_id|corpId|corp_id|"
                    + "corporateId|corporate_id)";
    private static final Pattern INTERNAL_IDENTIFIER_ASSIGNMENT_PATTERN = Pattern.compile(
            "(?im)(?:^[ \\t]*(?:[\\w.-]+\\.)*" + INTERNAL_IDENTIFIER_LABELS + "\\s*[:=]\\s*['\"]?|[\"']"
                    + INTERNAL_IDENTIFIER_LABELS + "[\"']\\s*:\\s*[\"'])([^\\s#\"']+)");
    private static final Pattern JWT_PATTERN = Pattern.compile(
            "\\beyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\b");
    private static final Pattern PRIVATE_KEY_BLOCK_PATTERN = Pattern.compile(
            "-----BEGIN (?:RSA |OPENSSH )?PRIVATE KEY-----[\\s\\S]*?-----END (?:RSA |OPENSSH )?PRIVATE KEY-----");
    private static final Pattern DATABASE_URL_PATTERN = Pattern.compile(
            "\\bjdbc:(?:postgresql|mysql)://[^\\s'\"]+|\\bjdbc:oracle(?::[\\w]+)?:(?:@//|//|@)[^\\s'\"]+");
    private static final Pattern AADHAAR_PATTERN = Pattern.compile(
            "\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}(?![\\s-]?\\d)\\b");
    private static final Pattern UPI_ID_PATTERN = Pattern.compile(
            "(?i)\\b[a-z0-9._-]{1,256}@(?:ok[a-z]{2,}|ybl|paytm|axl|ibl|upi)\\b");
    private static final Pattern CREDIT_CARD_PATTERN = Pattern.compile(
            "\\b(?:\\d{4}[\\s-]?){3}\\d{4}\\b|\\b\\d{13,19}\\b");
    private static final Pattern GSTIN_PATTERN = Pattern.compile(
            "\\b\\d{2}[A-Z]{5}\\d{4}[A-Z][1-9A-Z]Z[0-9A-Z]\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern TAN_PATTERN = Pattern.compile(
            "\\b[A-Z]{4}\\d{5}[A-Z]\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern CIN_PATTERN = Pattern.compile(
            "\\b[UL]\\d{5}[A-Z]{2}\\d{4}[A-Z]{3}\\d{6}\\b", Pattern.CASE_INSENSITIVE);

    private final List<DetectionRule> rules;
    private final Map<EntityType, Integer> priorities;

    /**
     * Creates a detection engine with the default Milestone 3 rule catalog.
     */
    public DetectionEngine() {
        this(CompanyDictionary.defaults(), JiraProjectKeys.defaults());
    }

    /**
     * Creates a detection engine using the provided company dictionary.
     *
     * @param companyDictionary dictionary for company brand matching
     */
    public DetectionEngine(CompanyDictionary companyDictionary) {
        this(companyDictionary, JiraProjectKeys.defaults());
    }

    /**
     * Creates a detection engine using the provided dictionaries.
     *
     * @param companyDictionary dictionary for company brand matching
     * @param jiraProjectKeys   allowlisted JIRA project keys for work item matching
     */
    public DetectionEngine(CompanyDictionary companyDictionary, JiraProjectKeys jiraProjectKeys) {
        this.rules = buildDefaultRules(companyDictionary, jiraProjectKeys);
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

        List<ResolvedMatch> candidates = new ArrayList<>();
        String content = context.getContent();

        for (DetectionRule rule : rules) {
            Matcher matcher = rule.getPattern().matcher(content);
            while (matcher.find()) {
                ResolvedMatch candidate = toResolvedMatch(rule, matcher);
                if (acceptsCandidate(rule, candidate)) {
                    candidates.add(candidate);
                }
            }
        }

        List<ResolvedMatch> resolved = resolveOverlaps(candidates, priorities);
        List<Entity> entities = resolved.stream()
                .map(ResolvedMatch::entity)
                .sorted(Comparator.comparingInt(Entity::getStartOffset))
                .toList();
        return new DetectionResult(context.getFileInfo(), entities);
    }

    private static ResolvedMatch toResolvedMatch(DetectionRule rule, Matcher matcher) {
        int overlapStart = matcher.start();
        int overlapEnd = matcher.end();

        String originalValue;
        int startOffset;
        int endOffset;
        if (rule.getValueGroupIndex() > 0) {
            originalValue = matcher.group(rule.getValueGroupIndex());
            startOffset = matcher.start(rule.getValueGroupIndex());
            endOffset = matcher.end(rule.getValueGroupIndex());
        } else {
            originalValue = matcher.group();
            startOffset = overlapStart;
            endOffset = overlapEnd;
        }

        Entity entity = new Entity(
                rule.getEntityDomain(),
                rule.getEntityType(),
                originalValue,
                startOffset,
                endOffset);
        return new ResolvedMatch(entity, overlapStart, overlapEnd);
    }

    private record ResolvedMatch(Entity entity, int overlapStart, int overlapEnd) {
    }

    private static Map<EntityType, Integer> indexPriorities(List<DetectionRule> rules) {
        Map<EntityType, Integer> indexed = new EnumMap<>(EntityType.class);
        for (DetectionRule rule : rules) {
            indexed.put(rule.getEntityType(), rule.getPriority());
        }
        return indexed;
    }

    private static List<DetectionRule> buildDefaultRules(
            CompanyDictionary companyDictionary,
            JiraProjectKeys jiraProjectKeys) {
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
                EntityType.GSTIN,
                EntityDomain.SPII,
                GSTIN_PATTERN,
                91,
                0,
                DetectionValidators::isValidGstin));

        catalog.add(new DetectionRule(
                EntityType.UPI_ID,
                EntityDomain.SPII,
                UPI_ID_PATTERN,
                88));

        catalog.add(new DetectionRule(
                EntityType.CREDIT_CARD,
                EntityDomain.SPII,
                CREDIT_CARD_PATTERN,
                85,
                0,
                DetectionValidators::isValidCreditCard));

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
                60,
                0,
                DetectionValidators::isValidPan));

        catalog.add(new DetectionRule(
                EntityType.TAN,
                EntityDomain.SPII,
                TAN_PATTERN,
                58,
                0,
                DetectionValidators::isValidTan));

        catalog.add(new DetectionRule(
                EntityType.CIN,
                EntityDomain.COMPANY,
                CIN_PATTERN,
                94,
                0,
                DetectionValidators::isValidCin));

        catalog.add(new DetectionRule(
                EntityType.AADHAAR,
                EntityDomain.PII,
                AADHAAR_PATTERN,
                75,
                0,
                DetectionValidators::isValidAadhaar));

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
                30,
                1));

        catalog.add(new DetectionRule(
                EntityType.API_KEY,
                EntityDomain.SECRETS,
                API_KEY_ASSIGNMENT_PATTERN,
                29,
                1));

        catalog.add(new DetectionRule(
                EntityType.SECRET_KEY,
                EntityDomain.SECRETS,
                SECRET_KEY_ASSIGNMENT_PATTERN,
                28,
                1));

        catalog.add(new DetectionRule(
                EntityType.INTERNAL_IDENTIFIER,
                EntityDomain.PII,
                INTERNAL_IDENTIFIER_ASSIGNMENT_PATTERN,
                27,
                1,
                DetectionValidators::isValidInternalIdentifier));

        catalog.add(new DetectionRule(
                EntityType.WORK_ITEM_ID,
                EntityDomain.INFRASTRUCTURE,
                jiraProjectKeys.compileWorkItemPattern(),
                92)); // above IFSC (90) so RITM/TASK ServiceNow IDs are not misclassified

        return List.copyOf(catalog);
    }

    private static boolean acceptsCandidate(DetectionRule rule, ResolvedMatch candidate) {
        return rule.getValueValidator().test(candidate.entity().getOriginalValue());
    }

    private static List<ResolvedMatch> resolveOverlaps(List<ResolvedMatch> candidates, Map<EntityType, Integer> priorities) {
        List<ResolvedMatch> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator
                .comparingInt((ResolvedMatch match) -> match.overlapEnd() - match.overlapStart()).reversed()
                .thenComparing(Comparator.comparingInt(
                        (ResolvedMatch match) -> priorities.getOrDefault(match.entity().getType(), 0)).reversed())
                .thenComparingInt(match -> match.overlapStart()));

        List<ResolvedMatch> accepted = new ArrayList<>();
        for (ResolvedMatch candidate : sorted) {
            if (!overlapsAny(candidate, accepted)) {
                accepted.add(candidate);
            }
        }
        return accepted;
    }

    private static boolean overlapsAny(ResolvedMatch candidate, List<ResolvedMatch> accepted) {
        for (ResolvedMatch existing : accepted) {
            if (candidate.overlapStart() < existing.overlapEnd()
                    && existing.overlapStart() < candidate.overlapEnd()) {
                return true;
            }
        }
        return false;
    }
}
