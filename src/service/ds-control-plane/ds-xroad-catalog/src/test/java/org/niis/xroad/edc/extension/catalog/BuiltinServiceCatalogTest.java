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

import ee.ria.xroad.common.identifier.SecurityServerId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.serverconf.ServerConfProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.niis.xroad.edc.extension.catalog.BuiltinServiceCatalog.META_ALLOWED_METHODS_SERVICE_CODE;
import static org.niis.xroad.edc.extension.catalog.BuiltinServiceCatalog.META_GET_OPEN_API_SERVICE_CODE;
import static org.niis.xroad.edc.extension.catalog.BuiltinServiceCatalog.META_GET_WSDL_SERVICE_CODE;
import static org.niis.xroad.edc.extension.catalog.BuiltinServiceCatalog.META_LIST_METHODS_SERVICE_CODE;
import static org.niis.xroad.edc.extension.catalog.BuiltinServiceCatalog.OP_MONITOR_HEALTH_DATA_SERVICE_CODE;
import static org.niis.xroad.edc.extension.catalog.BuiltinServiceCatalog.OP_MONITOR_OPERATIONAL_DATA_SERVICE_CODE;
import static org.niis.xroad.edc.extension.catalog.BuiltinServiceCatalog.PROXY_MONITOR_SERVICE_CODE;

@ExtendWith(MockitoExtension.class)
class BuiltinServiceCatalogTest {

    private static final SecurityServerId.Conf SERVER_ID =
            SecurityServerId.Conf.create("DEV", "GOV", "1111", "ss0");

    @Mock
    private ServerConfProvider serverConfProvider;

    private BuiltinServiceCatalog catalog(boolean proxyMonitor, boolean opMonitor, boolean meta) {
        lenient().when(serverConfProvider.getIdentifier()).thenReturn(SERVER_ID);
        return new BuiltinServiceCatalog(serverConfProvider, proxyMonitor, opMonitor, meta,
                BuiltinServiceCatalog.DEFAULT_SERVER_PROXY_URL);
    }

    @Test
    void canonicalListContainsAllSevenServiceCodes() {
        var cat = catalog(true, true, true);

        var codes = cat.activeServiceIds().stream()
                .map(s -> s.getServiceCode())
                .toList();

        assertThat(codes).containsExactlyInAnyOrder(
                PROXY_MONITOR_SERVICE_CODE,
                OP_MONITOR_OPERATIONAL_DATA_SERVICE_CODE,
                OP_MONITOR_HEALTH_DATA_SERVICE_CODE,
                META_LIST_METHODS_SERVICE_CODE,
                META_ALLOWED_METHODS_SERVICE_CODE,
                META_GET_WSDL_SERVICE_CODE,
                META_GET_OPEN_API_SERVICE_CODE
        );
    }

    @Test
    void disablingProxyMonitorRemovesOneEntry() {
        var cat = catalog(false, true, true);

        var codes = cat.activeServiceIds().stream().map(s -> s.getServiceCode()).toList();

        assertThat(codes).doesNotContain(PROXY_MONITOR_SERVICE_CODE);
        assertThat(codes).hasSize(6);
    }

    @Test
    void disablingOpMonitorRemovesTwoEntries() {
        var cat = catalog(true, false, true);

        var codes = cat.activeServiceIds().stream().map(s -> s.getServiceCode()).toList();

        assertThat(codes).doesNotContain(OP_MONITOR_OPERATIONAL_DATA_SERVICE_CODE, OP_MONITOR_HEALTH_DATA_SERVICE_CODE);
        assertThat(codes).hasSize(5);
    }

    @Test
    void disablingMetaservicesRemovesFourEntries() {
        var cat = catalog(true, true, false);

        var codes = cat.activeServiceIds().stream().map(s -> s.getServiceCode()).toList();

        assertThat(codes).doesNotContain(META_LIST_METHODS_SERVICE_CODE, META_ALLOWED_METHODS_SERVICE_CODE,
                META_GET_WSDL_SERVICE_CODE, META_GET_OPEN_API_SERVICE_CODE);
        assertThat(codes).hasSize(3);
    }

    @Test
    void disablingAllAddonsYieldsEmptyCatalog() {
        var cat = catalog(false, false, false);

        assertThat(cat.activeServiceIds()).isEmpty();
        assertThat(cat.hasActiveServices()).isFalse();
    }

