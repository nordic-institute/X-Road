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

import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.MimeTypes;
import ee.ria.xroad.common.util.MimeUtils;

import lombok.SneakyThrows;
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

/**
 * OCSP client downloads OCSP responses for specified certificates using responders defined in the Global Configuration.
 */
@Slf4j
final class OcspClient {

    // TODO make it configurable
    private static final int CONNECT_TIMEOUT_MS = 20000;
    private static final int READ_TIMEOUT_MS = 60000;

    // TODO make it configurable
    private static final String DIGEST_ALGORITHM_ID = CryptoUtils.SHA512_ID;
    private static final String SIGN_MECHANISM_NAME = CryptoUtils.CKM_RSA_PKCS_NAME;

    private OcspClient() {
    }

    static OCSPResp queryCertStatus(X509Certificate subject) throws Exception {
        X509Certificate issuer = GlobalConf.getCaCert(GlobalConf.getInstanceIdentifier(), subject);

        PrivateKey signerKey = getOcspRequestKey(subject);
        X509Certificate signer = getOcspSignerCert();
        String signAlgoId = getSignAlgorithmId();

        return fetchResponse(subject, issuer, signerKey, signer, signAlgoId);
    }

    @SneakyThrows
    static String getSignAlgorithmId() {
        return CryptoUtils.getSignatureAlgorithmId(DIGEST_ALGORITHM_ID, SIGN_MECHANISM_NAME);
    }

    static PrivateKey getOcspRequestKey(X509Certificate subject) {
        // FUTURE task #8162. If key is used, the appropriate SIGN_MECHANISM_NAME must be used!
        return null;
    }

    static X509Certificate getOcspSignerCert() {
        return null; // FUTURE task #8162.
    }

    static OCSPResp fetchResponse(X509Certificate subject, X509Certificate issuer, PrivateKey signerKey,
            X509Certificate signer, String signAlgoId) throws Exception {
        List<String> responderURIs = GlobalConf.getOcspResponderAddresses(subject);

        log.trace("responder URIs: {}", responderURIs);

        if (responderURIs.isEmpty()) {
            throw new ConnectException("No OCSP responder URIs available");
        }

        for (String responderURI : responderURIs) {
            try {
                log.trace("fetch response from: {}", responderURI);

                return fetchResponse(responderURI, subject, issuer, signerKey, signer, signAlgoId);
            } catch (Exception e) {
                log.error("Unable to fetch response from responder at {}", responderURI, e);
            }
        }

        throw new OCSPException("Unable to get valid OCSP response from any responders");
    }

    static OCSPResp fetchResponse(String responderURI, X509Certificate subject, X509Certificate issuer,
            PrivateKey signerKey, X509Certificate signer, String signAlgoId) throws Exception {
        HttpURLConnection connection = createConnection(responderURI);

        OCSPReq ocspRequest = createRequest(subject, issuer, signerKey, signer, signAlgoId);

        log.debug("Fetching certificate '{}' status from responder: {}", subject.getIssuerX500Principal(),
                connection.getURL());

        sendRequest(connection, ocspRequest);
        verifyResponseCode(connection);

        byte[] responseData = getResponseData(connection);
        OCSPResp response = parseResponse(responseData);

        verifyResponse(response);

        return response;
    }

    private static byte[] getResponseData(HttpURLConnection connection) throws IOException {
        byte[] responseData = IOUtils.toByteArray((InputStream) connection.getContent());

        if (responseData == null || responseData.length == 0) {
            throw new IOException("No response from responder");
        }

        return responseData;
    }

    private static void verifyResponseCode(HttpURLConnection connection) throws IOException {
        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
            throw new IOException("Invalid http response code from responder: " + connection.getResponseCode());
        }
    }

    private static void sendRequest(HttpURLConnection connection, OCSPReq ocspRequest) throws IOException {
        try (DataOutputStream outStream = new DataOutputStream(
                new BufferedOutputStream(connection.getOutputStream()))) {
            outStream.write(ocspRequest.getEncoded());
        }
    }

    private static void verifyResponse(OCSPResp response) throws Exception {
        int responseStatus = response.getStatus();

        switch (responseStatus) {
            case OCSPResponseStatus.SUCCESSFUL:
                return;
            case OCSPResponseStatus.MALFORMED_REQUEST:
                throw new OCSPException("OCSP responder received malformed request");
            case OCSPResponseStatus.INTERNAL_ERROR:
                throw new OCSPException("OCSP responder encountered an internal error");
            case OCSPResponseStatus.TRY_LATER:
                throw new OCSPException("OCSP responder unable to process request, try again later");
            case OCSPResponseStatus.SIG_REQUIRED:
                throw new OCSPException("OCSP responder requires request to be signed");
            case OCSPResponseStatus.UNAUTHORIZED:
                throw new OCSPException("OCSP responder did not authorize the request");
            default:
                throw new OCSPException("Invalid OCSP response status: " + responseStatus);
        }
    }

    private static HttpURLConnection createConnection(String destination) throws IOException {
        return createConnection(new URL(destination));
    }

    private static HttpURLConnection createConnection(URL url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestProperty(MimeUtils.HEADER_CONTENT_TYPE, MimeTypes.OCSP_REQUEST);
        connection.setRequestProperty("Accept", MimeTypes.OCSP_RESPONSE);
        connection.setDoOutput(true);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.connect();

        return connection;
    }

    private static OCSPReq createRequest(X509Certificate subjectCert, X509Certificate issuerCert, PrivateKey signerKey,
            X509Certificate signerCert, String signAlgoId) throws Exception {
        OCSPReqBuilder requestBuilder = new OCSPReqBuilder();

        CertificateID id = CryptoUtils.createCertId(subjectCert, issuerCert);
        requestBuilder.addRequest(id);

        if (signerKey != null && signerCert != null) {
            X509CertificateHolder signerCertHolder = new X509CertificateHolder(signerCert.getEncoded());
            ContentSigner contentSigner = CryptoUtils.createContentSigner(signAlgoId, signerKey);

            log.trace("Creating signed OCSP request for certificate '{}' (signed by {})",
                    subjectCert.getSubjectX500Principal(), signerCertHolder.getSubject());

            // needs to be set when generating signed requests
            requestBuilder.setRequestorName(signerCertHolder.getSubject());

            return requestBuilder.build(contentSigner, new X509CertificateHolder[] {signerCertHolder});
        }

        log.trace("Creating unsigned OCSP request for certificate '{}'", subjectCert.getSubjectX500Principal());

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
