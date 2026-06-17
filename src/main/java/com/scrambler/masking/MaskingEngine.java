package com.scrambler.masking;

import com.scrambler.detection.DetectionResult;
import com.scrambler.detection.Entity;
import com.scrambler.detection.EntityType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Converts detected entities into masked tokens using a supplied detection result.
 */
public final class MaskingEngine {

    private final EntityReplacer entityReplacer;
    private final Map<EntityType, Integer> tokenCounters;

    /**
     * Creates a masking engine with default collaborators and fresh per-run token counters.
     */
    public MaskingEngine() {
        this(new EntityReplacer(), new EnumMap<>(EntityType.class));
    }

    /**
     * Creates a masking engine with shared token counters for deterministic numbering across files.
     *
     * @param entityReplacer span replacement collaborator
     * @param tokenCounters  mutable per-type counters keyed by entity type
     */
    MaskingEngine(EntityReplacer entityReplacer, Map<EntityType, Integer> tokenCounters) {
        this.entityReplacer = Objects.requireNonNull(entityReplacer, "entityReplacer must not be null");
        this.tokenCounters = Objects.requireNonNull(tokenCounters, "tokenCounters must not be null");
    }

    /**
     * Masks entities described by the detection result and registers mappings.
     *
     * @param content          original file content
     * @param detectionResult  resolved detection output; detection is not re-run
     * @param mappingRegistry  registry that receives one record per masked occurrence
     * @return masked content, or the original content when no entities were detected
     */
    public String mask(String content, DetectionResult detectionResult, MappingRegistry mappingRegistry) {
        Objects.requireNonNull(content, "content must not be null");
        Objects.requireNonNull(detectionResult, "detectionResult must not be null");
        Objects.requireNonNull(mappingRegistry, "mappingRegistry must not be null");

        if (!detectionResult.hasEntities()) {
            return content;
        }

        String repoRelativePath = detectionResult.getFileInfo().getRepoRelativePath();
        List<Entity> entities = new ArrayList<>(detectionResult.getEntities());
        entities.sort(Comparator.comparingInt(Entity::getStartOffset));

        List<EntityReplacer.Replacement> replacements = new ArrayList<>(entities.size());
        for (Entity entity : entities) {
            String maskedValue = nextToken(entity.getType());
            mappingRegistry.register(new MappingRecord(
                    repoRelativePath,
                    entity.getType(),
                    entity.getOriginalValue(),
                    maskedValue,
                    entity.getStartOffset(),
                    entity.getEndOffset()));
            replacements.add(new EntityReplacer.Replacement(
                    entity.getStartOffset(),
                    entity.getEndOffset(),
                    maskedValue));
        }

        return entityReplacer.replace(content, replacements);
    }

    private String nextToken(EntityType entityType) {
        int sequence = tokenCounters.merge(entityType, 1, Integer::sum);
        return entityType.name() + "_" + String.format("%06d", sequence);
    }
}
