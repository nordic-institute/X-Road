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
package org.niis.xroad.proxy.core;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.RestMessage;

import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.niis.xroad.common.agreementtoken.AgreementTokenGrant;
import org.niis.xroad.common.agreementtoken.AgreementTokenMinter;
import org.niis.xroad.common.agreementtoken.AgreementTokenScope;
import org.niis.xroad.common.agreementtoken.key.AgreementTokenKeyProvider;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.proxy.core.configuration.AgreementTokenKeyMaterial;
import org.niis.xroad.proxy.core.configuration.ProxyAgreementTokenProperties;
import org.niis.xroad.proxy.core.dsp.AssetAccessResponse;
import org.niis.xroad.proxy.core.dsp.DspRequest;
import org.niis.xroad.proxy.core.dsp.DspRequestProcessor;
import org.niis.xroad.proxy.core.serverproxy.AgreementTokenAccessCheck;
import org.niis.xroad.proxy.core.test.InMemoryAgreementTokenKeyProvider;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static ee.ria.xroad.common.ErrorCodes.SERVER_SERVERPROXY_X;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The agreement token over the REST path, through the real client and server proxy: the token the consumer-side
 * DSP dependency hands back rides the request, the server proxy verifies it with real key material, and a
 * refusal comes back to the caller as {@code agreement_token_rejected} after the client proxy's own
 * {@code process()} dropped the cached asset access.
 */
class RestAgreementTokenTest extends AbstractProxyIntegrationTest {

    private static final String PREFIX = "/r" + RestMessage.PROTOCOL_VERSION;
    private static final String SERVICE_PATH = PREFIX + "/EE/BUSINESS/producer/sub/service";
    private static final String TOKEN_REJECTED_FAULT = SERVER_SERVERPROXY_X + "." + ErrorCode.AGREEMENT_TOKEN_REJECTED.code();

    private static final ClientId CONSUMER = ClientId.Conf.create("EE", "BUSINESS", "consumer", "sub");
    private static final ServiceId SERVICE = ServiceId.Conf.create("EE", "BUSINESS", "producer", "sub", "service");
    private static final ServiceId OTHER_SERVICE = ServiceId.Conf.create("EE", "BUSINESS", "producer", "sub", "other");

    private static final Duration TOKEN_TTL = Duration.ofSeconds(60);

    private static final DspRequestProcessor CONSUMER_SIDE_DSP_PROCESSOR = mock(DspRequestProcessor.class);
    private static final AgreementTokenKeyProvider KEY_PROVIDER = InMemoryAgreementTokenKeyProvider.withGeneratedKey("1");
    private static final ProxyAgreementTokenProperties TOKEN_PROPERTIES = tokenProperties();

    @BeforeAll
    static void enableDspWithRealTokenVerification() throws Exception {
        var keyMaterial = mock(AgreementTokenKeyMaterial.class);
        when(keyMaterial.provider()).thenReturn(Optional.of(KEY_PROVIDER));

        ADDITIONAL_PROPERTIES.put("xroad.proxy.dsp-enabled", "true");
        consumerSideDspProcessorOverride = CONSUMER_SIDE_DSP_PROCESSOR;
        agreementTokenAccessCheckOverride = new AgreementTokenAccessCheck(keyMaterial, TOKEN_PROPERTIES);
        restartProxies();
    }

    @BeforeEach
    void resetDspProcessor() {
        reset(CONSUMER_SIDE_DSP_PROCESSOR);
    }

    @Test
    void validTokenIsServedEvenThoughTheAclWouldDeny() {
        denyEveryQueryInTheAcl();
        stubAssetAccess(mintToken(SERVICE, Clock.systemUTC()));

        callService().then().statusCode(200);

        verify(CONSUMER_SIDE_DSP_PROCESSOR, never()).invalidateAssetAccess(any());
    }

    @Test
    void tokenNamingAnotherServiceIsRefusedAndTheCachedAssetAccessDropped() {
        stubAssetAccess(mintToken(OTHER_SERVICE, Clock.systemUTC()));

        callService().then()
                .statusCode(500)
                .header("X-Road-Error", TOKEN_REJECTED_FAULT);

        assertCachedAssetAccessDroppedForTheCalledService();
    }

    @Test
    void expiredTokenIsRefusedAndTheCachedAssetAccessDropped() {
        stubAssetAccess(mintToken(SERVICE, Clock.fixed(Instant.now().minus(TOKEN_TTL.multipliedBy(2)), ZoneOffset.UTC)));

        callService().then()
                .statusCode(500)
                .header("X-Road-Error", TOKEN_REJECTED_FAULT);

        assertCachedAssetAccessDroppedForTheCalledService();
    }

    private void assertCachedAssetAccessDroppedForTheCalledService() {
        var dropped = ArgumentCaptor.forClass(DspRequest.class);
        verify(CONSUMER_SIDE_DSP_PROCESSOR).invalidateAssetAccess(dropped.capture());
        assertThat(dropped.getValue().serviceId()).isEqualTo(SERVICE);
        assertThat(dropped.getValue().sender()).isEqualTo(CONSUMER);
    }

    private static Response callService() {
        return given()
                .baseUri("http://127.0.0.1")
                .port(proxyClientPort)
                .header("X-Road-Client", "EE/BUSINESS/consumer/sub")
                .get(SERVICE_PATH);
    }

    private static void stubAssetAccess(String token) {
        var endpoint = "https://127.0.0.1:" + proxyServerPort + "/";
        when(CONSUMER_SIDE_DSP_PROCESSOR.execute(any())).thenReturn(new AssetAccessResponse(endpoint, token));
    }

    private static void denyEveryQueryInTheAcl() {
        TEST_SERVER_CONF.setServerConfProvider(new TestServiceServerConf() {
            @Override
            public boolean isQueryAllowed(ClientId sender, ServiceId service, String method, String path) {
                return false;
            }
        });
    }

    private static String mintToken(ServiceId service, Clock mintingClock) {
        return new AgreementTokenMinter(KEY_PROVIDER, TOKEN_PROPERTIES, mintingClock)
                .mint(new AgreementTokenGrant("agreement-1", CONSUMER, service, List.of(new AgreementTokenScope("*", "**"))));
    }

    private static ProxyAgreementTokenProperties tokenProperties() {
        var properties = mock(ProxyAgreementTokenProperties.class);
        when(properties.issuer()).thenReturn("x-road-provider-data-plane");
        when(properties.audience()).thenReturn("x-road-server-proxy");
        when(properties.tokenTtl()).thenReturn(TOKEN_TTL);
        when(properties.expiryLeeway()).thenReturn(Duration.ZERO);
        return properties;
    }
}
