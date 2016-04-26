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
package ee.ria.xroad.proxy.conf;

import ee.ria.xroad.common.cert.CertChain;
import ee.ria.xroad.common.cert.CertChainVerifier;
import ee.ria.xroad.common.conf.globalconf.AuthKey;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cert.ocsp.OCSPResp;

import java.security.PrivateKey;
import java.util.Date;
import java.util.List;

@Slf4j
@Getter
@RequiredArgsConstructor
class CachedAuthKeyInfoImpl extends AbstractCachedInfo {

    private final PrivateKey pkey;
    private final CertChain certChain;
    private final List<OCSPResp> ocspResponses;

    AuthKey getAuthKey() {
        return new AuthKey(certChain, pkey);
    }

    @Override
    boolean verifyValidity(Date atDate) {
        try {
            log.trace("CachedAuthKeyInfoImpl.verifyValidity date: {}", atDate);
            CertChainVerifier verifier = new CertChainVerifier(certChain);
            verifier.verify(ocspResponses, atDate);
            return true;
        } catch (Exception e) {
            log.warn("Cached authentication info failed verification: {}",
                    e);
            return false;
        }
    }

}
