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
package org.niis.xroad.proxy.core.messagelog;

import ee.ria.xroad.common.messagelog.MessageLogProperties;

import jakarta.xml.bind.JAXBException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.xml.security.signature.XMLSignatureException;
import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.tsp.TSPException;
import org.bouncycastle.tsp.TimeStampRequest;
import org.bouncycastle.tsp.TimeStampRequestGenerator;
import org.bouncycastle.tsp.TimeStampResponse;
import org.bouncycastle.tsp.TimeStampToken;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.impl.signature.TimestampVerifier;

import javax.xml.transform.TransformerException;

import java.io.IOException;
import java.io.InputStream;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ee.ria.xroad.common.crypto.Digests.calculateDigest;
import static ee.ria.xroad.common.crypto.Digests.getAlgorithmIdentifier;
import static org.niis.xroad.common.core.exception.ErrorCode.ADDING_SIGNATURE_TO_TS_TOKEN_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.CALCULATING_MESSAGE_DIGEST_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.TIMESTAMPING_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.TIMESTAMP_RESPONSE_VALIDATION_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.TIMESTAMP_TOKEN_ENCODING_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.TSP_CERTIFICATE_NOT_FOUND;
import static org.niis.xroad.proxy.core.messagelog.TimestamperUtil.addSignerCertificate;
import static org.niis.xroad.proxy.core.messagelog.TimestamperUtil.getTimestampResponse;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractTimestampRequest {
    protected final GlobalConfProvider globalConfProvider;
    protected final Long[] logRecords;

    abstract byte[] getRequestData() throws XMLSignatureException, JAXBException, IOException;

    abstract Timestamper.TimestampResult result(TimeStampResponse tsResponse, String url)
            throws CertificateEncodingException, IOException, TSPException, CMSException, TransformerException;

    Timestamper.TimestampResult execute(List<String> tspUrls) throws JAXBException, XMLSignatureException, IOException {
        TimeStampRequest tsRequest = createTimestampRequest(getRequestData());

        return makeTsRequest(tsRequest, tspUrls);
    }

    @Getter
    public static class TsRequest {
        private final InputStream inputStream;
        private final String url;

        TsRequest(final InputStream inputStream, final String url) {
            this.inputStream = inputStream;
            this.url = url;
        }
    }

    protected Timestamper.TimestampResult makeTsRequest(TimeStampRequest tsRequest, List<String> tspUrls)  {
        log.debug("tspUrls: {}", tspUrls);
        Map<String, Exception> errorsByUrl = new HashMap<>();
        for (String url : tspUrls) {
            try {
                log.debug("Sending time-stamp request to {}", url);

                TsRequest req = new TsRequest(getTsRequestInputStream(tsRequest, url), url);

                TimeStampResponse tsResponse = getTimestampResponse(req.getInputStream());
                log.info("tsresponse {}", tsResponse);

                verify(tsRequest, tsResponse);

                Timestamper.TimestampResult result = result(tsResponse, url);
                result.setErrorsByUrl(errorsByUrl);
                return result;

            } catch (Exception ex) {
                log.error("Failed to get time stamp from " + url, ex);
                errorsByUrl.put(url, ex);
            }
        }

        Timestamper.TimestampFailed timestampFailed = new Timestamper.TimestampFailed(logRecords,
                XrdRuntimeException.systemException(TIMESTAMPING_FAILED)
                        .details("Failed to get time stamp from any time-stamping providers")
                        .build()
        );
        timestampFailed.setErrorsByUrl(errorsByUrl);
        return timestampFailed;
    }

    private static InputStream getTsRequestInputStream(TimeStampRequest tsRequest, String url) {
        try {
            return TimestamperUtil.makeTsRequest(tsRequest, url);
        } catch (IOException e) {
            throw XrdRuntimeException.systemException(ErrorCode.TIMESTAMP_PROVIDER_CONNECTION_FAILED)
                    .details("Could not get response from TSP")
                    .build();
        }
    }

    private TimeStampRequest createTimestampRequest(byte[] data) {
        TimeStampRequestGenerator reqgen = new TimeStampRequestGenerator();

        var tsaHashAlg = MessageLogProperties.getHashAlg();

        log.trace("Creating time-stamp request (algorithm: {})", tsaHashAlg);

        byte[] digest;
        try {
            digest = calculateDigest(tsaHashAlg, data);
        } catch (IOException e) {
            throw XrdRuntimeException.systemException(CALCULATING_MESSAGE_DIGEST_FAILED)
                    .cause(e)
                    .build();
        }

        ASN1ObjectIdentifier algorithm = getAlgorithmIdentifier(tsaHashAlg).getAlgorithm();

        return reqgen.generate(algorithm, digest);
    }

    protected byte[] getTimestampDer(TimeStampResponse tsResponse) {
        X509Certificate signerCertificate = getSignerCertificate(tsResponse);
        TimeStampToken token = getTimeStampToken(tsResponse, signerCertificate);
        try {
            return token.getEncoded(ASN1Encoding.DER);
        } catch (IOException e) {
            throw XrdRuntimeException.systemException(TIMESTAMP_TOKEN_ENCODING_FAILED)
                    .details("Timestamp token der encoding failed")
                    .cause(e)
                    .build();
        }
    }

    private X509Certificate getSignerCertificate(TimeStampResponse tsResponse) {
        X509Certificate signerCertificate;
        signerCertificate = TimestampVerifier.getSignerCertificate(
                tsResponse.getTimeStampToken(),
                globalConfProvider.getTspCertificates());
        if (signerCertificate == null) {
            throw XrdRuntimeException.systemException(TSP_CERTIFICATE_NOT_FOUND)
                    .details("Could not find signer certificate")
                    .build();
        }
        return signerCertificate;
    }

    private static TimeStampToken getTimeStampToken(TimeStampResponse tsResponse, X509Certificate signerCertificate) {
        try {
            return addSignerCertificate(tsResponse, signerCertificate);
        } catch (CMSException | TSPException | CertificateEncodingException | IOException e) {
            throw XrdRuntimeException.systemException(ADDING_SIGNATURE_TO_TS_TOKEN_FAILED)
                    .details("Adding signer certificate to timestamp token failed")
                    .cause(e)
                    .build();
        }
    }

    protected void verify(TimeStampRequest request, TimeStampResponse response) {
        try {
            response.validate(request);
        } catch (TSPException e) {
            throw XrdRuntimeException.systemException(TIMESTAMP_RESPONSE_VALIDATION_FAILED)
                    .details("Timestamp response validation against the request failed")
                    .metadataItems(e.getMessage())
                    .cause(e)
                    .build();
        }

        TimeStampToken token = response.getTimeStampToken();
        TimestampVerifier.verify(token, globalConfProvider.getTspCertificates());
    }
}
