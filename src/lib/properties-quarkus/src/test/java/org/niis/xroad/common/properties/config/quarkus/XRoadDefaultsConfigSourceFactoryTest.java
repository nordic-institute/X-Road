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

package org.niis.xroad.common.properties.config.quarkus;

import io.smallrye.config.ConfigSourceContext;
import io.smallrye.config.ConfigValue;
import io.smallrye.config.EnvConfigSource;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.niis.xroad.common.properties.config.ConfigKey;
import org.niis.xroad.common.properties.config.ConfigKeyProvider;
import org.niis.xroad.common.properties.config.keys.CommonConfigKeys;
import org.niis.xroad.common.properties.config.keys.ConfigKeyProviders;
import org.niis.xroad.common.properties.config.keys.DspConfigKeys;
import org.niis.xroad.common.properties.dbsource.NodeConfigTestTable;
import org.niis.xroad.common.properties.dbsource.NodeConfigTestTable.Row;
import org.niis.xroad.common.properties.dbsource.NodeNameProvider;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SystemStubsExtension.class)
class XRoadDefaultsConfigSourceFactoryTest {

    private static final String SHARED_VALUE = "1111";
    private static final String NODE_VALUE = "2222";
    private static final String OTHER_NODE_VALUE = "3333";
    private static final String OTHER_NODE_NAME = "node_2";
    private static final String NODE_ZERO_VALUE = "4444";

    private final List<ConfigKeyProvider> all = ConfigKeyProviders.allProviders();

    @SystemStub
    private final EnvironmentVariables variables = new EnvironmentVariables();

    @Test
    void publishesEveryProviderWhenScopesUnset() {
        assertThat(XRoadDefaultsConfigSourceFactory.selectProviders(all, null)).isEqualTo(all);
        assertThat(XRoadDefaultsConfigSourceFactory.selectProviders(all, "   ")).isEqualTo(all);
    }

    @Test
    void publishesOnlyListedScopes() {
        var selected = XRoadDefaultsConfigSourceFactory.selectProviders(all, "xroad.common");

        assertThat(selected).containsExactly(CommonConfigKeys.instance());
    }

    @Test
    void ignoresBlankAndUnknownScopes() {
        var selected = XRoadDefaultsConfigSourceFactory.selectProviders(all, " xroad.common , , xroad.unknown ");

        assertThat(selected).containsExactly(CommonConfigKeys.instance());
    }

    @Test
    void selectsBareKeysOfProvidersSpanningSeveralRoots() {
        var selected = XRoadDefaultsConfigSourceFactory.selectProviders(all, "xroad.acme");

        assertThat(selected).hasSize(1);
        assertThat(selected.getFirst().keys())
                .extracting(ConfigKey::key)
                .containsExactly("xroad.acme");
    }

    @Test
    void publishesTheNodeRowOfAFrameworkPublishedKeyWhenThisNodeMatches() throws SQLException {
        var key = DspConfigKeys.DID_RESOLUTION_FETCH_TIMEOUT_MS.key();
        NodeConfigTestTable.create("quarkusfactorynodewins", key, SHARED_VALUE, NODE_VALUE).forEach(variables::set);

        var sources = new XRoadDefaultsConfigSourceFactory()
                .getConfigSources(new NodeContext(NodeConfigTestTable.NODE_NAME));

        assertThat(sources)
                .filteredOn(XRoadFrameworkConfigSource.class::isInstance)
                .singleElement()
                .satisfies(source -> {
                    assertThat(source.getValue(key)).isEqualTo(NODE_VALUE);
                    assertThat(source.getOrdinal()).isEqualTo(299);
                });
    }

    @Test
    void publishesTheSharedRowWhenOnlyASharedRowExists() throws SQLException {
        var key = DspConfigKeys.DID_RESOLUTION_FETCH_TIMEOUT_MS.key();
        NodeConfigTestTable.create("quarkusfactorysharedonly", List.of(new Row(key, SHARED_VALUE, null)))
                .forEach(variables::set);

        var sources = new XRoadDefaultsConfigSourceFactory()
                .getConfigSources(new NodeContext(NodeConfigTestTable.NODE_NAME));

        assertThat(sources)
                .filteredOn(XRoadFrameworkConfigSource.class::isInstance)
                .singleElement()
                .satisfies(source -> assertThat(source.getValue(key)).isEqualTo(SHARED_VALUE));
    }

