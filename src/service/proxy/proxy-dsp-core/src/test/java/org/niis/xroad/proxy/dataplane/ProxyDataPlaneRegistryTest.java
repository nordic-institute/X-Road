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
package org.niis.xroad.proxy.dataplane;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link ProxyDataPlaneRegistry}.
 *
 * <p>Cases 2–5 use WireMock to assert the actual HTTP wire shape sent to the control plane.
 * The {@link TypeTransformerRegistry} is the production instance from
 * {@link XRoadDpsTransformerRegistry#registry()} for parity with runtime behaviour.
 */
@ExtendWith(MockitoExtension.class)
class ProxyDataPlaneRegistryTest {

    private static final String DATA_FLOW_ENDPOINT = "http://127.0.0.1:5590/full/api/v1/dataflows";
    private static final String REGISTRATION_PATH = "/api/v1/control/v1/dataplanes";
    private static final String EDC_NAMESPACE = "https://w3id.org/edc/v0.0.1/ns/";

    @Mock
    private DataPlaneServer dataPlaneServer;
    @Mock
    private XRoadDataPlaneSignalingApiController signalingApiController;
    @Mock
    private ProxyDspProperties dspProperties;

    private WireMockServer wireMock;
    private TypeTransformerRegistry transformerRegistry;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
        transformerRegistry = new XRoadDpsTransformerRegistry().registry();
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    // -----------------------------------------------------------------------
    // Case 1: initialize registers the signaling controller at the correct path
    // -----------------------------------------------------------------------

    @Test
    void initialize_registersJaxRsResourceWithApiContextPath() throws Exception {
        stubProperties();
        stubRegistrationOk();

        createRegistry().initialize();

        verify(dataPlaneServer).registerJaxRsResource(eq("/full/api/"), eq(signalingApiController));
    }

    // -----------------------------------------------------------------------
    // Case 2: initialize POSTs to /v1/dataplanes with correct JSON-LD body
    // -----------------------------------------------------------------------

    @Test
    void initialize_postsToControlPlaneRegistrationEndpoint() throws Exception {
        stubProperties();
        stubRegistrationOk();

        var registry = createRegistry();
        registry.initialize();

        wireMock.verify(postRequestedFor(urlEqualTo(REGISTRATION_PATH))
                .withHeader("Content-Type", equalTo("application/json"))
                .withRequestBody(containing("\"@id\":\"xroad-proxy-xroad-provider\""))
                .withRequestBody(containing("@context"))
                .withRequestBody(containing(EDC_NAMESPACE)));
        assertThat(registry.isRegistered()).isTrue();
    }

    // -----------------------------------------------------------------------
    // Case 3: 4xx response — isRegistered stays false
    // -----------------------------------------------------------------------

    @Test
    void initialize_http4xxResponse_doesNotMarkRegistered() throws Exception {
        stubProperties();
        wireMock.stubFor(post(urlEqualTo(REGISTRATION_PATH))
                .willReturn(aResponse().withStatus(400).withBody("{\"error\":\"bad request\"}")));

        var registry = createRegistry();
        registry.initialize();

        assertThat(registry.isRegistered()).isFalse();
    }

    // -----------------------------------------------------------------------
    // Case 4: retry succeeds after initial failure
    // -----------------------------------------------------------------------

    @Test
    void retryControlPlaneRegistration_succeedsAfterInitialFailure() throws Exception {
        stubProperties();
        // First call returns 500, second returns 200
        wireMock.stubFor(post(urlEqualTo(REGISTRATION_PATH))
                .inScenario("retry")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("second"));
        wireMock.stubFor(post(urlEqualTo(REGISTRATION_PATH))
                .inScenario("retry")
                .whenScenarioStateIs("second")
                .willReturn(aResponse().withStatus(200).withBody("{}")));

        var registry = createRegistry();
        registry.initialize();
        assertThat(registry.isRegistered()).isFalse();

        registry.retryControlPlaneRegistration();
        assertThat(registry.isRegistered()).isTrue();
    }

    // -----------------------------------------------------------------------
    // Case 5: JSON body carries correct Xrd-PULL / http / url values
    // -----------------------------------------------------------------------

    @Test
    void initialize_bodyCarriesCorrectTransferTypeSourceTypeAndUrl() throws Exception {
        stubProperties();
        stubRegistrationOk();

        createRegistry().initialize();

        wireMock.verify(postRequestedFor(urlEqualTo(REGISTRATION_PATH))
                .withRequestBody(containing("Xrd-PULL"))
                .withRequestBody(containing("\"http\""))
                .withRequestBody(containing(DATA_FLOW_ENDPOINT)));
    }

    // -----------------------------------------------------------------------
    // Additional: server start failure propagates as XrdRuntimeException
    // -----------------------------------------------------------------------

    @Test
    void initialize_serverStartFailure_throwsXrdRuntimeException() throws Exception {
        stubProperties();
        doThrow(new RuntimeException("Port in use")).when(dataPlaneServer).start();

        assertThatThrownBy(() -> createRegistry().initialize())
                .isInstanceOf(XrdRuntimeException.class);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private ProxyDataPlaneRegistry createRegistry() {
        return new ProxyDataPlaneRegistry(dspProperties, dataPlaneServer, signalingApiController, transformerRegistry);
    }

    private void stubProperties() {
        lenient().when(dspProperties.dataFlowEndpoint()).thenReturn(DATA_FLOW_ENDPOINT);
        lenient().when(dspProperties.controlPlaneEndpoint())
                .thenReturn("http://localhost:" + wireMock.port() + "/api/v1/control");
    }

    private void stubRegistrationOk() {
        wireMock.stubFor(post(urlEqualTo(REGISTRATION_PATH))
                .willReturn(aResponse().withStatus(200).withBody("{}")));
    }
}
