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
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.monitor.common.SystemMetricNames;
import ee.ria.xroad.signer.protocol.SignerClient;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;
import ee.ria.xroad.signer.protocol.message.ListTokens;
import lombok.extern.slf4j.Slf4j;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import java.io.Serializable;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Collects certificate information.
 * Before using CertificateInfoSensor, SignerClient needs to have been initialized
 * with SignerClient.init()
 */
@Slf4j
public class CertificateInfoSensor extends AbstractSensor {

    // give signer some time to become available
    private static final FiniteDuration INITIAL_DELAY = Duration.create(10, TimeUnit.SECONDS);
    private static final String JMX_HEADER = "ID\tISSUER\tSUBJECT\tNOT BEFORE\tNOT AFTER\tSTATUS";

    private TokenInfoLister tokenInfoLister = new TokenInfoLister();

    private SimpleDateFormat certificateFormat;

    private String formatCertificateDate(Date date) {
        return certificateFormat.format(date);
    }

    /**
     * for testability
     * @param lister
     */
    void setTokenInfoLister(TokenInfoLister lister) {
        this.tokenInfoLister = lister;
    }

    static class TokenInfoLister {
        List<TokenInfo> listTokens() throws Exception {
            return SignerClient.execute(new ListTokens());
        }
    }

    /**
     * Create new CertificateInfoSensor
     * @throws Exception
     */
    public CertificateInfoSensor() throws Exception {
        log.info("Creating sensor, measurement interval: {}", getInterval());
        certificateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        certificateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));

        updateOrRegisterData(new JmxStringifiedData());
        scheduleSingleMeasurement(INITIAL_DELAY, new CertificateInfoMeasure());
    }

    /**
     * Update existing metric with the data, or register metric as a new (with the data)
     * @param data
     */
    private void updateOrRegisterData(JmxStringifiedData<CertificateMonitoringInfo> data) throws Exception {

        MetricRegistry metricRegistry = MetricRegistryHolder.getInstance().getMetrics();

        SimpleSensor<JmxStringifiedData<CertificateMonitoringInfo>> certificateSensor = getOrCreateSimpleSensor(
                metricRegistry,
                SystemMetricNames.CERTIFICATES);
        certificateSensor.update(data);

        SimpleSensor<ArrayList<String>> certificateTextSensor = getOrCreateSimpleSensor(
                metricRegistry,
                SystemMetricNames.CERTIFICATES_STRINGS);
        certificateTextSensor.update(data.getJmxStringData());
    }

    /**
     * Either registers a new sensor to metricRegistry, or reuses already registered one
     */
    private <T extends Serializable> SimpleSensor<T> getOrCreateSimpleSensor(
            MetricRegistry metricRegistry,
            String metricName) {

        SimpleSensor<T> typeDefiningSensor = ((SimpleSensor) metricRegistry.getMetrics().get(metricName));
        if (typeDefiningSensor == null) {
            typeDefiningSensor = new SimpleSensor<>();
            metricRegistry.register(metricName, typeDefiningSensor);
        }
        return typeDefiningSensor;
    }


    private JmxStringifiedData<CertificateMonitoringInfo> list() throws Exception {

        log.debug("listing certificate data");

        ArrayList<String> jmxRepresentation = new ArrayList<>();
        jmxRepresentation.add(JMX_HEADER);
        ArrayList<CertificateMonitoringInfo> parsedData = new ArrayList<>();
        List<TokenInfo> tokens = tokenInfoLister.listTokens();

        for (TokenInfo token : tokens) {
            for (KeyInfo keyInfo : token.getKeyInfo()) {
                for (CertificateInfo certInfo : keyInfo.getCerts()) {
                    byte[] certBytes = certInfo.getCertificateBytes();
                    X509Certificate cert = CryptoUtils.readCertificate(certBytes);

                    CertificateMonitoringInfo certificateInfo = new CertificateMonitoringInfo();
                    certificateInfo.setIssuer(cert.getIssuerDN().getName());
                    certificateInfo.setSubject(cert.getSubjectDN().getName());
                    certificateInfo.setNotAfter(formatCertificateDate(cert.getNotAfter()));
                    certificateInfo.setNotBefore(formatCertificateDate(cert.getNotBefore()));
                    certificateInfo.setStatus(certInfo.getStatus());
                    certificateInfo.setId(certInfo.getId());
                    parsedData.add(certificateInfo);
                    jmxRepresentation.add(getJxmRepresentationFrom(certificateInfo));
                }
            }
        }
        JmxStringifiedData<CertificateMonitoringInfo> listedData = new JmxStringifiedData<>();
        listedData.setJmxStringData(jmxRepresentation);
        listedData.setDtoData(parsedData);
        log.debug("got listedData {}", listedData);
        return listedData;
    }

    /**
     * Tab-delimited strings, as with package / process listing
     */
    private String getJxmRepresentationFrom(CertificateMonitoringInfo info) {
        StringBuilder b = new StringBuilder();
        addWithTab(info.getId(), b);
        addWithTab(info.getIssuer(), b);
        addWithTab(info.getSubject(), b);
        addWithTab(info.getNotBefore(), b);
        addWithTab(info.getNotAfter(), b);
        b.append(info.getStatus());
        return b.toString();
    }

    private void addWithTab(String s, StringBuilder b) {
        b.append(s);
        b.append('\t');
    }

    @Override
    public void onReceive(Object o) throws Exception {
        if (o instanceof CertificateInfoMeasure) {
            log.info("Updating CertificateInfo metrics");
            updateOrRegisterData(list());
            scheduleSingleMeasurement(getInterval(), new CertificateInfoMeasure());
        } else {
            log.error("received unhandled message {}", o);
            unhandled(o);
        }
    }

    @Override
    protected FiniteDuration getInterval() {
        return Duration.create(SystemProperties.getEnvMonitorCertificateInfoSensorInterval(), TimeUnit.SECONDS);
    }

    /**
     * Akka message
     */
    public static class CertificateInfoMeasure { }

}
