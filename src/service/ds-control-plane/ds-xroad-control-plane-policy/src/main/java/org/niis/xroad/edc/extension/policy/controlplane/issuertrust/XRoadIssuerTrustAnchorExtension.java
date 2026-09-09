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
package org.niis.xroad.edc.extension.policy.controlplane.issuertrust;

import org.eclipse.edc.iam.verifiablecredentials.spi.validation.TrustedIssuerRegistry;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.niis.xroad.edc.reload.PeriodicMaterialReloader;
import org.niis.xroad.globalconf.GlobalConfProvider;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.niis.xroad.edc.extension.policy.controlplane.issuertrust.XRoadIssuerTrustAnchorExtension.EXTENSION_NAME;

/**
 * Populates the DCP {@link TrustedIssuerRegistry} from the dataspace issuer trust anchor distributed in
 * globalconf. Every distributed Issuer DID becomes a trusted issuer for every credential type, so a
 * credential issued by any Central Server node verifies.
 *
 * <p>{@link GlobalConfProvider#getIssuerDids(String)} is an in-memory read over parsed globalconf, and
 * globalconf refreshes itself on the same basis the proxy relies on for every message — no TTL caching is
 * needed here. This extension exposes its own {@link XRoadTrustedIssuerRegistry} via an explicit
 * {@link Provider} method rather than relying on EDC's built-in additive-only registry, so a DID dropped
 * from globalconf (a compromised issuer removed by the Central Server operator) loses trust within one
 * refresh cycle instead of surviving until process restart. An explicit, non-default {@code @Provider} is
 * resolved by EDC ahead of any {@code isDefault} provider for the same type (the default in-memory registry
 * is registered lazily, only as a last-resort fallback for an injection point still unsatisfied at resolve
 * time), so every consumer that injects {@link TrustedIssuerRegistry} sees this implementation.
 */
@Extension(value = EXTENSION_NAME)
public class XRoadIssuerTrustAnchorExtension implements ServiceExtension {

    public static final String EXTENSION_NAME = "X-Road Issuer Trust Anchor";

    static final String SETTING_REFRESH_INTERVAL_SECONDS = "xroad.dsp.issuer-trust.refresh-interval-seconds";

    private static final long DEFAULT_REFRESH_INTERVAL_SECONDS = 60L;
    private static final int MAX_RELOAD_ATTEMPTS_PER_CYCLE = 3;
    private static final Duration RELOAD_RETRY_DELAY = Duration.ofSeconds(5);

    @Inject
    private GlobalConfProvider globalConfProvider;

    private final XRoadTrustedIssuerRegistry issuerRegistry = new XRoadTrustedIssuerRegistry();

    private final AtomicBoolean notEnabledLogged = new AtomicBoolean(false);

    private PeriodicMaterialReloader<Set<String>> reloader;

    @Override
    public String name() {
        return EXTENSION_NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var monitor = context.getMonitor();
        var refreshIntervalSeconds = context.getSetting(SETTING_REFRESH_INTERVAL_SECONDS, DEFAULT_REFRESH_INTERVAL_SECONDS);

        var initialDids = loadTrustedIssuerDids(monitor);
        replaceTrustedIssuers(initialDids);
        monitor.info("%s: trusting %d issuer DID(s) from globalconf".formatted(EXTENSION_NAME, initialDids.size()));

        var initial = new PeriodicMaterialReloader.Loaded<>(initialDids, fingerprint(initialDids));
        reloader = PeriodicMaterialReloader.schedule(EXTENSION_NAME, initial, Duration.ofSeconds(refreshIntervalSeconds),
                MAX_RELOAD_ATTEMPTS_PER_CYCLE, RELOAD_RETRY_DELAY, () -> {
                    var dids = loadTrustedIssuerDids(monitor);
                    return new PeriodicMaterialReloader.Loaded<>(dids, fingerprint(dids));
                }, this::replaceTrustedIssuers, monitor);
    }

    /**
     * Exposes this extension's {@link XRoadTrustedIssuerRegistry} as the {@link TrustedIssuerRegistry} EDC
     * resolves, ahead of its built-in {@code isDefault} in-memory registry.
     */
    @Provider
    public TrustedIssuerRegistry trustedIssuerRegistry() {
        return issuerRegistry;
    }

    @Override
    public void shutdown() {
        if (reloader != null) {
            reloader.close();
        }
    }

    private Set<String> loadTrustedIssuerDids(Monitor monitor) {
        var instanceIdentifier = globalConfProvider.getInstanceIdentifier();
        var dids = Set.copyOf(globalConfProvider.getIssuerDids(instanceIdentifier));
        if (dids.isEmpty()) {
            if (notEnabledLogged.compareAndSet(false, true)) {
                monitor.info(("%s: instance '%s' has no distributed issuer DIDs (no dataspaceParameters in globalconf); "
                        + "dataspace issuance and trust are not enabled").formatted(EXTENSION_NAME, instanceIdentifier));
            }
        } else {
            notEnabledLogged.set(false);
        }
        return dids;
    }

    private void replaceTrustedIssuers(Set<String> dids) {
        issuerRegistry.replaceAll(dids, TrustedIssuerRegistry.WILDCARD);
    }

    private static String fingerprint(Set<String> dids) {
        return dids.stream().sorted().collect(Collectors.joining(","));
    }
}
