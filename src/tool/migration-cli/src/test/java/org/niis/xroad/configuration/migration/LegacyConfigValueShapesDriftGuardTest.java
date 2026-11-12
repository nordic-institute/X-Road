/*
 * The MIT License
 *
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.niis.xroad.configuration.migration;

import ee.ria.xroad.common.ServicePrioritizationStrategy;
import ee.ria.xroad.common.crypto.identifier.KeyAlgorithm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.niis.xroad.messagelog.archive.GroupingStrategy;
import org.niis.xroad.signer.common.config.SignerModuleConfig;
import tools.jackson.databind.util.NamingStrategyImpls;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards {@link LegacyConfigValueShapes} against drifting from {@link GroupingStrategy},
 * {@link SignerModuleConfig}, {@link KeyAlgorithm} and {@link ServicePrioritizationStrategy}, whose constants
 * and fields its hardcoded table duplicates.
 */
class LegacyConfigValueShapesDriftGuardTest {

    private static final String GROUPING_KEY = "xroad.message-log-encryption.archive.grouping-strategy";

    private static final String SIGNING_KEY_ALGORITHM_KEY = "xroad.proxy-ui-api.signing-key-algorithm";
    private static final String AUTHENTICATION_KEY_ALGORITHM_KEY = "xroad.proxy-ui-api.authentication-key-algorithm";
    private static final String INTERNAL_KEY_ALGORITHM_KEY = "xroad.admin-service.internal-key-algorithm";
    private static final String EXTERNAL_KEY_ALGORITHM_KEY = "xroad.admin-service.external-key-algorithm";

    private static final String OCSP_PRIORITIZATION_STRATEGY_KEY = "xroad.signer.ocsp-prioritization-strategy";
    private static final String TIMESTAMPING_PRIORITIZATION_STRATEGY_KEY =
            "xroad.proxy.message-log.timestamping-prioritization-strategy";

    @Test
    void groupingStrategyShapeMatchesMessageLogGroupingStrategyEnum() {
        var shape = (LegacyConfigValueShapes.EnumShape) LegacyConfigValueShapes.shapeOf(GROUPING_KEY);

        List<String> expectedConstants = Arrays.stream(GroupingStrategy.values())
                .map(Enum::name)
                .toList();

        assertThat(shape.allowedConstants()).containsExactlyInAnyOrderElementsOf(expectedConstants);
    }

    @ParameterizedTest
    @ValueSource(strings = {SIGNING_KEY_ALGORITHM_KEY, AUTHENTICATION_KEY_ALGORITHM_KEY,
            INTERNAL_KEY_ALGORITHM_KEY, EXTERNAL_KEY_ALGORITHM_KEY})
    void keyAlgorithmShapeMatchesKeyAlgorithmEnum(String key) {
        var shape = (LegacyConfigValueShapes.EnumShape) LegacyConfigValueShapes.shapeOf(key);

        List<String> expectedConstants = Arrays.stream(KeyAlgorithm.values())
                .map(Enum::name)
                .toList();

        assertThat(shape.allowedConstants()).containsExactlyInAnyOrderElementsOf(expectedConstants);
    }

    @ParameterizedTest
    @ValueSource(strings = {OCSP_PRIORITIZATION_STRATEGY_KEY, TIMESTAMPING_PRIORITIZATION_STRATEGY_KEY})
    void prioritizationStrategyShapeMatchesServicePrioritizationStrategyEnum(String key) {
        var shape = (LegacyConfigValueShapes.EnumShape) LegacyConfigValueShapes.shapeOf(key);

        List<String> expectedConstants = Arrays.stream(ServicePrioritizationStrategy.values())
                .map(Enum::name)
                .toList();

        assertThat(shape.allowedConstants()).containsExactlyInAnyOrderElementsOf(expectedConstants);
    }

    @Test
    void signerModulesShapeFieldsMatchSignerModuleConfigFields() {
        Map<String, LegacyConfigValueShapes.FieldType> expectedFields = new HashMap<>();
        for (Field field : SignerModuleConfig.class.getDeclaredFields()) {
            expectedFields.put(NamingStrategyImpls.KEBAB_CASE.translate(field.getName()), fieldTypeOf(field));
        }

        assertThat(LegacyConfigValueShapes.signerModuleFields()).containsExactlyInAnyOrderEntriesOf(expectedFields);
    }

    private static LegacyConfigValueShapes.FieldType fieldTypeOf(Field field) {
        Class<?> type = field.getType();
        if (type == boolean.class || type == Boolean.class) {
            return LegacyConfigValueShapes.FieldType.BOOLEAN;
        }
        if (type == String.class) {
            return LegacyConfigValueShapes.FieldType.STRING;
        }
        if (type == List.class) {
            return LegacyConfigValueShapes.FieldType.STRING_LIST;
        }
        if (type == Set.class) {
            return LegacyConfigValueShapes.FieldType.SLOT_IDS;
        }
        throw new IllegalStateException("Unexpected field type %s for field %s".formatted(type, field.getName()));
    }

}
