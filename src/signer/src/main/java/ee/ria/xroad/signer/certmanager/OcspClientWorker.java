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
package ee.ria.xroad.signer.certmanager;

import ee.ria.xroad.common.CertificationServiceDiagnostics;
import ee.ria.xroad.common.CertificationServiceStatus;
import ee.ria.xroad.common.DiagnosticsErrorCodes;
import ee.ria.xroad.common.OcspResponderStatus;
import ee.ria.xroad.common.cert.CertChain;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.globalconfextension.GlobalConfExtensions;
import ee.ria.xroad.common.conf.globalconfextension.OcspFetchInterval;
import ee.ria.xroad.common.ocsp.OcspVerifier;
import ee.ria.xroad.common.ocsp.OcspVerifierOptions;
import ee.ria.xroad.common.util.CertUtils;
import ee.ria.xroad.signer.OcspClientJob;
import ee.ria.xroad.signer.certmanager.OcspResponseManager.IsCachedOcspResponse;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.message.SetOcspResponses;
import ee.ria.xroad.signer.tokenmanager.TokenManager;
import ee.ria.xroad.signer.util.AbstractSignerActor;
import ee.ria.xroad.signer.util.SignerUtil;

import akka.actor.ActorRef;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cert.ocsp.OCSPException;
import org.bouncycastle.cert.ocsp.OCSPResp;

import java.io.IOException;
import java.net.ConnectException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import static ee.ria.xroad.common.util.CryptoUtils.calculateCertHexHash;
import static ee.ria.xroad.common.util.CryptoUtils.encodeBase64;
import static ee.ria.xroad.common.util.CryptoUtils.readCertificate;
import static ee.ria.xroad.signer.protocol.ComponentNames.OCSP_CLIENT_JOB;
import static ee.ria.xroad.signer.tokenmanager.ServiceLocator.getOcspResponseManager;
import static java.util.Collections.emptyList;


/**
 * This class is responsible for retrieving the OCSP responses from the OCSP
 * server and providing the responses to the message signer.
 *
 * The certificate status is queried from the server at a fixed interval.
 */
@Slf4j
@RequiredArgsConstructor
public class OcspClientWorker extends AbstractSignerActor {

    public static final String EXECUTE = "Execute";
    public static final String RELOAD = "Reload";
    public static final String DIAGNOSTICS = "Diagnostics";
    public static final String FAILED = "Failed";
    public static final String SUCCESS = "Success";
    public static final String GLOBAL_CONF_INVALIDATED = "GlobalConfInvalidated";

    private static final String OCSP_FRESHNESS_SECONDS = "ocspFreshnessSeconds";
    private static final String VERIFY_OCSP_NEXTUPDATE = "verifyOcspNextUpdate";
    private static final String OCSP_FETCH_INTERVAL = "ocspFetchInterval";

    private static final String OCSP_CLIENT_JOB_PATH = "/user/" + OCSP_CLIENT_JOB;

    private GlobalConfChangeChecker changeChecker;

    private CertificationServiceDiagnostics certServDiagnostics;

    @Override
    public void preStart() throws Exception {
        super.preStart();
        changeChecker = new GlobalConfChangeChecker();
        certServDiagnostics = new CertificationServiceDiagnostics();
    }

    @Override
    public void onReceive(Object message) throws Exception {
        if (EXECUTE.equals(message)) {
            handleExecute();
        } else if (RELOAD.equals(message)) {
            handleReload();
        } else if (DIAGNOSTICS.equals(message)) {
            handleDiagnostics();
        } else {
            if (message instanceof Exception) {
                log.error("received Exception message", ((Exception)message));
            }

            unhandled(message);
        }
    }

    void handleDiagnostics() {
        getSender().tell(certServDiagnostics, getSelf());
    }

