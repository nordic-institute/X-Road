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
import org.eclipse.edc.participantcontext.spi.types.ParticipantResource;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.signaling.port.api.management.DataPlaneRegistrationApiV4Controller;
import org.eclipse.edc.signaling.port.api.management.v5.DataPlaneRegistrationApiV5Controller;
import org.eclipse.edc.signaling.port.api.signaling.DataPlaneTransferApiController;
import org.eclipse.edc.signaling.port.api.signaling.DataPlaneTransferAuthorizationFilter;
import org.eclipse.edc.signaling.port.transformer.DataAddressToDspDataAddressTransformer;
import org.eclipse.edc.signaling.port.transformer.DataFlowStatusMessageToDataFlowResponseTransformer;
import org.eclipse.edc.signaling.port.transformer.DspDataAddressToDataAddressTransformer;
import org.eclipse.edc.signaling.spi.authorization.SignalingAuthorizationRegistry;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.apiversion.ApiVersionService;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.web.spi.WebService;
import org.eclipse.edc.web.spi.configuration.ApiContext;
import org.eclipse.edc.web.spi.configuration.PortMapping;
import org.eclipse.edc.web.spi.configuration.PortMappingRegistry;

import java.io.IOException;

import static org.eclipse.edc.participantcontext.spi.types.ParticipantResource.queryByParticipantContextId;
import static org.niis.xroad.edc.extension.dataplane.registration.XRoadDataPlaneRegistrationApiExtension.NAME;

/**
 * Replaces upstream {@code DataPlaneSignalingApiExtension}: keeps the data-plane transfer API on
 * {@link ApiContext#SIGNALING} unchanged, but binds the data-plane registration controller (V4 in
 * single-participant mode, V5 in multi-participant mode, mirroring upstream's mode branching) to
 * {@link ApiContext#SIGNALING} instead of {@link ApiContext#MANAGEMENT}. X-Road seeds its
 * {@code DataPlaneInstanceStore} in-process ({@code XRoadDataPlaneRegistrarExtension}) and never calls this
 * REST API, but keeps the controller mounted for DSP protocol fidelity.
 *
 * <p>The upstream extension must be excluded from the boot's dependency graph (see
 * {@code xroad.edc.boot.excluded-service-extensions}) so both extensions do not race to bind the same ports.
 */
@Extension(NAME)
public class XRoadDataPlaneRegistrationApiExtension implements ServiceExtension {

    public static final String NAME = "X-Road Data Plane Registration Api";

    private static final String API_VERSION_JSON_FILE = "signaling-api-version.json";
    private static final String DEFAULT_SIGNALING_PATH = "/api/signaling";
    private static final int DEFAULT_SIGNALING_PORT = 8182;

    @Setting(key = "web.http." + ApiContext.SIGNALING + ".port", description = "Port for " + ApiContext.SIGNALING + " api context",
            defaultValue = DEFAULT_SIGNALING_PORT + "")
    private int signalingPort;

    @Setting(key = "web.http." + ApiContext.SIGNALING + ".path", description = "Path for " + ApiContext.SIGNALING + " api context",
            defaultValue = DEFAULT_SIGNALING_PATH)
    private String signalingPath;

    @Inject
    private PortMappingRegistry portMappingRegistry;
    @Inject
    private DataPlaneSelectorService dataPlaneSelectorService;
    @Inject
    private WebService webService;
    @Inject
    private TransferProcessService transferProcessService;
    @Inject
    private TypeTransformerRegistry transformerRegistry;
    @Inject
    private ApiVersionService apiVersionService;
    @Inject
    private SignalingAuthorizationRegistry signalingAuthorizationRegistry;

    @Inject(required = false)
    private SingleParticipantContextSupplier participantContextSupplier;

    @Inject(required = false)
    private AuthorizationService authorizationService;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        portMappingRegistry.register(new PortMapping(ApiContext.SIGNALING, signalingPort, signalingPath));

        var typeTransformerRegistry = transformerRegistry.forContext("signaling-api");
        typeTransformerRegistry.register(new DataAddressToDspDataAddressTransformer());
        typeTransformerRegistry.register(new DataFlowStatusMessageToDataFlowResponseTransformer());
        typeTransformerRegistry.register(new DspDataAddressToDataAddressTransformer());

        if (participantContextSupplier != null) {
            webService.registerResource(ApiContext.SIGNALING,
                    new DataPlaneRegistrationApiV4Controller(dataPlaneSelectorService, participantContextSupplier));
        } else if (authorizationService != null) {
            authorizationService.addLookupFunction(DataPlaneInstance.class, this::findDataPlaneInstance);
            webService.registerResource(ApiContext.SIGNALING,
                    new DataPlaneRegistrationApiV5Controller(dataPlaneSelectorService, authorizationService));
        } else {
            throw new EdcException("Missing AuthorizationService which is required when running in multi participant mode.");
        }

        webService.registerResource(ApiContext.SIGNALING,
                new DataPlaneTransferAuthorizationFilter(signalingAuthorizationRegistry, transferProcessService, dataPlaneSelectorService));
        webService.registerResource(ApiContext.SIGNALING, new DataPlaneTransferApiController(transferProcessService,
                typeTransformerRegistry, context.getMonitor().withPrefix("DataPlaneTransferApiController")));

        try (var versionContent = getClass().getClassLoader().getResourceAsStream(API_VERSION_JSON_FILE)) {
            apiVersionService.registerVersionInfo(ApiContext.SIGNALING, versionContent);
        } catch (IOException e) {
            throw new EdcException(e);
        }
    }

    private ParticipantResource findDataPlaneInstance(String ownerId, String dataplaneId) {
        return dataPlaneSelectorService.search(queryByParticipantContextId(ownerId)
                        .filter(new Criterion("id", "=", dataplaneId))
                        .build())
                .map(it -> it.stream().findFirst().orElse(null))
                .orElse(f -> null);
    }
}
