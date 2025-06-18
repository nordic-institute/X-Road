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

package org.niis.xroad.signer.core.tokenmanager;

import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.crypto.identifier.KeyAlgorithm;
import ee.ria.xroad.common.crypto.identifier.SignMechanism;
import ee.ria.xroad.common.identifier.ClientId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.signer.core.TestDataUtil;
import org.niis.xroad.signer.core.certmanager.OcspResponseManager;
import org.niis.xroad.signer.core.model.CertData;
import org.niis.xroad.signer.core.model.CertRequestData;
import org.niis.xroad.signer.core.model.RuntimeCertImpl;
import org.niis.xroad.signer.core.model.RuntimeKey;
import org.niis.xroad.signer.core.model.RuntimeKeyImpl;
import org.niis.xroad.signer.core.model.RuntimeTokenImpl;
import org.niis.xroad.signer.core.model.SoftwareTokenData;
import org.niis.xroad.signer.core.service.TokenService;
import org.niis.xroad.signer.core.tokenmanager.token.SoftwareTokenDefinition;
import org.niis.xroad.signer.protocol.dto.TokenStatusInfo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenRegistryLoaderTest {

    @Mock
    private TokenService tokenService;

    @Mock
    private OcspResponseManager ocspResponseManager;

    @InjectMocks
    private TokenRegistryLoader tokenRegistryLoader;

    @Test
    void testLoadTokens() throws Exception {
        TokenService.LoadedTokens loadedTokens = loadedTokens();
        when(tokenService.loadAllTokens()).thenReturn(loadedTokens);

        var tokens = tokenRegistryLoader.loadTokens();

        assertEquals(1, tokens.size());

        var token = tokens.stream().findFirst().get();
        assertEquals("externalId", token.externalId());
        assertEquals("serialNumber", token.serialNumber());

        assertEquals(2, token.keys().size());
        assertEquals("keyExternalId-0", new ArrayList<>(token.keys()).getFirst().externalId());
        assertEquals("keyExternalId-1", new ArrayList<>(token.keys()).get(1).externalId());

        var key1 = token.keys().stream().findFirst().get();
        assertEquals(1, key1.certs().size());
        assertEquals(1, key1.certRequests().size());

        var key2 = token.keys().stream().skip(1).findFirst().get();
        assertEquals(0, key2.certs().size());
        assertEquals(0, key2.certRequests().size());
    }

    @Test
    void testRefreshTokens() throws Exception {
        when(tokenService.loadAllTokens()).thenReturn(loadedTokens());

        var currentToken = new RuntimeTokenImpl();
        currentToken.setData(new SoftwareTokenData(1L, "old-ext-id", "old-type", "old-serialNumber", "old-label",
                "old-friendlyName", new byte[]{'h', 'a', 's', 'h'}, Instant.now(), Instant.now()));
        var tokenDefinition = new SoftwareTokenDefinition(
                Map.of(
                        KeyAlgorithm.EC, SignMechanism.CKM_ECDSA,
                        KeyAlgorithm.RSA, SignMechanism.CKM_RSA_PKCS
                ));
        currentToken.setTokenDefinition(tokenDefinition);
        currentToken.setStatus(TokenStatusInfo.OK);
        currentToken.setActive(true);

        var transientCert = new RuntimeCertImpl();
        transientCert.setTransientCert(true);

        var key1 = new RuntimeKeyImpl();
        key1.setData(TestDataUtil.softwareKeyData(0L, 1L));
        key1.setAvailable(true); //transient data should be copied to new token
        key1.addCert(transientCert);
        var key2 = new RuntimeKeyImpl();
        key2.setData(TestDataUtil.softwareKeyData(12L, 1L));
        var key3 = new RuntimeKeyImpl();
        key3.setData(TestDataUtil.softwareKeyData(13L, 1L));

        currentToken.addKey(key1);
        currentToken.addKey(key2);
        currentToken.addKey(key3);

        Map<String, String> tokenInfo = Map.of("key1", "value1", "key2", "value2");
        currentToken.getTokenInfo().putAll(tokenInfo);

        Set<RuntimeTokenImpl> newTokens = tokenRegistryLoader.refreshTokens(Set.of(currentToken));

        //verify new token data is loaded from DB
        var newToken = newTokens.stream().findFirst().get();
        assertEquals(1L, newToken.id());
        assertEquals("externalId", newToken.externalId());
        assertEquals("type", newToken.type());
        assertEquals("serialNumber", newToken.serialNumber());
        assertEquals("label", newToken.label());
        assertEquals("friendlyName", newToken.friendlyName());
        assertArrayEquals(new byte[]{'p', 'i', 'n', 'h', 'a', 's', 'h'}, newToken.softwareTokenPinHash().get());

        //verify token transient data is updated from current token
        assertEquals(TokenStatusInfo.OK, newToken.getStatus());
        assertTrue(newToken.isActive());
        assertEquals(tokenInfo, newToken.getTokenInfo());
        assertEquals(tokenDefinition, newToken.getTokenDefinition().get());

        //verify token keys
        assertEquals(2, newToken.keys().size()); // keys are loaded from DB, not from current token

        Map<Long, RuntimeKey> keysById = newToken.keys().stream()
                .collect(Collectors.toMap(RuntimeKey::id, key -> key));

        assertTrue(keysById.get(0L).isAvailable()); //transient data copied
        assertFalse(keysById.get(1L).isAvailable());

        //key 0 should have cert from DB and transient cert
        assertEquals(2, keysById.get(0L).certs().size());

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(ocspResponseManager).refreshCache(captor.capture());
        assertEquals(List.of("sha256hash-1"), captor.getValue());
    }

    private TokenService.LoadedTokens loadedTokens() {

        var tokenData = new SoftwareTokenData(1L, "externalId", "type", "serialNumber", "label",
                "friendlyName", new byte[]{'p', 'i', 'n', 'h', 'a', 's', 'h'}, Instant.now(), Instant.now());
        var key1 = TestDataUtil.softwareKeyData(0L, 1L);
        var key2 = TestDataUtil.softwareKeyData(1L, 1L);

        var cert = TestCertUtil.getProducer().certChain[0];
        var cert1 = CertData.create("cert-external-id-1", 0L, cert, "sha256hash-1");

        var certRequest1 = new CertRequestData(0L, "cert-request-external-id-1", 0L, ClientId.Conf.create("a", "b", "c"),
                "sn", "subject alt name", "", Instant.now(), Instant.now());

        return new TokenService.LoadedTokens(
                Set.of(tokenData),
                Map.of(1L, List.of(key1, key2)),
                Map.of(0L, List.of(cert1)),
                Map.of(0L, List.of(certRequest1))
        );
    }

}
