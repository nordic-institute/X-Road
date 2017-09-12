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

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.monitor.CertificateMonitoringInfo.CertificateType;
import ee.ria.xroad.monitor.common.SystemMetricNames;
import ee.ria.xroad.signer.protocol.SignerClient;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;
import ee.ria.xroad.signer.protocol.message.ListTokens;
import lombok.extern.slf4j.Slf4j;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import java.security.cert.X509Certificate;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
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
    private static final FiniteDuration INITIAL_DELAY = Duration.create(10, TimeUnit.SECONDS);
    private static final String JMX_HEADER = "SHA1HASH\t\t\t\t\t\t\tCERT TYPE\t\tNOT BEFORE\t\tNOT AFTER";

    private CertificateInfoCollector certificateInfoCollector;

    public static final String CERT_HEX_DELIMITER = ":";

    static class TokenInfoLister {
        List<TokenInfo> listTokens() throws Exception {
            return SignerClient.execute(new ListTokens());
        }
    }

    public void setCertificateInfoCollector(CertificateInfoCollector collector) {
        this.certificateInfoCollector = collector;
    }

    /**
     * Create new CertificateInfoSensor
     *
     * @throws Exception
     */
    public CertificateInfoSensor() throws Exception {
        log.info("Creating sensor, measurement interval: {}", getInterval());

        certificateInfoCollector = new CertificateInfoCollector()
                .addExtractor(CertificateType.INTERNAL_IS_CLIENT_TLS, ServerConf::getAllIsCerts)
                .addExtractor(CertificateType.SECURITY_SERVER_TLS, new InternalTlsExtractor())
                .addExtractor(CertificateType.AUTH_OR_SIGN, new TokenExtractor());

        scheduleSingleMeasurement(INITIAL_DELAY, new CertificateInfoMeasure());
    }

    /**
     * Update existing metric with the data, or register metric as a new (with the data)
     *
     * @param data
     */
    private void updateOrRegisterData(JmxStringifiedData<CertificateMonitoringInfo> data) throws Exception {

        MetricRegistryHolder registryHolder = MetricRegistryHolder.getInstance();

        registryHolder
                .getOrCreateSimpleSensor(SystemMetricNames.CERTIFICATES)
                .update(data);
        registryHolder
                .getOrCreateSimpleSensor(SystemMetricNames.CERTIFICATES_STRINGS)
                .update(data.getJmxStringData());
    }

    private JmxStringifiedData<CertificateMonitoringInfo> list() throws Exception {

        log.debug("listing certificate data");

        // The lists need to implement Serializable
        ArrayList<String> jmxRepresentation = new ArrayList<>();
        jmxRepresentation.add(JMX_HEADER);

        ArrayList<CertificateMonitoringInfo> dtoRepresentation = new ArrayList<>();

        // we filter out the same certificates here or JMX will have duplicates but the metrics xml will not
        for (CertificateMonitoringInfo certInfo : certificateInfoCollector.extractToSet()) {
            dtoRepresentation.add(certInfo);
            jmxRepresentation.add(getJxmRepresentationFrom(certInfo));
        }

        JmxStringifiedData<CertificateMonitoringInfo> listedData = new JmxStringifiedData<>();
        listedData.setJmxStringData(jmxRepresentation);
        listedData.setDtoData(dtoRepresentation);
        log.debug("got listedData {}", listedData);
        return listedData;
    }

    static class InternalTlsExtractor implements CertificateInfoExtractor {

        @Override
        public List<X509Certificate> getCertificates() {

            try {
                return Collections.singletonList(ServerConf.getSSLKey().getCert());
            } catch (Exception e) {
                return Collections.emptyList();
            }
        }

    }

    @FunctionalInterface
    interface CertificateInfoExtractor {
        List<X509Certificate> getCertificates();
    }

    static class CertificateInfoCollector {

        private DateFormat certificateFormat;
        private Map<CertificateType, CertificateInfoExtractor> extractors = new HashMap<>();


        CertificateInfoCollector() {
            certificateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
            certificateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        }

        CertificateInfoCollector addExtractor(CertificateType type, CertificateInfoExtractor extractor) {
            extractors.put(type, extractor);
            return this;
        }

        Set<CertificateMonitoringInfo> extractToSet() {
            return extractors.entrySet().stream()
                    .flatMap((entry -> extractNonEmptyCertsAsStreamFrom(entry.getValue(), entry.getKey())))
                    .collect(Collectors.toSet());
        }

        private Stream<CertificateMonitoringInfo> extractNonEmptyCertsAsStreamFrom(CertificateInfoExtractor extractor,
                                                                                   CertificateType certificateType) {
            return extractor.getCertificates().stream().map(cert -> {
                try {
                    return convertToMonitoringInfo(cert, certificateType);
                } catch (Exception e) {
                    log.error("Extracting monitoring information failed for certificate type {} with certificate {}",
                            certificateType, cert.getIssuerDN().getName());
                }
                return null;
            }).filter(Objects::nonNull);

        }

        private CertificateMonitoringInfo convertToMonitoringInfo(X509Certificate certificate,
                                                                  CertificateType certificateType) throws Exception {
            CertificateMonitoringInfo certificateInfo = new CertificateMonitoringInfo();
            certificateInfo.setNotAfter(certificateFormat.format(certificate.getNotAfter()));
            certificateInfo.setNotBefore(certificateFormat.format(certificate.getNotBefore()));
            certificateInfo.setSha1hash(CryptoUtils
                    .calculateDelimitedCertHexHash(certificate, CERT_HEX_DELIMITER));
            certificateInfo.setType(certificateType);
            return certificateInfo;
        }

    }

    static class TokenExtractor implements CertificateInfoExtractor {

        private TokenInfoLister tokenInfoLister = new TokenInfoLister();

        /** Constructor for test purposes
         * @param tokenInfoLister
         */
        TokenExtractor(TokenInfoLister tokenInfoLister) {
            this.tokenInfoLister = tokenInfoLister;
        }

        TokenExtractor() {
            tokenInfoLister = new TokenInfoLister();
        }

        @Override
        public List<X509Certificate> getCertificates() {
            List<TokenInfo> tokens;
            try {
                tokens = tokenInfoLister.listTokens();
            } catch (Exception e) {
                return Collections.emptyList();
            }

            List<X509Certificate> certs = new ArrayList<>();

            for (TokenInfo token : tokens) {
                for (KeyInfo keyInfo : token.getKeyInfo()) {
                    for (CertificateInfo certInfo : keyInfo.getCerts()) {
                        byte[] certBytes = certInfo.getCertificateBytes();
                        X509Certificate cert = CryptoUtils.readCertificate(certBytes);
                        certs.add(cert);
                    }
                }
            }
            return certs;
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
        String type = info.getType().name();
        addWithTab(info.getType().name(), b);
        if (info.getType() == CertificateType.AUTH_OR_SIGN) {
            b.append('\t'); // a bit of extra padding for the shorter name
        }
        addWithTab(info.getNotBefore(), b);
        b.append(info.getNotAfter());
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
    public static class CertificateInfoMeasure {
    }

}
