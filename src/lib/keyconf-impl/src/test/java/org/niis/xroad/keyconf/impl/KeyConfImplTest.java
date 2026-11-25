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
package org.niis.xroad.keyconf.impl;

import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.identifier.SecurityServerId;

import com.google.protobuf.ByteString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.impl.ocsp.OcspVerifierFactory;
import org.niis.xroad.keyconf.dto.AuthKeyStatus;
import org.niis.xroad.serverconf.ServerConfProvider;
import org.niis.xroad.signer.api.dto.AuthKeyInfo;
import org.niis.xroad.signer.api.dto.CertificateInfo;
import org.niis.xroad.signer.client.SignerRpcClient;
import org.niis.xroad.signer.protocol.dto.CertificateInfoProto;
import org.niis.xroad.test.globalconf.EmptyGlobalConf;
import org.niis.xroad.test.serverconf.EmptyServerConf;

import java.security.cert.X509Certificate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeyConfImplTest {

    private static final SecurityServerId.Conf SERVER_ID = SecurityServerId.Conf.create("TEST", "CLASS", "CODE", "SERVER");

    @Mock
    private SignerRpcClient signerRpcClient;

    private GlobalConfProvider globalConfProvider;
    private ServerConfProvider serverConfProvider;
    private KeyConfImpl keyConf;

    @BeforeEach
    void setUp() {
        globalConfProvider = new EmptyGlobalConf();
        serverConfProvider = new EmptyServerConf() {
            @Override
            public SecurityServerId.Conf getIdentifier() {
                return SERVER_ID;
            }
        };
        keyConf = new KeyConfImpl(globalConfProvider, new OcspVerifierFactory(), serverConfProvider, signerRpcClient);
    }

    @Test
    void returnsPresentForAValidKey() throws Exception {
        X509Certificate cert = TestCertUtil.getConsumer().certChain[0];
        var key = TestCertUtil.getConsumer().key;
        when(signerRpcClient.getAuthKey(SERVER_ID)).thenReturn(new AuthKeyInfo("alias", key, testCertificateInfo(cert)));

        AuthKeyStatus status = keyConf.getAuthKeyStatus();

        assertThat(status).isInstanceOfSatisfying(AuthKeyStatus.Present.class,
                present -> assertThat(present.authKey().key()).isEqualTo(key));
    }

    @Test
    void mapsTokenNotActiveToNotActive() {
        when(signerRpcClient.getAuthKey(SERVER_ID))
                .thenThrow(XrdRuntimeException.systemException(ErrorCode.TOKEN_NOT_ACTIVE).build());

        assertThat(keyConf.getAuthKeyStatus()).isInstanceOf(AuthKeyStatus.NotActive.class);
    }

    @Test
    void mapsTokenNotInitializedToNotConfigured() {
        when(signerRpcClient.getAuthKey(SERVER_ID))
                .thenThrow(XrdRuntimeException.systemException(ErrorCode.TOKEN_NOT_INITIALIZED).build());

        assertThat(keyConf.getAuthKeyStatus()).isInstanceOf(AuthKeyStatus.NotConfigured.class);
    }

    @Test
    void mapsAnyOtherSignerErrorToNotConfigured() {
        when(signerRpcClient.getAuthKey(SERVER_ID))
                .thenThrow(XrdRuntimeException.systemException(ErrorCode.INTERNAL_ERROR).build());

        assertThat(keyConf.getAuthKeyStatus()).isInstanceOf(AuthKeyStatus.NotConfigured.class);
    }

    @Test
    void returnsNotConfiguredWhenNoServerIdWithoutCallingSigner() {
        serverConfProvider = new EmptyServerConf();
        keyConf = new KeyConfImpl(globalConfProvider, new OcspVerifierFactory(), serverConfProvider, signerRpcClient);

        assertThat(keyConf.getAuthKeyStatus()).isInstanceOf(AuthKeyStatus.NotConfigured.class);
        verifyNoInteractions(signerRpcClient);
    }

    @Test
    void returnsNotConfiguredWhenIdentifierLookupFails() {
        serverConfProvider = new EmptyServerConf() {
            @Override
            public SecurityServerId.Conf getIdentifier() {
                throw XrdRuntimeException.systemException(ErrorCode.MALFORMED_SERVERCONF).build();
            }
        };
        keyConf = new KeyConfImpl(globalConfProvider, new OcspVerifierFactory(), serverConfProvider, signerRpcClient);

        assertThat(keyConf.getAuthKeyStatus()).isInstanceOf(AuthKeyStatus.NotConfigured.class);
        verifyNoInteractions(signerRpcClient);
    }

    private CertificateInfo testCertificateInfo(X509Certificate cert) throws Exception {
        return new CertificateInfo(CertificateInfoProto.newBuilder()
                .setActive(true)
                .setSavedToConfiguration(true)
                .setStatus(CertificateInfo.STATUS_SAVED)
                .setId("id")
                .setCertificateBytes(ByteString.copyFrom(cert.getEncoded()))
                .build());
    }
}
