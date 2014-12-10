package ee.cyber.sdsb.signer.certmanager;

import java.io.Serializable;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Map.Entry;

import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import org.bouncycastle.cert.ocsp.OCSPResp;

import akka.actor.Props;
import akka.actor.UntypedActorContext;

import ee.cyber.sdsb.common.conf.globalconf.GlobalConf;
import ee.cyber.sdsb.signer.protocol.message.GetOcspResponses;
import ee.cyber.sdsb.signer.protocol.message.GetOcspResponsesResponse;
import ee.cyber.sdsb.signer.protocol.message.SetOcspResponses;
import ee.cyber.sdsb.signer.tokenmanager.ServiceLocator;
import ee.cyber.sdsb.signer.tokenmanager.TokenManager;
import ee.cyber.sdsb.signer.util.AbstractSignerActor;
import ee.cyber.sdsb.signer.util.SignerUtil;

import static ee.cyber.sdsb.common.util.CryptoUtils.*;


/**
 * This class is responsible for managing the OCSP responses for certificates.
 *
 * Certificates are identified by their SHA-1 fingerprint calculated over
 * the entire certificate.
 *
 * When an OCSP response is added to the manager, it is first cached in memory
 * (overwriting any existing response) and then attempted to be written to disk
 * (overwriting any existing response file).
 *
 * When an OCSP response is queried from the manager, first the cache is checked
 * for the response. If the response exists in the memory cache, it is returned.
 * If the response does not exist in the memory cache, the response will be
 * loaded from disk, if it exists and is cached in memory as well.
 */
@Slf4j
public class OcspResponseManager extends AbstractSignerActor {

    /**
     * Value object for checking if certificate has OCSP response at
     * specified date.
     */
    @Value
    public static class IsCachedOcspResponse implements Serializable {
        private final String certHash;
        private final Date atDate;
    }

    /** Maps a certificate hash to an OCSP response. */
    private final FileBasedOcspCache responseCache = new FileBasedOcspCache();

    // ------------------------------------------------------------------------

    /**
     * Utility method for getting OCSP response for a certificate.
     * @param ctx the actor context
     * @param cert the certificate
     * @return OCSP response as byte array
     * @throws Exception if an error occurs
     */
    public static byte[] getOcspResponse(UntypedActorContext ctx,
            X509Certificate cert) throws Exception {
        return getOcspResponse(ctx, calculateCertHexHash(cert));
    }

    /**
     * Utility method for getting OCSP response for a certificate hash.
     * @param ctx the actor context
     * @param certHash the certificate hash
     * @return OCSP response as byte array
     * @throws Exception if an error occurs
     */
    public static byte[] getOcspResponse(UntypedActorContext ctx,
            String certHash) throws Exception {
        GetOcspResponses message =
                new GetOcspResponses(new String[] {certHash});

        GetOcspResponsesResponse result =
                (GetOcspResponsesResponse) SignerUtil.ask(
                        ServiceLocator.getOcspResponseManager(ctx), message);

        if (result.getBase64EncodedResponses().length > 0
                && result.getBase64EncodedResponses()[0] != null) {
            return decodeBase64(result.getBase64EncodedResponses()[0]);
        } else {
            return null;
        }
    }

    // ------------------------------------------------------------------------

    @Override
    public void preStart() throws Exception {
        super.preStart();
        try {
            responseCache.reloadFromDisk();

            for (Entry<String, OCSPResp> e : responseCache.entrySet()) {
                TokenManager.setOcspResponse(e.getKey(), e.getValue());
            }
        } catch (Exception e) {
            log.error("Failed to load OCSP responses from disk", e);
        }
    }

    @Override
    public void onReceive(Object message) throws Exception {
        log.trace("onReceive({})", message);

        try {
            if (message instanceof GetOcspResponses) {
                handleGetOcspResponses((GetOcspResponses) message);
            } else if (message instanceof SetOcspResponses) {
                handleSetOcspResponses((SetOcspResponses) message);
            } else if (message instanceof IsCachedOcspResponse) {
                handleIsCachedOcspResponse((IsCachedOcspResponse) message);
            } else {
                unhandled(message);
            }
        } catch (Exception e) {
            sendResponse(e);
        }
    }

