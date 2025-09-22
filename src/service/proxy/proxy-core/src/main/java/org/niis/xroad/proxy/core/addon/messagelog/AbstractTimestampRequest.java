/*
 * The MIT License
 *
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
package org.niis.xroad.proxy.core.addon.messagelog;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.messagelog.MessageLogProperties;

import jakarta.xml.bind.JAXBException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.xml.security.signature.XMLSignatureException;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.tsp.TSPException;
import org.bouncycastle.tsp.TimeStampRequest;
import org.bouncycastle.tsp.TimeStampRequestGenerator;
import org.bouncycastle.tsp.TimeStampResponse;
import org.bouncycastle.tsp.TimeStampToken;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.impl.signature.TimestampVerifier;

import javax.xml.transform.TransformerException;

import java.io.IOException;
import java.io.InputStream;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.List;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.crypto.Digests.calculateDigest;
import static ee.ria.xroad.common.crypto.Digests.getAlgorithmIdentifier;
import static org.niis.xroad.proxy.core.addon.messagelog.TimestamperUtil.addSignerCertificate;
import static org.niis.xroad.proxy.core.addon.messagelog.TimestamperUtil.getTimestampResponse;

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
        for (String url : tspUrls) {
            try {
                log.debug("Sending time-stamp request to {}", url);

                TsRequest req = new TsRequest(TimestamperUtil.makeTsRequest(tsRequest, url), url);

                TimeStampResponse tsResponse = getTimestampResponse(req.getInputStream());
                log.info("tsresponse {}", tsResponse);

                verify(tsRequest, tsResponse);

                return result(tsResponse, url);

            } catch (Exception ex) {
                log.error("Failed to get time stamp from " + url, ex);
            }
        }

        // All the URLs failed. Throw exception.
        throw XrdRuntimeException.systemInternalError("Failed to get time stamp from any time-stamping providers");
    }

    private TimeStampRequest createTimestampRequest(byte[] data) throws IOException {
        TimeStampRequestGenerator reqgen = new TimeStampRequestGenerator();

        var tsaHashAlg = MessageLogProperties.getHashAlg();

        log.trace("Creating time-stamp request (algorithm: {})", tsaHashAlg);

        byte[] digest = calculateDigest(tsaHashAlg, data);

        ASN1ObjectIdentifier algorithm =
                getAlgorithmIdentifier(tsaHashAlg).getAlgorithm();

        return reqgen.generate(algorithm, digest);
    }

    protected byte[] getTimestampDer(TimeStampResponse tsResponse)
            throws CertificateEncodingException, IOException, TSPException, CMSException {
        X509Certificate signerCertificate =
                TimestampVerifier.getSignerCertificate(
                        tsResponse.getTimeStampToken(),
                        globalConfProvider.getTspCertificates());
        if (signerCertificate == null) {
            throw new CodedException(X_INTERNAL_ERROR,
                    "Could not find signer certificate");
        }

        TimeStampToken token =
                addSignerCertificate(tsResponse, signerCertificate);
        return token.getEncoded();
    }

    protected void verify(TimeStampRequest request, TimeStampResponse response)
            throws TSPException, CertificateEncodingException, IOException, OperatorCreationException, CMSException {
        response.validate(request);

        TimeStampToken token = response.getTimeStampToken();
        TimestampVerifier.verify(token, globalConfProvider.getTspCertificates());
    }
}
