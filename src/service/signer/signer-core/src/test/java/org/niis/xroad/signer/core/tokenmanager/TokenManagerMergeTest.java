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
package org.niis.xroad.signer.core.tokenmanager;

import ee.ria.xroad.common.identifier.ClientId;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.signer.api.dto.CertificateInfo;
import org.niis.xroad.signer.api.dto.KeyInfo;
import org.niis.xroad.signer.core.model.Cert;
import org.niis.xroad.signer.core.model.Token;
import org.niis.xroad.signer.core.tokenmanager.merge.TokenMergeAddedCertificatesListener;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Class for testing {@link TokenManager} merging of configuration files
 */
@Slf4j
@ExtendWith(MockitoExtension.class)
class TokenManagerMergeTest {

    private TokenRegistry tokenRegistry;
    private TokenManager tokenManager;
    @Mock
    private TokenConf tokenConf;

    @Captor
    private ArgumentCaptor<List<Cert>> certListArgumentCaptor;

    /**
     * Set up the original key conf file for testing and init the {@link TokenManager}
     */
    @BeforeEach
    void setUp() throws Exception {
        tokenRegistry = new TokenRegistry(tokenConf);
        tokenManager = new TokenManager(tokenRegistry);

        when(tokenConf.retrieveTokensFromDb()).thenReturn(createDefaultTokens());

        tokenRegistry.init();
    }

    @Test
    void shouldMergeKeyAvailability() throws Exception {
        var currentTokens = tokenConf.retrieveTokensFromDb();

        final String testKeyId = currentTokens.tokens().iterator().next().getKeys().getFirst().getId();

        KeyInfo beforeKeyInfo = tokenManager.getKeyInfo(testKeyId);
        assertNotNull(beforeKeyInfo, "test setup failure");
        assertFalse(tokenManager.isKeyAvailable(testKeyId));

        tokenManager.setKeyAvailable(testKeyId, true);

        var defaultTokens = createDefaultTokens();
        assertFalse(defaultTokens.tokens().iterator().next().getKeys().getFirst().isAvailable());

        when(tokenConf.retrieveTokensFromDb()).thenReturn(defaultTokens);
        when(tokenConf.hasChanged(any())).thenReturn(true);

        tokenRegistry.merge(addedCerts -> {
        });

        Assertions.assertTrue(tokenManager.isKeyAvailable(testKeyId), "key availability was not merged");
    }

    @Test
    void shouldNotMergeEmptyDatabase() throws Exception {

        Set<Token> beforeTokens = tokenConf.retrieveTokensFromDb().tokens();

        final int beforeSize = beforeTokens.size();
        final int beforeCertCount = tokenManager.getAllCerts().size();

        when(tokenConf.retrieveTokensFromDb()).thenReturn(new TokenConf.LoadedTokens(Set.of(), 0));
        when(tokenConf.hasChanged(any())).thenReturn(true);

        tokenRegistry.merge(addedCerts -> {
        });

        Set<Token> afterTokens = tokenRegistry.getCurrentTokens().tokens();
        assertEquals(beforeSize, afterTokens.size(), "token amount changed");
        assertEquals(beforeCertCount, tokenManager.getAllCerts().size(), "cert amount changed");
    }

    /**
     * Test that a key added in the Database appears in tokens after merge.
     */
    @Test
    void shouldAddCertFromDatabase() throws Exception {
        final int beforeCertCount = tokenManager.getAllCerts().size();

        var defaultTokens = createDefaultTokens();
        var newCert = TokenTestUtils.createTestCert("cert1", ClientId.Conf.create("INSTANCE", "CLASS", "MEMBER"));
        defaultTokens.tokens().stream().findFirst().orElseThrow().getKeys().getFirst().addCert(newCert);

        when(tokenConf.retrieveTokensFromDb()).thenReturn(defaultTokens);
        when(tokenConf.hasChanged(any())).thenReturn(true);

        TokenMergeAddedCertificatesListener listenerMock = mock(TokenMergeAddedCertificatesListener.class);

        tokenRegistry.merge(listenerMock);

        assertEquals(beforeCertCount + 1, tokenManager.getAllCerts().size(), "cert amount should be original + 1");
        verify(listenerMock, times(1)).mergeDone(certListArgumentCaptor.capture());
        assertThat(certListArgumentCaptor.getValue()).hasSize(1);
    }

