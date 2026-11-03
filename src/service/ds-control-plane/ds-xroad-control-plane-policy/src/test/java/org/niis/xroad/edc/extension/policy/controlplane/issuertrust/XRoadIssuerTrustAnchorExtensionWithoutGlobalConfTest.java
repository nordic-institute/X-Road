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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.model.GlobalConfInitException;
import org.niis.xroad.globalconf.model.GlobalConfInitState;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(DependencyInjectionExtension.class)
class XRoadIssuerTrustAnchorExtensionWithoutGlobalConfTest {

    private static final String INSTANCE_IDENTIFIER = "TEST";
    private static final String PROFILE_NAME = "test-profile";
    private static final String REGISTERED_DID = "did:web:registered.example.test%3A6183:issuer";
    private static final String DISTRIBUTED_DID = "did:web:cs1.example.test%3A6183:issuer";

    private final GlobalConfProvider globalConfProvider = mock();
    private final DataspaceProfileContextRegistry profileRegistry = new DataspaceProfileContextRegistryImpl();
    private final ScheduledExecutorService scheduler = mock();

    private XRoadIssuerTrustAnchorExtension extension;

    @BeforeEach
    void setUp(ServiceExtensionContext context) {
        var instrumentation = new ExecutorInstrumentation() {

            @Override
            public ScheduledExecutorService instrument(ScheduledExecutorService executor, String name) {
                executor.shutdownNow();
                return scheduler;
            }
        };
        context.registerService(GlobalConfProvider.class, globalConfProvider);
        context.registerService(DataspaceProfileContextRegistry.class, profileRegistry);
        context.registerService(ExecutorInstrumentation.class, instrumentation);
        profileRegistry.registerDefault(new DataspaceProfileContext(PROFILE_NAME,
                new ProtocolVersion("2025-1", "/" + PROFILE_NAME, "HTTPS"),
                () -> "https://localhost/dsp/" + PROFILE_NAME,
                claimToken -> "participant",
                new JsonLdNamespace("https://w3id.org/dspace/2025/1/"),
                List.of(),
                List.of(TrustedIssuer.Builder.newInstance().id(REGISTERED_DID)
                        .supportedTypes(List.of(TrustedIssuer.WILDCARD_TYPE)).build())));
    }

    @AfterEach
    void tearDown() {
        if (extension != null) {
            extension.shutdown();
        }
    }

    @Test
    void startCompletesWhileGlobalConfIsUninitialized(ServiceExtensionContext context, ObjectFactory factory) {
        when(globalConfProvider.getInstanceIdentifier()).thenThrow(new GlobalConfInitException(GlobalConfInitState.UNINITIALIZED));
        extension = factory.constructInstance(XRoadIssuerTrustAnchorExtension.class);
        extension.initialize(context);
        assertThatCode(extension::start).doesNotThrowAnyException();
    }

    @Test
    void startCompletesWhileGlobalConfIsUnreachable(ServiceExtensionContext context, ObjectFactory factory) {
        when(globalConfProvider.getInstanceIdentifier()).thenThrow(new GlobalConfInitException(GlobalConfInitState.FAILURE_MALFORMED));
        extension = factory.constructInstance(XRoadIssuerTrustAnchorExtension.class);
        extension.initialize(context);
        assertThatCode(extension::start).doesNotThrowAnyException();
    }

    @Test
    void theProfileKeepsItsRegisteredIssuersAndNothingIsRefreshedUntilTheFirstLoad(ServiceExtensionContext context,
                                                                                    ObjectFactory factory) {
        when(globalConfProvider.getInstanceIdentifier()).thenThrow(new GlobalConfInitException(GlobalConfInitState.UNINITIALIZED));
        extension = factory.constructInstance(XRoadIssuerTrustAnchorExtension.class);
        extension.initialize(context);
        extension.start();
        assertThat(trustedIssuerDids()).containsExactly(REGISTERED_DID);
        verify(scheduler).schedule(any(Runnable.class), eq(5L), eq(TimeUnit.SECONDS));
        verify(scheduler, never()).scheduleAtFixedRate(any(), anyLong(), anyLong(), any());
    }

    @Test
    void theFirstPollAfterGlobalConfArrivesTrustsTheDistributedIssuersAndSwitchesToTheNormalRefresh(
            ServiceExtensionContext context, ObjectFactory factory) {
        when(globalConfProvider.getInstanceIdentifier()).thenThrow(new GlobalConfInitException(GlobalConfInitState.UNINITIALIZED));
        extension = factory.constructInstance(XRoadIssuerTrustAnchorExtension.class);
        extension.initialize(context);
        extension.start();
        var poll = capturePendingPoll(1);
        doReturn(INSTANCE_IDENTIFIER).when(globalConfProvider).getInstanceIdentifier();
        when(globalConfProvider.getCredentialIssuerDids(INSTANCE_IDENTIFIER)).thenReturn(List.of(DISTRIBUTED_DID));
        poll.run();
        assertThat(trustedIssuerDids()).containsExactly(DISTRIBUTED_DID);
        verify(scheduler).scheduleAtFixedRate(any(), eq(60L), eq(60L), eq(TimeUnit.SECONDS));
        verify(scheduler, times(1)).schedule(any(Runnable.class), anyLong(), any());
    }

    @Test
    void aPollThatStillFindsNoGlobalConfSchedulesAnother5SecondPoll(ServiceExtensionContext context, ObjectFactory factory) {
        when(globalConfProvider.getInstanceIdentifier()).thenThrow(new GlobalConfInitException(GlobalConfInitState.UNINITIALIZED));
        extension = factory.constructInstance(XRoadIssuerTrustAnchorExtension.class);
        extension.initialize(context);
        extension.start();
        var poll = capturePendingPoll(1);
        poll.run();
        verify(scheduler, times(2)).schedule(any(Runnable.class), eq(5L), eq(TimeUnit.SECONDS));
        verify(scheduler, never()).scheduleAtFixedRate(any(), anyLong(), anyLong(), any());
        assertThat(trustedIssuerDids()).containsExactly(REGISTERED_DID);
    }

    private Runnable capturePendingPoll(int expectedSchedules) {
        var captor = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler, times(expectedSchedules)).schedule(captor.capture(), eq(5L), eq(TimeUnit.SECONDS));
        return captor.getValue();
    }

    private List<String> trustedIssuerDids() {
        return profileRegistry.getProfile(PROFILE_NAME).trustedIssuers().stream().map(TrustedIssuer::getId).toList();
    }
}
