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
package ee.ria.xroad.signer.certmanager;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.io.IOUtils;
import org.bouncycastle.asn1.ocsp.OCSPResponseStatus;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.ocsp.CertificateID;
import org.bouncycastle.cert.ocsp.OCSPException;
import org.bouncycastle.cert.ocsp.OCSPReq;
import org.bouncycastle.cert.ocsp.OCSPReqBuilder;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.operator.ContentSigner;

import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.MimeTypes;

/**
 * OCSP client downloads OCSP responses for specified certificates using
 * responders defined in the Global Configuration.
 */
@Slf4j
public final class OcspClient {

    private static final int CONNECT_TIMEOUT_MS = 20000;

    private OcspClient() {
    }

    static OCSPResp queryCertStatus(X509Certificate subject) throws Exception {
        X509Certificate issuer = GlobalConf.getCaCert(
                GlobalConf.getInstanceIdentifier(), subject);

        PrivateKey signerKey = getOcspRequestKey(subject);
        X509Certificate signer = getOcspSignerCert();

        return fetchResponse(subject, issuer, signerKey, signer);
    }

    protected static PrivateKey getOcspRequestKey(X509Certificate subject) {
        return null; // TODO
    }

    protected static X509Certificate getOcspSignerCert() {
        return null; // TODO
    }

    protected static OCSPResp fetchResponse(X509Certificate subject,
            X509Certificate issuer, PrivateKey signerKey,
            X509Certificate signer) throws Exception {
        List<String> responderURIs =
                GlobalConf.getOcspResponderAddresses(subject);
        log.trace("responder URIs: {}", responderURIs);

        if (responderURIs.isEmpty()) {
            throw new ConnectException("No OCSP responder URIs available");
        }

        for (String responderURI : responderURIs) {
            try {
                log.trace("fetch response from: {}", responderURI);
                return fetchResponse(responderURI, subject, issuer, signerKey,
                        signer);
            } catch (Exception e) {
                log.error("Unable to fetch response from responder at "
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

        log.debug("Fetching certificate '{}' status from responder: {}",
                subject.getIssuerX500Principal(), connection.getURL());

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
        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
            throw new IOException("Invalid http response code from responder: "
                    + connection.getResponseCode());
        }
    }

    private static void sendRequest(HttpURLConnection connection,
            OCSPReq ocspRequest) throws IOException {
        try (DataOutputStream outStream = new DataOutputStream(
                new BufferedOutputStream(connection.getOutputStream()))) {
            outStream.write(ocspRequest.getEncoded());
        }
    }

    private static void verifyResponse(OCSPResp response) throws Exception {
        int responseStatus = response.getStatus();
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

            log.trace("Creating signed OCSP request for certificate '{}' "
                    + "(signed by {})",
                    subjectCert.getSubjectX500Principal(),
                    signerCertHolder.getSubject());

            // needs to be set when generating signed requests
            requestBuilder.setRequestorName(signerCertHolder.getSubject());

            return requestBuilder.build(contentSigner,
                    new X509CertificateHolder[] {signerCertHolder});
        }

        log.trace("Creating unsigned OCSP request for certificate '{}'",
                subjectCert.getSubjectX500Principal());
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
