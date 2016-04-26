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
package ee.ria.xroad.proxy.testsuite;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import ee.ria.xroad.common.ocsp.OcspVerifierOptions;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.joda.time.DateTime;

import ee.ria.xroad.common.ErrorCodes;
import ee.ria.xroad.common.OcspTestUtils;
import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.TestCertUtil.PKCS12;
import ee.ria.xroad.common.cert.CertChain;
import ee.ria.xroad.common.conf.globalconf.AuthKey;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.ocsp.OcspVerifier;
import ee.ria.xroad.proxy.conf.SigningCtx;
import ee.ria.xroad.proxy.util.TestUtil;

import static ee.ria.xroad.common.util.CryptoUtils.calculateCertHexHash;

/**
 * Test keyconf implementation.
 */
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
        return new AuthKey(CertChain.create("EE", consumer.cert, null),
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
                        GlobalConf.getCaCert("EE", cert), getOcspSignerCert(),
                        getOcspRequestKey(), CertificateStatus.GOOD,
                        thisUpdate, null);
                OcspVerifier verifier = new OcspVerifier(GlobalConf.getOcspFreshnessSeconds(false),
                        new OcspVerifierOptions(true));
                verifier.verifyValidityAndStatus(resp, cert,
                        GlobalConf.getCaCert("EE", cert));
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
