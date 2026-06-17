package com.scrambler.detection;

/**
 * High-level grouping for detected entity types.
 */
public enum EntityDomain {
    PII,
    SPII,
    SECRETS,
    INFRASTRUCTURE,
    COMPANY
}
