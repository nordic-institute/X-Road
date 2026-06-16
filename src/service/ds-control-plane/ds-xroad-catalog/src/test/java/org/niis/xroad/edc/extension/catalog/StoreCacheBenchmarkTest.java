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

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.metadata.Endpoint;
import ee.ria.xroad.common.metadata.RestServiceDetailsListType;

import org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset;
import org.eclipse.edc.spi.query.QuerySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.serverconf.IsAuthentication;
import org.niis.xroad.serverconf.ServerConfProvider;
import org.niis.xroad.serverconf.model.AccessRight;
import org.niis.xroad.serverconf.model.DescriptionType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreCacheBenchmarkTest {

    private static final int WARMUP = 50;
    private static final int K = 200;

    @Mock
    private GlobalConfProvider globalConfProvider;

    @Test
    void benchmarkCatalogAndFindById() {
        when(globalConfProvider.getManagementRequestService()).thenReturn(null);

        System.out.println("\n=== Benchmark Results ===");
        System.out.printf("%-14s %-6s %-10s %-14s %-10s%n", "Path", "N", "Cache", "Median (µs)", "P95 (µs)");

        runCatalogBenchmark(100);
        runCatalogBenchmark(1000);
        runFindByIdBenchmark(100);
        runFindByIdBenchmark(1000);
        runResolveForAssetBenchmark(100);
        runResolveForAssetBenchmark(1000);
    }

    private void runCatalogBenchmark(int serviceCount) {
        var fakeProvider = new FakeServerConfProvider(serviceCount);

        var storeDisabled = buildStore(fakeProvider, false);
        for (int i = 0; i < WARMUP; i++) {
            storeDisabled.queryAssets(QuerySpec.max()).count();
        }
        var timesDisabled = measureQueryAssets(storeDisabled);

        var storeEnabled = buildStore(fakeProvider, true);
        for (int i = 0; i < WARMUP; i++) {
            storeEnabled.queryAssets(QuerySpec.max()).count();
        }
        var timesEnabled = measureQueryAssets(storeEnabled);

        System.out.printf("%-14s %-6d %-10s %-14d %-10d%n",
                "queryAssets", serviceCount, "disabled", toMicros(median(timesDisabled)), toMicros(p95(timesDisabled)));
        System.out.printf("%-14s %-6d %-10s %-14d %-10d%n",
                "queryAssets", serviceCount, "enabled", toMicros(median(timesEnabled)), toMicros(p95(timesEnabled)));
    }

    private void runFindByIdBenchmark(int serviceCount) {
        var fakeProvider = new FakeServerConfProvider(serviceCount);
        var firstId = fakeProvider.seededIds().get(0).asEncodedId();

        var storeDisabled = buildStore(fakeProvider, false);
        for (int i = 0; i < WARMUP; i++) {
            storeDisabled.findById(firstId);
        }
        var timesDisabled = measureFindById(storeDisabled, firstId);

        var storeEnabled = buildStore(fakeProvider, true);
        for (int i = 0; i < WARMUP; i++) {
            storeEnabled.findById(firstId);
        }
        var timesEnabled = measureFindById(storeEnabled, firstId);

        System.out.printf("%-14s %-6d %-10s %-14d %-10d%n",
                "findById", serviceCount, "disabled", toMicros(median(timesDisabled)), toMicros(p95(timesDisabled)));
        System.out.printf("%-14s %-6d %-10s %-14d %-10d%n",
                "findById", serviceCount, "enabled", toMicros(median(timesEnabled)), toMicros(p95(timesEnabled)));
    }

    private long[] measureQueryAssets(AssetIndexServerConfStore store) {
        var times = new long[K];
        for (int i = 0; i < K; i++) {
            long t = System.nanoTime();
            store.queryAssets(QuerySpec.max()).count();
            times[i] = System.nanoTime() - t;
        }
        return times;
    }

    private void runResolveForAssetBenchmark(int serviceCount) {
        var fakeProvider = new FakeServerConfProvider(serviceCount);
        var firstId = fakeProvider.seededIds().get(0).asEncodedId();

        var storeDisabled = buildStore(fakeProvider, false);
        for (int i = 0; i < WARMUP; i++) {
            storeDisabled.resolveForAsset(firstId);
        }
        var timesDisabled = measureResolveForAsset(storeDisabled, firstId);

        var storeEnabled = buildStore(fakeProvider, true);
        for (int i = 0; i < WARMUP; i++) {
            storeEnabled.resolveForAsset(firstId);
        }
        var timesEnabled = measureResolveForAsset(storeEnabled, firstId);

        System.out.printf("%-17s %-6d %-10s %-14d %-10d%n",
                "resolveForAsset", serviceCount, "disabled", toMicros(median(timesDisabled)), toMicros(p95(timesDisabled)));
        System.out.printf("%-17s %-6d %-10s %-14d %-10d%n",
                "resolveForAsset", serviceCount, "enabled", toMicros(median(timesEnabled)), toMicros(p95(timesEnabled)));
    }

    private long[] measureResolveForAsset(AssetIndexServerConfStore store, String id) {
        var times = new long[K];
        for (int i = 0; i < K; i++) {
            long t = System.nanoTime();
            store.resolveForAsset(id);
            times[i] = System.nanoTime() - t;
        }
        return times;
    }

    private long[] measureFindById(AssetIndexServerConfStore store, String id) {
        var times = new long[K];
        for (int i = 0; i < K; i++) {
            long t = System.nanoTime();
            store.findById(id);
            times[i] = System.nanoTime() - t;
        }
        return times;
    }

    private AssetIndexServerConfStore buildStore(ServerConfProvider provider, boolean cacheEnabled) {
        var cache = new StoreEnumerationCache<Asset>(cacheEnabled, 3600, 10000, "bench");
        return new AssetIndexServerConfStore(provider, globalConfProvider,
                "participant", "participant-mgmt",
                new BuiltinServiceCatalog(provider, false, false, false,
                        BuiltinServiceCatalog.DEFAULT_SERVER_PROXY_URL),
                cache);
    }

    static long median(long[] times) {
        var sorted = times.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }

    static long p95(long[] times) {
        var sorted = times.clone();
        Arrays.sort(sorted);
        return sorted[(int) (sorted.length * 0.95)];
    }

    static long toMicros(long nanos) {
        return nanos / 1000;
    }

    static class FakeServerConfProvider implements ServerConfProvider {

        private final List<ServiceId.Conf> services;
        private final ClientId.Conf member = ClientId.Conf.create("DEV", "GOV", "1111", "SubsystemA");

        FakeServerConfProvider(int serviceCount) {
            services = new ArrayList<>(serviceCount);
            for (int i = 0; i < serviceCount; i++) {
                services.add(ServiceId.Conf.create("DEV", "GOV", "1111", "SubsystemA", "svc" + i, "v1"));
            }
        }

        List<ServiceId.Conf> seededIds() {
            return services;
        }

        @Override
        public List<ClientId.Conf> getMembers() {
            return List.of(member);
        }

        @Override
        public List<ServiceId.Conf> getAllServices(ClientId serviceProviderId) {
            return services;
        }

        @Override
        public String getDisabledNotice(ServiceId serviceId) {
            return null;
        }

        @Override
        public List<AccessRight> getServiceAccessRights(ServiceId serviceId) {
            var ar = new AccessRight();
            ar.setSubjectId(ClientId.Conf.create("DEV", "GOV", "9999", "Consumer"));
            ar.setEndpoint(new org.niis.xroad.serverconf.model.Endpoint("svc", "GET", "/", false));
            ar.setRightsGiven(new Date());
            return List.of(ar);
        }

        @Override
        public boolean serviceExists(ServiceId serviceId) {
            return true;
        }

        @Override
        public SecurityServerId.Conf getIdentifier() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getServiceAddress(ServiceId serviceId) {
            return "https://example.com/r1/" + serviceId.asEncodedId();
        }

        @Override
        public int getServiceTimeout(ServiceId serviceId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RestServiceDetailsListType getRestServices(ClientId serviceProviderId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RestServiceDetailsListType getAllowedRestServices(ClientId serviceProviderId, ClientId clientId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ServiceId.Conf> getServicesByDescriptionType(ClientId serviceProviderId,
                                                                 DescriptionType descriptionType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ServiceId.Conf> getAllowedServices(ClientId serviceProviderId, ClientId clientId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ServiceId.Conf> getAllowedServicesByDescriptionType(ClientId serviceProviderId, ClientId clientId,
                                                                        DescriptionType descriptionType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public IsAuthentication getIsAuthentication(ClientId clientId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<java.security.cert.X509Certificate> getIsCerts(ClientId clientId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isSslAuthentication(ServiceId serviceId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getMemberStatus(ClientId memberId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isQueryAllowed(ClientId senderId, ServiceId serviceId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isQueryAllowed(ClientId senderId, ServiceId serviceId, String method, String path) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DescriptionType getDescriptionType(ServiceId serviceId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getServiceDescriptionURL(ServiceId serviceId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Endpoint> getServiceEndpoints(ServiceId serviceId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<String> getTspUrls() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<java.security.cert.X509Certificate> getAllIsCerts() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ee.ria.xroad.common.conf.InternalSSLKey getSSLKey() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<String> getOrderedTspUrls(ee.ria.xroad.common.ServicePrioritizationStrategy prioritizationStrategy) {
            throw new UnsupportedOperationException();
        }

        @Override
        public org.niis.xroad.common.CostType getTspCostType(String tspUrl) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MaintenanceMode getMaintenanceMode() {
            throw new UnsupportedOperationException();
        }
    }
}
