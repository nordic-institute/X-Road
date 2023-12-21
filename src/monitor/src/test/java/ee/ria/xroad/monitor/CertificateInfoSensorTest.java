/*
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
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.CertificateInfoProto;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfoProto;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfoProto;
import ee.ria.xroad.signer.protocol.dto.TokenStatusInfo;

import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.google.protobuf.ByteString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;

import java.security.cert.X509Certificate;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static ee.ria.xroad.monitor.CertificateInfoSensor.CERT_HEX_DELIMITER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * CertificateInfoSensorTest
 */
@ExtendWith(MockitoExtension.class)
class CertificateInfoSensorTest {
    private MetricRegistry metrics;
    private TokenInfo caTokenInfo;
    private TokenInfo tspTokenInfo;

    private static String caCertId;
    private static String tspCertId;
    private static final String CA_NOT_BEFORE = "2014-09-29T09:41:37Z";
    private static final String CA_NOT_AFTER = "2024-09-26T09:41:37Z";
    private static final String TSP_NOT_BEFORE = "2012-11-29T11:53:06Z";
    private static final String TSP_NOT_AFTER = "2014-11-29T11:53:06Z";

    private CertificateInfoSensor certificateInfoSensor;

    @BeforeEach
    void init() throws Exception {
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

        var taskScheduler = spy(TaskScheduler.class);
        when(taskScheduler.getClock()).thenReturn(Clock.systemDefaultZone());

        certificateInfoSensor = new CertificateInfoSensor(taskScheduler);
    }

    private TokenInfo createTestTokenInfo(KeyInfo... keyInfoParams) {
        List<KeyInfoProto> keyInfos = new ArrayList<>();
        for (KeyInfo info : keyInfoParams) {
            keyInfos.add(info.getMessage());
        }

        return new TokenInfo(TokenInfoProto.newBuilder()
                .setType("type")
                .setFriendlyName("friendlyName")
                .setId("id")
                .setReadOnly(false)
                .setAvailable(false)
                .setActive(false)
                .setSerialNumber("serialNumber")
                .setLabel("label")
                .setSlotIndex(-1)
                .setStatus(TokenStatusInfo.OK)
                .addAllKeyInfo(keyInfos)
                .build());
    }

    private KeyInfo createTestKeyInfo(CertificateInfo caInfo) {
        return new KeyInfo(KeyInfoProto.newBuilder()
                .setAvailable(true)
                .setFriendlyName("friendlyName")
                .setId("id")
                .setLabel("label")
                .setPublicKey("publickey")
                .addCerts(caInfo.getMessage())
                .setSignMechanismName("mechanismName")
                .build());
    }

    private CertificateInfo createTestCertificateInfo(X509Certificate cert)
            throws Exception {
        return new CertificateInfo(CertificateInfoProto.newBuilder()
                .setActive(false)
                .setSavedToConfiguration(false)
                .setStatus("status")
                .setId(CryptoUtils.calculateDelimitedCertHexHash(cert, CERT_HEX_DELIMITER))
                .setCertificateBytes(ByteString.copyFrom(cert.getEncoded()))
                .build());
    }

    @Test
    void testSystemMetricsRequest() {
        CertificateInfoCollector collector = new CertificateInfoCollector()
                .addExtractor(new TokenExtractor(() -> Arrays.asList(caTokenInfo, tspTokenInfo)));

        certificateInfoSensor.setCertificateInfoCollector(collector);
        certificateInfoSensor.measure();

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
    }

    @Test
    void testFailingCertExtractionSystemMetricsRequest() throws Exception {
        X509Certificate mockCert = mock(X509Certificate.class, Mockito.RETURNS_DEEP_STUBS);
        when(mockCert.getEncoded()).thenThrow(new IllegalStateException("some random exception"));
        when(mockCert.getIssuerDN().getName()).thenReturn("DN");

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

        certificateInfoSensor.setCertificateInfoCollector(collector);
        certificateInfoSensor.measure();

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
