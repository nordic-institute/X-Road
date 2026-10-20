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
package org.niis.xroad.cs.admin.application.dataspace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.properties.config.XRoadConfig;
import org.niis.xroad.cs.admin.api.dto.HAConfigStatus;
import org.niis.xroad.cs.admin.api.service.DataspaceIssuerDidService;
import org.niis.xroad.cs.admin.api.service.DataspaceIssuerProvisioningService;
import org.niis.xroad.cs.admin.api.service.DsTlsCertificationAuthoritiesService;
import org.niis.xroad.cs.admin.api.service.SystemParameterService;
import org.niis.xroad.cs.admin.application.connectortls.CentralServerConnectorTlsAcmeHostContext;
import org.niis.xroad.cs.admin.core.dataspace.DataspaceIssuerProperties;
import org.niis.xroad.cs.admin.core.dataspace.DataspaceIssuerProvisioningWorker;
import org.niis.xroad.cs.admin.core.entity.SystemParameterEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.SystemParameterMapperImpl;
import org.niis.xroad.cs.admin.core.repository.SystemParameterRepository;
import org.niis.xroad.cs.admin.core.service.IssuerHostServiceImpl;
import org.niis.xroad.cs.admin.core.service.SystemParameterServiceImpl;
import org.niis.xroad.cs.admin.core.validation.DnsNameValidator;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises {@link CentralServerConnectorTlsAcmeHostContext} and {@link DataspaceIssuerProvisioningWorker} behind
 * the real {@link DataspaceIssuerProperties}/{@link IssuerHostServiceImpl} chain, proving both readers see a host
 * set through {@link org.niis.xroad.cs.admin.api.service.IssuerHostService#setHost} immediately, with nothing cached
 * at any layer in between.
 */
@ExtendWith(MockitoExtension.class)
class DataspaceIssuerHostReadersIntegrationTest {

    @Mock
    private DsTlsCertificationAuthoritiesService connectorTlsCertificationAuthoritiesService;
    @Mock
    private DataspaceIssuerDidService dataspaceIssuerDidService;
    @Mock
    private DataspaceIssuerProvisioningService dataspaceIssuerProvisioningService;
    @Mock
    private TaskScheduler taskScheduler;

    private CentralServerConnectorTlsAcmeHostContext acmeHostContext;
    private DataspaceIssuerProvisioningWorker provisioningWorker;
    private IssuerHostServiceImpl issuerHostService;

    @BeforeEach
    void setUp() {
        lenient().when(taskScheduler.getClock()).thenReturn(Clock.systemUTC());

        var repository = new FakeSystemParameterRepository();
        var systemParameterService =
                new SystemParameterServiceImpl(repository, mock(HAConfigStatus.class), new SystemParameterMapperImpl());
        systemParameterService.updateOrCreateParameter(SystemParameterService.INSTANCE_IDENTIFIER, "TEST");
        systemParameterService.updateOrCreateParameter(SystemParameterService.CENTRAL_SERVER_ADDRESS, "cs.example");
        when(dataspaceIssuerDidService.findAll()).thenReturn(List.of());

        issuerHostService = new IssuerHostServiceImpl(systemParameterService, dataspaceIssuerDidService, new DnsNameValidator());
        var dataspaceIssuerProperties = new DataspaceIssuerProperties(mock(XRoadConfig.class), issuerHostService);
        acmeHostContext = new CentralServerConnectorTlsAcmeHostContext(
                dataspaceIssuerProperties, connectorTlsCertificationAuthoritiesService);
        provisioningWorker = new DataspaceIssuerProvisioningWorker(dataspaceIssuerProvisioningService, systemParameterService,
                dataspaceIssuerProperties, taskScheduler);
    }

    @Test
    void readersSkipWhenNoHostIsSet() {
        assertThat(acmeHostContext.getPublicHostname()).isNull();

        ReflectionTestUtils.invokeMethod(provisioningWorker, "scheduledProvision");

        verify(dataspaceIssuerProvisioningService, never()).provisionIssuer();
        assertThat(provisioningWorker.getState().status()).isEqualTo(DataspaceIssuerProvisioningWorker.Status.WAITING_FOR_CONFIGURATION);
    }

    @Test
    void readersSeeEachSetHostImmediatelyWithoutRestart() {
        issuerHostService.setHost("first.example.org");

        assertThat(acmeHostContext.getPublicHostname()).isEqualTo("first.example.org");
        assertThat((boolean) ReflectionTestUtils.invokeMethod(provisioningWorker, "preconditionsMet")).isTrue();

        issuerHostService.setHost("second.example.org");

        assertThat(acmeHostContext.getPublicHostname()).isEqualTo("second.example.org");
        assertThat((boolean) ReflectionTestUtils.invokeMethod(provisioningWorker, "preconditionsMet")).isTrue();
    }

    /**
     * Minimal in-memory stand-in for the {@code system_parameters} table, mirroring
     * {@code IssuerHostServiceImplTest}'s fake: rows are tracked by object identity, as real persistence would by
     * primary key.
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
    }
}
