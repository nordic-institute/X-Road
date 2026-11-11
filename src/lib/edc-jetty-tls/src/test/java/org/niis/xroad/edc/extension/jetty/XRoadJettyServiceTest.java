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
package org.niis.xroad.edc.extension.jetty;

import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.TestPortUtils;

import jakarta.servlet.Servlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.web.spi.configuration.ApiContext;
import org.eclipse.edc.web.spi.configuration.PortMapping;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.net.ssl.HttpsURLConnection;

import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.niis.xroad.edc.extension.jetty.ConnectorTlsTestSupport.keyStoreOf;

class XRoadJettyServiceTest {

    private static final Monitor MONITOR = new Monitor() {
    };

    private XRoadJettyService jettyService;

    @AfterEach
    void tearDown() {
        if (jettyService != null) {
            jettyService.shutdown();
        }
    }

    @Test
    void aServletIsMountedAtThePortMappingsPathNotAtItsRegistrationAlias() throws Exception {
        var port = TestPortUtils.findRandomPort();
        var registry = new XRoadPortMappingRegistry();
        registry.register(new PortMapping("holder-service", port, "/served-path"));
        jettyService = new XRoadJettyService(MONITOR, registry);
        disableSniHostCheck(jettyService);
        jettyService.start();
        jettyService.applyConnectorTlsMaterial(keyStoreOf(TestCertUtil.getInternalKey()));

        jettyService.registerServlet("holder-service", echoServlet("hello"));

        assertThat(get(port, "/served-path").body()).isEqualTo("hello");
        assertThat(get(port, "/holder-service").status()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
    }

    @Test
    void registeringAServletUnderAnUnknownAliasFails() throws Exception {
        var registry = new XRoadPortMappingRegistry();
        registry.register(new PortMapping("known-alias", TestPortUtils.findRandomPort(), "/api"));
        jettyService = new XRoadJettyService(MONITOR, registry);
        disableSniHostCheck(jettyService);
        jettyService.start();
        jettyService.applyConnectorTlsMaterial(keyStoreOf(TestCertUtil.getInternalKey()));

        assertThatThrownBy(() -> jettyService.registerServlet("unknown-alias", echoServlet("unreachable")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reloadRotatesTheCertificateServedByEveryConnectorWithoutRestart() throws Exception {
        var externalPort = TestPortUtils.findRandomPort();
        var internalStsPort = TestPortUtils.findRandomPort();
        var registry = new XRoadPortMappingRegistry();
        registry.register(new PortMapping("external", externalPort, "/api"));
        registry.register(new PortMapping("sts", internalStsPort, "/sts"));
        jettyService = new XRoadJettyService(MONITOR, registry);
        disableSniHostCheck(jettyService);
        jettyService.start();
        jettyService.applyConnectorTlsMaterial(keyStoreOf(TestCertUtil.getInternalKey()));
        jettyService.registerServlet("external", echoServlet("external-ok"));
        jettyService.registerServlet("sts", echoServlet("sts-ok"));

        assertThat(servedCertificate(externalPort, "/api")).isEqualTo(TestCertUtil.getInternalKey().certChain[0]);
        assertThat(servedCertificate(internalStsPort, "/sts")).isEqualTo(TestCertUtil.getInternalKey().certChain[0]);

        jettyService.applyConnectorTlsMaterial(keyStoreOf(TestCertUtil.getOcspSigner()));

        assertThat(servedCertificate(externalPort, "/api")).isEqualTo(TestCertUtil.getOcspSigner().certChain[0]);
        assertThat(servedCertificate(internalStsPort, "/sts")).isEqualTo(TestCertUtil.getOcspSigner().certChain[0]);
        // still serving live traffic on both connectors post-reload, without a restart
        assertThat(get(externalPort, "/api").body()).isEqualTo("external-ok");
        assertThat(get(internalStsPort, "/sts").body()).isEqualTo("sts-ok");
    }

    @Test
    void aConnectorAddedAfterTheServerStartedCompletesATlsHandshake() throws Exception {
        var port = TestPortUtils.findRandomPort();
        var registry = new XRoadPortMappingRegistry();
        registry.register(new PortMapping("svc", port, "/api"));
        jettyService = new XRoadJettyService(MONITOR, registry);
        disableSniHostCheck(jettyService);
        jettyService.start();
        jettyService.registerServlet("svc", echoServlet("ok"));

        jettyService.applyConnectorTlsMaterial(keyStoreOf(TestCertUtil.getInternalKey()));

        assertThat(servedCertificate(port, "/api")).isEqualTo(TestCertUtil.getInternalKey().certChain[0]);
        assertThat(get(port, "/api").body()).isEqualTo("ok");
    }

    @Test
    void thePortRefusesConnectionsUntilMaterialArrives() throws Exception {
        var port = TestPortUtils.findRandomPort();
        var registry = new XRoadPortMappingRegistry();
        registry.register(new PortMapping("svc", port, "/api"));
        jettyService = new XRoadJettyService(MONITOR, registry);
        jettyService.start();
        jettyService.registerServlet("svc", echoServlet("ok"));

        assertThatThrownBy(() -> new Socket("localhost", port).close()).isInstanceOf(ConnectException.class);
    }

    @Test
    void materialAppliedAfterShutdownIsIgnored() throws Exception {
        var port = TestPortUtils.findRandomPort();
        var registry = new XRoadPortMappingRegistry();
        registry.register(new PortMapping("svc", port, "/api"));
        var stoppedService = new XRoadJettyService(MONITOR, registry);
        stoppedService.start();
        stoppedService.shutdown();
        stoppedService.applyConnectorTlsMaterial(keyStoreOf(TestCertUtil.getInternalKey()));
        assertThatThrownBy(() -> new Socket("localhost", port).close()).isInstanceOf(ConnectException.class);
    }

    @Test
    void connectorCallbacksRegisteredBeforeMaterialArrivesApplyToTheLateConnectorsOnce() throws Exception {
        var registry = new XRoadPortMappingRegistry();
        registry.register(new PortMapping("first", TestPortUtils.findRandomPort(), "/first"));
        registry.register(new PortMapping("second", TestPortUtils.findRandomPort(), "/second"));
        var configured = new ConcurrentLinkedQueue<String>();
        jettyService = new XRoadJettyService(MONITOR, registry);
        jettyService.addConnectorConfigurationCallback(connector -> configured.add(connector.getName()));
        jettyService.start();
        assertThat(configured).isEmpty();

        jettyService.applyConnectorTlsMaterial(keyStoreOf(TestCertUtil.getInternalKey()));
        jettyService.applyConnectorTlsMaterial(keyStoreOf(TestCertUtil.getOcspSigner()));

        assertThat(configured).containsExactlyInAnyOrder("first", "second");
    }

    @Test
    void theDspPortPassesADoubleEncodedParticipantSegmentDecodedOnce() throws Exception {
        var dspPort = TestPortUtils.findRandomPort();
        var registry = new XRoadPortMappingRegistry();
        registry.register(new PortMapping(ApiContext.PROTOCOL, dspPort, "/api/dsp"));
        jettyService = new XRoadJettyService(MONITOR, registry);
        disableSniHostCheck(jettyService);
        jettyService.start();
        jettyService.applyConnectorTlsMaterial(keyStoreOf(TestCertUtil.getInternalKey()));
        jettyService.registerServlet(ApiContext.PROTOCOL, pathInfoServlet());

        var response = get(dspPort, "/api/dsp/DEV:COM:12%252B34/profile");

        assertThat(response.status()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(response.body()).isEqualTo("/DEV:COM:12%2B34/profile");
    }

    @Test
    void aNonDspPortRejectsADoubleEncodedPathSegment() throws Exception {
        var otherPort = TestPortUtils.findRandomPort();
        var registry = new XRoadPortMappingRegistry();
        registry.register(new PortMapping("management", otherPort, "/api/management"));
        jettyService = new XRoadJettyService(MONITOR, registry);
        disableSniHostCheck(jettyService);
        jettyService.start();
        jettyService.applyConnectorTlsMaterial(keyStoreOf(TestCertUtil.getInternalKey()));
        jettyService.registerServlet("management", pathInfoServlet());

        assertThat(get(otherPort, "/api/management/DEV:COM:12%252B34/profile").status())
                .isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(get(otherPort, "/api/management/DEV:COM:12%2B34/profile").status())
                .isEqualTo(HttpServletResponse.SC_OK);
    }

    /**
     * The test certificates' subjects do not match "localhost", so Jetty's default SNI host check would
     * reject every request with HTTP 400 before it reaches a servlet. Production traffic dials the
     * public hostname the certificate actually carries, so this check is not something the production
     * connector configuration needs to touch — only the test setup does.
     */
    private static void disableSniHostCheck(XRoadJettyService service) {
        service.addConnectorConfigurationCallback(connector -> connector.getConnectionFactories().stream()
                .filter(HttpConnectionFactory.class::isInstance)
                .map(HttpConnectionFactory.class::cast)
                .map(factory -> factory.getHttpConfiguration().getCustomizer(SecureRequestCustomizer.class))
                .filter(Objects::nonNull)
                .forEach(customizer -> customizer.setSniHostCheck(false)));
    }

    private static Servlet echoServlet(String body) {
        return new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
                response.getWriter().write(body);
            }
        };
    }

    private static Servlet pathInfoServlet() {
        return new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
                response.getWriter().write(request.getPathInfo());
            }
        };
    }

    private record Response(int status, String body) {
    }

    private static Response get(int port, String path) throws Exception {
        var connection = openTrustingConnection(port, path);
        try {
            var status = connection.getResponseCode();
            var stream = status < HttpServletResponse.SC_BAD_REQUEST ? connection.getInputStream() : connection.getErrorStream();
            var body = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            return new Response(status, body);
        } finally {
            connection.disconnect();
        }
    }

    private static X509Certificate servedCertificate(int port, String path) throws Exception {
        var connection = openTrustingConnection(port, path);
        try {
            connection.connect();
            return (X509Certificate) connection.getServerCertificates()[0];
        } finally {
            connection.disconnect();
        }
    }

    private static HttpsURLConnection openTrustingConnection(int port, String path) throws Exception {
        var url = URI.create("https://localhost:" + port + path).toURL();
        var connection = (HttpsURLConnection) url.openConnection();
        connection.setSSLSocketFactory(ConnectorTlsTestSupport.trustAllTlsContext().getSocketFactory());
        connection.setHostnameVerifier((hostname, session) -> true);
        return connection;
    }
}
