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
package org.niis.xroad.edc.extension.catalog;

import org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance;
import org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstanceStates;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.system.configuration.ConfigFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.niis.xroad.edc.protocol.assetaccess.XRoadTransferType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.eclipse.edc.participantcontext.spi.types.ParticipantResource.queryByParticipantContextId;

class XRoadDataPlaneInstanceStoreTest {

    private static final String PROXY_URL = "http://127.0.0.1:5590/full/api/v1/dataflows";
    private static final String PULL = XRoadTransferType.PULL.wireValue();
    private static final String MEMBER_CONTEXT = "DEV:COM:222";

    @Test
    void findByIdResolvesNeverRegisteredMemberContext() {
        var store = store(proxyEntry());

        var instance = store.findById("xroad-proxy::" + MEMBER_CONTEXT);

        assertThat(instance).isNotNull();
        assertThat(instance.getId()).isEqualTo("xroad-proxy::" + MEMBER_CONTEXT);
        assertThat(instance.getParticipantContextId()).isEqualTo(MEMBER_CONTEXT);
        assertThat(instance.getUrl()).hasToString(PROXY_URL);
        assertThat(instance.getAllowedSourceTypes()).containsExactlyInAnyOrder("http", "https");
        assertThat(instance.getAllowedTransferTypes()).containsExactly(PULL);
        assertThat(instance.getState()).isEqualTo(DataPlaneInstanceStates.REGISTERED.code());
        assertThat(instance.stateAsString()).isEqualTo("REGISTERED");
        assertThat(instance.getLabels()).isEmpty();
        assertThat(instance.getAuthorizationProfile()).isNull();
    }

