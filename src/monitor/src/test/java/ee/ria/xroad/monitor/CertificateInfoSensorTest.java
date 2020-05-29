/**
 * The MIT License
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
package ee.ria.xroad.monitor;

import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.monitor.CertificateInfoSensor.CertificateInfoCollector;
import ee.ria.xroad.monitor.CertificateInfoSensor.TokenExtractor;
import ee.ria.xroad.monitor.common.SystemMetricNames;
import ee.ria.xroad.signer.protocol.dto.CertRequestInfo;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;
import ee.ria.xroad.signer.protocol.dto.TokenStatusInfo;

import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.TestActorRef;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static ee.ria.xroad.monitor.CertificateInfoSensor.CERT_HEX_DELIMITER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * CertificateInfoSensorTest
 */
@Slf4j
public class CertificateInfoSensorTest {
    private static ActorSystem actorSystem;
    private MetricRegistry metrics;
    private TokenInfo caTokenInfo;
    private TokenInfo tspTokenInfo;

    private static String caCertId;
    private static String tspCertId;
    private static final String CA_NOT_BEFORE = "2014-09-29T09:41:37Z";
    private static final String CA_NOT_AFTER = "2024-09-26T09:41:37Z";
    private static final String TSP_NOT_BEFORE = "2012-11-29T11:53:06Z";
    private static final String TSP_NOT_AFTER = "2014-11-29T11:53:06Z";

    /**
     * Before test handler
     */
    @Before
    public void init() throws Exception {
        actorSystem = ActorSystem.create("AkkaRemoteServer", ConfigFactory.load());
        metrics = new MetricRegistry();
        MetricRegistryHolder.getInstance().setMetrics(metrics);

        // test uses real certificates from common-test module
        X509Certificate caCert = TestCertUtil.getCaCert();
        X509Certificate tspCert = TestCertUtil.getTspCert();

        CertificateInfo caInfo = createTestCertificateInfo(caCert);
        caCertId = caInfo.getId();
        CertificateInfo tspInfo = createTestCertificateInfo(tspCert);
        tspCertId = tspInfo.getId();

        KeyInfo caKeyInfo = createTestKeyInfo(caInfo);
        KeyInfo tspKeyInfo = createTestKeyInfo(tspInfo);

        caTokenInfo = createTestTokenInfo(caKeyInfo);
        tspTokenInfo = createTestTokenInfo(tspKeyInfo);

        ServerConf.reload(new EmptyServerConf());

    }

    /**
     * Shut down actor system and wait for clean up, so that other tests are not disturbed
     */
    @After
    public void tearDown() throws Exception {
        Await.result(actorSystem.terminate(), Duration.Inf());
    }

    private TokenInfo createTestTokenInfo(KeyInfo... keyInfoParams) {
        List<KeyInfo> keyInfos = new ArrayList<>();
        for (KeyInfo info: keyInfoParams) {
            keyInfos.add(info);
        }
        Map<String, String> tokenInfos = new HashMap<>();

        return new TokenInfo("type",
                "friendlyName",
                "id",
                false, false, false,
                "serialNumber",
                "label",
                -1,
                TokenStatusInfo.OK,
                Collections.unmodifiableList(keyInfos),
                Collections.unmodifiableMap(tokenInfos));
    }

    private KeyInfo createTestKeyInfo(CertificateInfo caInfo) {
        KeyInfo keyInfo = new KeyInfo(true,
                null, "friendlyName", "id",
                "label", "publickey", new ArrayList<CertificateInfo>(),
                new ArrayList<CertRequestInfo>(), "mechanismName");
        keyInfo.getCerts().add(caInfo);
        return keyInfo;
    }

    private CertificateInfo createTestCertificateInfo(X509Certificate cert)
            throws Exception {
        CertificateInfo cInfo = new CertificateInfo(
                null,
                false,
                false,
                "status",
                CryptoUtils.calculateDelimitedCertHexHash(cert, CERT_HEX_DELIMITER),
                cert.getEncoded(),
                null);
        return cInfo;
    }