    @Test
    void findServiceIdByEncodedAssetId() {
        var cat = catalog(true, true, true);

        var proxyMonitorId = cat.activeServiceIds().stream()
                .filter(s -> PROXY_MONITOR_SERVICE_CODE.equals(s.getServiceCode()))
                .findFirst()
                .orElseThrow();

        var found = cat.findServiceId(proxyMonitorId.asEncodedId());

        assertThat(found).isNotNull();
        assertThat(found.getServiceCode()).isEqualTo(PROXY_MONITOR_SERVICE_CODE);
    }

    @Test
    void findServiceIdReturnsNullForUnknownId() {
        var cat = catalog(true, true, true);

        assertThat(cat.findServiceId("DEV:GOV:1111:unknown")).isNull();
        assertThat(cat.findServiceId(null)).isNull();
        assertThat(cat.findServiceId("")).isNull();
    }

    @Test
    void builtinServiceIdsOwnedByServerOwner() {
        var cat = catalog(true, true, true);

        cat.activeServiceIds().forEach(serviceId -> {
            assertThat(serviceId.getXRoadInstance()).isEqualTo("DEV");
            assertThat(serviceId.getMemberClass()).isEqualTo("GOV");
            assertThat(serviceId.getMemberCode()).isEqualTo("1111");
            assertThat(serviceId.getSubsystemCode()).isNull();
        });
    }

    @Test
    void serverProxyUrlIsRetained() {
        var cat = catalog(true, true, true);

        assertThat(cat.serverProxyUrl()).isEqualTo(BuiltinServiceCatalog.DEFAULT_SERVER_PROXY_URL);
    }

    @Test
    void settingConstantsMatchExpectedNames() {
        assertThat(PROXY_MONITOR_SERVICE_CODE).isEqualTo("getSecurityServerMetrics");
        assertThat(OP_MONITOR_OPERATIONAL_DATA_SERVICE_CODE).isEqualTo("getSecurityServerOperationalData");
        assertThat(OP_MONITOR_HEALTH_DATA_SERVICE_CODE).isEqualTo("getSecurityServerHealthData");
        assertThat(META_LIST_METHODS_SERVICE_CODE).isEqualTo("listMethods");
        assertThat(META_ALLOWED_METHODS_SERVICE_CODE).isEqualTo("allowedMethods");
        assertThat(META_GET_WSDL_SERVICE_CODE).isEqualTo("getWsdl");
        assertThat(META_GET_OPEN_API_SERVICE_CODE).isEqualTo("getOpenAPI");
    }

    @Test
    void isBuiltinAssetIdPredicateMatchesActiveIds() {
        var cat = catalog(true, false, false);
        var predicate = cat.isBuiltinAssetId();

        var proxyMonitorAssetId = cat.activeServiceIds().stream()
                .filter(s -> PROXY_MONITOR_SERVICE_CODE.equals(s.getServiceCode()))
                .findFirst()
                .orElseThrow()
                .asEncodedId();

        assertThat(predicate.test(proxyMonitorAssetId)).isTrue();
        assertThat(predicate.test("DEV:GOV:1111:unknown")).isFalse();
    }

    @Test
    void activeServiceIdsAreDistinct() {
        var cat = catalog(true, true, true);

        var ids = cat.activeServiceIds().stream()
                .map(s -> s.asEncodedId())
                .toList();

        assertThat(ids).doesNotHaveDuplicates();
    }

    @Test
    void disabledBuiltinAssetIdNotFoundByFindServiceId() {
        var cat = catalog(false, true, true);

        var opMonitorCat = catalog(true, true, true);
        var proxyMonitorAssetId = opMonitorCat.activeServiceIds().stream()
                .filter(s -> PROXY_MONITOR_SERVICE_CODE.equals(s.getServiceCode()))
                .findFirst()
                .orElseThrow()
                .asEncodedId();

        assertThat(cat.findServiceId(proxyMonitorAssetId)).isNull();
    }

    @Test
    void hasActiveServicesReturnsTrueWhenAnyEnabled() {
        var cat = catalog(true, false, false);
        assertThat(cat.hasActiveServices()).isTrue();
    }

    @Test
    void assetIdsContainNoSubsystem() {
        var cat = catalog(true, true, true);

        cat.activeServiceIds().forEach(serviceId -> {
            var encoded = serviceId.asEncodedId();
            var parts = encoded.split(":");
            assertThat(parts).hasSize(4);
        });
    }
}
