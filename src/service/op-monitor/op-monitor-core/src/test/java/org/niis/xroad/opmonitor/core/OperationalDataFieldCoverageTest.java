/*
 * The MIT License
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
package org.niis.xroad.opmonitor.core;

import com.google.protobuf.Descriptors;
import org.junit.jupiter.api.Test;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.opmonitor.api.OperationalDataRecordProto;
import org.niis.xroad.opmonitor.core.jpa.entity.OperationalDataRecordEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.opmonitor.api.OpMonitoringData.SecurityServerType.PRODUCER;

/**
 * A store record field lives in the proxy field constants, the gRPC contract, op-monitor's record and its entity.
 * These tests fail until a new field is added to all of them, to the test fixture and to both mappers.
 */
class OperationalDataFieldCoverageTest {
    private static final Set<String> SERVER_ASSIGNED_FIELDS = Set.of("id", "monitoringDataTs");

    @Test
    void proxyFieldConstantsGrpcContractRecordAndEntityHaveTheSameFields() {
        var proxyFields = normalized(proxyFieldConstants());

        assertThat(normalized(protoFields())).isEqualTo(proxyFields);
        assertThat(normalized(storedFields(OperationalDataRecord.class))).isEqualTo(proxyFields);
        assertThat(normalized(storedFields(OperationalDataRecordEntity.class))).isEqualTo(proxyFields);
    }

    @Test
    void fixtureAndBothMappersCoverEveryField() {
        var data = OpMonitoringDataFixtures.fullyPopulated(PRODUCER);
        var proto = data.toProto();

        assertThat(data.getData().keySet()).isEqualTo(proxyFieldConstants());
        assertThat(proto.getAllFields()).hasSameSizeAs(protoFields());
        assertThat(OperationalDataRecordProtoMapper.fromProto(proto))
                .hasNoNullFieldsOrPropertiesExcept(SERVER_ASSIGNED_FIELDS.toArray(String[]::new));
    }

    private static Set<String> proxyFieldConstants() {
        return Arrays.stream(OpMonitoringData.class.getDeclaredFields())
                .filter(field -> Modifier.isStatic(field.getModifiers()) && field.getType() == String.class)
                .filter(field -> !field.getName().startsWith("ERROR_"))
                .map(OperationalDataFieldCoverageTest::constantValue)
                .collect(Collectors.toSet());
    }

    private static Set<String> protoFields() {
        return OperationalDataRecordProto.getDescriptor().getFields().stream()
                .map(Descriptors.FieldDescriptor::getJsonName)
                .collect(Collectors.toSet());
    }

    private static Set<String> storedFields(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(Field::getName)
                .filter(name -> !SERVER_ASSIGNED_FIELDS.contains(name))
                .collect(Collectors.toSet());
    }

    private static Set<String> normalized(Set<String> names) {
        return names.stream().map(name -> name.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
    }

    private static String constantValue(Field field) {
        try {
            field.setAccessible(true);
            return (String) field.get(null);
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }
}