    void handleReload() {
        log.trace("handleReload()");
        log.debug("Checking global configuration for validity and extension changes");

        GlobalConf.reload();

        if (!GlobalConf.isValid()) {
            log.error("Global configuration is not valid, skipping change detection");

            return;
        }

        initializeDiagnostics();

        boolean sendReschedule = false;
        boolean sendExecute = false;

        changeChecker.addChange(OCSP_FRESHNESS_SECONDS, GlobalConf.getOcspFreshnessSeconds(true));
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
            log.debug("sending cancel");

            getContext().actorSelection(OCSP_CLIENT_JOB_PATH).tell(OcspClientJob.CANCEL, ActorRef.noSender());

            log.debug("sending execute");

            getContext().actorSelection(OCSP_CLIENT_JOB_PATH).tell(OcspClientWorker.EXECUTE, ActorRef.noSender());
        } else if (sendReschedule) {
            log.info("Rescheduling a new OCSP-response refresh due to "
                    + "change in global configuration's additional parameters");
            log.debug("sending cancel");

            getContext().actorSelection(OCSP_CLIENT_JOB_PATH).tell(OcspClientJob.CANCEL, ActorRef.noSender());

            log.debug("sending reschedule");

            getContext().actorSelection(OCSP_CLIENT_JOB_PATH).tell(OcspClientJob.RESCHEDULE, ActorRef.noSender());
        } else {
            log.debug("No global configuration extension changes detected");
        }
    }

    void handleExecute() {
        log.trace("handleExecute()");
        log.info("OCSP-response refresh cycle started");

        if (!GlobalConf.isValid()) {
            log.debug("invalid global conf, returning");

            getSender().tell(GLOBAL_CONF_INVALIDATED, getSelf());

            return;
        }

        List<X509Certificate> certs = getCertsForOcsp();

        if (certs == null || certs.isEmpty()) {
            log.debug("Found no certificates that need OCSP responses");

            return;
        }

        log.info("Fetching OCSP responses for {} certificates", certs.size());

        Boolean failed = false;
        Map<String, OCSPResp> statuses = new HashMap<>();

        for (X509Certificate subject : certs) {
            try {
                OCSPResp status = queryCertStatus(subject, new OcspVerifierOptions(
                        GlobalConfExtensions.getInstance().shouldVerifyOcspNextUpdate()));
                if (status != null) {
                    String subjectHash = calculateCertHexHash(subject);
                    statuses.put(subjectHash, status);
                } else {
                    failed = true;
                }
            } catch (Exception e) {
                failed = true;

                log.error("Error when querying certificate '{}'", subject.getSerialNumber(), e);
            }
        }

        if (failed) {
            getSender().tell(FAILED, getSelf());
        } else {
            getSender().tell(SUCCESS, getSelf());
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

        OCSPResp response = null;

        for (String responderURI : responderURIs) {
            final OffsetDateTime prevUpdate = OffsetDateTime.now();
            final OffsetDateTime nextUpdate =
                    prevUpdate.plusSeconds(GlobalConfExtensions.getInstance().getOcspFetchInterval());

            try {
                log.debug("Fetching response from: {}", responderURI);
                response = OcspClient.fetchResponse(responderURI, subject, issuer, signerKey, signer, signAlgoId);

                if (response != null) {
                    reportOcspDiagnostics(issuer, responderURI, DiagnosticsErrorCodes.RETURN_SUCCESS, prevUpdate,
                            nextUpdate);
                    break;
                }
            } catch (OCSPException e) {
                log.error("Parsing OCSP response from " + responderURI + " failed", e);

                reportOcspDiagnostics(issuer, responderURI, DiagnosticsErrorCodes.ERROR_CODE_OCSP_RESPONSE_INVALID,
                        prevUpdate, nextUpdate);
            } catch (IOException e) {
                log.error("Unable to connect to responder at " + responderURI, e);
                reportOcspDiagnostics(issuer, responderURI, DiagnosticsErrorCodes.ERROR_CODE_OCSP_CONNECTION_ERROR,
                        prevUpdate, nextUpdate);
            } catch (Exception e) {
                log.error("Unable to fetch response from responder at " + responderURI, e);

                reportOcspDiagnostics(issuer, responderURI, DiagnosticsErrorCodes.ERROR_CODE_OCSP_FAILED,
                        prevUpdate, nextUpdate);
            }
        }
        try {
            log.debug("Verifying response: {}", response);

            OcspVerifier verifier = new OcspVerifier(GlobalConf.getOcspFreshnessSeconds(true), verifierOptions);
            verifier.verifyValidity(response, subject, issuer);

            log.debug("Received OCSP response for certificate '{}'", subject.getSubjectX500Principal());
            log.debug("Verification successful");

            return response;
        } catch (Exception e) {
            log.warn("Received OCSP response that failed verification", e);

            return null;
        }
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

        getOcspResponseManager(getContext()).tell(new SetOcspResponses(hashes.toArray(
                new String[statuses.size()]), responses.toArray(new String[statuses.size()])), getSelf());
    }

    /**
     * @return true if the response for given certificate does not exist, is expired (in which case it is also
     * removed from cache) or is not valid
     */
    boolean shouldFetchResponse(X509Certificate subject) throws Exception {
        if (!CertUtils.isValid(subject)) {
            log.warn("Certificate '{}' is not valid", subject.getSubjectX500Principal());

            return false;
        }

        String subjectHash = calculateCertHexHash(subject);

        try {
            boolean shouldFetchResponse = !isCachedOcspResponse(subjectHash);

            log.debug("shouldFetchResponse for cert: {} value: {}", subjectHash, shouldFetchResponse);

            return shouldFetchResponse;
        } catch (Exception e) {
            log.debug("shouldFetchResponse encountered an error, returning true ", e);

            // Ignore this error, since any kind of failure to get the response
            // or validate it means we should fetch the response from the
            // responder.

            return true;
        }
    }

    boolean isCertValid(X509Certificate subject) {
        try {
            return shouldFetchResponse(subject);
        } catch (Exception e) {
            log.error("Unable to check if should fetch status for " + subject.getSerialNumber(), e);
            return false;
        }
    }

    boolean isCachedOcspResponse(String certHash) throws Exception {
        // Check if the OCSP response is in the cache
        Date atDate = new Date();
        Object isCachedOcspResponseObject = SignerUtil.ask(getOcspResponseManager(getContext()),
                new IsCachedOcspResponse(certHash, atDate));

        if (isCachedOcspResponseObject instanceof Exception) {
            Exception e = (Exception)isCachedOcspResponseObject;

            log.debug("cannot figure out if IsCachedOcspResponse");

            throw e;
        }

        Boolean isCachedOcspResponse = (Boolean)isCachedOcspResponseObject;

        log.trace("isCachedOcspResponse(certHash: {}, atDate: {}) = {}", certHash, atDate, isCachedOcspResponse);

        return isCachedOcspResponse;
    }

    private List<X509Certificate> getCertChain(X509Certificate cert) {
        try {
            CertChain chain = GlobalConf.getCertChain(GlobalConf.getInstanceIdentifier(), cert);

            if (chain == null) {
                return Arrays.asList(cert);
            }

            return chain.getAllCertsWithoutTrustedRoot();
        } catch (Exception e) {
            log.error("Error getting certificate chain for certificate " + cert.getSubjectX500Principal(), e);
        }

        return emptyList();
    }

    private void initializeDiagnostics() {
        for (X509Certificate caCertificate : GlobalConf.getAllCaCerts()) {
            try {
                final String key = caCertificate.getSubjectDN().toString();

                // add certification service if it does not exist
                if (!certServDiagnostics.getCertificationServiceStatusMap().containsKey(key)) {
                    CertificationServiceStatus newServiceStatus = new CertificationServiceStatus(key);
                    certServDiagnostics.getCertificationServiceStatusMap().put(key, newServiceStatus);
                }

                CertificationServiceStatus serviceStatus =
                        certServDiagnostics.getCertificationServiceStatusMap().get(key);
                // add ocsp responder if it does not exist
                GlobalConf.getOcspResponderAddressesForCaCertificate(caCertificate).stream()
                        .filter(responderURI -> !serviceStatus.getOcspResponderStatusMap().containsKey(responderURI))
                        .forEach(responderURI -> {
                            OcspResponderStatus responderStatus = new OcspResponderStatus(
                                    DiagnosticsErrorCodes.ERROR_CODE_OCSP_UNINITIALIZED, responderURI, null,
                                    OffsetDateTime.now().plusSeconds(
                                            GlobalConfExtensions.getInstance().getOcspFetchInterval()));
                            serviceStatus.getOcspResponderStatusMap().put(responderURI, responderStatus);
                        });
            } catch (Exception e) {
                log.error("Error while initializing diagnostics: {}", e);
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
