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


import ee.ria.xroad.common.db.TransactionCallback;

import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.serverconf.impl.ServerConfDatabaseCtx;
import org.niis.xroad.serverconf.impl.dao.KeyConfDeviceDaoImpl;
import org.niis.xroad.serverconf.impl.entity.KeyConfCertificateEntity;
import org.niis.xroad.serverconf.impl.entity.KeyConfDeviceEntity;
import org.niis.xroad.serverconf.impl.entity.KeyConfKeyEntity;
import org.niis.xroad.serverconf.impl.mapper.XRoadIdMapper;
import org.niis.xroad.signer.core.model.Cert;
import org.niis.xroad.signer.core.model.CertRequest;
import org.niis.xroad.signer.core.model.Key;
import org.niis.xroad.signer.core.model.Token;
import org.niis.xroad.signer.core.tokenmanager.mapper.CertMapper;
import org.niis.xroad.signer.core.tokenmanager.mapper.CertRequestMapper;
import org.niis.xroad.signer.core.tokenmanager.mapper.KeyMapper;
import org.niis.xroad.signer.core.tokenmanager.mapper.TokenMapper;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenConfTest {

    @Mock
    private ServerConfDatabaseCtx serverConfDatabaseCtx;

    @Mock
    private KeyConfDeviceDaoImpl keyConfDeviceDao;

    // Real mappers
    private TokenMapper tokenMapper;
    private KeyMapper keyMapper;
    private CertMapper certMapper;
    private CertRequestMapper certRequestMapper;
    private XRoadIdMapper xroadIdMapper;

    private TokenConf tokenConf;

    @Captor
    private ArgumentCaptor<Set<KeyConfDeviceEntity>> deviceEntitySetCaptor;

    @BeforeEach
    void setUp() {
        xroadIdMapper = XRoadIdMapper.get();

        certMapper = Mappers.getMapper(CertMapper.class);
        certMapper.setXroadIdMapper(xroadIdMapper);

        certRequestMapper = Mappers.getMapper(CertRequestMapper.class);
        certRequestMapper.setXroadIdMapper(xroadIdMapper);

        keyMapper = Mappers.getMapper(KeyMapper.class);
        keyMapper.setCertMapper(certMapper);
        keyMapper.setCertRequestMapper(certRequestMapper);

        tokenMapper = Mappers.getMapper(TokenMapper.class);
        tokenMapper.setKeyMapper(keyMapper);

        // Instantiate TokenConf with the real TokenMapper
        tokenConf = new TokenConf(serverConfDatabaseCtx, keyConfDeviceDao, tokenMapper);
    }


    @Test
    void retrieveTokensFromDbShouldReturnLoadedTokensWithFullGraph() throws Exception {
        KeyConfDeviceEntity deviceEntity1 = TokenTestUtils.createFullTestDeviceEntity("dev1", 1L, "Device 1", "SN001",
                1, 1, 1);
        Set<KeyConfDeviceEntity> mockEntitiesFromDb = Set.of(deviceEntity1);
        Session mockSession = mock(Session.class);

        when(serverConfDatabaseCtx.doInTransaction(any()))
                .thenAnswer(invocation -> {
                    TransactionCallback<TokenConf.LoadedTokens> callback = invocation.getArgument(0);
                    when(keyConfDeviceDao.findAll(mockSession)).thenReturn(new HashSet<>(mockEntitiesFromDb));
                    // No need to mock tokenMapper.toTargets anymore
                    return callback.apply(mockSession);
                });

        TokenConf.LoadedTokens actualLoadedTokens = tokenConf.retrieveTokensFromDb();

        assertNotNull(actualLoadedTokens);
        assertNotNull(actualLoadedTokens.tokens());
        assertEquals(1, actualLoadedTokens.tokens().size());
        assertEquals(mockEntitiesFromDb.hashCode(), actualLoadedTokens.entitySetHashCode());

        Token actualToken = actualLoadedTokens.tokens().iterator().next();
        assertEquals("dev1", actualToken.getId()); // deviceId is mapped to Token.id
        assertEquals(1L, actualToken.getInternalId());
        assertEquals("Device 1", actualToken.getFriendlyName());
        assertEquals("SN001", actualToken.getSerialNumber()); // tokenId is mapped to serialNumber
        assertEquals(1, actualToken.getKeys().size());

        Key actualKey = actualToken.getKeys().getFirst();
        assertEquals("key-dev1-0", actualKey.getId());
        assertEquals(1, actualKey.getCerts().size());
        assertEquals(1, actualKey.getCertRequests().size());

        Cert actualCert = actualKey.getCerts().getFirst();
        assertEquals("cert-key-dev1-0-0", actualCert.getId());

        CertRequest actualCertRequest = actualKey.getCertRequests().getFirst();
        assertEquals("req-key-dev1-0-0", actualCertRequest.id());

        verify(keyConfDeviceDao).findAll(mockSession);
    }

    @Test
    void retrieveTokensFromDbShouldThrowTokenConfException() throws Exception {
        when(serverConfDatabaseCtx.doInTransaction(any()))
                .thenThrow(new RuntimeException("DB error"));

        TokenConf.TokenConfException exception = assertThrows(TokenConf.TokenConfException.class, () -> tokenConf.retrieveTokensFromDb());
        assertEquals("Error while loading or validating key config", exception.getMessage());
        assertInstanceOf(RuntimeException.class, exception.getCause());
    }

    @Test
    void saveShouldSaveChangesWhenHashCodesDifferWithFullGraph() throws Exception {
        Token tokenToSave = TokenTestUtils.createFullTestToken("token1", 10L, "Save Token 1", "SN_SAVE_001",
                false, true, 1, 1, 1);
        // Ensure the key has certs/cert requests to be saved based on TokenMapper logic
        assertTrue(tokenToSave.getKeys().getFirst().hasCertsOrCertRequests());

        Set<Token> tokensToSaveSet = Set.of(tokenToSave);
        TokenConf.LoadedTokens loadedTokens = new TokenConf.LoadedTokens(tokensToSaveSet, 12345); // Different hash code
        Session mockSession = mock(Session.class);

        when(serverConfDatabaseCtx.doInTransaction(any()))
                .thenAnswer(invocation -> {
                    TransactionCallback<Void> callback = invocation.getArgument(0);
                    callback.apply(mockSession);
                    return null;
                });

        // 3. Execute
        tokenConf.save(loadedTokens);

        // 4. Verify and Assert
        verify(serverConfDatabaseCtx).doInTransaction(any());
        verify(keyConfDeviceDao).deleteAll(mockSession);
        verify(keyConfDeviceDao).saveTokens(eq(mockSession), deviceEntitySetCaptor.capture());

        Set<KeyConfDeviceEntity> capturedEntities = deviceEntitySetCaptor.getValue();
        assertNotNull(capturedEntities);
        assertEquals(1, capturedEntities.size());

        KeyConfDeviceEntity capturedDeviceEntity = capturedEntities.iterator().next();
        assertEquals("token1", capturedDeviceEntity.getDeviceId()); // Token.id mapped to deviceId
        // Note: Token's internalId is NOT directly mapped to KeyConfDeviceEntity.id by this mapper direction
        // KeyConfDeviceEntity.id is usually DB generated or set if updating existing. Here it will be null.
        assertEquals("Save Token 1", capturedDeviceEntity.getFriendlyName());
        assertEquals("SN_SAVE_001", capturedDeviceEntity.getTokenId()); // Token.serialNumber mapped to tokenId
        assertEquals(1, capturedDeviceEntity.getKeys().size());

        KeyConfKeyEntity capturedKeyEntity = capturedDeviceEntity.getKeys().iterator().next();
        assertEquals(tokenToSave.getKeys().getFirst().getId(), capturedKeyEntity.getKeyId());
        assertEquals(1, capturedKeyEntity.getCertificates().size());
        assertEquals(1, capturedKeyEntity.getCertRequests().size());

        KeyConfCertificateEntity capturedCertEntity = capturedKeyEntity.getCertificates().iterator().next();
        assertEquals(tokenToSave.getKeys().getFirst().getCerts().getFirst().getId(), capturedCertEntity.getCertId());
    }

    @Test
    void saveShouldNotSaveIfKeyHasNoCertsOrRequests() throws Exception {
        // Create a token with a key that has NO certs and NO cert requests
        Token tokenWithEmptyKey = TokenTestUtils.createFullTestToken("emptyKeyToken", 20L, "Empty Key Token",
                "SN_EMPTY_002", false, true, 1, 0, 0);
        // Ensure key is indeed empty for this test's purpose
        assertFalse(tokenWithEmptyKey.getKeys().getFirst().hasCertsOrCertRequests());

        Set<Token> tokensToSaveSet = Set.of(tokenWithEmptyKey);
        // Use a different hashcode to trigger the save attempt logic in TokenConf
        TokenConf.LoadedTokens loadedTokens = new TokenConf.LoadedTokens(tokensToSaveSet, 98765);
        Session mockSession = mock(Session.class);

        when(serverConfDatabaseCtx.doInTransaction(any()))
                .thenAnswer(invocation -> {
                    TransactionCallback<Void> callback = invocation.getArgument(0);
                    callback.apply(mockSession);
                    return null;
                });

        tokenConf.save(loadedTokens);

        verify(serverConfDatabaseCtx).doInTransaction(any());
        verify(keyConfDeviceDao).deleteAll(mockSession);
        verify(keyConfDeviceDao).saveTokens(eq(mockSession), deviceEntitySetCaptor.capture());

        Set<KeyConfDeviceEntity> capturedEntities = deviceEntitySetCaptor.getValue();
        assertNotNull(capturedEntities);
        assertEquals(1, capturedEntities.size()); // Device entity itself is created

        KeyConfDeviceEntity capturedDeviceEntity = capturedEntities.iterator().next();
        // The key itself is NOT added to the device entity because it has no certs/requests
        // This is due to: token.getKeys().stream().filter(Key::hasCertsOrCertRequests)... in TokenMapper
        assertTrue(capturedDeviceEntity.getKeys().isEmpty());
    }


    @Test
    void saveShouldNotSaveChangesWhenHashCodesAreEqual() throws Exception {
        Token token1 = TokenTestUtils.createFullTestToken("id1", 1L, "Token 1", "SN001",
                true, true, 0, 0, 0);
        Set<Token> tokensToSave = Set.of(token1);

        // Calculate expected entities and their hashcode *as if* they were mapped
        // For this test, we only care that the hashcode matches, so a simplified entity might be okay
        // if the full mapping is complex and not the point of *this specific* hashcode check.
        // However, to be accurate, we should map it.
        Set<KeyConfDeviceEntity> expectedEntities = tokensToSave.stream()
                .map(tokenMapper::toSource)
                .collect(Collectors.toSet());
        var loadedTokens = new TokenConf.LoadedTokens(tokensToSave, expectedEntities.hashCode());

        // No need to mock tokenMapper.toEntities as it's real
        // tokenConf.save will call tokenMapper.toEntities(tokensToSave) internally.

        tokenConf.save(loadedTokens);

        verify(serverConfDatabaseCtx, never()).doInTransaction(any());
        verify(keyConfDeviceDao, never()).deleteAll(any());
        verify(keyConfDeviceDao, never()).saveTokens(any(), any());
    }

    @Test
    void hasChangedShouldReturnTrueWhenDbIdsDifferFromTokenIds() throws Exception {
        Token loadedDomainToken = TokenTestUtils.createFullTestToken("id1", 1L, "Token 1", "SN001",
                true, true, 0, 0, 0);
        TokenConf.LoadedTokens loadedTokens = new TokenConf.LoadedTokens(Set.of(loadedDomainToken), 123);
        var dbTokenIds = Set.of(2L, 3L); // Different internal IDs in DB
        Session mockSession = mock(Session.class);

        when(serverConfDatabaseCtx.doInTransaction(any()))
                .thenAnswer(invocation -> {
                    TransactionCallback<Boolean> callback = invocation.getArgument(0);
                    when(keyConfDeviceDao.findAllIds(mockSession)).thenReturn(dbTokenIds);
                    return callback.apply(mockSession);
                });
        when(serverConfDatabaseCtx.getSession()).thenReturn(mockSession);

        assertTrue(tokenConf.hasChanged(loadedTokens));
        verify(keyConfDeviceDao).findAllIds(mockSession);
    }

    @Test
    void hasChangedShouldReturnFalseWhenDbIdsMatchTokenIds() throws Exception {
        Token loadedDomainToken = TokenTestUtils.createFullTestToken("id1", 1L, "Token 1", "SN001",
                true, true, 0, 0, 0);
        Token loadedDomainToken2 = TokenTestUtils.createFullTestToken("id5", 5L, "Token 1", "SN001",
                true, true, 0, 0, 0);
        TokenConf.LoadedTokens loadedTokens = new TokenConf.LoadedTokens(Set.of(loadedDomainToken, loadedDomainToken2), 123);
        var dbTokenIds = Set.of(5L, 1L); // Same internal ID in DB
        Session mockSession = mock(Session.class);

        when(serverConfDatabaseCtx.doInTransaction(any()))
                .thenAnswer(invocation -> {
                    TransactionCallback<Boolean> callback = invocation.getArgument(0);
                    when(keyConfDeviceDao.findAllIds(mockSession)).thenReturn(dbTokenIds);
                    return callback.apply(mockSession);
                });
        when(serverConfDatabaseCtx.getSession()).thenReturn(mockSession);

        assertFalse(tokenConf.hasChanged(loadedTokens));
        verify(keyConfDeviceDao).findAllIds(mockSession);
    }

    @Test
    void hasChangedShouldReturnTrueWhenSizesDiffer() throws Exception {
        Token t1 = TokenTestUtils.createFullTestToken("id1", 1L, "Token 1", "SN001",
                true, true, 0, 0, 0);
        Token t2 = TokenTestUtils.createFullTestToken("id2", 2L, "Token 2", "SN002",
                true, true, 0, 0, 0);
        TokenConf.LoadedTokens loadedTokens = new TokenConf.LoadedTokens(Set.of(t1, t2), 123);
        var dbTokenIds = Set.of(1L); // DB has fewer tokens
        Session mockSession = mock(Session.class);

        when(serverConfDatabaseCtx.doInTransaction(any()))
                .thenAnswer(invocation -> {
                    TransactionCallback<Boolean> callback = invocation.getArgument(0);
                    when(keyConfDeviceDao.findAllIds(mockSession)).thenReturn(dbTokenIds);
                    return callback.apply(mockSession);
                });
        when(serverConfDatabaseCtx.getSession()).thenReturn(mockSession);

        assertTrue(tokenConf.hasChanged(loadedTokens));
        verify(keyConfDeviceDao).findAllIds(mockSession);
    }

    @Test
    void hasChangedShouldReturnFalseOnError() throws Exception {
        Token loadedDomainToken = TokenTestUtils.createFullTestToken("id1", 1L, "Token 1", "SN001",
                true, true, 0, 0, 0);
        var loadedTokens = new TokenConf.LoadedTokens(Set.of(loadedDomainToken), 123);

        when(serverConfDatabaseCtx.doInTransaction(any()))
                .thenThrow(new RuntimeException("DB error"));

        assertFalse(tokenConf.hasChanged(loadedTokens));
    }

}
