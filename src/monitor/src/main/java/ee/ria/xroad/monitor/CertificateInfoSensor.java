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

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.monitor.CertificateMonitoringInfo.CertificateType;
import ee.ria.xroad.monitor.common.SystemMetricNames;
import ee.ria.xroad.signer.SignerProxy;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;

import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Collects certificate information.
 * Before using CertificateInfoSensor, SignerClient needs to have been initialized
 * with SignerClient.init()
 */
@Slf4j
public class CertificateInfoSensor extends AbstractSensor {

    // give signer some time to become available
    private static final Duration INITIAL_DELAY = Duration.ofSeconds(10);
    private static final String JMX_HEADER = "SHA1HASH\t\t\t\t\t\t\tCERT TYPE\t\tNOT BEFORE\t\tNOT AFTER\t\tACTIVE";

    private CertificateInfoCollector certificateInfoCollector;

    public static final String CERT_HEX_DELIMITER = ":";

    public void setCertificateInfoCollector(CertificateInfoCollector collector) {
        this.certificateInfoCollector = collector;
    }

    /**
     * Create new CertificateInfoSensor
     */
    public CertificateInfoSensor(TaskScheduler taskScheduler) {
        super(taskScheduler);
        log.info("Creating sensor, measurement interval: {}", getInterval());

        certificateInfoCollector = new CertificateInfoCollector()
                .addExtractor(new InternalServerCertificateExtractor())
                .addExtractor(new InternalTlsExtractor())
                .addExtractor(new TokenExtractor());

        scheduleSingleMeasurement(INITIAL_DELAY);
    }

    /**
     * Update existing metric with the data, or register metric as a new (with the data)
     */
    private void updateOrRegisterData(JmxStringifiedData<CertificateMonitoringInfo> data) {

        MetricRegistryHolder registryHolder = MetricRegistryHolder.getInstance();

        registryHolder
                .getOrCreateSimpleSensor(SystemMetricNames.CERTIFICATES)
                .update(data);
        registryHolder
                .getOrCreateSimpleSensor(SystemMetricNames.CERTIFICATES_STRINGS)
                .update(data.getJmxStringData());
    }

    private JmxStringifiedData<CertificateMonitoringInfo> list() {
        log.trace("listing certificate data");

        // The lists need to implement Serializable
        ArrayList<String> jmxRepresentation = new ArrayList<>();
        jmxRepresentation.add(JMX_HEADER);

        ArrayList<CertificateMonitoringInfo> dtoRepresentation = new ArrayList<>();

        for (CertificateMonitoringInfo certInfo : certificateInfoCollector.extractToSet()) {
            dtoRepresentation.add(certInfo);
            jmxRepresentation.add(getJxmRepresentationFrom(certInfo));
        }

        JmxStringifiedData<CertificateMonitoringInfo> listedData = new JmxStringifiedData<>();
        listedData.setJmxStringData(jmxRepresentation);
        listedData.setDtoData(dtoRepresentation);

        if (log.isTraceEnabled()) {
            log.trace("got listedData {}", listedData);
        }
        return listedData;
    }

    abstract static class CertificateInfoExtractor {
        abstract Stream<CertificateMonitoringInfo> getCertificates();

        static Stream<CertificateMonitoringInfo> convertToMonitoringInfo(X509Certificate certificate,
                                                                         CertificateType certificateType,
                                                                         boolean active) {
            try {
                return Stream.of(new CertificateMonitoringInfo(
                        certificateType,
                        CryptoUtils.calculateDelimitedCertHexHash(certificate, CERT_HEX_DELIMITER),
                        DateTimeFormatter.ISO_INSTANT.format(certificate.getNotBefore().toInstant()),
                        DateTimeFormatter.ISO_INSTANT.format(certificate.getNotAfter().toInstant()),
                        active
                ));
            } catch (Exception e) {
                log.error("Extracting monitoring information failed for certificate type {} with certificate {}",
                        certificateType, certificate.getIssuerDN().getName());
                return Stream.empty();
            }

        }

    }

    static class InternalTlsExtractor extends CertificateInfoExtractor {

        @Override
        public Stream<CertificateMonitoringInfo> getCertificates() {
            try {
                return convertToMonitoringInfo(
                        ServerConf.getSSLKey().getCertChain()[0],
                        CertificateType.SECURITY_SERVER_TLS,
                        true);
            } catch (Exception e) {
                throw new SensorException(e);
            }
        }

    }

    static class InternalServerCertificateExtractor extends CertificateInfoExtractor {

        @Override
        public Stream<CertificateMonitoringInfo> getCertificates() {
            return ServerConf.getAllIsCerts().stream()
                    .flatMap(c -> convertToMonitoringInfo(c, CertificateType.INTERNAL_IS_CLIENT_TLS, true));
        }

    }

    static class TokenExtractor extends CertificateInfoExtractor {

        @FunctionalInterface
        interface Lister {
            List<TokenInfo> listTokens() throws Exception;
        }

        private final Lister tokenInfoLister;

        /**
         * Constructor for test purposes
         *
         * @param tokenInfoLister
         */
        TokenExtractor(Lister tokenInfoLister) {
            this.tokenInfoLister = tokenInfoLister;
        }

        TokenExtractor() {
            tokenInfoLister = SignerProxy::getTokens;
        }

        @Override
        public Stream<CertificateMonitoringInfo> getCertificates() {
            Stream<TokenInfo> tokens;
            try {
                tokens = tokenInfoLister.listTokens().stream();
            } catch (Exception e) {
                throw new SensorException(e);
            }
            return tokens
                    .flatMap(t -> t.getKeyInfo().stream())
                    .flatMap(k -> k.getCerts().stream())
                    .flatMap(c -> convertToMonitoringInfo(
                            CryptoUtils.readCertificate(c.getCertificateBytes()),
                            CertificateType.AUTH_OR_SIGN,
                            c.isActive()));
        }
    }

    static class CertificateInfoCollector {

        private final List<CertificateInfoExtractor> extractors = new ArrayList<>();

        CertificateInfoCollector() {
        }

        CertificateInfoCollector addExtractor(CertificateInfoExtractor extractor) {
            extractors.add(extractor);
            return this;
        }

        Set<CertificateMonitoringInfo> extractToSet() {
            return extractors.stream()
                    .flatMap(CertificateInfoExtractor::getCertificates)
                    .collect(Collectors.toSet());
        }

    }

    /**
     * Tab-delimited strings, as with package / process listing
     */
    private String getJxmRepresentationFrom(CertificateMonitoringInfo info) {
        StringBuilder b = new StringBuilder();
        if (info.getSha1hash() != null) {
            addWithTab(info.getSha1hash(), b);
        }
        addWithTab(info.getType().name(), b);
        if (info.getType() == CertificateType.AUTH_OR_SIGN) {
            b.append('\t'); // a bit of extra padding for the shorter name
        }
        addWithTab(info.getNotBefore(), b);
        addWithTab(info.getNotAfter(), b);
        b.append(info.isActive());
        return b.toString();
    }

    private void addWithTab(String s, StringBuilder b) {
        b.append(s);
        b.append('\t');
    }

    @Override
    public void measure() {
        log.info("Updating CertificateInfo metrics");
        updateOrRegisterData(list());
        scheduleSingleMeasurement(getInterval());
    }

    @Override
    protected Duration getInterval() {
        return Duration.ofSeconds(SystemProperties.getEnvMonitorCertificateInfoSensorInterval());
    }

}
