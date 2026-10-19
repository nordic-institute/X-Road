/*
 * The MIT License
 * <p>
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.cs.admin.core.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.cs.admin.api.dto.HAConfigStatus;
import org.niis.xroad.cs.admin.api.service.DataspaceIssuerDidService;
import org.niis.xroad.cs.admin.api.service.SystemParameterService;
import org.niis.xroad.cs.admin.core.entity.SystemParameterEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.SystemParameterMapperImpl;
import org.niis.xroad.cs.admin.core.repository.SystemParameterRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IssuerHostServiceImplTest {

    @Mock
    SystemParameterService systemParameterService;
    @Mock
    DataspaceIssuerDidService dataspaceIssuerDidService;

    @InjectMocks
    IssuerHostServiceImpl issuerHostService;

    @Test
    void getStateReturnsUnboundWhenNoIssuerDidIsRegistered() {
        when(systemParameterService.getIssuerServiceHost()).thenReturn("cs.example.org");
        when(dataspaceIssuerDidService.findAll()).thenReturn(List.of());

        var state = issuerHostService.getState();

        assertEquals("cs.example.org", state.host());
        assertFalse(state.bound());
    }

    @Test
    void getStateReturnsNoHostWhenNoneIsSet() {
        when(systemParameterService.getIssuerServiceHost()).thenReturn(null);
        when(dataspaceIssuerDidService.findAll()).thenReturn(List.of());

        var state = issuerHostService.getState();

        assertNull(state.host());
        assertFalse(state.bound());
    }

    @Test
    void getStateReturnsBoundWhenAnIssuerDidIsRegistered() {
        when(systemParameterService.getIssuerServiceHost()).thenReturn("cs.example.org");
        when(dataspaceIssuerDidService.findAll()).thenReturn(List.of("did:web:cs.example.org"));

        var state = issuerHostService.getState();

        assertTrue(state.bound());
    }

    @Test
    void setHostStoresTheHostAndReturnsTheResultingState() {
        when(dataspaceIssuerDidService.findAll()).thenReturn(List.of());

        var state = issuerHostService.setHost("cs.example.org");

        verify(systemParameterService).updateOrCreateParameter(SystemParameterService.ISSUER_SERVICE_HOST, "cs.example.org");
        assertEquals("cs.example.org", state.host());
        assertFalse(state.bound());
    }

    /**
     * Exercises the real {@link SystemParameterServiceImpl} behind a fake repository so that a second,
     * identical {@code setHost} call is proven to update the one row created by the first call rather than
     * inserting another: a check-then-insert regression here would leave two rows for the same key.
     */
    @Test
    void setHostCalledTwiceLeavesOneSystemParameterRow() {
        var repository = new FakeSystemParameterRepository();
        var haConfigStatus = mock(HAConfigStatus.class);
        var realSystemParameterService = new SystemParameterServiceImpl(repository, haConfigStatus, new SystemParameterMapperImpl());
        var dataspaceIssuerDidServiceMock = mock(DataspaceIssuerDidService.class);
        when(dataspaceIssuerDidServiceMock.findAll()).thenReturn(List.of());
        var realIssuerHostService = new IssuerHostServiceImpl(realSystemParameterService, dataspaceIssuerDidServiceMock);

        realIssuerHostService.setHost("cs.example.org");
        realIssuerHostService.setHost("cs.example.org");

        assertEquals(1, repository.rowCount());
        assertEquals("cs.example.org", realIssuerHostService.getState().host());
    }

    /**
     * Proves {@code getState()} has no cache of its own: a value changed directly in {@code system_parameters},
     * bypassing {@code setHost}, is seen by the very next call.
     */
    @Test
    void getStateReadsTheDatabaseOnEachCall() {
        var repository = new FakeSystemParameterRepository();
        var haConfigStatus = mock(HAConfigStatus.class);
        var realSystemParameterService = new SystemParameterServiceImpl(repository, haConfigStatus, new SystemParameterMapperImpl());
        var dataspaceIssuerDidServiceMock = mock(DataspaceIssuerDidService.class);
        when(dataspaceIssuerDidServiceMock.findAll()).thenReturn(List.of());
        var realIssuerHostService = new IssuerHostServiceImpl(realSystemParameterService, dataspaceIssuerDidServiceMock);

        realIssuerHostService.setHost("first.example.org");
        assertEquals("first.example.org", realIssuerHostService.getState().host());

        repository.findByKey(SystemParameterService.ISSUER_SERVICE_HOST).get(0).setValue("second.example.org");

        assertEquals("second.example.org", realIssuerHostService.getState().host());
    }

    @Test
    void setHostCalledTwiceIssuesTwoUpdates() {
        when(dataspaceIssuerDidService.findAll()).thenReturn(List.of());

        issuerHostService.setHost("cs.example.org");
        issuerHostService.setHost("cs.example.org");

        verify(systemParameterService, times(2))
                .updateOrCreateParameter(SystemParameterService.ISSUER_SERVICE_HOST, "cs.example.org");
    }

    /**
     * Minimal in-memory stand-in for the {@code system_parameters} table. Rows are tracked by object
     * identity, as real persistence would by primary key: an entity instance only ever occupies one row,
     * however many times it is saved, while every distinct instance passed to {@link #save} becomes a new
     * row, exactly as a transient JPA entity would insert on first save.
     */
    private static final class FakeSystemParameterRepository implements SystemParameterRepository {
        private final List<SystemParameterEntity> rows = new ArrayList<>();

        @Override
        public Optional<SystemParameterEntity> findByKeyAndHaNodeName(String key, String haNodeName) {
            return rows.stream()
                    .filter(row -> key.equals(row.getKey()) && Objects.equals(haNodeName, row.getHaNodeName()))
                    .findFirst();
        }

        @Override
        public List<SystemParameterEntity> findByKey(String key) {
            return rows.stream().filter(row -> key.equals(row.getKey())).toList();
        }

        @Override
        public <S extends SystemParameterEntity> S save(S entity) {
            if (rows.stream().noneMatch(row -> row == entity)) {
                rows.add(entity);
            }
            return entity;
        }

        @Override
        public <S extends SystemParameterEntity> S saveAndFlush(S entity) {
            return save(entity);
        }

        @Override
        public <S extends SystemParameterEntity> Iterable<S> saveAll(Iterable<S> entities) {
            entities.forEach(this::save);
            return entities;
        }

        @Override
        public List<SystemParameterEntity> findAll() {
            return List.copyOf(rows);
        }

        @Override
        public Optional<SystemParameterEntity> findById(String entityId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(SystemParameterEntity entity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteById(String entityId) {
            throw new UnsupportedOperationException();
        }

        int rowCount() {
            return rows.size();
        }
    }
}
