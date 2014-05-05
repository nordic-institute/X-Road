package ee.cyber.sdsb.proxy.util;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.bouncycastle.asn1.ocsp.OCSPResponseStatus;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.ocsp.CertificateID;
import org.bouncycastle.cert.ocsp.OCSPException;
import org.bouncycastle.cert.ocsp.OCSPReq;
import org.bouncycastle.cert.ocsp.OCSPReqBuilder;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.operator.ContentSigner;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.conf.GlobalConf;
import ee.cyber.sdsb.common.ocsp.OcspVerifier;
import ee.cyber.sdsb.common.util.CryptoUtils;
import ee.cyber.sdsb.common.util.MimeTypes;
import ee.cyber.sdsb.proxy.conf.KeyConf;
import ee.cyber.sdsb.proxy.conf.ServerConf;


/**
 * This class is responsible for retrieving the OCSP responses from
 * the OCSP server and providing the responses to the message signer.
 *
 * The certificate status is queried from the server at a fixed interval.
 * The client runs in a Quartz scheduler.
 */
@DisallowConcurrentExecution
public final class OcspClient implements Job {

    private static final Logger LOG = LoggerFactory.getLogger(OcspClient.class);

    private static final int CONNECT_TIMEOUT_MS = 20000;

    public OcspClient() {
    }

    @Override
    public void execute(JobExecutionContext ctx) throws JobExecutionException {
        LOG.debug("OcspClient executing...");

        List<X509Certificate> certs = getCertsForOcsp();
        if (certs == null || certs.isEmpty()) {
            return;
        }

        LOG.debug("Fetching OCSP responses for " + certs.size()
                + " certificates");
        for (X509Certificate subject : certs) {
            try {
                queryAndUpdateCertStatus(subject);
            } catch (Exception e) {
                LOG.error("Error when querying certificate '"
                        + subject.getSerialNumber() + "'", e);
            }
        }
    }

    public static OCSPResp queryCertStatus(X509Certificate subject)
            throws Exception {
        X509Certificate issuer = GlobalConf.getCaCert(subject);

        PrivateKey signerKey = KeyConf.getOcspRequestKey(subject);
        X509Certificate signer = KeyConf.getOcspSignerCert();

        return fetchResponse(subject, issuer, signerKey, signer);
    }

    protected static List<X509Certificate> getCertsForOcsp() {
        List<X509Certificate> allCerts = null;
        try {
            allCerts = ServerConf.getCertsForOcsp();
        } catch (Exception e) {
            LOG.error("Unable to get list of certificates", e);
            return null;
        }

        // select only certs that need (new) OCSP responses
        List<X509Certificate> certs = new ArrayList<>();
        for (X509Certificate cert : allCerts) {
            try {
                if (shouldFetchResponse(cert)) {
                    certs.add(cert);
                }
            } catch (Exception e) {
                LOG.error("Unable to check if should fetch status for " +
                        cert.getSerialNumber(), e);
            }
        }
        return certs;
    }

    protected static void queryAndUpdateCertStatus(X509Certificate subject)
            throws Exception {
        X509Certificate issuer = GlobalConf.getCaCert(subject);

        PrivateKey signerKey = KeyConf.getOcspRequestKey(subject);
        X509Certificate signer = KeyConf.getOcspSignerCert();

        queryAndUpdateCertStatus(subject, issuer, signerKey, signer);
    }

    protected static void queryAndUpdateCertStatus(X509Certificate subject,
            X509Certificate issuer, PrivateKey signerKey,
            X509Certificate signer) throws Exception {
        OCSPResp response = fetchResponse(subject, issuer, signerKey, signer);
        String subjectHash = CryptoUtils.calculateCertHexHash(subject);
        ServerConf.setOcspResponse(subjectHash, response);

        try {
            OcspVerifier.verify(response, subject, issuer);
            LOG.info("Received OCSP response for certificate '{}' is good",
                    subject.getSubjectX500Principal().toString());
        } catch (Exception e) {
            LOG.warn("Received OCSP response that failed verification", e);
        }
    }

    // Returns true, if the response for given certificate does not exist
    // or is expired (in which case it is also removed from cache).
    protected static boolean shouldFetchResponse(X509Certificate subject)
            throws Exception {
        String subjectHash = CryptoUtils.calculateCertHexHash(subject);

        try {
            return !ServerConf.isCachedOcspResponse(subjectHash);
        } catch (Exception e) {
            // Ignore this error, since any kind of failure to get the response
            // means we should fetch the response from the responder.
            return false;
        }
    }

