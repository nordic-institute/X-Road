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
package ee.ria.xroad.signer.certmanager;

import ee.ria.xroad.common.CertificationServiceDiagnostics;
import ee.ria.xroad.common.CertificationServiceStatus;
import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.DiagnosticsErrorCodes;
import ee.ria.xroad.common.OcspResponderStatus;
import ee.ria.xroad.common.cert.CertChain;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.globalconfextension.GlobalConfExtensions;
import ee.ria.xroad.common.conf.globalconfextension.OcspFetchInterval;
import ee.ria.xroad.common.ocsp.OcspVerifier;
import ee.ria.xroad.common.ocsp.OcspVerifierOptions;
import ee.ria.xroad.common.util.CertUtils;
import ee.ria.xroad.common.util.TimeUtils;
import ee.ria.xroad.signer.job.OcspClientExecuteScheduler;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.tokenmanager.TokenManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cert.ocsp.OCSPException;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.niis.xroad.signer.proto.SetOcspResponsesReq;

import java.io.IOException;
import java.net.ConnectException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import static ee.ria.xroad.common.util.CryptoUtils.calculateCertSha1HexHash;
import static ee.ria.xroad.common.util.CryptoUtils.encodeBase64;
import static ee.ria.xroad.common.util.CryptoUtils.readCertificate;
import static java.util.Collections.emptyList;

/**
 * This class is responsible for retrieving the OCSP responses from the OCSP
 * server and providing the responses to the message signer.
 * <p>
 * The certificate status is queried from the server at a fixed interval.
 */
@Slf4j
@RequiredArgsConstructor
public class OcspClientWorker {
    private static final String OCSP_FRESHNESS_SECONDS = "ocspFreshnessSeconds";
    private static final String VERIFY_OCSP_NEXTUPDATE = "verifyOcspNextUpdate";
    private static final String OCSP_FETCH_INTERVAL = "ocspFetchInterval";

    private final OcspResponseManager ocspResponseManager;

    private final GlobalConfChangeChecker changeChecker = new GlobalConfChangeChecker();

    private final CertificationServiceDiagnostics certServDiagnostics = new CertificationServiceDiagnostics();

    public CertificationServiceDiagnostics getDiagnostics() {
        return certServDiagnostics;
    }

    public void reload(OcspClientExecuteScheduler ocspClientExecuteScheduler) {
        log.trace("reload()");
        log.debug("Checking global configuration for validity and extension changes");

        GlobalConf.reload();

        if (!GlobalConf.isValid()) {
            log.error("Global configuration is not valid, skipping change detection");

            return;
        }

        initializeDiagnostics();

        boolean sendReschedule = false;
        boolean sendExecute = false;

        changeChecker.addChange(OCSP_FRESHNESS_SECONDS, GlobalConf.getOcspFreshnessSeconds());
        changeChecker.addChange(VERIFY_OCSP_NEXTUPDATE,
                GlobalConfExtensions.getInstance().shouldVerifyOcspNextUpdate());
        changeChecker.addChange(OCSP_FETCH_INTERVAL, GlobalConfExtensions.getInstance().getOcspFetchInterval());

        if (changeChecker.hasChanged(OCSP_FRESHNESS_SECONDS)) {
            log.debug("Detected change in global configuration ocspFreshnessSeconds parameter");

            sendReschedule = true;
        }
        if (changeChecker.hasChanged(VERIFY_OCSP_NEXTUPDATE)) {
            log.debug("Detected change in global configuration extension shouldVerifyOcspNextUpdate parameter");

            sendReschedule = true;
        }
        if (changeChecker.hasChanged(OCSP_FETCH_INTERVAL)) {
            log.debug("Detected change in global configuration extension ocspFetchInterval parameter");

            sendExecute = true;
        }
        if (sendExecute) {
            log.info("Launching a new OCSP-response refresh due to change in OcspFetchInterval");
            log.debug("sending execute");
            ocspClientExecuteScheduler.execute();
        } else if (sendReschedule) {
            log.info("Rescheduling a new OCSP-response refresh due to "
                    + "change in global configuration's additional parameters");
            log.debug("sending reschedule");
            ocspClientExecuteScheduler.reschedule();
        } else {
            log.debug("No global configuration extension changes detected");
        }
    }

