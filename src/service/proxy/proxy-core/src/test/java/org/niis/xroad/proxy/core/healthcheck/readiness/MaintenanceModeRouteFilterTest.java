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
package org.niis.xroad.proxy.core.healthcheck.readiness;

import io.vertx.core.Future;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.proxy.core.healthcheck.MaintenanceModeState;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaintenanceModeRouteFilterTest {

    @Mock
    private RoutingContext rc;

    @Mock
    private HttpServerResponse response;

    @Mock
    private MaintenanceModeState maintenanceModeState;

    private MaintenanceModeRouteFilter filter;

    @BeforeEach
    void setUp() {
        filter = newFilter("/", "q", "health", "ready");
        lenient().when(rc.response()).thenReturn(response);
        lenient().when(response.setStatusCode(503)).thenReturn(response);
        lenient().when(response.putHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=UTF-8")).thenReturn(response);
        lenient().when(response.end(anyString())).thenReturn(Future.succeededFuture());
    }

    private MaintenanceModeRouteFilter newFilter(String httpRoot, String nonAppRoot, String healthRoot, String readiness) {
        return new MaintenanceModeRouteFilter(maintenanceModeState, httpRoot, nonAppRoot, healthRoot, readiness);
    }

    @Test
    void shortCircuitsReadinessPathWhenMaintenanceOn() {
        when(rc.normalizedPath()).thenReturn("/q/health/ready");
        when(maintenanceModeState.isMaintenanceMode()).thenReturn(true);

        filter.filter(rc);

        verify(response).setStatusCode(503);
        verify(response).putHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=UTF-8");
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).end(bodyCaptor.capture());
        verify(rc, never()).next();

        assertJsonIsMaintenanceDown(bodyCaptor.getValue());
    }

    @Test
    void shortCircuitsAggregateHealthPathWhenMaintenanceOn() {
        when(rc.normalizedPath()).thenReturn("/q/health");
        when(maintenanceModeState.isMaintenanceMode()).thenReturn(true);

        filter.filter(rc);

        verify(response).setStatusCode(503);
        verify(response).putHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=UTF-8");
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).end(bodyCaptor.capture());
        verify(rc, never()).next();

        assertJsonIsMaintenanceDown(bodyCaptor.getValue());
    }

    @Test
    void delegatesReadinessWhenMaintenanceOff() {
        when(rc.normalizedPath()).thenReturn("/q/health/ready");
        when(maintenanceModeState.isMaintenanceMode()).thenReturn(false);

        filter.filter(rc);

        verify(rc, times(1)).next();
        verify(response, never()).setStatusCode(503);
        verify(response, never()).end(anyString());
    }

    @Test
    void delegatesLivenessEvenWhenMaintenanceOn() {
        when(rc.normalizedPath()).thenReturn("/q/health/live");

        filter.filter(rc);

        verify(rc, times(1)).next();
        verify(response, never()).setStatusCode(503);
        verify(response, never()).end(anyString());
    }

    @Test
    void delegatesUnrelatedPathsEvenWhenMaintenanceOn() {
        when(rc.normalizedPath()).thenReturn("/foo/bar");

        filter.filter(rc);

        verify(rc, times(1)).next();
        verify(response, never()).setStatusCode(503);
        verify(response, never()).end(anyString());
    }

    @Test
    void delegatesWhenPathIsNull() {
        when(rc.normalizedPath()).thenReturn(null);

        filter.filter(rc);

        verify(rc, times(1)).next();
        verify(response, never()).setStatusCode(503);
        verify(response, never()).end(anyString());
    }

    @Test
    void honoursConfigOverrideForReadinessPath() {
        // quarkus.smallrye-health.readiness-path=readyz => /q/health/readyz
        filter = newFilter("/", "q", "health", "readyz");
        when(rc.normalizedPath()).thenReturn("/q/health/readyz");
        when(maintenanceModeState.isMaintenanceMode()).thenReturn(true);

        filter.filter(rc);

        verify(response).setStatusCode(503);
        verify(rc, never()).next();
    }

    @Test
    void honoursConfigOverrideForHealthRootAndHttpRoot() {
        // quarkus.http.root-path=/app, non-application-root-path=ops,
        //   smallrye-health.root-path=h, readiness-path=r => /app/ops/h/r
        filter = newFilter("/app", "ops", "h", "r");
        when(rc.normalizedPath()).thenReturn("/app/ops/h/r");
        when(maintenanceModeState.isMaintenanceMode()).thenReturn(true);

        filter.filter(rc);

        verify(response).setStatusCode(503);
        verify(rc, never()).next();
    }

    @Test
    void absoluteReadinessSegmentResetsAccumulatedPrefix() {
        // Absolute readiness path (leading slash) overrides the /q/health prefix entirely.
        filter = newFilter("/", "q", "health", "/custom/ready");
        when(rc.normalizedPath()).thenReturn("/custom/ready");
        when(maintenanceModeState.isMaintenanceMode()).thenReturn(true);

        filter.filter(rc);

        verify(response).setStatusCode(503);
        verify(rc, never()).next();
    }

    @Test
    void defaultPathsNoLongerMatchAfterReadinessOverride() {
        // When readiness is overridden, the old /q/health/ready should pass through —
        // the path check returns false before maintenance state is even consulted.
        filter = newFilter("/", "q", "health", "readyz");
        when(rc.normalizedPath()).thenReturn("/q/health/ready");

        filter.filter(rc);

        verify(rc, times(1)).next();
        verify(response, never()).setStatusCode(503);
    }

    @Test
    void buildPathHandlesNestedRelativeSegments() {
        assertThat(MaintenanceModeRouteFilter.buildPath("/", "q", "health")).isEqualTo("/q/health");
        assertThat(MaintenanceModeRouteFilter.buildPath("/q/health", "ready")).isEqualTo("/q/health/ready");
    }

    @Test
    void buildPathHandlesAbsoluteSegmentReset() {
        assertThat(MaintenanceModeRouteFilter.buildPath("/q/health", "/absolute")).isEqualTo("/absolute");
        assertThat(MaintenanceModeRouteFilter.buildPath("/app", "/q", "health")).isEqualTo("/q/health");
    }

    @Test
    void buildPathSkipsEmptyAndNullSegments() {
        assertThat(MaintenanceModeRouteFilter.buildPath(null, "", "/q", "", "health")).isEqualTo("/q/health");
    }

    private static void assertJsonIsMaintenanceDown(String body) {
        JsonObject parsed = new JsonObject(body);
        assertThat(parsed.getString("status")).isEqualTo("DOWN");
        assertThat(parsed.getJsonArray("checks")).hasSize(1);
        JsonObject check = parsed.getJsonArray("checks").getJsonObject(0);
        assertThat(check.getString("name")).isEqualTo(MaintenanceModeRouteFilter.MAINTENANCE_CHECK_NAME);
        assertThat(check.getString("status")).isEqualTo("DOWN");
        assertThat(check.getJsonObject("data").getString("status")).isEqualTo(MaintenanceModeRouteFilter.MAINTENANCE_STATUS_VALUE);
    }
}
