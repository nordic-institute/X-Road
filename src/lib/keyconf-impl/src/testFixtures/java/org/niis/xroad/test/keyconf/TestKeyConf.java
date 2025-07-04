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
package org.niis.xroad.test.keyconf;

import ee.ria.xroad.common.ErrorCodes;
import ee.ria.xroad.common.OcspTestUtils;
import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.TestCertUtil.PKCS12;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.util.TimeUtils;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.impl.cert.CertChainFactory;
import org.niis.xroad.globalconf.impl.ocsp.OcspVerifier;
import org.niis.xroad.globalconf.impl.ocsp.OcspVerifierOptions;
import org.niis.xroad.keyconf.SigningInfo;
import org.niis.xroad.keyconf.dto.AuthKey;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static ee.ria.xroad.common.util.CryptoUtils.calculateCertHexHash;

/**
 * Test keyconf implementation.
 */
@Slf4j
@RequiredArgsConstructor
public class TestKeyConf extends EmptyKeyConf {
    private final GlobalConfProvider globalConfProvider;

    private final Map<String, OCSPResp> ocspResponses = new HashMap<>();
    @Setter
    private PKCS12 authKey = TestCertUtil.getConsumer();

    @Override
    public SigningInfo getSigningInfo(ClientId clientId) {
        return super.getSigningInfo(clientId);
    }

    @Override
    @SneakyThrows
    public AuthKey getAuthKey() {
        return new AuthKey(CertChainFactory
                .create("EE",
                        globalConfProvider.getCaCert("EE", authKey.certChain[0]),
                        authKey.certChain[0], null), authKey.key);
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
                Date thisUpdate = Date.from(TimeUtils.now().plus(1, ChronoUnit.DAYS));
                OCSPResp resp = OcspTestUtils.createOCSPResponse(cert,
                        globalConfProvider.getCaCert("EE", cert), getOcspSignerCert(),
                        getOcspRequestKey(), CertificateStatus.GOOD,
                        thisUpdate, null);
                OcspVerifier verifier = new OcspVerifier(globalConfProvider,
                        new OcspVerifierOptions(true));
                verifier.verifyValidityAndStatus(resp, cert,
                        globalConfProvider.getCaCert("EE", cert));
                ocspResponses.put(certHash, resp);
            } catch (Exception e) {
                log.error("Error when creating OCSP response", e);
            }
        }

        return ocspResponses.get(certHash);
    }

    private X509Certificate getOcspSignerCert() {
        return TestCertUtil.getOcspSigner().certChain[0];
    }

    private PrivateKey getOcspRequestKey() {
        return TestCertUtil.getOcspSigner().key;
    }
}