    @SuppressWarnings("squid:S3776")
    public void execute(OcspClientExecuteScheduler ocspClientExecuteScheduler) {
        log.trace("execute()");
        log.info("OCSP-response refresh cycle started");

        if (!GlobalConf.isValid()) {
            log.debug("invalid global conf, returning");
            if (ocspClientExecuteScheduler != null) {
                ocspClientExecuteScheduler.globalConfInvalidated();
            }
            return;
        }

        List<X509Certificate> certs = getCertsForOcsp();

        if (certs == null || certs.isEmpty()) {
            log.debug("Found no certificates that need OCSP responses");

            return;
        }

        log.info("Fetching OCSP responses for {} certificates", certs.size());

        boolean failed = false;
        Map<String, OCSPResp> statuses = new HashMap<>();

        for (X509Certificate subject : certs) {
            try {
                OCSPResp status = queryCertStatus(subject, new OcspVerifierOptions(
                        GlobalConfExtensions.getInstance().shouldVerifyOcspNextUpdate()));
                if (status != null) {
                    String subjectHash = calculateCertSha1HexHash(subject);
                    statuses.put(subjectHash, status);
                } else {
                    failed = true;
                }
            } catch (Exception e) {
                failed = true;

                log.error("Error when querying certificate '{}'", subject.getSerialNumber(), e);
            }
        }
        if (ocspClientExecuteScheduler != null) {
            if (failed) {
                ocspClientExecuteScheduler.failure();
            } else {
                ocspClientExecuteScheduler.success();
            }
        }

        try {
            updateCertStatuses(statuses);
        } catch (Exception e) {
            log.error("Error updating certificate statuses", e);
        }
    }

    List<X509Certificate> getCertsForOcsp() {
        Set<X509Certificate> certs = new HashSet<>();

        for (CertificateInfo certInfo : TokenManager.getAllCerts()) {
            if (!certInfo.isActive()) {
                // do not download OCSP responses for inactive certificates
                log.debug("Skipping inactive certificate {}", certInfo.getId());

                continue;
            }

            if (!CertificateInfo.STATUS_REGISTERED.equals(certInfo.getStatus())) {
                // do not download OCSP responses for non-registered
                // certificates
                log.debug("Skipping non-registered certificate {}", certInfo.getId());

                continue;
            }

            X509Certificate cert;

            try {
                cert = readCertificate(certInfo.getCertificateBytes());
            } catch (Exception e) {
                log.error("Failed to parse certificate " + certInfo.getId(), e);

                continue;
            }

            if (CertUtils.isSelfSigned(cert)) {
                log.debug("Ignoring self-signed certificate {}", cert.getIssuerX500Principal());

                continue; // ignore self-signed certificates
            }

            getCertChain(cert).stream().filter(this::isCertValid).forEach(certs::add);
        }

        return new ArrayList<>(certs);
    }

    OCSPResp queryCertStatus(X509Certificate subject, OcspVerifierOptions verifierOptions) throws Exception {
        X509Certificate issuer = GlobalConf.getCaCert(GlobalConf.getInstanceIdentifier(), subject);

        PrivateKey signerKey = OcspClient.getOcspRequestKey(subject);
        X509Certificate signer = OcspClient.getOcspSignerCert();
        String signAlgoId = OcspClient.getSignAlgorithmId();

        List<String> responderURIs = GlobalConf.getOcspResponderAddresses(subject);

        log.debug("responder URIs: {}", responderURIs);

        if (responderURIs.isEmpty()) {
            throw new ConnectException("No OCSP responder URIs available");
        }

        final OcspVerifier verifier = new OcspVerifier(GlobalConf.getOcspFreshnessSeconds(), verifierOptions);

        for (String responderURI : responderURIs) {
            final OffsetDateTime prevUpdate = TimeUtils.offsetDateTimeNow();
            final OffsetDateTime nextUpdate = prevUpdate
                    .plusSeconds(GlobalConfExtensions.getInstance().getOcspFetchInterval());
            int errorCode = DiagnosticsErrorCodes.ERROR_CODE_OCSP_RESPONSE_INVALID;

            try {
                log.debug("Fetching response from: {}", responderURI);
                final OCSPResp response = OcspClient
                        .fetchResponse(responderURI, subject, issuer, signerKey, signer, signAlgoId);

                if (response != null) {
                    log.debug("Verifying response: {}", response);
                    verifier.verifyValidity(response, subject, issuer);
                    log.debug("Verified OCSP response for certificate '{}'", subject.getSubjectX500Principal());

                    reportOcspDiagnostics(issuer, responderURI, DiagnosticsErrorCodes.RETURN_SUCCESS, prevUpdate,
                            nextUpdate);

                    return response;
                }
            } catch (OCSPException e) {
                log.error("Parsing OCSP response from {} failed", responderURI, e);
                errorCode = DiagnosticsErrorCodes.ERROR_CODE_OCSP_RESPONSE_INVALID;
            } catch (IOException e) {
                log.error("Unable to connect to responder at {}", responderURI, e);
                errorCode = DiagnosticsErrorCodes.ERROR_CODE_OCSP_CONNECTION_ERROR;
            } catch (CodedException e) {
                log.warn("Received OCSP response that failed verification", e);
                errorCode = DiagnosticsErrorCodes.ERROR_CODE_OCSP_RESPONSE_UNVERIFIED;
            } catch (Exception e) {
                log.error("Unable to fetch response from responder at {}", responderURI, e);
                errorCode = DiagnosticsErrorCodes.ERROR_CODE_OCSP_RESPONSE_INVALID;
            }

            reportOcspDiagnostics(issuer, responderURI, errorCode, prevUpdate, nextUpdate);
        }

        return null;
    }

