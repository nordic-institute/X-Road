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
    private static final FiniteDuration INITIAL_DELAY =
            Duration.create(10, TimeUnit.SECONDS);

    /**
     * TODO: Javadocsia checkstylelle
     * @throws Exception
     */
    public CertificateInfoSensor() throws Exception {
        log.info("creating CertificateInfoSensor");
        cf = CertificateFactory.getInstance("X.509");
        updateOrRegisterData(new ListedData());
        scheduleSingleMeasurement(INITIAL_DELAY, new CertificateInfoMeasure());
    }

    /**
     * Update existing metric with the data, or register metric as a new (with the data)
     * @param data
     */
    private void updateOrRegisterData(ListedData<CertificateMonitoringInfo> data) {
        MetricRegistry metricRegistry = MetricRegistryHolder.getInstance().getMetrics();
        String metricName = SystemMetricNames.CERTIFICATES;
        SimpleSensor sensor = ((SimpleSensor) metricRegistry.getMetrics().get(metricName));
        if (sensor == null) {
            sensor = new SimpleSensor<>();
            metricRegistry.register(metricName, sensor);
        }
        sensor.update(data);
    }

    private ListedData<CertificateMonitoringInfo> list() throws Exception {

        log.info("listing certificate data");

//        if (true) throw new ee.ria.xroad.common.CodedException("testing timeout error");
//
        // TODO: why cant we use JMX more easily, without 2 different representations?
        ArrayList<String> jmxRepresentation = new ArrayList<>();
        ArrayList<CertificateMonitoringInfo> parsedData = new ArrayList<>();
        List<TokenInfo> tokens = null;

//        try {
            tokens = SignerClient.execute(new ListTokens());
//        } catch (Exception e) {
//            log.error("could not read certificate information from signer", e);
//            throw e;
//        }
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


    @Override
    public void onReceive(Object o) throws Exception {
        if (o instanceof CertificateInfoMeasure) {
            log.info("Updating metrics");
            ListedData<CertificateMonitoringInfo> data = list();
            updateOrRegisterData(data);
            scheduleSingleMeasurement(getInterval(), new CertificateInfoMeasure());
        } else {
            unhandled(o);
        }
    }

    @Override
    protected FiniteDuration getInterval() {
        // TODO: real interval
        final int magic = 20;
        return Duration.create(magic, TimeUnit.SECONDS);
//        return Duration.create(SystemProperties.getEnvMonitorDiskSpaceSensorInterval(), TimeUnit.SECONDS);
    }

    private static class CertificateInfoMeasure { }

}