    @Test
    void findByIdTreatsEverythingAfterFirstSeparatorAsParticipantContext() {
        var store = store(proxyEntry());

        var instance = store.findById("xroad-proxy::a::b");

        assertThat(instance).isNotNull();
        assertThat(instance.getParticipantContextId()).isEqualTo("a::b");
        assertThat(instance.getId()).isEqualTo("xroad-proxy::a::b");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "xroad-proxy", "xroad-proxy:" + MEMBER_CONTEXT, "xroad-proxy::", "unknown::" + MEMBER_CONTEXT,
            "::" + MEMBER_CONTEXT, "disabled::" + MEMBER_CONTEXT})
    void findByIdReturnsNullForNullMalformedUnknownOrDisabledEntryId(String id) {
        var settings = proxyEntry();
        settings.putAll(entry("disabled", "disabled", "http://disabled:5590", PULL, false));
        var store = store(settings);

        assertThat(store.findById(id)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ss1", "ss1-mgmt", "system", MEMBER_CONTEXT})
    void queryReturnsOneInstancePerEnabledEntryForAnyContext(String participantContextId) {
        var settings = proxyEntry();
        settings.putAll(entry("dp2", "dp2", "http://dp2:5591", XRoadTransferType.PUSH.wireValue(), true));
        settings.putAll(entry("off", "off", "http://off:5592", PULL, false));
        var store = store(settings);

        var instances = store.query(queryByParticipantContextId(participantContextId).build()).toList();

        assertThat(instances).extracting(DataPlaneInstance::getId)
                .containsExactlyInAnyOrder("xroad-proxy::" + participantContextId, "dp2::" + participantContextId);
        assertThat(instances).allSatisfy(instance -> {
            assertThat(instance.getParticipantContextId()).isEqualTo(participantContextId);
            assertThat(instance.getState()).isEqualTo(DataPlaneInstanceStates.REGISTERED.code());
        });
        assertThat(instances).filteredOn(instance -> instance.getId().startsWith("dp2::"))
                .singleElement()
                .satisfies(instance -> {
                    assertThat(instance.getUrl()).hasToString("http://dp2:5591");
                    assertThat(instance.getAllowedTransferTypes()).containsExactly(XRoadTransferType.PUSH.wireValue());
                });
    }

    @Test
    void queryAndFindByIdReturnFreshObjectsOnEveryCall() {
        var store = store(proxyEntry());
        var query = queryByParticipantContextId(MEMBER_CONTEXT).build();

        var firstQuery = store.query(query).toList();
        var secondQuery = store.query(query).toList();
        var firstLookup = store.findById("xroad-proxy::" + MEMBER_CONTEXT);
        var secondLookup = store.findById("xroad-proxy::" + MEMBER_CONTEXT);

        assertThat(firstQuery).singleElement().isNotSameAs(secondQuery.getFirst());
        assertThat(firstLookup).isNotSameAs(secondLookup).isNotSameAs(firstQuery.getFirst());
    }

    @Test
    void queryWithNoEntriesConfiguredReturnsNothing() {
        var store = store(new LinkedHashMap<>());

        assertThat(store.query(queryByParticipantContextId(MEMBER_CONTEXT).build())).isEmpty();
        assertThat(store.findById("xroad-proxy::" + MEMBER_CONTEXT)).isNull();
    }

    @Test
    void allowedTypesAreSplitOnCommasAndTrimmed() {
        var settings = entry("dp1", "dp1", "http://dp1:5590", PULL + ", " + XRoadTransferType.PUSH.wireValue(), true);
        settings.put("xroad.cp.dataplane.dp1.allowed-source-types", "http, https");
        var store = store(settings);

        var instance = store.findById("dp1::" + MEMBER_CONTEXT);

        assertThat(instance.getAllowedSourceTypes()).containsExactlyInAnyOrder("http", "https");
        assertThat(instance.getAllowedTransferTypes())
                .containsExactlyInAnyOrder(PULL, XRoadTransferType.PUSH.wireValue());
    }

    @ParameterizedTest
    @MethodSource("unsupportedQueries")
    void queryWithUnsupportedFilterFailsLoudly(QuerySpec querySpec) {
        var store = store(proxyEntry());

        assertThatThrownBy(() -> store.query(querySpec)).isInstanceOf(UnsupportedOperationException.class);
    }

    static Stream<Arguments> unsupportedQueries() {
        return Stream.of(
                Arguments.of(QuerySpec.none()),
                Arguments.of(QuerySpec.Builder.newInstance()
                        .filter(new Criterion("participantContextId", "=", MEMBER_CONTEXT))
                        .filter(new Criterion("state", "=", 100)).build()),
                Arguments.of(QuerySpec.Builder.newInstance().filter(new Criterion("state", "=", 100)).build()),
                Arguments.of(QuerySpec.Builder.newInstance()
                        .filter(new Criterion("participantContextId", "like", MEMBER_CONTEXT)).build()),
                Arguments.of(QuerySpec.Builder.newInstance()
                        .filter(new Criterion("participantContextId", "=", null)).build()),
                Arguments.of(QuerySpec.Builder.newInstance()
                        .filter(new Criterion("participantContextId", "=", "")).build()),
                Arguments.of(QuerySpec.Builder.newInstance()
                        .filter(new Criterion("participantContextId", "=", 42)).build())
        );
    }

    @ParameterizedTest
    @MethodSource("unsupportedOperations")
    void unsupportedOperationsFailLoudly(String operation, StoreCall call) {
        var store = store(proxyEntry());

        assertThatThrownBy(() -> call.invoke(store))
                .as(operation)
                .isInstanceOf(UnsupportedOperationException.class);
    }

    static Stream<Arguments> unsupportedOperations() {
        var instance = DataPlaneInstance.Builder.newInstance().id("xroad-proxy::" + MEMBER_CONTEXT).url(PROXY_URL).build();
        return Stream.of(
                Arguments.of("getAll", (StoreCall) store -> store.getAll()),
                Arguments.of("save", (StoreCall) store -> store.save(instance)),
                Arguments.of("deleteById", (StoreCall) store -> store.deleteById(instance.getId())),
                Arguments.of("findByIdAndLease", (StoreCall) store -> store.findByIdAndLease(instance.getId())),
                Arguments.of("breakLease", (StoreCall) store -> store.breakLease(instance)),
                Arguments.of("nextNotLeased", (StoreCall) store -> store.nextNotLeased(1))
        );
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void entryIdContainingSeparatorFailsStartupNamingTheEntry(boolean enabled) {
        var settings = entry("bad-entry", "bad::id", "http://bad:5590", PULL, enabled);

        assertThatThrownBy(() -> store(settings))
                .isInstanceOf(EdcException.class)
                .hasMessageContaining("bad-entry")
                .hasMessageContaining("bad::id");
    }

    @Test
    void invalidUrlFailsStartupNamingTheEntry() {
        var settings = entry("broken", "broken", "not a url", PULL, true);

        assertThatThrownBy(() -> store(settings))
                .isInstanceOf(EdcException.class)
                .hasMessageContaining("broken");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void duplicateEntryIdsFailStartupNamingBothEntries(boolean secondEnabled) {
        var settings = entry("first", "xroad-proxy", PROXY_URL, PULL, true);
        settings.putAll(entry("second", "xroad-proxy", "http://other:5590/full/api/v1/dataflows", PULL, secondEnabled));

        assertThatThrownBy(() -> store(settings))
                .isInstanceOf(EdcException.class)
                .hasMessageContaining("first")
                .hasMessageContaining("second")
                .hasMessageContaining("xroad-proxy");
    }

    @Test
    void disabledEntryNeedsNoUrl() {
        var settings = new LinkedHashMap<String, String>();
        settings.put("xroad.cp.dataplane.off.id", "off");
        settings.put("xroad.cp.dataplane.off.enabled", "false");

        var store = store(settings);

        assertThat(store.query(queryByParticipantContextId(MEMBER_CONTEXT).build())).isEmpty();
    }

    private static XRoadDataPlaneInstanceStore store(Map<String, String> settings) {
        var entries = ConfigFactory.fromMap(settings).getConfig("xroad.cp.dataplane").partition().toList();
        return new XRoadDataPlaneInstanceStore(entries);
    }

    private static Map<String, String> proxyEntry() {
        var settings = entry("proxy", "xroad-proxy", PROXY_URL, PULL, true);
        settings.put("xroad.cp.dataplane.proxy.allowed-source-types", "http,https");
        return settings;
    }

    private static Map<String, String> entry(String node, String id, String url, String transferTypes, boolean enabled) {
        var settings = new LinkedHashMap<String, String>();
        var prefix = "xroad.cp.dataplane." + node + ".";
        settings.put(prefix + "id", id);
        settings.put(prefix + "url", url);
        settings.put(prefix + "allowed-transfer-types", transferTypes);
        settings.put(prefix + "enabled", String.valueOf(enabled));
        return settings;
    }

    @FunctionalInterface
    interface StoreCall {
        void invoke(XRoadDataPlaneInstanceStore store) throws Exception;
    }
}