    private void reportOcspDiagnostics(X509Certificate issuer, String responderURI, int statusCode,
                                       OffsetDateTime prevUpdate, OffsetDateTime nextUpdate) {

        OcspResponderStatus responderStatus = new OcspResponderStatus(statusCode, responderURI, prevUpdate, nextUpdate);

        String subjectName = issuer.getSubjectDN().toString();

        CertificationServiceStatus serviceStatus;

        Map<String, CertificationServiceStatus> serviceStatusMap =
                certServDiagnostics.getCertificationServiceStatusMap();

        if (!serviceStatusMap.containsKey(subjectName)) {
            serviceStatus = new CertificationServiceStatus(subjectName);
            serviceStatusMap.put(subjectName, serviceStatus);
        } else {
            serviceStatus = serviceStatusMap.get(subjectName);
        }

        serviceStatus.getOcspResponderStatusMap().put(responderURI, responderStatus);
    }

    void updateCertStatuses(Map<String, OCSPResp> statuses) throws Exception {
        List<String> hashes = new ArrayList<>(statuses.size());
        List<String> responses = new ArrayList<>(statuses.size());

        for (Entry<String, OCSPResp> e : statuses.entrySet()) {
            hashes.add(e.getKey());
            responses.add(encodeBase64(e.getValue().getEncoded()));
        }

        SetOcspResponsesReq setOcspResponsesReq = SetOcspResponsesReq.newBuilder()
                .addAllCertHashes(hashes)
                .addAllBase64EncodedResponses(responses)
                .build();

        ocspResponseManager.handleSetOcspResponses(setOcspResponsesReq);
    }

    private boolean isCertValid(X509Certificate subject) {
        try {
            if (!CertUtils.isValid(subject)) {
                log.warn("Certificate '{}' is not valid", subject.getSubjectX500Principal());
                return false;
            }

            String subjectHash = calculateCertSha1HexHash(subject);
            try {
                // todo this should be separated from isValid check.
                //  This seems to be the only place where expired Ocsp response is cleared from TokenManager.
                ocspResponseManager.removeOcspResponseFromTokenManagerIfExpiredOrNotInCache(subjectHash);
                log.debug("shouldFetchResponse for cert: {} value: {}", subjectHash, true);
            } catch (Exception e) {
                log.debug("shouldFetchResponse encountered an error, returning true ", e);

                // Ignore this error, since any kind of failure to get the response
                // or validate it means we should fetch the response from the
                // responder.
            }

            return true;
        } catch (Exception e) {
            log.error("Unable to check if should fetch status for " + subject.getSerialNumber(), e);
            return false;
        }
    }

    private List<X509Certificate> getCertChain(X509Certificate cert) {
        try {
            CertChain chain = GlobalConf.getCertChain(GlobalConf.getInstanceIdentifier(), cert);

            if (chain == null) {
                return Arrays.asList(cert);
            }

            return chain.getAllCertsWithoutTrustedRoot();
        } catch (Exception e) {
            log.error("Error getting certificate chain for certificate {}", cert.getSubjectX500Principal(), e);
        }

        return emptyList();
    }

    private void initializeDiagnostics() {

        final int fetchInterval = GlobalConfExtensions.getInstance().getOcspFetchInterval();
        final Map<String, CertificationServiceStatus> serviceStatusMap = certServDiagnostics
                .getCertificationServiceStatusMap();

        final Collection<X509Certificate> caCerts = GlobalConf.getAllCaCerts();
        serviceStatusMap.keySet().retainAll(caCerts.stream()
                .map(X509Certificate::getSubjectDN)
                .map(Principal::toString)
                .collect(Collectors.toSet()));

        for (X509Certificate caCertificate : caCerts) {
            try {
                final String key = caCertificate.getSubjectDN().toString();
                final CertificationServiceStatus serviceStatus = serviceStatusMap
                        .computeIfAbsent(key, CertificationServiceStatus::new);

                final List<String> addresses = GlobalConf.getOcspResponderAddressesForCaCertificate(caCertificate);
                final Map<String, OcspResponderStatus> responderStatusMap = serviceStatus.getOcspResponderStatusMap();
                responderStatusMap.keySet().retainAll(addresses);

                addresses.forEach(responderURI -> responderStatusMap.computeIfAbsent(responderURI,
                        uri -> new OcspResponderStatus(DiagnosticsErrorCodes.ERROR_CODE_OCSP_UNINITIALIZED, uri, null,
                                TimeUtils.offsetDateTimeNow().plusSeconds(fetchInterval))));
            } catch (Exception e) {
                log.error("Error while initializing diagnostics", e);
            }
        }
    }

    /**
     * @return the next ocsp freshness time in seconds
     */
    public static int getNextOcspFetchIntervalSeconds() {
        int interval = GlobalConfExtensions.getInstance().getOcspFetchInterval();

        if (interval < OcspFetchInterval.OCSP_FETCH_INTERVAL_MIN) {
            interval = OcspFetchInterval.OCSP_FETCH_INTERVAL_MIN;
        }

        return interval;
    }
}
