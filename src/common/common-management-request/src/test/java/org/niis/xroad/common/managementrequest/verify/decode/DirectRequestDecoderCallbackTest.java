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
package org.niis.xroad.common.managementrequest.verify.decode;

import ee.ria.xroad.common.OcspTestUtils;
import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.certificateprofile.SignCertificateProfileInfo;
import ee.ria.xroad.common.certificateprofile.impl.EjbcaSignCertificateProfileInfo;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.request.AuthCertRegRequestType;
import ee.ria.xroad.common.request.SystemDidRegRequestType;

import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.managemenetrequest.test.TestAuthRegTypeRequest;
import org.niis.xroad.common.managemenetrequest.test.TestManagementRequestBuilder;
import org.niis.xroad.common.managementrequest.model.ManagementRequestType;
import org.niis.xroad.common.managementrequest.verify.ManagementRequestVerifier;
import org.niis.xroad.globalconf.impl.ocsp.OcspVerifierFactory;
import org.niis.xroad.test.globalconf.EmptyGlobalConf;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class DirectRequestDecoderCallbackTest {
    private static final String SYSTEM_DID = "did:web:ss1.example.org:v1:system";
    private static final String DSP_BASE_URL = "https://ss1.example.org:8443/dsp";
    private static final String ADDRESS = "ss1.example.org";

    private static KeyPair authKeyPair;
    private static KeyPair ownerKeyPair;

    private final TestGlobalConf globalConf = new TestGlobalConf();
    private final ManagementRequestVerifier verifier = new ManagementRequestVerifier(globalConf, new OcspVerifierFactory());
    private final SecurityServerId.Conf serverId = SecurityServerId.Conf.create("DEV", "COM", "222", "SS1");

    @BeforeAll
    static void generateKeys() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        authKeyPair = generator.generateKeyPair();
        ownerKeyPair = generator.generateKeyPair();
    }

    @Test
    void validSignedSystemDidRegPassesVerifierWithTypedFieldsIntact() throws Exception {
        var authCert = TestCertUtil.generateAuthCert(authKeyPair.getPublic());
        var request = builder().buildSystemDidRegRequest(serverId, SYSTEM_DID, DSP_BASE_URL);

        var result = verify(authCert, request);

        assertThat(result.requestType()).isEqualTo(ManagementRequestType.SYSTEM_DID_REGISTRATION_REQUEST);
        assertThat(result.callback().attachedAuthCert()).contains(authCert);
        var typed = result.getRequest(SystemDidRegRequestType.class).orElseThrow();
        assertThat(typed.getServer()).isEqualTo(serverId);
        assertThat(typed.getSystemDid()).isEqualTo(SYSTEM_DID);
        assertThat(typed.getDspBaseUrl()).isEqualTo(DSP_BASE_URL);
    }

    @Test
    void validSignedAuthCertRegPassesVerifierWithTypedFieldsIntact() throws Exception {
        var authCert = TestCertUtil.generateAuthCert(authKeyPair.getPublic());
        var request = builder().buildAuthCertRegRequest(serverId, ADDRESS, authCert);

        var result = verify(authCert, request);

        assertThat(result.requestType()).isEqualTo(ManagementRequestType.AUTH_CERT_REGISTRATION_REQUEST);
        var typed = result.getRequest(AuthCertRegRequestType.class).orElseThrow();
        assertThat(typed.getServer()).isEqualTo(serverId);
        assertThat(typed.getAddress()).isEqualTo(ADDRESS);
        assertThat(typed.getAuthCert()).isEqualTo(authCert);
    }

    @Test
    void systemDidRegSignedByWrongAuthKeyIsRejected() throws Exception {
        var authCert = TestCertUtil.generateAuthCert(authKeyPair.getPublic());
        var request = builder().buildSystemDidRegRequest(serverId, SYSTEM_DID, DSP_BASE_URL);
        var payload = envelope(authCert, request, ownerKeyPair.getPrivate()).createPayload();

        assertThatExceptionOfType(Exception.class)
                .isThrownBy(() -> verifier.readRequest(payload.getContentType(), payload.getPayloadAsStream()));
    }

    private TestManagementRequestBuilder builder() {
        return new TestManagementRequestBuilder(serverId.getOwner(), globalConf.getManagementRequestService());
    }

    private ManagementRequestVerifier.Result verify(byte[] authCert, SoapMessageImpl request)
            throws Exception {
        var payload = envelope(authCert, request, authKeyPair.getPrivate()).createPayload();
        return verifier.readRequest(payload.getContentType(), payload.getPayloadAsStream());
    }

    private TestAuthRegTypeRequest envelope(byte[] authCert, SoapMessageImpl request,
                                            PrivateKey authSigningKey) throws Exception {
        var ownerCert = TestCertUtil.generateSignCert(ownerKeyPair.getPublic(), serverId.getOwner());
        var ownerOcsp = OcspTestUtils.createOCSPResponse(ownerCert,
                TestCertUtil.getCaCert(),
                TestCertUtil.getOcspSigner().certChain[0],
                TestCertUtil.getOcspSigner().key,
                CertificateStatus.GOOD);
        return new TestAuthRegTypeRequest(authCert, ownerCert.getEncoded(), ownerOcsp.getEncoded(), request,
                authSigningKey, ownerKeyPair.getPrivate());
    }

    private static final class TestGlobalConf extends EmptyGlobalConf {
        private final ClientId.Conf managementService = ClientId.Conf.create("DEV", "COM", "MGMT", "MANAGEMENT");

        @Override
        public String getInstanceIdentifier() {
            return "DEV";
        }

        @Override
        public int getOcspFreshnessSeconds() {
            return Integer.MAX_VALUE / 2;
        }

        @Override
        public X509Certificate getCaCert(String instanceIdentifier, X509Certificate orgCert) {
            var ca = TestCertUtil.getCaCert();
            if (getInstanceIdentifier().equals(instanceIdentifier)
                    && ca.getSubjectX500Principal().equals(orgCert.getIssuerX500Principal())) {
                return ca;
            }
            throw XrdRuntimeException.systemInternalError("Certificate is not issued by approved certification service provider.");
        }

        @Override
        public SignCertificateProfileInfo getSignCertificateProfileInfo(
                SignCertificateProfileInfo.Parameters parameters, X509Certificate cert) {
            return new EjbcaSignCertificateProfileInfo(parameters);
        }

        @Override
        public ClientId.Conf getSubjectName(SignCertificateProfileInfo.Parameters parameters, X509Certificate cert) {
            return getSignCertificateProfileInfo(parameters, cert).getSubjectIdentifier(cert);
        }

        @Override
        public ClientId getManagementRequestService() {
            return managementService;
        }
    }
}
