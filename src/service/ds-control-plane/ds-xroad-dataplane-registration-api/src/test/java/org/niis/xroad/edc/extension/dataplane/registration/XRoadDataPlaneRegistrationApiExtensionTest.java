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
package org.niis.xroad.edc.extension.dataplane.registration;

import org.eclipse.edc.api.auth.spi.AuthorizationService;
import org.eclipse.edc.connector.controlplane.services.spi.transferprocess.TransferProcessService;
import org.eclipse.edc.connector.dataplane.selector.spi.DataPlaneSelectorService;
import org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance;
import org.eclipse.edc.participantcontext.single.spi.SingleParticipantContextSupplier;
import org.eclipse.edc.signaling.port.api.management.DataPlaneRegistrationApiV4Controller;
import org.eclipse.edc.signaling.port.api.management.v5.DataPlaneRegistrationApiV5Controller;
import org.eclipse.edc.signaling.port.api.signaling.DataPlaneTransferApiController;
import org.eclipse.edc.signaling.port.api.signaling.DataPlaneTransferAuthorizationFilter;
import org.eclipse.edc.signaling.spi.authorization.SignalingAuthorizationRegistry;
import org.eclipse.edc.spi.monitor.ConsoleMonitor;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.apiversion.ApiVersionService;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.web.spi.WebService;
import org.eclipse.edc.web.spi.configuration.ApiContext;
import org.eclipse.edc.web.spi.configuration.PortMapping;
import org.eclipse.edc.web.spi.configuration.PortMappingRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class XRoadDataPlaneRegistrationApiExtensionTest {

    @Mock
    private PortMappingRegistry portMappingRegistry;
    @Mock
    private DataPlaneSelectorService dataPlaneSelectorService;
    @Mock
    private WebService webService;
    @Mock
    private TransferProcessService transferProcessService;
    @Mock
    private TypeTransformerRegistry transformerRegistry;
    @Mock
    private TypeTransformerRegistry signalingApiTransformerRegistry;
    @Mock
    private ApiVersionService apiVersionService;
    @Mock
    private SignalingAuthorizationRegistry signalingAuthorizationRegistry;
    @Mock
    private AuthorizationService authorizationService;

    private ServiceExtensionContext context;
    private XRoadDataPlaneRegistrationApiExtension extension;

    @BeforeEach
    void setUp() throws Exception {
        extension = new XRoadDataPlaneRegistrationApiExtension();
        setField(extension, "signalingPort", 8185);
        setField(extension, "signalingPath", "/api/signaling");
        setField(extension, "portMappingRegistry", portMappingRegistry);
        setField(extension, "dataPlaneSelectorService", dataPlaneSelectorService);
        setField(extension, "webService", webService);
        setField(extension, "transferProcessService", transferProcessService);
        setField(extension, "transformerRegistry", transformerRegistry);
        setField(extension, "apiVersionService", apiVersionService);
        setField(extension, "signalingAuthorizationRegistry", signalingAuthorizationRegistry);
        setField(extension, "authorizationService", authorizationService);

        context = mock(ServiceExtensionContext.class);
        lenient().when(context.getMonitor()).thenReturn(new ConsoleMonitor());
        lenient().when(transformerRegistry.forContext("signaling-api")).thenReturn(signalingApiTransformerRegistry);
    }

    @Test
    void initializeRegistersPortMappingOnSignalingContext() {
        extension.initialize(context);

        var captor = ArgumentCaptor.forClass(PortMapping.class);
        verify(portMappingRegistry).register(captor.capture());
        assertThat(captor.getValue().name()).isEqualTo(ApiContext.SIGNALING);
        assertThat(captor.getValue().port()).isEqualTo(8185);
        assertThat(captor.getValue().path()).isEqualTo("/api/signaling");
    }

    @Test
    void initializeRegistersRegistrationControllerOnSignalingNotManagement() {
        extension.initialize(context);

        var captor = ArgumentCaptor.forClass(Object.class);
        verify(webService, times(3)).registerResource(eq(ApiContext.SIGNALING), captor.capture());
        assertThat(captor.getAllValues())
                .hasAtLeastOneElementOfType(DataPlaneRegistrationApiV5Controller.class)
                .hasAtLeastOneElementOfType(DataPlaneTransferAuthorizationFilter.class)
                .hasAtLeastOneElementOfType(DataPlaneTransferApiController.class);
        verify(webService, never()).registerResource(eq(ApiContext.MANAGEMENT), any());
    }

    @Test
    void initializeRegistersAuthorizationServiceLookupFunctionForDataPlaneInstance() {
        extension.initialize(context);

        verify(authorizationService).addLookupFunction(eq(DataPlaneInstance.class), any());
    }

    @Test
    void initializeRegistersV4ControllerOnSignalingInSingleParticipantMode() throws Exception {
        setField(extension, "authorizationService", null);
        setField(extension, "participantContextSupplier", mock(SingleParticipantContextSupplier.class));

        extension.initialize(context);

        var captor = ArgumentCaptor.forClass(Object.class);
        verify(webService, times(3)).registerResource(eq(ApiContext.SIGNALING), captor.capture());
        assertThat(captor.getAllValues()).hasAtLeastOneElementOfType(DataPlaneRegistrationApiV4Controller.class);
        verify(webService, never()).registerResource(eq(ApiContext.MANAGEMENT), any());
    }

    @Test
    void initializeFailsWhenNeitherParticipantContextSupplierNorAuthorizationServicePresent() throws Exception {
        setField(extension, "authorizationService", null);

        assertThatThrownBy(() -> extension.initialize(context))
                .hasMessageContaining("Missing AuthorizationService");
    }

    @Test
    void initializeRegistersSignalingVersionInfo() {
        extension.initialize(context);

        verify(apiVersionService).registerVersionInfo(eq(ApiContext.SIGNALING), any());
    }

    @Test
    void initializeRegistersSignalingTypeTransformers() {
        extension.initialize(context);

        verify(signalingApiTransformerRegistry, atLeastOnce()).register(any());
    }

    @Test
    void lookupFunctionDelegatesToDataPlaneSelectorSearchByOwnerAndId() throws Exception {
        var instance = mock(DataPlaneInstance.class);
        when(dataPlaneSelectorService.search(any())).thenReturn(ServiceResult.success(List.of(instance)));

        extension.initialize(context);

        var captor = ArgumentCaptor.forClass(BiFunction.class);
        verify(authorizationService).addLookupFunction(eq(DataPlaneInstance.class), captor.capture());

        var result = captor.getValue().apply("owner-1", "dataplane-1");
        assertThat(result).isSameAs(instance);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
