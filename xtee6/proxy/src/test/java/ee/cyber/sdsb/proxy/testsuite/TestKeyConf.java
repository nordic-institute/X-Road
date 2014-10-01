package ee.cyber.sdsb.proxy.testsuite;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.joda.time.DateTime;

import ee.cyber.sdsb.common.ErrorCodes;
import ee.cyber.sdsb.common.OcspTestUtils;
import ee.cyber.sdsb.common.TestCertUtil;
import ee.cyber.sdsb.common.TestCertUtil.PKCS12;
import ee.cyber.sdsb.common.cert.CertChain;
import ee.cyber.sdsb.common.conf.AuthKey;
import ee.cyber.sdsb.common.conf.GlobalConf;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.ocsp.OcspVerifier;
import ee.cyber.sdsb.proxy.conf.SigningCtx;
import ee.cyber.sdsb.proxy.util.TestUtil;

import static ee.cyber.sdsb.common.util.CryptoUtils.calculateCertHexHash;

@Slf4j
public class TestKeyConf extends EmptyKeyConf {

    Map<String, SigningCtx> signingCtx = new HashMap<>();
    Map<String, OCSPResp> ocspResponses = new HashMap<>();

    @Override
    public SigningCtx getSigningCtx(ClientId clientId) {
        String orgName = clientId.getMemberCode();
        SigningCtx ctx = currentTestCase().getSigningCtx(orgName);
        if (ctx != null) {
            return ctx;
        }

        if (!signingCtx.containsKey(orgName)) {
            signingCtx.put(orgName, TestUtil.getSigningCtx(orgName));
        }

        return signingCtx.get(orgName);
    }

    @Override
    public AuthKey getAuthKey() {
        PKCS12 consumer = TestCertUtil.getConsumer();
        return new AuthKey(CertChain.create(consumer.cert, null),
                consumer.key);
    }

    @Override
    public OCSPResp getOcspResponse(String certHash) {
        return ocspResponses.get(certHash);
    }

    @Override
    public OCSPResp getOcspResponse(X509Certificate cert) {
        String certHash;
        try {
            certHash = calculateCertHexHash(cert);
        } catch (Exception e) {
            throw ErrorCodes.translateException(e);
        }

        if (!ocspResponses.containsKey(certHash)) {
            try {
                Date thisUpdate = new DateTime().plusDays(1).toDate();
                OCSPResp resp = OcspTestUtils.createOCSPResponse(cert,
                        GlobalConf.getCaCert(cert), getOcspSignerCert(),
                        getOcspRequestKey(), CertificateStatus.GOOD,
                        thisUpdate, null);
                OcspVerifier.verifyValidityAndStatus(resp, cert,
                        GlobalConf.getCaCert(cert));
                ocspResponses.put(certHash, resp);
            } catch (Exception e) {
                log.error("Error when creating OCSP response", e);
            }
        }

        return ocspResponses.get(certHash);
    }

    private static MessageTestCase currentTestCase() {
        return ProxyTestSuite.currentTestCase;
    }

    private X509Certificate getOcspSignerCert() throws Exception {
        return TestCertUtil.getOcspSigner().cert;
    }

    private PrivateKey getOcspRequestKey() throws Exception {
        return TestCertUtil.getOcspSigner().key;
    }
}
