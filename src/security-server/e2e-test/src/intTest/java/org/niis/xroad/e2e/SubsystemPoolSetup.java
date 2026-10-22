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
package org.niis.xroad.e2e;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.e2e.AdminApi.AdminSession;
import org.niis.xroad.e2e.parity.acl.PoolVisibility;
import org.niis.xroad.e2e.parity.acl.SubsystemPool;
import org.niis.xroad.e2e.parity.acl.SubsystemPool.Entry;
import org.niis.xroad.e2e.parity.acl.VerdictPoller;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;

import static org.niis.xroad.e2e.AdminApi.addLocalClient;
import static org.niis.xroad.e2e.AdminApi.adminBaseUrl;
import static org.niis.xroad.e2e.AdminApi.awaitClientsRegistered;
import static org.niis.xroad.e2e.AdminApi.globalClientKnown;
import static org.niis.xroad.e2e.AdminApi.login;
import static org.niis.xroad.e2e.AdminApi.registerClient;

/**
 * Adds and registers the {@link SubsystemPool} on the Security Servers that host it. Every subsystem is
 * added and every registration submitted first, then the registrations are awaited, so registration
 * latency is paid once for the batch. A subsystem left by an earlier run is reused: the add and register
 * calls tolerate it, and a subsystem that is already REGISTERED passes the wait at once. Last, the other
 * Security Servers are awaited to know every registered subsystem from global configuration. Nothing is
 * torn down afterwards.
 */
@Slf4j
@UtilityClass
class SubsystemPoolSetup {

    private static final Duration REGISTRATION_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration VISIBILITY_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration VISIBILITY_INTERVAL = Duration.ofSeconds(2);

    static void ensure(E2eEnvironment env) {
        var start = Instant.now();
        var entriesByHost = SubsystemPool.byHost(SubsystemPool.entries());
        var csBaseUrl = CentralServerApi.baseUrl(env);
        var cs = login(csBaseUrl);

        entriesByHost.forEach((host, entries) -> addAndSubmit(env, host, entries));
        entriesByHost.forEach((host, entries) -> awaitRegistered(env, host, entries, csBaseUrl, cs));
        awaitKnownToOtherServers(env);

        log.info("Subsystem pool ready in {} s", Duration.between(start, Instant.now()).toSeconds());
    }

    private static void addAndSubmit(E2eEnvironment env, String host, List<Entry> entries) {
        var baseUrl = adminBaseUrl(env, host);
        var session = login(baseUrl);
        entries.forEach(entry -> addLocalClient(baseUrl, session, entry.clientId()));
        SubsystemPool.clientIdsToRegister(entries).forEach(clientId -> registerClient(baseUrl, session, clientId));
    }

    private static void awaitRegistered(E2eEnvironment env, String host, List<Entry> entries, String csBaseUrl,
                                        AdminSession cs) {
        var clientIds = SubsystemPool.clientIdsToRegister(entries);
        if (clientIds.isEmpty()) {
            return;
        }
        var baseUrl = adminBaseUrl(env, host);
        log.info("Waiting for {} pool subsystem registrations on {}", clientIds.size(), host);
        awaitClientsRegistered(baseUrl, login(baseUrl), csBaseUrl, cs, clientIds, REGISTRATION_TIMEOUT);
    }

    private static void awaitKnownToOtherServers(E2eEnvironment env) {
        var targets = PoolVisibility.targets(SubsystemPool.entries(), SubsystemPool.SERVERS);
        var sessions = new HashMap<String, AdminSession>();
        SubsystemPool.SERVERS.forEach(server -> sessions.put(server, login(adminBaseUrl(env, server))));
        log.info("Waiting for {} pool subsystem lookups on the other servers", targets.size());
        PoolVisibility.await(targets, target -> globalClientKnown(adminBaseUrl(env, target.server()),
                sessions.get(target.server()), target.clientId()), VISIBILITY_TIMEOUT, VISIBILITY_INTERVAL,
                VerdictPoller.systemClock());
    }
}
