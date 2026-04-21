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

import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.niis.xroad.proxy.core.healthcheck.MaintenanceModeState;

/**
 * Quarkus Vert.x route filter that short-circuits SmallRye readiness probes when maintenance
 * mode is active. Matches the SmallRye Health readiness path (default {@code /q/health/ready})
 * and the aggregate health path (default {@code /q/health}) but intentionally NOT the liveness
 * path — liveness must keep returning UP so Kubernetes does not kill the pod mid-drain. When
 * short-circuiting, returns HTTP 503 with a SmallRye-shaped payload naming a single
 * {@code MAINTENANCE_MODE} DOWN check; no {@code @Readiness} bean's {@code call()} is invoked.
 * <p>
 * Paths are resolved from the canonical Quarkus / SmallRye Health configuration properties,
 * so operator overrides of {@code quarkus.http.root-path},
 * {@code quarkus.http.non-application-root-path}, {@code quarkus.smallrye-health.root-path}, or
 * {@code quarkus.smallrye-health.readiness-path} are honoured automatically. Segment joining
 * follows Quarkus's rule: a segment starting with {@code "/"} is absolute and resets the
 * accumulated prefix; otherwise the segment is nested under the prefix.
 * <p>
 * Registered onto the Quarkus Vert.x {@link Router} at startup via an {@code @Observes} event
 * observer. A negative route order ensures the handler runs before SmallRye Health's default
 * aggregation handler (which sits at order 0).
 */
@Slf4j
@ApplicationScoped
public class MaintenanceModeRouteFilter {

    static final String MAINTENANCE_CHECK_NAME = "MAINTENANCE_MODE";
    static final String MAINTENANCE_MESSAGE = "Health check interface is in maintenance mode.";

    private static final int HTTP_SERVICE_UNAVAILABLE = 503;
    /** Negative order so this handler runs before SmallRye Health's default (order 0). */
    private static final int ROUTE_ORDER = -100;
    /** CDI observer priority — higher than default so we register early during Router setup. */
    private static final int OBSERVER_PRIORITY = 100;

    private final MaintenanceModeState maintenanceModeState;
    private final String aggregatePath;
    private final String readinessPath;

    @Inject
    public MaintenanceModeRouteFilter(
            MaintenanceModeState maintenanceModeState,
            @ConfigProperty(name = "quarkus.http.root-path", defaultValue = "/") String httpRootPath,
            @ConfigProperty(name = "quarkus.http.non-application-root-path", defaultValue = "q") String nonAppRootPath,
            @ConfigProperty(name = "quarkus.smallrye-health.root-path", defaultValue = "health") String healthRootPath,
            @ConfigProperty(name = "quarkus.smallrye-health.readiness-path", defaultValue = "ready") String readinessSegment) {
        this.maintenanceModeState = maintenanceModeState;
        this.aggregatePath = buildPath(httpRootPath, nonAppRootPath, healthRootPath);
        this.readinessPath = buildPath(this.aggregatePath, readinessSegment);
    }

    /**
     * Registers {@link #filter(RoutingContext)} as a catch-all route on the Quarkus Vert.x
     * {@link Router} at a negative order so it runs before SmallRye Health's handler.
     *
     * @param router Vert.x {@code Router} exposed by Quarkus at application startup
     */
    void registerOn(@Observes @Priority(OBSERVER_PRIORITY) Router router) {
        router.route().order(ROUTE_ORDER).handler(this::filter);
        log.debug("Registered MaintenanceModeRouteFilter (aggregate={}, readiness={}, order={})",
                aggregatePath, readinessPath, ROUTE_ORDER);
    }

    /**
     * Intercepts Quarkus Vert.x HTTP routes before SmallRye Health's handler. If maintenance
     * mode is on and the path targets readiness or the aggregate health endpoint, responds with
     * HTTP 503 + a {@code MAINTENANCE_MODE} DOWN payload and stops the chain. Otherwise
     * delegates to the next handler via {@link RoutingContext#next()}.
     *
     * @param rc Vert.x routing context for the current request
     */
    public void filter(RoutingContext rc) {
        String path = rc.normalizedPath();
        if (shouldShortCircuit(path) && maintenanceModeState.isMaintenanceMode()) {
            log.debug("Maintenance mode active - short-circuiting {} with 503", path);
            JsonObject body = new JsonObject()
                    .put("status", "DOWN")
                    .put("checks", JsonArray.of(
                            new JsonObject()
                                    .put("name", MAINTENANCE_CHECK_NAME)
                                    .put("status", "DOWN")
                                    .put("data", MAINTENANCE_MESSAGE)));
            rc.response()
                    .setStatusCode(HTTP_SERVICE_UNAVAILABLE)
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=UTF-8")
                    .end(body.encode());
            return;
        }
        rc.next();
    }

    private boolean shouldShortCircuit(String path) {
        if (path == null) {
            return false;
        }
        // Treat /q/health and /q/health/ (plus readiness variants) as the same endpoint.
        String normalized = (path.length() > 1 && path.endsWith("/"))
                ? path.substring(0, path.length() - 1)
                : path;
        if (normalized.equals(aggregatePath) || normalized.equals(readinessPath)) {
            return true;
        }
        // Match subpaths under the readiness endpoint (e.g. if SmallRye ever exposes ready/<group>).
        // The "/" suffix prevents false positives like /q/health/readyx accidentally matching.
        return normalized.startsWith(readinessPath + "/");
    }

    /**
     * Joins path segments per Quarkus's path-resolution rules: a segment starting with
     * {@code "/"} is absolute and resets the accumulated prefix; any other segment is appended
     * with a single slash separator. Empty or {@code null} segments are skipped. Trailing
     * slashes are normalised away (except when the result is the root {@code "/"}).
     */
    static String buildPath(String... segments) {
        StringBuilder sb = new StringBuilder();
        for (String raw : segments) {
            if (raw == null || raw.isEmpty()) {
                continue;
            }
            if (raw.startsWith("/")) {
                sb.setLength(0);
                sb.append(raw);
            } else {
                if (sb.isEmpty() || sb.charAt(sb.length() - 1) != '/') {
                    sb.append('/');
                }
                sb.append(raw);
            }
        }
        while (sb.length() > 1 && sb.charAt(sb.length() - 1) == '/') {
            sb.setLength(sb.length() - 1);
        }
        if (sb.isEmpty()) {
            sb.append('/');
        }
        return sb.toString();
    }
}
