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
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ExecutorInstrumentation;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.configuration.ConfigFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.model.GlobalConfInitException;
import org.niis.xroad.globalconf.model.GlobalConfInitState;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(DependencyInjectionExtension.class)
class XRoadIssuerTrustAnchorExtensionTest {

    private static final String INSTANCE_IDENTIFIER = "TEST";
    private static final String EXTENSION_PREFIX = XRoadIssuerTrustAnchorExtension.EXTENSION_NAME + ": ";
    private static final String PROFILE_NAME = "test-profile";
    private static final String REGISTERED_DID = "did:web:registered.example.test%3A6183:issuer";
    private static final String DID_1 = "did:web:cs1.example.test%3A6183:issuer";
    private static final String DID_2 = "did:web:cs2.example.test%3A6183:issuer";

    private final GlobalConfProvider globalConfProvider = mock();
    private final Monitor monitor = mock();
    private final DataspaceProfileContextRegistry profileRegistry = new DataspaceProfileContextRegistryImpl();

    private ScheduledExecutorService scheduler;
    private XRoadIssuerTrustAnchorExtension extension;

    @BeforeEach
    void setUp(ServiceExtensionContext context) {
        var instrumentation = new ExecutorInstrumentation() {

            @Override
            public ScheduledExecutorService instrument(ScheduledExecutorService executor, String name) {
                if (scheduler == null) {
                    return executor;
                }
                executor.shutdownNow();
                return scheduler;
            }
        };
        context.registerService(GlobalConfProvider.class, globalConfProvider);
        context.registerService(Monitor.class, monitor);
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
    void nameReturnsExpectedValue(ObjectFactory factory) {
        extension = factory.constructInstance(XRoadIssuerTrustAnchorExtension.class);

        assertThat(extension.name()).isEqualTo(XRoadIssuerTrustAnchorExtension.EXTENSION_NAME);
    }

    @Test
    void startTrustsEveryDistributedIssuerDid(ServiceExtensionContext context, ObjectFactory factory) {
        when(globalConfProvider.getInstanceIdentifier()).thenReturn(INSTANCE_IDENTIFIER);
        when(globalConfProvider.getCredentialIssuerDids(INSTANCE_IDENTIFIER)).thenReturn(List.of(DID_1, DID_2));

        extension = factory.constructInstance(XRoadIssuerTrustAnchorExtension.class);
        extension.initialize(context);
        extension.start();

        assertThat(trustedIssuerDids()).containsExactlyInAnyOrder(DID_1, DID_2);
        assertThat(profileRegistry.getProfile(PROFILE_NAME).trustedIssuers())
                .allSatisfy(issuer -> assertThat(issuer.getSupportedTypes()).containsExactly(TrustedIssuer.WILDCARD_TYPE));
    }

    @Test
    void startTrustsNothingWhenNoIssuerDidsAreDistributed(ServiceExtensionContext context, ObjectFactory factory) {
        when(globalConfProvider.getInstanceIdentifier()).thenReturn(INSTANCE_IDENTIFIER);
        when(globalConfProvider.getCredentialIssuerDids(INSTANCE_IDENTIFIER)).thenReturn(List.of());

        extension = factory.constructInstance(XRoadIssuerTrustAnchorExtension.class);
        extension.initialize(context);
        extension.start();

        assertThat(trustedIssuerDids()).isEmpty();
    }

    @Test
    void shutdownIsSafeAfterStart(ServiceExtensionContext context, ObjectFactory factory) {
        when(globalConfProvider.getInstanceIdentifier()).thenReturn(INSTANCE_IDENTIFIER);
        when(globalConfProvider.getCredentialIssuerDids(INSTANCE_IDENTIFIER)).thenReturn(List.of(DID_1));

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
        var refreshThread = new AtomicReference<Thread>();
        when(globalConfProvider.getCredentialIssuerDids(INSTANCE_IDENTIFIER))
                .thenReturn(List.of(DID_1))
                .thenAnswer(invocation -> {
                    refreshThread.set(Thread.currentThread());
                    return List.of(DID_2);
                });

        extension = factory.constructInstance(XRoadIssuerTrustAnchorExtension.class);
        extension.initialize(context);
        extension.start();

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(trustedIssuerDids()).containsExactly(DID_2));
        assertThat(refreshThread.get().getName()).isEqualTo(XRoadIssuerTrustAnchorExtension.THREAD_NAME);
        assertThat(refreshThread.get().isDaemon()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = GlobalConfInitState.class, names = {"UNINITIALIZED", "FAILURE_MALFORMED"})
    void startCompletesWhileGlobalConfIsNotAvailable(GlobalConfInitState state, ServiceExtensionContext context,
                                                     ObjectFactory factory) {
        when(globalConfProvider.getInstanceIdentifier()).thenThrow(new GlobalConfInitException(state));
        extension = factory.constructInstance(XRoadIssuerTrustAnchorExtension.class);
        extension.initialize(context);

        assertThatCode(extension::start).doesNotThrowAnyException();
    }

    @Test
    void theProfileKeepsItsRegisteredIssuersAndTheFirstPollIsAt5Seconds(ServiceExtensionContext context, ObjectFactory factory) {
        startWithGlobalConfUnavailable(context, factory);

        assertThat(trustedIssuerDids()).containsExactly(REGISTERED_DID);
        verify(scheduler).schedule(any(Runnable.class), eq(5L), eq(TimeUnit.SECONDS));
        verify(scheduler, times(1)).schedule(any(Runnable.class), anyLong(), any());
        verify(scheduler, never()).scheduleAtFixedRate(any(), anyLong(), anyLong(), any());
    }

    @Test
    void theFirstPollAfterGlobalConfArrivesTrustsTheDistributedIssuersAndSwitchesToTheRefreshInterval(
            ServiceExtensionContext context, ObjectFactory factory) {
        startWithGlobalConfUnavailable(context, factory);
        var poll = captureScheduled(5L, 1);
        globalConfReturns(DID_1);

        poll.run();

        assertThat(trustedIssuerDids()).containsExactly(DID_1);
        verify(scheduler, times(1)).schedule(any(Runnable.class), eq(60L), eq(TimeUnit.SECONDS));
        verify(scheduler, times(1)).schedule(any(Runnable.class), eq(5L), eq(TimeUnit.SECONDS));
        verify(monitor).info("%strusting 1 issuer DID(s) from globalconf".formatted(EXTENSION_PREFIX));
    }

    @Test
    void aPollThatStillFindsNoGlobalConfSchedulesAnother5SecondPoll(ServiceExtensionContext context, ObjectFactory factory) {
        startWithGlobalConfUnavailable(context, factory);
        var poll = captureScheduled(5L, 1);

        poll.run();

        verify(scheduler, times(2)).schedule(any(Runnable.class), eq(5L), eq(TimeUnit.SECONDS));
        verify(scheduler, never()).schedule(any(Runnable.class), eq(60L), any());
        assertThat(trustedIssuerDids()).containsExactly(REGISTERED_DID);
    }

    @Test
    void everyFailedAttemptLogsOneWarningWhateverTheMessage(ServiceExtensionContext context, ObjectFactory factory) {
        startWithGlobalConfUnavailable(context, factory);
        var uninitializedMessage = new GlobalConfInitException(GlobalConfInitState.UNINITIALIZED).getMessage();
        var malformedMessage = new GlobalConfInitException(GlobalConfInitState.FAILURE_MALFORMED).getMessage();
        var poll = captureScheduled(5L, 1);

        for (var i = 0; i < 4; i++) {
            poll.run();
        }
        verify(monitor, times(5)).warning(failureWarning(5, uninitializedMessage));

        doThrow(new GlobalConfInitException(GlobalConfInitState.FAILURE_MALFORMED)).when(globalConfProvider).getInstanceIdentifier();
        poll.run();
        poll.run();

        verify(monitor, times(2)).warning(failureWarning(5, malformedMessage));
        verify(monitor, times(7)).warning(anyString());
        verify(monitor, never()).info(anyString());
    }

    @Test
    void aRefreshFailureKeepsThePreviousTrustWarnsEveryTimeAndSchedulesTheNextRefresh(ServiceExtensionContext context,
                                                                                       ObjectFactory factory) {
        startWithScheduler(context, factory, DID_1);
        var refresh = captureScheduled(60L, 1);
        doThrow(new GlobalConfInitException(GlobalConfInitState.FAILURE_MALFORMED)).when(globalConfProvider).getInstanceIdentifier();
        var malformedMessage = new GlobalConfInitException(GlobalConfInitState.FAILURE_MALFORMED).getMessage();

        refresh.run();
        refresh.run();

        assertThat(trustedIssuerDids()).containsExactly(DID_1);
        verify(monitor, times(2)).warning(failureWarning(60, malformedMessage));
        verify(monitor, times(2)).warning(anyString());
        verify(monitor, never()).severe(anyString(), any(Throwable[].class));
        verify(monitor, times(1)).info(anyString());
        verify(scheduler, times(3)).schedule(any(Runnable.class), eq(60L), eq(TimeUnit.SECONDS));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-5"})
    void aNonPositiveRefreshIntervalFailsInitialize(String interval, ServiceExtensionContext context, ObjectFactory factory) {
        when(context.getConfig()).thenReturn(ConfigFactory.fromMap(
                Map.of(XRoadIssuerTrustAnchorExtension.SETTING_REFRESH_INTERVAL_SECONDS, interval)));
        extension = factory.constructInstance(XRoadIssuerTrustAnchorExtension.class);

        assertThatThrownBy(() -> extension.initialize(context))
                .isInstanceOf(EdcException.class)
                .hasMessageContaining(XRoadIssuerTrustAnchorExtension.SETTING_REFRESH_INTERVAL_SECONDS);
    }

    private void startWithGlobalConfUnavailable(ServiceExtensionContext context, ObjectFactory factory) {
        scheduler = mock();
        when(globalConfProvider.getInstanceIdentifier())
                .thenThrow(new GlobalConfInitException(GlobalConfInitState.UNINITIALIZED));
        extension = factory.constructInstance(XRoadIssuerTrustAnchorExtension.class);
        extension.initialize(context);
        extension.start();
    }

    private void startWithScheduler(ServiceExtensionContext context, ObjectFactory factory, String... dids) {
        scheduler = mock();
        globalConfReturns(dids);
        extension = factory.constructInstance(XRoadIssuerTrustAnchorExtension.class);
        extension.initialize(context);
        extension.start();
    }

    private void globalConfReturns(String... dids) {
        doReturn(INSTANCE_IDENTIFIER).when(globalConfProvider).getInstanceIdentifier();
        when(globalConfProvider.getCredentialIssuerDids(INSTANCE_IDENTIFIER)).thenReturn(List.of(dids));
    }

    private Runnable captureScheduled(long delaySeconds, int expectedSchedules) {
        var captor = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler, times(expectedSchedules)).schedule(captor.capture(), eq(delaySeconds), eq(TimeUnit.SECONDS));
        return captor.getValue();
    }

    private static String failureWarning(long retrySeconds, String message) {
        return "%scannot load the issuer trust, keeping the current trusted issuers, retrying every %d s: %s"
                .formatted(EXTENSION_PREFIX, retrySeconds, message);
    }

    private List<String> trustedIssuerDids() {
        return profileRegistry.getProfile(PROFILE_NAME).trustedIssuers().stream().map(TrustedIssuer::getId).toList();
    }
}
