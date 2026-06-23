package com.scrambler.masking;

import com.scrambler.detection.DetectionResult;
import com.scrambler.detection.Entity;
import com.scrambler.detection.EntityType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Converts detected entities into format-preserving masked values using a global value mapper.
 */
public final class MaskingEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(MaskingEngine.class);

    private final EntityReplacer entityReplacer;
    private final GlobalValueMapper globalValueMapper;

    /**
     * Creates a masking engine with default collaborators and a fresh global value mapper.
     */
    public MaskingEngine() {
        this(new EntityReplacer(), new GlobalValueMapper());
    }

    /**
     * Creates a masking engine with a shared global value mapper for deterministic mappings across files.
     *
     * @param entityReplacer     span replacement collaborator
     * @param globalValueMapper  shared original-to-masked dictionary for the run
     */
    MaskingEngine(EntityReplacer entityReplacer, GlobalValueMapper globalValueMapper) {
        this.entityReplacer = Objects.requireNonNull(entityReplacer, "entityReplacer must not be null");
        this.globalValueMapper = Objects.requireNonNull(globalValueMapper, "globalValueMapper must not be null");
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
        LOGGER.debug("Masking {} entities in {}", detectionResult.getEntities().size(), repoRelativePath);
        List<Entity> entities = new ArrayList<>(detectionResult.getEntities());
        entities.sort(Comparator.comparingInt(Entity::getStartOffset));

        List<EntityReplacer.Replacement> replacements = new ArrayList<>(entities.size());
        for (Entity entity : entities) {
            String maskedValue = globalValueMapper.resolve(entity.getType(), entity.getOriginalValue());
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

    /**
     * Returns the shared global value mapper used by this engine.
     *
     * @return global value mapper
     */
    public GlobalValueMapper getGlobalValueMapper() {
        return globalValueMapper;
    }
}