    protected static OCSPResp fetchResponse(X509Certificate subject,
            X509Certificate issuer, PrivateKey signerKey,
            X509Certificate signer) throws Exception {
        List<String> responderURIs =
                GlobalConf.getOcspResponderAddresses(subject);

        if (responderURIs.isEmpty()) {
            throw new ConnectException("No OCSP responder URIs available");
        }

        for (String responderURI : responderURIs) {
            try {
                return fetchResponse(responderURI, subject, issuer,
                        signerKey, signer);
            } catch (Exception e) {
                LOG.error("Unable to fetch response from responder at "
                        + responderURI, e);
            }
        }

        throw new OCSPException(
                "Unable to get valid OCSP response from any responders");
    }

    protected static OCSPResp fetchResponse(String responderURI,
            X509Certificate subject, X509Certificate issuer,
            PrivateKey signerKey, X509Certificate signer) throws Exception {
        HttpURLConnection connection = createConnection(responderURI);

        OCSPReq ocspRequest = createRequest(subject, issuer, signerKey, signer);

        LOG.debug("Fetching certificate '{}' status from responder: {}",
                subject.getSerialNumber(), connection.getURL().toString());

        sendRequest(connection, ocspRequest);
        verifyResponseCode(connection);

        byte[] responseData = getResponseData(connection);
        OCSPResp response = parseResponse(responseData);

        verifyResponse(response);
        return response;
    }

    private static byte[] getResponseData(HttpURLConnection connection)
            throws IOException {
        byte[] responseData =
                IOUtils.toByteArray((InputStream) connection.getContent());
        if (responseData == null || responseData.length == 0) {
            throw new IOException("No response from responder");
        }
        return responseData;
    }

    private static void verifyResponseCode(HttpURLConnection connection)
            throws IOException {
        if (connection.getResponseCode() / 100 != 2) {
            throw new IOException("Invalid http response code from responder: "
                    + connection.getResponseCode());
        }
    }

    private static void sendRequest(HttpURLConnection connection,
            OCSPReq ocspRequest) throws IOException {
        DataOutputStream outStream = new DataOutputStream(
                new BufferedOutputStream(connection.getOutputStream()));
        try {
            outStream.write(ocspRequest.getEncoded());
        } finally {
            outStream.close();
        }
    }

    private static void verifyResponse(OCSPResp response) throws Exception {
        final int responseStatus = response.getStatus();
        if (responseStatus == OCSPResponseStatus.SUCCESSFUL) {
            return;
        }

        if (responseStatus == OCSPResponseStatus.SIG_REQUIRED) {
            throw new OCSPException(
                    "OCSP responder requires request to be signed");
        }

        throw new OCSPException(
                "Invalid OCSP response status: " + responseStatus);
    }

    private static HttpURLConnection createConnection(String destination)
            throws IOException {
        return createConnection(new URL(destination));
    }

    private static HttpURLConnection createConnection(URL url)
            throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestProperty("Content-Type", MimeTypes.OCSP_REQUEST);
        connection.setRequestProperty("Accept", MimeTypes.OCSP_RESPONSE);
        connection.setDoOutput(true);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.connect();
        return connection;
    }

    private static OCSPReq createRequest(X509Certificate subjectCert,
            X509Certificate issuerCert, PrivateKey signerKey,
            X509Certificate signerCert) throws Exception {
        OCSPReqBuilder requestBuilder = new OCSPReqBuilder();

        CertificateID id = CryptoUtils.createCertId(subjectCert, issuerCert);
        requestBuilder.addRequest(id);

        if (signerKey != null && signerCert != null) {
            X509CertificateHolder signerCertHolder =
                    new X509CertificateHolder(signerCert.getEncoded());
            ContentSigner contentSigner =
                    CryptoUtils.createDefaultContentSigner(signerKey);

            LOG.debug("Creating signed OCSP request for certificate \""
                    + subjectCert.getSubjectX500Principal().getName()
                    + "\" (signed by " + signerCertHolder.getSubject() + ")");

            // needs to be set when generating signed requests
            requestBuilder.setRequestorName(signerCertHolder.getSubject());

            return requestBuilder.build(contentSigner,
                    new X509CertificateHolder[] { signerCertHolder });
        }

        LOG.debug("Creating unsigned OCSP request for certificate \""
                + subjectCert.getSubjectX500Principal().getName() + "\"");
        return requestBuilder.build();
    }

    private static OCSPResp parseResponse(byte[] data) throws OCSPException {
        try {
            return new OCSPResp(data);
        } catch (IOException e) {
            throw new OCSPException("Failed to parse OCSP response", e);
        }
    }

}
