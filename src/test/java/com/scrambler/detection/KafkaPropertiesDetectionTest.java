package com.scrambler.detection;

import com.scrambler.inventory.FileInfo;
import com.scrambler.masking.MappingRegistry;
import com.scrambler.masking.MaskingEngine;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KafkaPropertiesDetectionTest {

    private static final FileInfo FILE_INFO = new FileInfo(
            Paths.get("/workspace/repo/config/kafka.properties"),
            "config/kafka.properties",
            128L);

    private static final String KAFKA_PROPERTIES = """
            bootstrap.servers=kafka1.icici.internal:9092,kafka2.icici.internal:9092
            schema.registry.url=astra://lineastr.aline.ast/rali-nea/s6/tralin
            security.protocol=SASL_SSL
            sasl.jaas.config=password=secret123
            kafka.api.key=evermo2468024602REEVERMOREEV
            """;

    @Test
    void detectsSensitiveTermsInKafkaProperties() {
        DetectionResult result = new DetectionEngine().detect(new DetectionContext(FILE_INFO, KAFKA_PROPERTIES));
        var types = result.getEntities().stream()
                .map(entity -> entity.getType().name())
                .collect(Collectors.toList());

        assertTrue(types.contains("COMPANY_BRAND"), "icici in hostnames: " + types);
        assertTrue(types.contains("PASSWORD"), "embedded password in jaas config: " + types);
        assertTrue(types.contains("API_KEY"), "kafka.api.key: " + types);
    }

    @Test
    void masksSensitiveTermsInKafkaProperties() {
        DetectionEngine detectionEngine = new DetectionEngine();
        MaskingEngine maskingEngine = new MaskingEngine();
        MappingRegistry mappingRegistry = new MappingRegistry();

        DetectionResult detection = detectionEngine.detect(new DetectionContext(FILE_INFO, KAFKA_PROPERTIES));
        String masked = maskingEngine.mask(KAFKA_PROPERTIES, detection, mappingRegistry);

        assertFalse(masked.contains("icici"));
        assertFalse(masked.contains("secret123"));
        assertFalse(masked.contains("evermo2468024602REEVERMOREEV"));
        assertFalse(masked.contains("password=secret123"));
        assertTrue(masked.contains("bootstrap.servers="));
        assertTrue(masked.contains("kafka.api.key="));
        assertTrue(masked.contains("sasl.jaas.config="));
    }
}
