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

import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import ee.ria.xroad.monitor.common.SystemMetricNames;
import ee.ria.xroad.monitor.executablelister.ListedData;
import ee.ria.xroad.signer.protocol.SignerClient;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;
import ee.ria.xroad.signer.protocol.message.ListTokens;
import lombok.extern.slf4j.Slf4j;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Collects certificate information.
 * Before using CertificateInfoSensor, SignerClient needs to be initialized
 * with SignerClient.init()
 */
@Slf4j
public class CertificateInfoSensor extends AbstractSensor {

    private CertificateFactory cf;

    /**
     * TODO: Javadocsia checkstylelle
     * @throws Exception
     */
    public CertificateInfoSensor() throws Exception {
        log.info("CertificateInfoSensor created");
        cf = CertificateFactory.getInstance("X.509");
        MetricRegistry metricRegistry = MetricRegistryHolder.getInstance().getMetrics();
        metricRegistry.register(SystemMetricNames.CERTIFICATES, createParsedMetric(list()));
        scheduleSingleMeasurement(getInterval(), new CertificateInfoMeasure());
    }

    // TODO: refactor ListedData etc monitor.executablelister.*
    private ListedData<CertificateMonitoringInfo> list() throws Exception {

        log.info("listing certificate data");

        // TODO: why cant we use JMX more easily, without 2 different representations?
        ArrayList<String> jmxRepresentation = new ArrayList<>();
        ArrayList<CertificateMonitoringInfo> parsedData = new ArrayList<>();
        List<TokenInfo> tokens = SignerClient.execute(new ListTokens());
        for (TokenInfo token : tokens) {
            for (KeyInfo keyInfo : token.getKeyInfo()) {
                for (CertificateInfo certInfo : keyInfo.getCerts()) {
                    byte[] certBytes = certInfo.getCertificateBytes();

                    // TODO: find if this is the way certificate bytes are
                    // handled in Java elsewhere in X-Road
                    InputStream in = new ByteArrayInputStream(certBytes);
                    X509Certificate cert = (X509Certificate) cf.generateCertificate(in);

                    CertificateMonitoringInfo certificateInfo = new CertificateMonitoringInfo();
                    certificateInfo.setIssuer(cert.getIssuerDN().getName());
                    certificateInfo.setSubject(cert.getSubjectDN().getName());
                    certificateInfo.setNotAfter(cert.getNotAfter());
                    certificateInfo.setNotBefore(cert.getNotBefore());
                    certificateInfo.setStatus(certInfo.getStatus());
                    certificateInfo.setId(certInfo.getId());
                    parsedData.add(certificateInfo);
                }
            }
        }
        ListedData<CertificateMonitoringInfo> listedData = new ListedData<>();
        listedData.setJmxData(jmxRepresentation);
        listedData.setParsedData(parsedData);
        log.info("got listedData {}", listedData);
        return listedData;
    }

    private Metric createParsedMetric(ListedData data) {
        SimpleSensor<ListedData> sensor = new SimpleSensor<>();
        sensor.update(data);
        return sensor;
    }

    private void updateMetrics() throws Exception {
        log.info("updating certificate metrics");
        MetricRegistry metricRegistry = MetricRegistryHolder.getInstance().getMetrics();
        ((SimpleSensor) metricRegistry.getMetrics().get(SystemMetricNames.CERTIFICATES))
                .update(list());

    }

    @Override
    public void onReceive(Object o) throws Exception {
        log.info("onReceive {}", o);
        if (o instanceof CertificateInfoMeasure) {
            log.info("Updating metrics");
            updateMetrics();
            scheduleSingleMeasurement(getInterval(), new CertificateInfoMeasure());
        } else {
            // TODO: what to do?
            log.info("checkstyle :(");
        }
    }

    @Override
    protected FiniteDuration getInterval() {
        // TODO: real interval
        final int magic = 15;
        return Duration.create(magic, TimeUnit.SECONDS);
//        return Duration.create(SystemProperties.getEnvMonitorDiskSpaceSensorInterval(), TimeUnit.SECONDS);
    }

    private static class CertificateInfoMeasure { }

}
