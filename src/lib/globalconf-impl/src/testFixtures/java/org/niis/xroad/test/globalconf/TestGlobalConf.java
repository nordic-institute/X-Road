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
package org.niis.xroad.test.globalconf;

import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.certificateprofile.AuthCertificateProfileInfo;
import ee.ria.xroad.common.certificateprofile.SignCertificateProfileInfo;
import ee.ria.xroad.common.certificateprofile.impl.EjbcaSignCertificateProfileInfo;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import org.niis.xroad.globalconf.cert.CertChain;
import org.niis.xroad.globalconf.impl.cert.CertChainFactory;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Test globalconf implementation.
 */
public class TestGlobalConf extends EmptyGlobalConf {

    @Override
    public String getInstanceIdentifier() {
        return "EE";
    }

    @Override
    public Collection<String> getProviderAddress(ClientId provider) {
        return Collections.singleton("127.0.0.1");
    }

    @Override
    public List<X509Certificate> getOcspResponderCertificates() {
        try {
            return Arrays.asList(TestCertUtil.getOcspSigner().certChain[0]);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @Override
    public X509Certificate[] getAuthTrustChain() {
        return new X509Certificate[]{TestCertUtil.getCaCert()};
    }

    @Override
    public X509Certificate getCaCert(String instanceIdentifier, X509Certificate org) throws Exception {
        return TestCertUtil.getCaCert();
    }

    @Override
    public CertChain getCertChain(String instanceIdentifier, X509Certificate subject) throws Exception {
        return new CertChainFactory(this).create(instanceIdentifier, subject, null);
    }

    @Override
    public List<X509Certificate> getTspCertificates() throws CertificateException {
        return Collections.singletonList(TestCertUtil.getTspCert());
    }

    @Override
    public boolean authCertMatchesMember(X509Certificate cert, ClientId memberId) throws Exception {
        return true;
    }

    @Override
    public SignCertificateProfileInfo getSignCertificateProfileInfo(SignCertificateProfileInfo.Parameters parameters,
                                                                    X509Certificate cert) throws Exception {
        return new EjbcaSignCertificateProfileInfo(parameters) {
            @Override
            public ClientId.Conf getSubjectIdentifier(X509Certificate certificate) {
                // Currently the test certificate contains invalid member class
                // so we just fix the member class here instead of regenerating
                // new certificate.
                ClientId id = super.getSubjectIdentifier(certificate);
                return ClientId.Conf.create(
                        id.getXRoadInstance(),
                        "BUSINESS",
                        id.getMemberCode()
                );
            }
        };
    }

    @Override
    public AuthCertificateProfileInfo getAuthCertificateProfileInfo(AuthCertificateProfileInfo.Parameters parameters,
                                                                    X509Certificate cert) throws Exception {
        return null;
    }

    @Override
    public SecurityServerId.Conf getServerId(X509Certificate cert) throws Exception {
        // For SSL connections AuthTrustManager checks that client certificate
        // belongs to some X-Road member
        return SecurityServerId.Conf.create("FI", "COM", "1111", "SS1");
    }

    @Override
    public ClientId.Conf getSubjectName(SignCertificateProfileInfo.Parameters parameters, X509Certificate cert) throws Exception {
        return getSignCertificateProfileInfo(parameters, cert)
                .getSubjectIdentifier(cert);
    }
}