    void handleGetOcspResponses(GetOcspResponses message) throws Exception {
        log.trace("handleGetOcspResponses()");

        Props props = Props.create(GetOcspResponseHandler.class, this);
        getContext().actorOf(props).tell(message.getCertHash(), getSender());
    }

    void handleSetOcspResponses(SetOcspResponses message) throws Exception {
        log.trace("handleSetOcspResponses()");

        for (int i = 0; i < message.getCertHashes().length; i++) {
            setResponse(message.getCertHashes()[i], new OCSPResp(
                    decodeBase64(message.getBase64EncodedResponses()[i])));
        }
    }

    void handleIsCachedOcspResponse(IsCachedOcspResponse message)
            throws Exception {
        log.trace("handleIsCachedOcspResponse()");

        OCSPResp response =
                responseCache.get(message.getCertHash(), message.getAtDate());

        TokenManager.setOcspResponse(message.getCertHash(), response);

        Boolean isCached = response != null;

        log.trace("'{}' (at: {}) cached: {}",
                new Object[] {message.getCertHash(), message.getAtDate(),
                    isCached });

        sendResponse(isCached);
    }

    OCSPResp getResponse(String certHash) throws Exception {
        return responseCache.get(certHash);
    }

    void setResponse(String certHash, OCSPResp response) throws Exception {
        try {
            responseCache.put(certHash, response);
        } finally {
            TokenManager.setOcspResponse(certHash, response);
        }
    }

    @RequiredArgsConstructor
    private static class GetOcspResponseHandler extends AbstractSignerActor {

        private final OcspResponseManager manager;

        @Override
        public void onReceive(Object message) throws Exception {
            try {
                if (message instanceof String[]) { // cert hashes
                    handleGetOcspResponses((String[]) message);
                } else {
                    unhandled(message);
                }
            } catch (Exception e) {
                sendResponse(e);
            } finally {
                getContext().stop(getSelf());
            }
        }

        void handleGetOcspResponses(String[] certHashes) throws Exception {
            String[] base64EncodedResponses = new String[certHashes.length];
            for (int i = 0; i < certHashes.length; i++) {
                OCSPResp ocspResponse = manager.getResponse(certHashes[i]);
                if (ocspResponse == null) {
                    // if the response is not in local cache, download it
                    ocspResponse = downloadOcspResponse(certHashes[i]);
                    if (ocspResponse != null) {
                        manager.setResponse(certHashes[i], ocspResponse);
                    }
                }

                if (ocspResponse != null) {
                    log.trace("Found OCSP response for certificate {}",
                            certHashes[i]);
                    base64EncodedResponses[i] =
                            encodeBase64(ocspResponse.getEncoded());
                } else {
                    log.warn("Could not find OCSP response for "
                            + "certificate {}", certHashes[i]);
                }
            }

            sendResponse(new GetOcspResponsesResponse(base64EncodedResponses));
        }

        OCSPResp downloadOcspResponse(String certHash) throws Exception {
            log.trace("downloadOcspResponse({})", certHash);

            X509Certificate cert = getCertForCertHash(certHash);
            if (cert == null) {
                log.warn("Could not find certificate for hash {}", certHash);
                // unknown certificate
                return null;
            }

            try {
                return OcspClient.queryCertStatus(cert);
            } catch (Exception e) {
                log.error("Error downloading OCSP response for certificate "
                        + cert.getSubjectX500Principal().getName()
                        + " (hash: " + certHash + ")", e);
                return null;
            }
        }

        private X509Certificate getCertForCertHash(String certHash)
                throws Exception {
            X509Certificate cert =
                    TokenManager.getCertificateForCertHash(certHash);
            if (cert != null) {
                return cert;
            }

            // not in key conf, look elsewhere
            for (X509Certificate caCert : GlobalConf.getAllCaCerts()) {
                if (certHash.equals(calculateCertHexHash(caCert))) {
                    return caCert;
                }
            }

            return null;
        }
    }
}
