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
package org.niis.xroad.edc.sig;

import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.signer.SignerProxy;

import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.spi.x509.CommonTrustedCertificateSource;

import java.util.Map;
import java.util.function.Supplier;

public interface XrdSignatureCreator {
    String sign(SignerProxy.MemberSigningInfoDto signingInfo, byte[] messageBody, Map<String, String> messageHeaders)
            throws XrdSignatureCreationException;

    default String sign(SignerProxy.MemberSigningInfoDto signingInfo, Supplier<byte[]> messageSupplier, Supplier<byte[]> attachmentSupplier)
        throws XrdSignatureCreationException {

        throw new XrdSignatureCreationException("Unsupported operation. Should be implemented in subclass");
    }

    default CommonTrustedCertificateSource getTrustedListsCertificateSource() {
        var trustedCertSource = new CommonTrustedCertificateSource();
        GlobalConf.getAllCaCerts()
                        .forEach(cert -> trustedCertSource.addCertificate(new CertificateToken(cert)));
        GlobalConf.getOcspResponderCertificates()
                        .forEach(cert -> trustedCertSource.addCertificate(new CertificateToken(cert)));
        return trustedCertSource;
    }
}
