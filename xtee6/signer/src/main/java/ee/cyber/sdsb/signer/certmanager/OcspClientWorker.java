package ee.cyber.sdsb.signer.certmanager;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.bouncycastle.cert.ocsp.OCSPResp;

import ee.cyber.sdsb.common.cert.CertChain;
import ee.cyber.sdsb.common.conf.GlobalConf;
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

            List<X509Certificate> chain = getCertChain(cert);
            for (X509Certificate certChainCert : chain) {
                try {
                    if (shouldFetchResponse(certChainCert)) {
                        certs.add(certChainCert);
                    }
                } catch (Exception e) {
                    log.error("Unable to check if should fetch status for " +
                            certChainCert.getSerialNumber(), e);
                }
            }
        }

        return new ArrayList<>(certs);
    }

    OCSPResp queryCertStatus(X509Certificate subject) throws Exception {
        X509Certificate issuer = GlobalConf.getCaCert(subject);

        PrivateKey signerKey = OcspClient.getOcspRequestKey(subject);
        X509Certificate signer = OcspClient.getOcspSignerCert();

        OCSPResp response =
                OcspClient.fetchResponse(subject, issuer, signerKey, signer);
        try {
            OcspVerifier.verifyValidity(response, subject, issuer);
            log.trace("Received OCSP response for certificate '{}'",
                    subject.getSubjectX500Principal().toString());
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
            log.warn("Certificate '{}' is not valid", subject.getSubjectDN());
            return false;
        }

        String subjectHash = calculateCertHexHash(subject);
        try {
            return !isCachedOcspResponse(subjectHash);
        } catch (Exception e) {
            // Ignore this error, since any kind of failure to get the response
            // means we should fetch the response from the responder.
            return false;
        }
    }

    boolean isCachedOcspResponse(String certHash) throws Exception {
        return (Boolean) SignerUtil.ask(getOcspResponseManager(getContext()),
                new IsCachedOcspResponse(certHash));
    }

    private List<X509Certificate> getCertChain(X509Certificate cert) {
        try {
            CertChain chain = GlobalConf.getCertChain(cert);
            return chain.getAllCertsWithoutTrustedRoot();
        } catch (Exception e) {
            log.error("Error getting certificate chain for certificate "
                    + cert.getSubjectDN(), e);
        }

        return emptyList();
    }

}
