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

package org.niis.xroad.edc.extension.assetaccess;

import okhttp3.OkHttpClient;
import org.eclipse.edc.boot.system.injection.ObjectFactory;
import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.store.ContractNegotiationStore;
import org.eclipse.edc.connector.controlplane.services.spi.catalog.CatalogService;
import org.eclipse.edc.connector.controlplane.services.spi.contractnegotiation.ContractNegotiationService;
import org.eclipse.edc.connector.controlplane.services.spi.transferprocess.TransferProcessService;
import org.eclipse.edc.connector.controlplane.transfer.spi.flow.DataFlowController;
import org.eclipse.edc.connector.controlplane.transfer.spi.observe.TransferProcessObservable;
import org.eclipse.edc.connector.controlplane.transfer.spi.store.TransferProcessStore;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.DataAddressStore;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.junit.extensions.DependencyInjectionExtension;
import org.eclipse.edc.participant.spi.ParticipantIdMapper;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.system.ExecutorInstrumentation;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.configuration.ConfigFactory;
import org.eclipse.edc.transaction.spi.NoopTransactionContext;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.validator.spi.JsonObjectValidatorRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.niis.xroad.edc.extension.assetaccess.cooldown.CooldownTracker;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(DependencyInjectionExtension.class)
class XRoadAssetAccessApiExtensionTest {

    private static final String COOLDOWN_SETTING = "xroad.asset-access.cooldown.period-seconds";
    private static final String HANDSHAKE_RACE_DEADLINE_SETTING = "xroad.asset-access.handshake-race.deadline-millis";
    private static final String CANDIDATE_TIMEOUT_SETTING = "xroad.asset-access.candidate.timeout-seconds";

    @BeforeEach
    void setUp(ServiceExtensionContext context) {
        var transformerRegistry = mock(TypeTransformerRegistry.class);
        when(transformerRegistry.forContext(anyString())).thenReturn(mock(TypeTransformerRegistry.class));
        context.registerService(CatalogService.class, mock(CatalogService.class));
        context.registerService(ContractNegotiationService.class, mock(ContractNegotiationService.class));
        context.registerService(TransferProcessService.class, mock(TransferProcessService.class));
        context.registerService(ContractNegotiationStore.class, mock(ContractNegotiationStore.class));
        context.registerService(TransferProcessStore.class, mock(TransferProcessStore.class));
        context.registerService(DataAddressStore.class, mock(DataAddressStore.class));
        context.registerService(TransactionContext.class, new NoopTransactionContext());
        context.registerService(ExecutorInstrumentation.class, ExecutorInstrumentation.noop());
        context.registerService(Clock.class, Clock.systemUTC());
        context.registerService(JsonLd.class, mock(JsonLd.class));
        context.registerService(TypeTransformerRegistry.class, transformerRegistry);
        context.registerService(JsonObjectValidatorRegistry.class, mock(JsonObjectValidatorRegistry.class));
        context.registerService(ParticipantIdMapper.class, mock(ParticipantIdMapper.class));
        context.registerService(TransferProcessObservable.class, mock(TransferProcessObservable.class));
        context.registerService(DataFlowController.class, mock(DataFlowController.class));
        context.registerService(OkHttpClient.class, new OkHttpClient());
    }

    @Test
    void cooldownPeriodDefaultsToOneHundredEightySeconds(ServiceExtensionContext context, ObjectFactory factory) throws Exception {
        var tracker = initializedCooldownTracker(context, factory);

        assertThat(tracker.period()).isEqualTo(Duration.ofSeconds(180));
    }

    @Test
    void cooldownPeriodHonoursTheConfiguredOverride(ServiceExtensionContext context, ObjectFactory factory) throws Exception {
        when(context.getConfig()).thenReturn(ConfigFactory.fromMap(Map.of(COOLDOWN_SETTING, "30")));
        var tracker = initializedCooldownTracker(context, factory);

        assertThat(tracker.period()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void candidateTimeoutDefaultsToTwentySeconds() throws Exception {
        var setting = XRoadAssetAccessApiExtension.class.getDeclaredField("candidateTimeoutSeconds")
                .getAnnotation(Setting.class);
        assertThat(setting.key()).isEqualTo("xroad.asset-access.candidate.timeout-seconds");
        assertThat(setting.defaultValue()).isEqualTo("20");
    }

    @Test
    void candidateTimeoutRejectsZeroAndNegative(ServiceExtensionContext context, ObjectFactory factory) {
        when(context.getConfig()).thenReturn(ConfigFactory.fromMap(Map.of(CANDIDATE_TIMEOUT_SETTING, "0")));
        var zero = factory.constructInstance(XRoadAssetAccessApiExtension.class);
        when(context.getConfig()).thenReturn(ConfigFactory.fromMap(Map.of(CANDIDATE_TIMEOUT_SETTING, "-1")));
        var negative = factory.constructInstance(XRoadAssetAccessApiExtension.class);

        assertThatThrownBy(() -> zero.initialize(context))
                .isInstanceOf(EdcException.class)
                .hasMessageContaining(CANDIDATE_TIMEOUT_SETTING);
        assertThatThrownBy(() -> negative.initialize(context))
                .isInstanceOf(EdcException.class)
                .hasMessageContaining(CANDIDATE_TIMEOUT_SETTING);
    }

    @Test
    void handshakeRaceDeadlineDefaultsToFifteenHundredMilliseconds() throws Exception {
        var setting = XRoadAssetAccessApiExtension.class.getDeclaredField("handshakeRaceDeadlineMillis")
                .getAnnotation(Setting.class);
        assertThat(setting.key()).isEqualTo("xroad.asset-access.handshake-race.deadline-millis");
        assertThat(setting.defaultValue()).isEqualTo("1500");
    }

    @Test
    void handshakeRaceDeadlineRejectsZeroAndNegative(ServiceExtensionContext context, ObjectFactory factory) {
        when(context.getConfig()).thenReturn(ConfigFactory.fromMap(Map.of(HANDSHAKE_RACE_DEADLINE_SETTING, "0")));
        var zero = factory.constructInstance(XRoadAssetAccessApiExtension.class);
        when(context.getConfig()).thenReturn(ConfigFactory.fromMap(Map.of(HANDSHAKE_RACE_DEADLINE_SETTING, "-1")));
        var negative = factory.constructInstance(XRoadAssetAccessApiExtension.class);

        assertThatThrownBy(() -> zero.initialize(context))
                .isInstanceOf(EdcException.class)
                .hasMessageContaining(HANDSHAKE_RACE_DEADLINE_SETTING);
        assertThatThrownBy(() -> negative.initialize(context))
                .isInstanceOf(EdcException.class)
                .hasMessageContaining(HANDSHAKE_RACE_DEADLINE_SETTING);
    }

    private static CooldownTracker initializedCooldownTracker(ServiceExtensionContext context, ObjectFactory factory)
            throws Exception {
        var extension = factory.constructInstance(XRoadAssetAccessApiExtension.class);
        extension.initialize(context);
        var field = XRoadAssetAccessApiExtension.class.getDeclaredField("cooldownTracker");
        field.setAccessible(true);
        return (CooldownTracker) field.get(extension);
    }
}
