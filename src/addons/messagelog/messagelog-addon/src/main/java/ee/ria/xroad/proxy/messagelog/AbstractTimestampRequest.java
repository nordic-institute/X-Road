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
package ee.ria.xroad.proxy.messagelog;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.messagelog.MessageLogProperties;
import ee.ria.xroad.common.signature.TimestampVerifier;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.tsp.TimeStampRequest;
import org.bouncycastle.tsp.TimeStampRequestGenerator;
import org.bouncycastle.tsp.TimeStampResponse;
import org.bouncycastle.tsp.TimeStampToken;

import java.io.InputStream;
import java.security.cert.X509Certificate;
import java.util.List;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.util.CryptoUtils.calculateDigest;
import static ee.ria.xroad.common.util.CryptoUtils.getAlgorithmIdentifier;
import static ee.ria.xroad.proxy.messagelog.TimestamperUtil.addSignerCertificate;
import static ee.ria.xroad.proxy.messagelog.TimestamperUtil.getTimestampResponse;

@Slf4j
@RequiredArgsConstructor
abstract class AbstractTimestampRequest {

    protected final Long[] logRecords;

    abstract byte[] getRequestData() throws Exception;

    abstract Timestamper.TimestampResult result(TimeStampResponse tsResponse, String url) throws Exception;

    Timestamper.TimestampResult execute(List<String> tspUrls) throws Exception {
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

    protected Timestamper.TimestampResult makeTsRequest(TimeStampRequest tsRequest,
                                      List<String> tspUrls) throws Exception {
        log.debug("tspUrls: {}", tspUrls);
        for (String url: tspUrls) {
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
        throw new RuntimeException(
                "Failed to get time stamp from any time-stamping providers");
    }

    private TimeStampRequest createTimestampRequest(byte[] data)
            throws Exception {
        TimeStampRequestGenerator reqgen = new TimeStampRequestGenerator();

        String tsaHashAlg = MessageLogProperties.getHashAlg();

        log.trace("Creating time-stamp request (algorithm: {})", tsaHashAlg);

        byte[] digest = calculateDigest(tsaHashAlg, data);

        ASN1ObjectIdentifier algorithm =
                getAlgorithmIdentifier(tsaHashAlg).getAlgorithm();

        return reqgen.generate(algorithm, digest);
    }

    protected byte[] getTimestampDer(TimeStampResponse tsResponse)
            throws Exception {
        X509Certificate signerCertificate =
                TimestampVerifier.getSignerCertificate(
                        tsResponse.getTimeStampToken(),
                        GlobalConf.getTspCertificates());
        if (signerCertificate == null) {
            throw new CodedException(X_INTERNAL_ERROR,
                    "Could not find signer certificate");
        }

        TimeStampToken token =
                addSignerCertificate(tsResponse, signerCertificate);
        return token.getEncoded();
    }

    protected void verify(TimeStampRequest request,
                          TimeStampResponse response) throws Exception {
        response.validate(request);

        TimeStampToken token = response.getTimeStampToken();
        TimestampVerifier.verify(token, GlobalConf.getTspCertificates());
    }
}
