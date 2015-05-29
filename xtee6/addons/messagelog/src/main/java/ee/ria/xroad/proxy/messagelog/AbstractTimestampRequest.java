package ee.ria.xroad.proxy.messagelog;

import java.io.InputStream;
import java.security.cert.X509Certificate;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.tsp.TimeStampRequest;
import org.bouncycastle.tsp.TimeStampRequestGenerator;
import org.bouncycastle.tsp.TimeStampResponse;
import org.bouncycastle.tsp.TimeStampToken;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.messagelog.MessageLogProperties;
import ee.ria.xroad.common.signature.TimestampVerifier;

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

    abstract Object result(TimeStampResponse tsResponse) throws Exception;

    Object execute(List<String> tspUrls) throws Exception {
        TimeStampRequest tsRequest = createTimestampRequest(getRequestData());

        InputStream tsIn = makeTsRequest(tsRequest, tspUrls);
        if (tsIn == null) {
            throw new RuntimeException("Could not get response from TSP");
        }

        TimeStampResponse tsResponse = getTimestampResponse(tsIn);
        verify(tsRequest, tsResponse);

        return result(tsResponse);
    }

    protected InputStream makeTsRequest(TimeStampRequest request,
            List<String> tspUrls) throws Exception {
        for (String url: tspUrls) {
            try {
                log.debug("Sending time-stamp request to {}", url);

                return TimestamperUtil.makeTsRequest(request, url);
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
