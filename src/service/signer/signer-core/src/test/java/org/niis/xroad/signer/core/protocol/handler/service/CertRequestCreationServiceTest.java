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
package org.niis.xroad.signer.core.protocol.handler.service;

import ee.ria.xroad.common.crypto.KeyManagers;
import ee.ria.xroad.common.crypto.identifier.SignMechanism;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.signer.api.dto.KeyInfo;
import org.niis.xroad.signer.core.config.SignerProperties;
import org.niis.xroad.signer.core.tokenmanager.token.TokenWorker;
import org.niis.xroad.signer.core.tokenmanager.token.TokenWorkerProvider;
import org.niis.xroad.signer.core.util.TokenAndKey;
import org.niis.xroad.signer.protocol.dto.KeyInfoProto;
import org.niis.xroad.signer.protocol.dto.KeyUsageInfo;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static ee.ria.xroad.common.DerSubjectCertUtil.derName;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CertRequestCreationServiceTest {

    private static final String DER_COUNTRY_DEV_ORG_COM_CN_222 =
            "302a310c300a06035504061303444556310c300a060355040a0c03434f4d310c300a06035504030c03323232";

    private CertRequestCreationService service;
    private TokenAndKey tokenAndKey;

    @BeforeEach
    void setUp() throws Exception {
        var properties = mock(SignerProperties.class);
        when(properties.csrSignatureDigestAlgorithm()).thenReturn("SHA-256");

        var tokenWorker = mock(TokenWorker.class);
        when(tokenWorker.handleSign(any())).thenReturn(new byte[256]);
        var tokenWorkerProvider = mock(TokenWorkerProvider.class);
        when(tokenWorkerProvider.getTokenWorker("token-1")).thenReturn(tokenWorker);

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        var keyInfo = new KeyInfo(KeyInfoProto.newBuilder()
                .setId("key-1")
                .setPublicKey(Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()))
                .setSignMechanismName(SignMechanism.CKM_RSA_PKCS.name())
                .build());
        tokenAndKey = new TokenAndKey("token-1", keyInfo);

        service = new CertRequestCreationService(properties, tokenWorkerProvider, new KeyManagers(2048, "secp256r1"));
    }

    @Test
    void subjectWithCountryThatIsNotTwoCharactersKeepsDerEncoding() throws Exception {
        PKCS10CertificationRequest csr = service.buildSignedCertRequest(
                tokenAndKey, "C=DEV, O=COM, CN=222", null, KeyUsageInfo.AUTHENTICATION);

        assertThat(csr.getSubject().getEncoded()).isEqualTo(Hex.decode(DER_COUNTRY_DEV_ORG_COM_CN_222));
    }

    @Test
    void subjectWithCommonNameOverSixtyFourCharactersKeepsDerEncoding() throws Exception {
        String commonName = "x".repeat(65);

        PKCS10CertificationRequest csr = service.buildSignedCertRequest(
                tokenAndKey, "C=DEV, O=COM, CN=" + commonName, null, KeyUsageInfo.SIGNING);

        X500Name expected = derName("C", "DEV", "O", "COM", "CN", commonName);
        assertThat(csr.getSubject().getEncoded()).isEqualTo(expected.getEncoded());
    }
}
