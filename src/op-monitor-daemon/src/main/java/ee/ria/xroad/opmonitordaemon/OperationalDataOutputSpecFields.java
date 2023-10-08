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
package ee.ria.xroad.opmonitordaemon;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static ee.ria.xroad.common.opmonitoring.OpMonitoringData.SECURITY_SERVER_INTERNAL_IP;

/**
 * In the operational data request (getSecurityServerOperationalData) it is
 * possible to determine the set of the requested operational data fields.
 * This class provides list of all existing operational data fields.
 *
 * The output field <code>securityServerInternalIp</code> is allowed only to
 * the security server owner and to the central monitoring client.
 */
final class OperationalDataOutputSpecFields {

    private static final String ID = "id";
    static final String MONITORING_DATA_TS = "monitoringDataTs";

    /**
     * Complete list of output fields. Allowed only to the security server
     * owner and to central monitoring client.
     */
    static final Set<String> OUTPUT_FIELDS = getFieldList();

    /**
     * List of output fields allowed to any client.
     */
    static final Set<String> PUBLIC_OUTPUT_FIELDS = getPublicFields();

    private OperationalDataOutputSpecFields() {
    }

    private static Set<String> getFieldList() {
        return Arrays.stream(OperationalDataRecord.class.getDeclaredFields())
                .filter(i -> !i.isSynthetic()) // for excluding $jacocoData
                .map(Field::getName)
                .filter(i -> !ID.equals(i))
                .collect(Collectors.toSet());
    }

    private static Set<String> getPublicFields() {
        return getFieldList(Collections.singleton(SECURITY_SERVER_INTERNAL_IP));
    }

    private static Set<String> getFieldList(Set<String> excludeFields) {
        Set<String> fields = new HashSet<>(OUTPUT_FIELDS);

        fields.removeAll(excludeFields);

        return fields;
    }
}
