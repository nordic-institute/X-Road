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

import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.TestCertUtil.PKCS12;
import ee.ria.xroad.common.message.RestMessage;

import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.specification.ResponseSpecification;
import lombok.extern.slf4j.Slf4j;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.test.keyconf.TestKeyConf;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
public class AuthKeyReloadTest extends AbstractProxyIntegrationTest {

    static final String PREFIX = "/r" + RestMessage.PROTOCOL_VERSION;

    static final ResponseSpecification SUCCESSFUL_RESPONSE = new ResponseSpecBuilder()
            .expectStatusCode(200)
            .expectBody("value", Matchers.equalTo(42))
            .build();

    static final ResponseSpecification SERVER_ERROR = new ResponseSpecBuilder()
            .expectStatusCode(500)
            .expectHeader("X-Road-Error", "Server.ServerProxy.ServiceFailed.SslAuthenticationFailed")
            .build();

    public static final PKCS12 AUTH_KEY1 = TestCertUtil.loadPKCS12("consumer.p12", "1", "test");
    public static final PKCS12 AUTH_KEY2 = TestCertUtil.loadPKCS12("consumer-2.p12", "1", "test");


    @Test
    public void clientProxyReloadAuthKey() {
        doRequestAndExpect(SUCCESSFUL_RESPONSE);

        replaceAuthKey(clientKeyConf);
        clientProxy.reloadAuthKey();

        doRequestAndExpect(SUCCESSFUL_RESPONSE);
    }

    @Test
    public void clientProxyReplaceAuthKeyWithoutClientProxyReload() {
        doRequestAndExpect(SUCCESSFUL_RESPONSE);

        replaceAuthKey(clientKeyConf);

        doRequestAndExpect(SERVER_ERROR);
    }

    @Test
    public void serverProxyReloadAuthKey() {
        doRequestAndExpect(SUCCESSFUL_RESPONSE);

        replaceAuthKey(serverKeyConf);
        serverProxy.reloadAuthKey();

        doRequestAndExpect(SUCCESSFUL_RESPONSE);

        var serverCertificates = clientAuthTrustVerifier.getVerifiedCertificates();
        assertThat(serverCertificates).hasSize(2);
        assertThat(serverCertificates.stream().distinct().count()).isEqualTo(2);
    }

    @Test
    public void serverProxyReplaceAuthKeyWithoutServerProxyReload() {
        doRequestAndExpect(SUCCESSFUL_RESPONSE);

        replaceAuthKey(serverKeyConf);

        doRequestAndExpect(SUCCESSFUL_RESPONSE);

        var serverCertificates = clientAuthTrustVerifier.getVerifiedCertificates();
        assertThat(serverCertificates).hasSize(2);
        assertThat(serverCertificates.stream().distinct().count()).isEqualTo(1);

    }

    @AfterEach
    public void afterEach() {
        clientKeyConf.setAuthKey(AUTH_KEY1);
        clientProxy.reloadAuthKey();
        serverKeyConf.setAuthKey(AUTH_KEY1);
        serverProxy.reloadAuthKey();
    }

    private void replaceAuthKey(TestKeyConf keyConf) {
        keyConf.setAuthKey(AUTH_KEY2);
    }

    private void doRequestAndExpect(ResponseSpecification responseSpec) {
        given()
                .baseUri("http://127.0.0.1")
                .port(proxyClientPort)
                .header("Content-Type", "application/json")
                .header("X-Road-Client", "EE/BUSINESS/consumer/sub")
                .body("{\"value\" : 42}")
                .post(PREFIX + "/EE/BUSINESS/producer/sub/echo")
                .then()
                .spec(responseSpec);
    }

}