    @Test
    public void testSystemMetricsRequest() throws Exception {

        log.info("testing");
        final Props props = Props.create(CertificateInfoSensor.class);
        final TestActorRef<CertificateInfoSensor> ref = TestActorRef.create(actorSystem, props,
                "testActorRef");

        CertificateInfoSensor sensor = ref.underlyingActor();

        CertificateInfoCollector collector = new CertificateInfoCollector()
                .addExtractor(new TokenExtractor(() -> Arrays.asList(caTokenInfo, tspTokenInfo)));

        sensor.setCertificateInfoCollector(collector);

        sensor.onReceive(new CertificateInfoSensor.CertificateInfoMeasure());
        Map<String, Metric> result = metrics.getMetrics();
        assertEquals(2, result.entrySet().size()); // certs & jmx certs
        SimpleSensor<JmxStringifiedData<CertificateMonitoringInfo>> certificates =
                (SimpleSensor<JmxStringifiedData<CertificateMonitoringInfo>>)
                result.get(SystemMetricNames.CERTIFICATES);
        SimpleSensor<ArrayList<String>> certificatesAsText = (SimpleSensor<ArrayList<String>>)
                result.get(SystemMetricNames.CERTIFICATES_STRINGS);
        assertNotNull(certificates);
        assertNotNull(certificatesAsText);
        assertEquals(2, certificates.getValue().getDtoData().size());
        assertEquals(3, certificatesAsText.getValue().size()); // header line + 2 certs
        CertificateMonitoringInfo caInfo = getCertificateInfo(certificates.getValue().getDtoData(), caCertId);
        assertEquals(CA_NOT_AFTER, caInfo.getNotAfter());
        assertEquals(CA_NOT_BEFORE, caInfo.getNotBefore());
        CertificateMonitoringInfo tspInfo = getCertificateInfo(certificates.getValue().getDtoData(), tspCertId);
        assertEquals(TSP_NOT_AFTER, tspInfo.getNotAfter());
        assertEquals(TSP_NOT_BEFORE, tspInfo.getNotBefore());
        log.info("testing done");
    }

    @Test
    public void testFailingCertExtractionSystemMetricsRequest() throws Exception {

        final Props props = Props.create(CertificateInfoSensor.class);
        final TestActorRef<CertificateInfoSensor> ref = TestActorRef.create(actorSystem, props,
                "testActorRef");

        X509Certificate mockCert = mock(X509Certificate.class, Mockito.RETURNS_DEEP_STUBS);
        when(mockCert.getEncoded()).thenThrow(new IllegalStateException("some random exception"));
        when(mockCert.getIssuerDN().getName()).thenReturn("DN");

        CertificateInfoSensor sensor = ref.underlyingActor();

        CertificateInfoCollector collector = new CertificateInfoCollector()
                .addExtractor(new CertificateInfoSensor.CertificateInfoExtractor() {
                    @Override
                    Stream<CertificateMonitoringInfo> getCertificates() {
                        return convertToMonitoringInfo(
                                mockCert,
                                CertificateMonitoringInfo.CertificateType.AUTH_OR_SIGN,
                                true);
                    }
                });

        sensor.setCertificateInfoCollector(collector);

        sensor.onReceive(new CertificateInfoSensor.CertificateInfoMeasure());
        Map<String, Metric> result = metrics.getMetrics();
        assertEquals(2, result.entrySet().size()); // certs & jmx certs
        SimpleSensor<JmxStringifiedData<CertificateMonitoringInfo>> certificates =
                (SimpleSensor<JmxStringifiedData<CertificateMonitoringInfo>>)
                        result.get(SystemMetricNames.CERTIFICATES);
        SimpleSensor<ArrayList<String>> certificatesAsText = (SimpleSensor<ArrayList<String>>)
                result.get(SystemMetricNames.CERTIFICATES_STRINGS);
        assertNotNull(certificates);
        assertNotNull(certificatesAsText);
        assertEquals(0, certificates.getValue().getDtoData().size());
        assertEquals(1, certificatesAsText.getValue().size()); // header line + 0 certs
    }

    private CertificateMonitoringInfo getCertificateInfo(ArrayList<CertificateMonitoringInfo> dtoData,
                                                         String certId) {
        return dtoData.stream()
                .filter(c -> certId.equals(c.getSha1hash()))
                .findAny()
                .get();
    }
}
