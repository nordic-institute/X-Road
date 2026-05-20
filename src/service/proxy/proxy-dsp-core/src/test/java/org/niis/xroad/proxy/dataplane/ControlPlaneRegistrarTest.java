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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

/**
 * Unit tests for {@link ControlPlaneRegistrar}.
 * Uses WireMock to assert the HTTP wire shape sent to the control plane.
 * The {@link TypeTransformerRegistry} is the production instance from
 * {@link XRoadDpsTransformerRegistry#registry()} for parity with runtime behaviour.
 */
@ExtendWith(MockitoExtension.class)
class ControlPlaneRegistrarTest {

    private static final String DATA_FLOW_ENDPOINT = "http://127.0.0.1:5590/full/api/v1/dataflows";
    private static final String TEST_PARTICIPANT_CONTEXT_ID = "xrd-ss0";
    private static final String REGISTRATION_PATH = "/api/v1/control/v1/dataplanes";
    private static final String EDC_NAMESPACE = "https://w3id.org/edc/v0.0.1/ns/";

    @Mock
    private DataPlaneServerProperties dspProperties;

    private WireMockServer wireMock;
    private TypeTransformerRegistry transformerRegistry;
    private DataPlaneReadinessState readinessState;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
        transformerRegistry = new XRoadDpsTransformerRegistry().registry();
        readinessState = new DataPlaneReadinessState();
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void initialize_postsToControlPlaneRegistrationEndpoint() {
        stubProperties();
        stubRegistrationOk();

        createRegistrar().initialize();

        wireMock.verify(postRequestedFor(urlEqualTo(REGISTRATION_PATH))
                .withHeader("Content-Type", equalTo("application/json"))
                .withRequestBody(containing("\"@id\":\"xroad-proxy-" + TEST_PARTICIPANT_CONTEXT_ID + "\""))
                .withRequestBody(containing("@context"))
                .withRequestBody(containing(EDC_NAMESPACE)));
        assertThat(readinessState.isRegistered()).isTrue();
    }

    @Test
    void initialize_http4xxResponse_callsMarkNotRegistered() {
        stubProperties();
        wireMock.stubFor(post(urlEqualTo(REGISTRATION_PATH))
                .willReturn(aResponse().withStatus(400).withBody("{\"error\":\"bad request\"}")));

        createRegistrar().initialize();

        assertThat(readinessState.isRegistered()).isFalse();
    }

    @Test
    void attemptRegistration_successPath_callsMarkRegistered() {
        stubProperties();
        stubRegistrationOk();

        var registrar = createRegistrar();
        registrar.attemptControlPlaneRegistration();

        assertThat(readinessState.isRegistered()).isTrue();
    }

    @Test
    void attemptRegistration_failurePath_callsMarkNotRegistered() {
        stubProperties();
        wireMock.stubFor(post(urlEqualTo(REGISTRATION_PATH))
                .willReturn(aResponse().withStatus(500)));

        createRegistrar().attemptControlPlaneRegistration();

        assertThat(readinessState.isRegistered()).isFalse();
    }

    @Test
    void retryControlPlaneRegistration_succeedsAfterInitialFailure() {
        stubProperties();
        wireMock.stubFor(post(urlEqualTo(REGISTRATION_PATH))
                .inScenario("retry")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("second"));
        wireMock.stubFor(post(urlEqualTo(REGISTRATION_PATH))
                .inScenario("retry")
                .whenScenarioStateIs("second")
                .willReturn(aResponse().withStatus(200).withBody("{}")));

        var registrar = createRegistrar();
        registrar.initialize();
        assertThat(readinessState.isRegistered()).isFalse();

        registrar.retryControlPlaneRegistration();
        assertThat(readinessState.isRegistered()).isTrue();
    }

    @Test
    void retryControlPlaneRegistration_resendsHeartbeatWhileRegistered() {
        stubProperties();
        stubRegistrationOk();

        var registrar = createRegistrar();
        registrar.initialize();
        assertThat(readinessState.isRegistered()).isTrue();

        registrar.retryControlPlaneRegistration();
        registrar.retryControlPlaneRegistration();

        wireMock.verify(3, postRequestedFor(urlEqualTo(REGISTRATION_PATH)));
    }

    @Test
    void initialize_bodyCarriesCorrectTransferTypeSourceTypeAndUrl() {
        stubProperties();
        stubRegistrationOk();

        createRegistrar().initialize();

        wireMock.verify(postRequestedFor(urlEqualTo(REGISTRATION_PATH))
                .withRequestBody(containing("Xrd-PULL"))
                .withRequestBody(containing("\"http\""))
                .withRequestBody(containing(DATA_FLOW_ENDPOINT)));
    }

    private ControlPlaneRegistrar createRegistrar() {
        return new ControlPlaneRegistrar(dspProperties, transformerRegistry, readinessState);
    }

    private void stubProperties() {
        lenient().when(dspProperties.dataFlowEndpoint()).thenReturn(DATA_FLOW_ENDPOINT);
        lenient().when(dspProperties.controlPlaneEndpoint())
                .thenReturn("http://localhost:" + wireMock.port() + "/api/v1/control");
        lenient().when(dspProperties.participantContextId()).thenReturn(TEST_PARTICIPANT_CONTEXT_ID);
    }

    private void stubRegistrationOk() {
        wireMock.stubFor(post(urlEqualTo(REGISTRATION_PATH))
                .willReturn(aResponse().withStatus(200).withBody("{}")));
    }
}
