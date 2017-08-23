/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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

import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.TestActorRef;
import com.codahale.metrics.MetricRegistry;
import com.typesafe.config.ConfigFactory;
import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.monitor.common.SystemMetricNames;
import ee.ria.xroad.signer.protocol.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * CertificateInfoSensorTest
 */
@Slf4j
public class CertificateInfoSensorTest {
    private static ActorSystem actorSystem;
    private MetricRegistry metrics;
    private TokenInfo caTokenInfo;
    private TokenInfo tspTokenInfo;

    private static final String CA_CERT_ID = "CA_CERT_ID";
    private static final String TSP_CERT_ID = "TSP_CERT_ID";
    private static final String CA_NOT_BEFORE = "2014-09-29T09:41:37Z";
    private static final String CA_NOT_AFTER = "2024-09-26T09:41:37Z";
    private static final String CA_ISSUER = "EMAILADDRESS=aaa@bbb.ccc, CN=Cyber, OU=ITO, O=Cybernetica, C=EE";
    private static final String CA_SUBJECT = "EMAILADDRESS=aaa@bbb.ccc, CN=Cyber, OU=ITO, O=Cybernetica, C=EE";
    private static final String TSP_NOT_BEFORE = "2012-11-29T11:53:06Z";
    private static final String TSP_NOT_AFTER = "2014-11-29T11:53:06Z";
    private static final String TSP_ISSUER = "C=SE, O=EJBCA Sample, CN=AdminCA1";
    private static final String TSP_SUBJECT = "CN=timestamp1";

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

        CertificateInfo caInfo = createTestCertificateInfo(caCert, CA_CERT_ID);
        CertificateInfo tspInfo = createTestCertificateInfo(tspCert, TSP_CERT_ID);

        KeyInfo caKeyInfo = createTestKeyInfo(caInfo);
        KeyInfo tspKeyInfo = createTestKeyInfo(tspInfo);

        caTokenInfo = createTestTokenInfo(caKeyInfo);
        tspTokenInfo = createTestTokenInfo(tspKeyInfo);

    }

    /**
     * Shut down actor system and wait for clean up, so that other tests are not disturbed
     */
    @After
    public void tearDown() {
        actorSystem.shutdown();
        actorSystem.awaitTermination();
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

    private CertificateInfo createTestCertificateInfo(X509Certificate cert, String id)
            throws CertificateEncodingException {
        CertificateInfo cInfo = new CertificateInfo(
                null,
                false,
                false,
                "status",
                id,
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
        sensor.setTokenInfoLister(new CertificateInfoSensor.TokenInfoLister() {
            @Override
            List<TokenInfo> listTokens() throws Exception {
                return Arrays.asList(caTokenInfo, tspTokenInfo);
            };
        });

        sensor.onReceive(new CertificateInfoSensor.CertificateInfoMeasure());
        Map result = metrics.getMetrics();
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
        CertificateMonitoringInfo caInfo = getCertificateInfo(certificates.getValue().getDtoData(), CA_CERT_ID);
        assertEquals(CA_NOT_AFTER, caInfo.getNotAfter());
        assertEquals(CA_NOT_BEFORE, caInfo.getNotBefore());
        assertEquals(CA_ISSUER, caInfo.getIssuer());
        assertEquals(CA_SUBJECT, caInfo.getSubject());
        CertificateMonitoringInfo tspInfo = getCertificateInfo(certificates.getValue().getDtoData(), TSP_CERT_ID);
        assertEquals(TSP_NOT_AFTER, tspInfo.getNotAfter());
        assertEquals(TSP_NOT_BEFORE, tspInfo.getNotBefore());
        assertEquals(TSP_ISSUER, tspInfo.getIssuer());
        assertEquals(TSP_SUBJECT, tspInfo.getSubject());
        log.info("testing done");
    }

    private CertificateMonitoringInfo getCertificateInfo(ArrayList<CertificateMonitoringInfo> dtoData,
                                                         String caCertId) {
        return dtoData.stream()
                .filter(c -> caCertId.equals(c.getId()))
                .findAny()
                .get();
    }
}
