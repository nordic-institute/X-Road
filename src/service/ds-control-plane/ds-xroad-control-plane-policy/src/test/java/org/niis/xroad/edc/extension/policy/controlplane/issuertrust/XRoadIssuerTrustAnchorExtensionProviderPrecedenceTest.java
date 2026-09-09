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

import org.eclipse.edc.boot.system.DependencyGraph;
import org.eclipse.edc.boot.system.injection.lifecycle.ExtensionLifecycleManager;
import org.eclipse.edc.iam.decentralizedclaims.core.DcpDefaultServicesExtension;
import org.eclipse.edc.iam.decentralizedclaims.core.defaults.DefaultTrustedIssuerRegistry;
import org.eclipse.edc.iam.verifiablecredentials.spi.validation.TrustedIssuerRegistry;
import org.eclipse.edc.junit.extensions.TestServiceExtensionContext;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.query.CriterionOperatorRegistry;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.junit.jupiter.api.Test;
import org.niis.xroad.globalconf.GlobalConfProvider;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Boots the real EDC dependency-injection machinery ({@link DependencyGraph}, {@link ExtensionLifecycleManager})
 * with both {@link XRoadIssuerTrustAnchorExtension} and EDC's own {@link DcpDefaultServicesExtension} — the
 * extension that provides the built-in, additive-only {@link DefaultTrustedIssuerRegistry} as an
 * {@code isDefault} provider — present at once. A test consumer's {@code @Inject TrustedIssuerRegistry} must
 * resolve to this extension's registry regardless of which of the two extensions is discovered first, proving
 * the explicit, non-default {@code @Provider} takes precedence over EDC's default in this EDC version.
 */
class XRoadIssuerTrustAnchorExtensionProviderPrecedenceTest {

    @Test
    void xroadRegistryWinsWhenBootedBeforeTheEdcDefault() {
        assertXroadRegistryWins(new XRoadIssuerTrustAnchorExtension(), new DcpDefaultServicesExtension());
    }

    @Test
    void xroadRegistryWinsWhenBootedAfterTheEdcDefault() {
        assertXroadRegistryWins(new DcpDefaultServicesExtension(), new XRoadIssuerTrustAnchorExtension());
    }

    private void assertXroadRegistryWins(ServiceExtension extensionA, ServiceExtension extensionB) {
        var context = TestServiceExtensionContext.testServiceExtensionContext();

        var globalConfProvider = mock(GlobalConfProvider.class);
        when(globalConfProvider.getInstanceIdentifier()).thenReturn("TEST");
        when(globalConfProvider.getIssuerDids("TEST")).thenReturn(List.of());
        context.registerService(GlobalConfProvider.class, globalConfProvider);
        context.registerService(CriterionOperatorRegistry.class, mock(CriterionOperatorRegistry.class));

        var consumer = new TrustedIssuerRegistryConsumer();
        var extensions = List.of(extensionA, extensionB, consumer);

        var graph = DependencyGraph.of(context, extensions);
        assertThat(graph.isValid())
                .withFailMessage(() -> graph.getInjectionFailures().map(Object::toString).collect(Collectors.joining(", ")))
                .isTrue();

        var xroadExtension = (XRoadIssuerTrustAnchorExtension) extensions.stream()
                .filter(XRoadIssuerTrustAnchorExtension.class::isInstance)
                .findFirst()
                .orElseThrow();
        try {
            ExtensionLifecycleManager.bootServiceExtensions(graph.getInjectionContainers(), context);

            assertThat(consumer.registry)
                    .isInstanceOf(XRoadTrustedIssuerRegistry.class)
                    .isNotInstanceOf(DefaultTrustedIssuerRegistry.class);
        } finally {
            xroadExtension.shutdown();
        }
    }

    private static final class TrustedIssuerRegistryConsumer implements ServiceExtension {

        @Inject
        private TrustedIssuerRegistry registry;

        @Override
        public String name() {
            return "Trusted Issuer Registry Consumer (test)";
        }
    }
}
