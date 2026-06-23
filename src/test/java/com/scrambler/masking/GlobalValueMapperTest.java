package com.scrambler.masking;

import com.scrambler.detection.EntityType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalValueMapperTest {

    @Test
    void companyBrandCaseVariantsShareMaskedValue() {
        GlobalValueMapper mapper = new GlobalValueMapper();

        String upper = mapper.resolve(EntityType.COMPANY_BRAND, "ICICI");
        String mixed = mapper.resolve(EntityType.COMPANY_BRAND, "Icici");
        String lower = mapper.resolve(EntityType.COMPANY_BRAND, "icici");

        assertEquals(upper, mixed);
        assertEquals(upper, lower);
        assertEquals(3, mapper.size());
    }

    @Test
    void companyBrandPhraseCaseVariantsShareMaskedValue() {
        GlobalValueMapper mapper = new GlobalValueMapper();

        String canonical = mapper.resolve(EntityType.COMPANY_BRAND, "ICICI Direct");
        String variant = mapper.resolve(EntityType.COMPANY_BRAND, "icici direct");

        assertEquals(canonical, variant);
        assertEquals(2, mapper.size());
    }

    @Test
    void distinctBrandTermsWithSimilarSpellingKeepSeparateMappings() {
        GlobalValueMapper mapper = new GlobalValueMapper();

        String instaBiz = mapper.resolve(EntityType.COMPANY_BRAND, "InstaBIZ");
        String instaBizAlt = mapper.resolve(EntityType.COMPANY_BRAND, "InstaBiz");

        assertEquals("Vertex Business", instaBiz);
        assertEquals("Vertex Commerce", instaBizAlt);
    }
}
