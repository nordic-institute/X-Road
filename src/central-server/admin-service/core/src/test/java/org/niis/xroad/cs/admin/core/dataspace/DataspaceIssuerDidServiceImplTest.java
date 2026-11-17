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
package org.niis.xroad.cs.admin.core.dataspace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.cs.admin.api.dto.BoundIssuer;
import org.niis.xroad.cs.admin.api.dto.TrustedIssuerRemoval;
import org.niis.xroad.cs.admin.core.entity.IssuerDidEntity;
import org.niis.xroad.cs.admin.core.repository.IssuerDidRepository;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataspaceIssuerDidServiceImplTest {

    private static final int ID = 5;
    private static final String DID_1 = "did:web:cs1.example%3A443:issuer";
    private static final String DID_2 = "did:web:cs2.example%3A443:issuer";
    private static final String HOST = "cs1.example";
    private static final int DID_PORT = 443;
    private static final String ISSUANCE_URL = "https://cs1.example:6185/api/issuance/v1/participants/issuer";
    private static final String STATUSLIST_BASE_URL = "https://cs1.example:6187/statuslist";

    @Mock
    private IssuerDidRepository issuerDidRepository;

    private DataspaceIssuerDidServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DataspaceIssuerDidServiceImpl(issuerDidRepository);
    }

    /**
     * The database's failing unique constraint on {@code did} is what makes a second bind an error, so the
     * service has no application-level branching to unit-test beyond this delegation — the conflict case
     * belongs in a real-database test of the repository method itself.
     */
    @Test
    void registerDelegatesToTheInsert() {
        service.register(DID_1, HOST, DID_PORT, ISSUANCE_URL, STATUSLIST_BASE_URL);

        verify(issuerDidRepository).insert(DID_1, HOST, DID_PORT, ISSUANCE_URL, STATUSLIST_BASE_URL);
    }

    @Test
    void findAllReturnsEveryRegisteredDidSorted() {
        when(issuerDidRepository.findAll()).thenReturn(List.of(
                new IssuerDidEntity(DID_2, HOST, DID_PORT, ISSUANCE_URL, STATUSLIST_BASE_URL),
                new IssuerDidEntity(DID_1, HOST, DID_PORT, ISSUANCE_URL, STATUSLIST_BASE_URL)));

        assertThat(service.findAll()).containsExactly(DID_1, DID_2);
    }

    @Test
    void findAllReturnsEmptyListWhenNoNodeHasProvisionedAnIssuer() {
        when(issuerDidRepository.findAll()).thenReturn(List.of());

        assertThat(service.findAll()).isEmpty();
    }

    @Test
    void findAllBoundReturnsRemovedAndLiveRowsEachWithItsRemovedFlag() {
        var removedEntity = mock(IssuerDidEntity.class);
        when(removedEntity.getDid()).thenReturn(DID_2);
        when(removedEntity.getRemovedAt()).thenReturn(Instant.now());

        var liveEntity = mock(IssuerDidEntity.class);
        when(liveEntity.getDid()).thenReturn(DID_1);
        when(liveEntity.getRemovedAt()).thenReturn(null);

        when(issuerDidRepository.findAll()).thenReturn(List.of(removedEntity, liveEntity));

        assertThat(service.findAllBound()).containsExactly(
                new BoundIssuer(DID_1, false),
                new BoundIssuer(DID_2, true));
    }

    @Test
    void findAllTrustedMapsEveryRowTheRepositoryReturns() {
        var entity = mock(IssuerDidEntity.class);
        when(entity.getId()).thenReturn(ID);
        when(entity.getDid()).thenReturn(DID_1);

        when(issuerDidRepository.findAllTrusted()).thenReturn(List.of(entity));

        assertThat(service.findAllTrusted()).extracting("id", "did").containsExactly(tuple(ID, DID_1));
    }

    @Test
    void removeReturnsTheDidAndLastTrustedIssuerTrueForTheOnlyEntry() {
        var entity = mock(IssuerDidEntity.class);
        when(entity.getId()).thenReturn(ID);
        when(entity.getDid()).thenReturn(DID_1);
        when(issuerDidRepository.findAllTrusted()).thenReturn(List.of(entity));
        when(issuerDidRepository.markRemoved(ID)).thenReturn(true);

        assertThat(service.remove(ID)).isEqualTo(new TrustedIssuerRemoval(DID_1, true));
    }

    @Test
    void removeReturnsLastTrustedIssuerFalseWithTwoSeededEntries() {
        var target = mock(IssuerDidEntity.class);
        when(target.getId()).thenReturn(ID);
        when(target.getDid()).thenReturn(DID_1);

        var other = mock(IssuerDidEntity.class);
        when(other.getId()).thenReturn(ID + 1);
        when(other.getDid()).thenReturn(DID_2);

        when(issuerDidRepository.findAllTrusted()).thenReturn(List.of(target, other));
        when(issuerDidRepository.markRemoved(ID)).thenReturn(true);

        assertThat(service.remove(ID)).isEqualTo(new TrustedIssuerRemoval(DID_1, false));
    }

    @Test
    void removeOfAnUnknownOrRemovedIdThrowsNotFoundAndDoesNotUpdate() {
        when(issuerDidRepository.findAllTrusted()).thenReturn(List.of());

        assertThatThrownBy(() -> service.remove(ID)).isInstanceOf(NotFoundException.class);

        verify(issuerDidRepository, never()).markRemoved(anyInt());
    }

}
