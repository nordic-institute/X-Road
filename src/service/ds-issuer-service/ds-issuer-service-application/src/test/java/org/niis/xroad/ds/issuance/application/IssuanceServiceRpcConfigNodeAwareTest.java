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
package org.niis.xroad.ds.issuance.application;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.niis.xroad.common.properties.config.keys.CommonRpcConfigKeys;
import org.niis.xroad.common.properties.dbsource.NodeConfigTestTable;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SystemStubsExtension.class)
class IssuanceServiceRpcConfigNodeAwareTest {

    private static final String SHARED_VALUE = "shared.example.test";
    private static final String NODE_VALUE = "node-1.example.test";

    private static Map<String, String> databaseEnvironment;

    @SystemStub
    private final EnvironmentVariables variables = new EnvironmentVariables();

    @BeforeAll
    static void createTable() throws SQLException {
        databaseEnvironment = NodeConfigTestTable.create("issuernodeaware",
                CommonRpcConfigKeys.CERT_PROVISIONING_COMMON_NAME.key(), SHARED_VALUE, NODE_VALUE);
    }

    @BeforeEach
    void pointAtTheDatabase() {
        databaseEnvironment.forEach(variables::set);
    }

    @Test
    void optedInWithANodeNameResolvesTheNodeValue() {
        var config = new IssuanceServiceRpcConfig().xRoadConfig("ds-issuer-service", true, Optional.of(NodeConfigTestTable.NODE_NAME));

        assertEquals(NODE_VALUE, config.value(CommonRpcConfigKeys.CERT_PROVISIONING_COMMON_NAME));
    }

    @Test
    void notOptedInResolvesTheSharedValue() {
        var config = new IssuanceServiceRpcConfig().xRoadConfig("ds-issuer-service", false, Optional.of(NodeConfigTestTable.NODE_NAME));

        assertEquals(SHARED_VALUE, config.value(CommonRpcConfigKeys.CERT_PROVISIONING_COMMON_NAME));
    }
}
