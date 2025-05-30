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
package org.niis.xroad.proxy.core.conf;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.signature.SignatureData;

import lombok.RequiredArgsConstructor;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.cert.CertChain;
import org.niis.xroad.keyconf.KeyConfProvider;
import org.niis.xroad.proxy.core.signature.SignatureBuilder;
import org.niis.xroad.proxy.core.signedmessage.SigningKey;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import static ee.ria.xroad.common.ErrorCodes.X_CANNOT_CREATE_SIGNATURE;

/**
 * Encapsulates security-related parameters of a given member,
 * such as currently used signing key and cert.
 */
@RequiredArgsConstructor
public class SigningCtxImpl implements SigningCtx {
    private final GlobalConfProvider globalConfProvider;
    private final KeyConfProvider keyConfProvider;
    /**
     * The subject id of the signer.
     */
    private final ClientId subject;

    /**
     * Encapsulates private key of the signer.
     */
    private final SigningKey key;

    /**
     * The certificate of the signer.
     */
    private final X509Certificate cert;

    @Override
    public SignatureData buildSignature(SignatureBuilder builder) throws Exception {
        List<X509Certificate> extraCerts = getIntermediateCaCerts();
        List<OCSPResp> ocspResponses = getOcspResponses(extraCerts);

        builder.addExtraCertificates(extraCerts);
        builder.addOcspResponses(ocspResponses);
        builder.setSigningCert(cert);

        return builder.build(key, SystemProperties.getProxyMessageSignDigestName());
    }

    private List<OCSPResp> getOcspResponses(List<X509Certificate> certs) throws Exception {
        List<X509Certificate> allCerts = new ArrayList<>(certs.size() + 1);
        allCerts.add(cert);
        allCerts.addAll(certs);

        return keyConfProvider.getAllOcspResponses(allCerts);
    }

    private List<X509Certificate> getIntermediateCaCerts() throws Exception {
        CertChain chain = globalConfProvider.getCertChain(subject.getXRoadInstance(), cert);

        if (chain == null) {
            throw new CodedException(X_CANNOT_CREATE_SIGNATURE, "Got empty certificate chain for certificate %s",
                    cert.getSerialNumber());
        }

        return chain.getAdditionalCerts();
    }
}
