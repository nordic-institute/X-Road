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

import org.eclipse.edc.boot.system.injection.ObjectFactory;
import org.eclipse.edc.connector.core.profile.DataspaceProfileContextRegistryImpl;
import org.eclipse.edc.jsonld.spi.JsonLdNamespace;
import org.eclipse.edc.junit.extensions.DependencyInjectionExtension;
import org.eclipse.edc.protocol.spi.DataspaceProfileContext;
import org.eclipse.edc.protocol.spi.DataspaceProfileContextRegistry;
import org.eclipse.edc.protocol.spi.ProtocolVersion;
import org.eclipse.edc.protocol.spi.TrustedIssuer;
import org.eclipse.edc.spi.system.ExecutorInstrumentation;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.configuration.ConfigFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.niis.xroad.globalconf.GlobalConfProvider;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(DependencyInjectionExtension.class)
class XRoadIssuerTrustAnchorExtensionTest {

    private static final String INSTANCE_IDENTIFIER = "TEST";
    private static final String PROFILE_NAME = "test-profile";
    private static final String DID_1 = "did:web:cs1.example.test%3A6183:issuer";
    private static final String DID_2 = "did:web:cs2.example.test%3A6183:issuer";

    private final GlobalConfProvider globalConfProvider = mock();
    private final DataspaceProfileContextRegistry profileRegistry = new DataspaceProfileContextRegistryImpl();

    private XRoadIssuerTrustAnchorExtension extension;

    @BeforeEach
    void setUp(ServiceExtensionContext context) {
        context.registerService(GlobalConfProvider.class, globalConfProvider);
        context.registerService(DataspaceProfileContextRegistry.class, profileRegistry);
        context.registerService(ExecutorInstrumentation.class, ExecutorInstrumentation.noop());
        profileRegistry.registerDefault(new DataspaceProfileContext(PROFILE_NAME,
                new ProtocolVersion("2025-1", "/" + PROFILE_NAME, "HTTPS"),
                () -> "https://localhost/dsp/" + PROFILE_NAME,
                claimToken -> "participant",
                new JsonLdNamespace("https://w3id.org/dspace/2025/1/"),
                List.of(),
                List.of()));
    }

    @AfterEach
    void tearDown() {
        if (extension != null) {
            extension.shutdown();
        }
    }

    @Test
    void nameReturnsExpectedValue(ObjectFactory factory) {
        extension = factory.constructInstance(XRoadIssuerTrustAnchorExtension.class);

        assertThat(extension.name()).isEqualTo(XRoadIssuerTrustAnchorExtension.EXTENSION_NAME);
    }

    @Test
    void startTrustsEveryDistributedIssuerDid(ServiceExtensionContext context, ObjectFactory factory) {
        when(globalConfProvider.getInstanceIdentifier()).thenReturn(INSTANCE_IDENTIFIER);
        when(globalConfProvider.getIssuerDids(INSTANCE_IDENTIFIER)).thenReturn(List.of(DID_1, DID_2));

        extension = factory.constructInstance(XRoadIssuerTrustAnchorExtension.class);
        extension.initialize(context);
        extension.start();

        assertThat(trustedIssuerDids()).containsExactlyInAnyOrder(DID_1, DID_2);
        assertThat(profileRegistry.getProfile(PROFILE_NAME).trustedIssuers())
                .allSatisfy(issuer -> assertThat(issuer.getSupportedTypes()).containsExactly(TrustedIssuer.WILDCARD_TYPE));
    }

    @Test
    void startTrustsNothingWhenNotDataspaceEnabled(ServiceExtensionContext context, ObjectFactory factory) {
        when(globalConfProvider.getInstanceIdentifier()).thenReturn(INSTANCE_IDENTIFIER);
        when(globalConfProvider.getIssuerDids(INSTANCE_IDENTIFIER)).thenReturn(List.of());

        extension = factory.constructInstance(XRoadIssuerTrustAnchorExtension.class);
        extension.initialize(context);
        extension.start();

        assertThat(trustedIssuerDids()).isEmpty();
    }

    @Test
    void shutdownIsSafeAfterStart(ServiceExtensionContext context, ObjectFactory factory) {
        when(globalConfProvider.getInstanceIdentifier()).thenReturn(INSTANCE_IDENTIFIER);
        when(globalConfProvider.getIssuerDids(INSTANCE_IDENTIFIER)).thenReturn(List.of(DID_1));

        extension = factory.constructInstance(XRoadIssuerTrustAnchorExtension.class);
        extension.initialize(context);
        extension.start();

        assertThat(catchThrowable(extension::shutdown)).isNull();
    }

    @Test
    void refreshTickReReadsGlobalConfDroppingDidsMissingFromTheNewSnapshot(ServiceExtensionContext context, ObjectFactory factory) {
        when(context.getConfig()).thenReturn(ConfigFactory.fromMap(
                Map.of(XRoadIssuerTrustAnchorExtension.SETTING_REFRESH_INTERVAL_SECONDS, "1")));
        when(globalConfProvider.getInstanceIdentifier()).thenReturn(INSTANCE_IDENTIFIER);
        when(globalConfProvider.getIssuerDids(INSTANCE_IDENTIFIER))
                .thenReturn(List.of(DID_1))
                .thenReturn(List.of(DID_2));

        extension = factory.constructInstance(XRoadIssuerTrustAnchorExtension.class);
        extension.initialize(context);
        extension.start();

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(trustedIssuerDids()).containsExactly(DID_2));
    }

    private List<String> trustedIssuerDids() {
        return profileRegistry.getProfile(PROFILE_NAME).trustedIssuers().stream().map(TrustedIssuer::getId).toList();
    }
}
