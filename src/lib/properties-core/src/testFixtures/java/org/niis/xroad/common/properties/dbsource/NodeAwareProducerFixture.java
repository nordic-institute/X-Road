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
package org.niis.xroad.common.properties.dbsource;

import org.niis.xroad.common.properties.config.XRoadConfig;
import org.niis.xroad.common.properties.config.keys.CommonRpcConfigKeys;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves one key, stored as a shared row, a {@code node_0} row and a {@code node_1} row, through the
 * {@link XRoadConfig} producer of an application, with and without a configured node name.
 */
public final class NodeAwareProducerFixture {

    public static final String SHARED_VALUE = "shared.example.test";
    public static final String NODE_VALUE = "node-1.example.test";
    public static final String DEFAULT_NODE_VALUE = "node-0.example.test";

    private static final String KEY = CommonRpcConfigKeys.CERT_PROVISIONING_COMMON_NAME.key();

    private final Map<String, String> databaseEnvironment;
    private final Producer producer;

    /**
     * Creates the table.
     * @param databaseName name of the in-memory database, unique per test class
     * @param producer the producer under test
     */
    public NodeAwareProducerFixture(String databaseName, Producer producer) {
        try {
            this.databaseEnvironment = NodeConfigTestTable.create(databaseName, List.of(
                    new NodeConfigTestTable.Row(KEY, SHARED_VALUE, null),
                    new NodeConfigTestTable.Row(KEY, DEFAULT_NODE_VALUE, NodeNameProvider.DEFAULT_NODE_NAME),
                    new NodeConfigTestTable.Row(KEY, NODE_VALUE, NodeConfigTestTable.NODE_NAME)));
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot create the configuration table", e);
        }
        this.producer = producer;
    }

    /**
     * @return the value the producer resolves when the application has a configured node name
     * @throws Exception when the producer fails
     */
    public String resolveWithNodeName() throws Exception {
        return resolve(Optional.of(NodeConfigTestTable.NODE_NAME));
    }

    /**
     * @return the value the producer resolves when the application has no configured node name
     * @throws Exception when the producer fails
     */
    public String resolveWithoutNodeName() throws Exception {
        return resolve(Optional.empty());
    }

    private String resolve(Optional<String> nodeName) throws Exception {
        return new EnvironmentVariables(databaseEnvironment).execute(
                () -> producer.create(nodeName).value(CommonRpcConfigKeys.CERT_PROVISIONING_COMMON_NAME));
    }

    /** The {@code xRoadConfig} producer method of an application. */
    @FunctionalInterface
    public interface Producer {
        XRoadConfig create(Optional<String> nodeName);
    }
}