    @Test
    void shouldAddCertToCorrectKey() throws Exception {
        var currentTokens = tokenConf.retrieveTokensFromDb();

        final String testKeyId = currentTokens.tokens().iterator().next().getKeys().getFirst().getId();
        KeyInfo beforeKeyInfo = tokenManager.getKeyInfo(testKeyId);
        assertNotNull(beforeKeyInfo, "test setup failure");

        final int beforeCount = beforeKeyInfo.getCerts().size();

        var defaultTokens = createDefaultTokens();
        var newCert = TokenTestUtils.createTestCert("cert1", ClientId.Conf.create("INSTANCE", "CLASS", "MEMBER"));
        defaultTokens.tokens().stream().findFirst().orElseThrow().getKeys().getFirst().addCert(newCert);

        when(tokenConf.retrieveTokensFromDb()).thenReturn(defaultTokens);
        when(tokenConf.hasChanged(any())).thenReturn(true);

        tokenRegistry.merge(addedCerts -> {
        });

        assertEquals(beforeCount + 1, tokenManager.getKeyInfo(testKeyId).getCerts().size(), "cert amount for key should be original + 1");
    }

    @Test
    void shouldAddOcspResponse() throws Exception {
        var currentTokens = tokenConf.retrieveTokensFromDb();

        final var testKey = currentTokens.tokens().iterator().next().getKeys().getFirst();
        final String testKeyId = testKey.getId();
        KeyInfo beforeKeyInfo = tokenManager.getKeyInfo(testKeyId);
        assertNotNull(beforeKeyInfo, "test setup failure");

        final Cert testCert = testKey.getCerts().getFirst();
        final String testCertId = testCert.getId();
        final String testCertSha1Hash = testCert.getSha1hash();
        CertificateInfo beforeCertInfo = tokenManager.getCertificateInfo(testCertId);
        assertNotNull(beforeCertInfo, "test setup failure");

        // assert no ocsp response exists before test
        Assertions.assertArrayEquals(new byte[0], beforeCertInfo.getOcspBytes(), "test setup failure");

        OCSPResp shouldMatchResponse = mock(OCSPResp.class);
        final byte[] shouldMatchOcspResponseBytes = "some example string  11 2 34".getBytes();
        when(shouldMatchResponse.getEncoded()).thenReturn(shouldMatchOcspResponseBytes);
        tokenManager.setOcspResponse(testCertSha1Hash, shouldMatchResponse);

        final int beforeCertCount = tokenManager.getAllCerts().size();

        var defaultTokens = createDefaultTokens();
        var newCert = TokenTestUtils.createTestCert("cert1", ClientId.Conf.create("INSTANCE", "CLASS", "MEMBER"));
        newCert.setOcspResponse(shouldMatchResponse);
        defaultTokens.tokens().stream().findFirst().orElseThrow().getKeys().getFirst().addCert(newCert);

        when(tokenConf.retrieveTokensFromDb()).thenReturn(defaultTokens);
        when(tokenConf.hasChanged(any())).thenReturn(true);

        tokenRegistry.merge(addedCerts -> {
        });

        // make sure the merge actually reads the file, otherwise the ocsp response will of course be there
        assertEquals(beforeCertCount + 1, tokenManager.getAllCerts().size(), "merge did not add expected cert");

        Assertions.assertArrayEquals(shouldMatchOcspResponseBytes,
                tokenManager.getCertificateInfo(testCertId).getOcspBytes(),
                "ocsp response bytes does not match");
    }

    private TokenConf.LoadedTokens createDefaultTokens() {
        Set<Token> defaultTokens = Set.of(
                TokenTestUtils.createFullTestToken("token1", 10L, "Save Token 1", "SN_SAVE_001",
                        false, true, 1, 1, 1),
                TokenTestUtils.createFullTestToken("token2", 20L, "Save Token 2", "SN_SAVE_002",
                        false, true, 1, 1, 1));

        return new TokenConf.LoadedTokens(defaultTokens, 12345);
    }
}