    @Test
    void publishesTheSharedRowNeverTheOtherNodesRow() throws SQLException {
        var key = DspConfigKeys.DID_RESOLUTION_FETCH_TIMEOUT_MS.key();
        NodeConfigTestTable.create("quarkusfactoryothernode", List.of(
                new Row(key, SHARED_VALUE, null),
                new Row(key, OTHER_NODE_VALUE, OTHER_NODE_NAME))).forEach(variables::set);

        var sources = new XRoadDefaultsConfigSourceFactory()
                .getConfigSources(new NodeContext(NodeConfigTestTable.NODE_NAME));

        assertThat(sources)
                .filteredOn(XRoadFrameworkConfigSource.class::isInstance)
                .singleElement()
                .satisfies(source -> assertThat(source.getValue(key)).isEqualTo(SHARED_VALUE));
    }

    @Test
    void publishesTheSharedRowWhenNoNodeNameConfiguredAndNoNodeZeroRowExists() throws SQLException {
        var key = DspConfigKeys.DID_RESOLUTION_FETCH_TIMEOUT_MS.key();
        NodeConfigTestTable.create("quarkusfactorynonode", List.of(new Row(key, SHARED_VALUE, null)))
                .forEach(variables::set);

        var sources = new XRoadDefaultsConfigSourceFactory().getConfigSources(new NodeContext(null));

        assertThat(sources)
                .filteredOn(XRoadFrameworkConfigSource.class::isInstance)
                .singleElement()
                .satisfies(source -> assertThat(source.getValue(key)).isEqualTo(SHARED_VALUE));
    }

    @Test
    void publishesTheNodeZeroRowWhenNoNodeNameConfigured() throws SQLException {
        var key = DspConfigKeys.DID_RESOLUTION_FETCH_TIMEOUT_MS.key();
        NodeConfigTestTable.create("quarkusfactorynodezero", List.of(
                new Row(key, SHARED_VALUE, null),
                new Row(key, NODE_ZERO_VALUE, NodeNameProvider.DEFAULT_NODE_NAME))).forEach(variables::set);

        var sources = new XRoadDefaultsConfigSourceFactory().getConfigSources(new NodeContext(null));

        assertThat(sources)
                .filteredOn(XRoadFrameworkConfigSource.class::isInstance)
                .singleElement()
                .satisfies(source -> assertThat(source.getValue(key)).isEqualTo(NODE_ZERO_VALUE));
    }

    @Test
    void resolvesTheNodeRowWhenTheNodeNameIsSuppliedAsAnEnvironmentVariable() throws SQLException {
        var key = DspConfigKeys.DID_RESOLUTION_FETCH_TIMEOUT_MS.key();
        NodeConfigTestTable.create("quarkusfactoryenvnodename", key, SHARED_VALUE, NODE_VALUE).forEach(variables::set);
        ConfigSource envSource = new EnvConfigSource(
                Map.of("XROAD_ADMIN_SERVICE_HA_NODE_NAME", NodeConfigTestTable.NODE_NAME), EnvConfigSource.ORDINAL);

        var sources = new XRoadDefaultsConfigSourceFactory().getConfigSources(new DelegatingContext(envSource));

        assertThat(sources)
                .filteredOn(XRoadFrameworkConfigSource.class::isInstance)
                .singleElement()
                .satisfies(source -> assertThat(source.getValue(key)).isEqualTo(NODE_VALUE));
    }

    private static final class NodeContext implements ConfigSourceContext {
        private final String nodeName;

        private NodeContext(String nodeName) {
            this.nodeName = nodeName;
        }

        @Override
        public ConfigValue getValue(String name) {
            if (nodeName != null && NodeNameProvider.HA_NODE_NAME_PROPERTY_KEY.equals(name)) {
                return ConfigValue.builder().withName(name).withValue(nodeName).build();
            }
            return null;
        }

        @Override
        public Iterator<String> iterateNames() {
            return List.<String>of().iterator();
        }
    }

    /**
     * Models the context a late {@code ConfigSourceFactory} receives, where only one backing
     * {@link ConfigSource} is visible, by forwarding every lookup to it.
     */
    private static final class DelegatingContext implements ConfigSourceContext {
        private final ConfigSource delegate;

        private DelegatingContext(ConfigSource delegate) {
            this.delegate = delegate;
        }

        @Override
        public ConfigValue getValue(String name) {
            var value = delegate.getValue(name);
            return value == null ? null : ConfigValue.builder().withName(name).withValue(value).build();
        }

        @Override
        public Iterator<String> iterateNames() {
            return delegate.getPropertyNames().iterator();
        }
    }
}
