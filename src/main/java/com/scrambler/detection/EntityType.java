package com.scrambler.detection;

/**
 * Specific kind of sensitive entity detected in text content.
 */
public enum EntityType {
    EMAIL(EntityDomain.PII),
    PHONE(EntityDomain.PII),
    PAN(EntityDomain.PII),
    AADHAAR(EntityDomain.PII),

    IFSC(EntityDomain.SPII),
    UPI_ID(EntityDomain.SPII),
    CREDIT_CARD(EntityDomain.SPII),
    GSTIN(EntityDomain.SPII),
    TAN(EntityDomain.SPII),

    PASSWORD(EntityDomain.SECRETS),
    API_KEY(EntityDomain.SECRETS),
    SECRET_KEY(EntityDomain.SECRETS),
    INTERNAL_IDENTIFIER(EntityDomain.PII),
    WORK_ITEM_ID(EntityDomain.INFRASTRUCTURE),
    JWT(EntityDomain.SECRETS),
    PRIVATE_KEY(EntityDomain.SECRETS),

    URL(EntityDomain.INFRASTRUCTURE),
    IP_ADDRESS(EntityDomain.INFRASTRUCTURE),
    DATABASE_URL(EntityDomain.INFRASTRUCTURE),

    COMPANY_BRAND(EntityDomain.COMPANY),
    CIN(EntityDomain.COMPANY);

    private final EntityDomain domain;

    EntityType(EntityDomain domain) {
        this.domain = domain;
    }

    /**
     * Returns the domain that owns this entity type.
     *
     * @return entity domain
     */
    public EntityDomain getDomain() {
        return domain;
    }
}
