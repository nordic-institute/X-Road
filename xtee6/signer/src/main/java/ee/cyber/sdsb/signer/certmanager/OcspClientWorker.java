package ee.cyber.sdsb.signer.certmanager;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.Map.Entry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.joda.time.DateTime;

import ee.cyber.sdsb.common.cert.CertChain;
import ee.cyber.sdsb.common.conf.globalconf.GlobalConf;
import ee.cyber.sdsb.common.ocsp.OcspVerifier;
import ee.cyber.sdsb.common.util.CertUtils;
import ee.cyber.sdsb.signer.certmanager.OcspResponseManager.IsCachedOcspResponse;
import ee.cyber.sdsb.signer.protocol.dto.CertificateInfo;
import ee.cyber.sdsb.signer.protocol.message.SetOcspResponses;
import ee.cyber.sdsb.signer.tokenmanager.TokenManager;
import ee.cyber.sdsb.signer.util.AbstractSignerActor;
import ee.cyber.sdsb.signer.util.SignerUtil;

import static ee.cyber.sdsb.common.util.CryptoUtils.*;
import static ee.cyber.sdsb.signer.tokenmanager.ServiceLocator.getOcspResponseManager;
import static java.util.Collections.emptyList;


/**
 * This class is responsible for retrieving the OCSP responses from
 * the OCSP server and providing the responses to the message signer.
 *
 * The certificate status is queried from the server at a fixed interval.
 */
@Slf4j
@RequiredArgsConstructor
public class OcspClientWorker extends AbstractSignerActor {

    private static final int MIN_FRESHNESS_SECONDS = 5;
    private static final int DEFAULT_FRESHNESS_SECONDS = 600;
    private static final int FRESHNESS_DIVISOR = 10;

    public static final String EXECUTE = "Execute";

    @Override
    public void onReceive(Object message) throws Exception {
        if (EXECUTE.equals(message)) {
            handleExecute();
        } else {
            unhandled(message);
        }
    }

    void handleExecute() {
        log.trace("handleExecute()");

        GlobalConf.reload(); // ideally Signer should simply listen to global conf download events and reload the conf

        if (!GlobalConf.isValid()) {
            return;
        }

        List<X509Certificate> certs = getCertsForOcsp();
        if (certs == null || certs.isEmpty()) {
            log.trace("Found no certificates that need OCSP responses");
            return;
        }

        log.debug("Fetching OCSP responses for {} certificates", certs.size());

        Map<String, OCSPResp> statuses = new HashMap<>();
        for (X509Certificate subject : certs) {
            try {
                OCSPResp status = queryCertStatus(subject);
                if (status != null) {
                    String subjectHash = calculateCertHexHash(subject);
                    statuses.put(subjectHash, status);
                }
            } catch (Exception e) {
                log.error("Error when querying certificate '"
                        + subject.getSerialNumber() + "'", e);
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
                continue; // ignore self-signed certificates
            }

            List<X509Certificate> chain = getCertChain(cert);
            for (X509Certificate certChainCert : chain) {
                try {
                    if (shouldFetchResponse(certChainCert)) {
                        certs.add(certChainCert);
                    }
                } catch (Exception e) {
                    log.error("Unable to check if should fetch status for "
                            + certChainCert.getSerialNumber(), e);
                }
            }
        }

        return new ArrayList<>(certs);
    }

    OCSPResp queryCertStatus(X509Certificate subject) throws Exception {
        X509Certificate issuer = GlobalConf.getCaCert(
                GlobalConf.getInstanceIdentifier(), subject);

        PrivateKey signerKey = OcspClient.getOcspRequestKey(subject);
        X509Certificate signer = OcspClient.getOcspSignerCert();

        OCSPResp response =
                OcspClient.fetchResponse(subject, issuer, signerKey, signer);
        try {
            OcspVerifier verifier =
                    new OcspVerifier(GlobalConf.getOcspFreshnessSeconds(true));
            verifier.verifyValidity(response, subject, issuer);

            log.trace("Received OCSP response for certificate '{}'",
                    subject.getSubjectX500Principal());
            return response;
        } catch (Exception e) {
            log.warn("Received OCSP response that failed verification", e);
            return null;
        }
    }

    void updateCertStatuses(Map<String, OCSPResp> statuses) throws Exception {
        List<String> hashes = new ArrayList<>(statuses.size());
        List<String> responses = new ArrayList<>(statuses.size());

        for (Entry<String, OCSPResp> e : statuses.entrySet()) {
            hashes.add(e.getKey());
            responses.add(encodeBase64(e.getValue().getEncoded()));
        }

        getOcspResponseManager(getContext()).tell(
                new SetOcspResponses(
                        hashes.toArray(new String[statuses.size()]),
                        responses.toArray(new String[statuses.size()])),
                        getSelf());
    }

    // Returns true, if the response for given certificate does not exist
    // or is expired (in which case it is also removed from cache).
    boolean shouldFetchResponse(X509Certificate subject) throws Exception {
        if (!CertUtils.isValid(subject)) {
            log.warn("Certificate '{}' is not valid",
                    subject.getSubjectX500Principal());
            return false;
        }

        String subjectHash = calculateCertHexHash(subject);
        try {
            return !isCachedOcspResponse(subjectHash);
        } catch (Exception e) {
            // Ignore this error, since any kind of failure to get the response
            // means we should fetch the response from the responder.
            return true;
        }
    }

    boolean isCachedOcspResponse(String certHash) throws Exception {
        // Check if the OCSP response is in the cache. We need to check if the
        // OCSP response expires in the future in order to not leave a gap,
        // where the OCSP is expired, but the new one is currently being
        // retrieved.
        Date atDate = new DateTime().plusSeconds(
                getNextOcspFreshnessSeconds()).toDate();

        log.trace("isCachedOcspResponse(certHash: {}, atDate: {})", certHash,
                atDate);

        return (Boolean) SignerUtil.ask(getOcspResponseManager(getContext()),
                new IsCachedOcspResponse(certHash, atDate));
    }

    private List<X509Certificate> getCertChain(X509Certificate cert) {
        try {
            CertChain chain = GlobalConf.getCertChain(
                    GlobalConf.getInstanceIdentifier(), cert);
            if (chain == null) {
                return Arrays.asList(cert);
            }

            return chain.getAllCertsWithoutTrustedRoot();
        } catch (Exception e) {
            log.error("Error getting certificate chain for certificate "
                    + cert.getSubjectX500Principal(), e);
        }

        return emptyList();
    }

    /**
     * @return the next ocsp freshness time in seconds
     */
    public static int getNextOcspFreshnessSeconds() {
        int freshness;
        try {
            freshness = GlobalConf.getOcspFreshnessSeconds(true);
        } catch (Exception ignored) {
            freshness = DEFAULT_FRESHNESS_SECONDS;
        }

        return Math.max(freshness / FRESHNESS_DIVISOR, MIN_FRESHNESS_SECONDS);
    }

}
