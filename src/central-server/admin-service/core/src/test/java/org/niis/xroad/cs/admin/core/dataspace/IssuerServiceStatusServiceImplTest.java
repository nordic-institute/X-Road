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
import org.niis.xroad.common.exception.InternalServerErrorException;
import org.niis.xroad.cs.admin.api.domain.CanSignCause;
import org.niis.xroad.cs.admin.api.domain.CanSignResult;
import org.niis.xroad.cs.admin.api.domain.IssuerServiceStatus;
import org.niis.xroad.cs.admin.api.service.DataspaceIssuerDidService;
import org.niis.xroad.cs.admin.api.service.IssuerHostService;
import org.niis.xroad.cs.admin.api.service.IssuerSetUpLeaseService;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IssuerServiceStatusServiceImplTest {

    private static final String HOST = "issuer.example.org";
    private static final int DID_PORT = 6183;
    private static final String DID = "did:web:issuer.example.org%3A6183:issuer";
    private static final String NODE = "node-a";

    @Mock
    private IssuerHostService issuerHostService;
    @Mock
    private ConnectorCertificateCheck connectorCertificateCheck;
    @Mock
    private IssuerSetUpLeaseService issuerSetUpLeaseService;
    @Mock
    private IssuerProvisioningRpcClient issuerProvisioningRpcClient;
    @Mock
    private DataspaceIssuerProperties dataspaceIssuerProperties;
    @Mock
    private DataspaceIssuerDidService dataspaceIssuerDidService;

    private IssuerServiceStatusServiceImpl issuerServiceStatusService;

    @BeforeEach
    void setUp() {
        issuerServiceStatusService = new IssuerServiceStatusServiceImpl(issuerHostService, connectorCertificateCheck,
                issuerSetUpLeaseService, issuerProvisioningRpcClient, dataspaceIssuerProperties, dataspaceIssuerDidService);
    }

    @Test
    void composesTheRowsFromEverySourceWhenEverythingIsReady() {
        when(issuerHostService.getHost()).thenReturn(HOST);
        when(connectorCertificateCheck.isReadyFor(HOST)).thenReturn(true);
        when(issuerSetUpLeaseService.currentHolder()).thenReturn(Optional.empty());
        when(dataspaceIssuerProperties.getDidPort()).thenReturn(DID_PORT);
        when(dataspaceIssuerDidService.findAll()).thenReturn(List.of(DID));
        when(issuerProvisioningRpcClient.canSign(DataspaceIssuerProvisioningServiceImpl.ISSUER_PARTICIPANT_ID))
                .thenReturn(CanSignResult.canSign(DID));

        var status = issuerServiceStatusService.getStatus();

        assertThat(status.hostSet()).isTrue();
        assertThat(status.certificateReady()).isTrue();
        assertThat(status.runInProgress()).isFalse();
        assertThat(status.didToBind()).isEqualTo(DID);
        assertThat(status.didBound()).isTrue();
        assertThat(status.canSign()).isTrue();
        assertThat(status.canSignCause()).isNull();
        assertThat(status.overall()).isEqualTo(IssuerServiceStatus.Overall.READY);
    }

    @Test
    void readsTheIssuerHostExactlyOnce() {
        when(issuerHostService.getHost()).thenReturn(HOST);
        when(connectorCertificateCheck.isReadyFor(HOST)).thenReturn(true);
        when(issuerSetUpLeaseService.currentHolder()).thenReturn(Optional.empty());
        when(dataspaceIssuerProperties.getDidPort()).thenReturn(DID_PORT);
        when(dataspaceIssuerDidService.findAll()).thenReturn(List.of(DID));
        when(issuerProvisioningRpcClient.canSign(DataspaceIssuerProvisioningServiceImpl.ISSUER_PARTICIPANT_ID))
                .thenReturn(CanSignResult.canSign(DID));

        var status = issuerServiceStatusService.getStatus();

        verify(issuerHostService, times(1)).getHost();
        assertThat(status.host()).isEqualTo(HOST);
    }

    @Test
    void readsTheBoundIssuerDidsExactlyOnce() {
        when(issuerHostService.getHost()).thenReturn(HOST);
        when(connectorCertificateCheck.isReadyFor(HOST)).thenReturn(true);
        when(issuerSetUpLeaseService.currentHolder()).thenReturn(Optional.empty());
        when(dataspaceIssuerProperties.getDidPort()).thenReturn(DID_PORT);
        when(dataspaceIssuerDidService.findAll()).thenReturn(List.of(DID));
        when(issuerProvisioningRpcClient.canSign(DataspaceIssuerProvisioningServiceImpl.ISSUER_PARTICIPANT_ID))
                .thenReturn(CanSignResult.canSign(DID));

        issuerServiceStatusService.getStatus();

        verify(dataspaceIssuerDidService, times(1)).findAll();
    }

    @Test
    void readyWhenTheIssuerCanSignForAnyOfSeveralBoundDids() {
        var laterDid = "did:web:other.example.org%3A6183:issuer";
        when(issuerHostService.getHost()).thenReturn(HOST);
        when(connectorCertificateCheck.isReadyFor(HOST)).thenReturn(true);
        when(issuerSetUpLeaseService.currentHolder()).thenReturn(Optional.empty());
        when(dataspaceIssuerProperties.getDidPort()).thenReturn(DID_PORT);
        when(dataspaceIssuerDidService.findAll()).thenReturn(List.of(DID, laterDid));
        when(issuerProvisioningRpcClient.canSign(DataspaceIssuerProvisioningServiceImpl.ISSUER_PARTICIPANT_ID))
                .thenReturn(CanSignResult.canSign(laterDid));

        var status = issuerServiceStatusService.getStatus();

        assertThat(status.overall()).isEqualTo(IssuerServiceStatus.Overall.READY);
        assertThat(status.canSignCause()).isNull();
    }

    @Test
    void runInProgressReflectsWhicheverNodeHoldsTheLease() {
        when(issuerHostService.getHost()).thenReturn(HOST);
        when(connectorCertificateCheck.isReadyFor(HOST)).thenReturn(true);
        when(issuerSetUpLeaseService.currentHolder()).thenReturn(Optional.of(NODE));
        when(dataspaceIssuerProperties.getDidPort()).thenReturn(DID_PORT);
        when(issuerProvisioningRpcClient.canSign(DataspaceIssuerProvisioningServiceImpl.ISSUER_PARTICIPANT_ID))
                .thenReturn(CanSignResult.absent());

        var status = issuerServiceStatusService.getStatus();

        assertThat(status.runInProgress()).isTrue();
        assertThat(status.overall()).isEqualTo(IssuerServiceStatus.Overall.INITIALIZING);
    }

    @Test
    void certificateNotReadyAndDidToBindAreBothAbsentWhenTheHostIsNotSet() {
        when(issuerHostService.getHost()).thenReturn(null);
        when(issuerSetUpLeaseService.currentHolder()).thenReturn(Optional.empty());

        var status = issuerServiceStatusService.getStatus();

        assertThat(status.hostSet()).isFalse();
        assertThat(status.certificateReady()).isFalse();
        assertThat(status.didToBind()).isNull();
        assertThat(status.canSign()).isFalse();
        assertThat(status.canSignCause()).isNull();
        assertThat(status.overall()).isEqualTo(IssuerServiceStatus.Overall.NOT_CONFIGURED);
        // No host means the certificate check has nothing to validate against yet, and the "can sign"
        // probe has nothing to probe.
        verifyNoInteractions(connectorCertificateCheck);
        verifyNoInteractions(issuerProvisioningRpcClient);
    }

    @Test
    void cannotSignWithTheRpcCauseWhenTheIssuerServiceReportsItCannotSign() {
        when(issuerHostService.getHost()).thenReturn(HOST);
        when(connectorCertificateCheck.isReadyFor(HOST)).thenReturn(true);
        when(issuerSetUpLeaseService.currentHolder()).thenReturn(Optional.empty());
        when(dataspaceIssuerProperties.getDidPort()).thenReturn(DID_PORT);
        when(dataspaceIssuerDidService.findAll()).thenReturn(List.of(DID));
        when(issuerProvisioningRpcClient.canSign(DataspaceIssuerProvisioningServiceImpl.ISSUER_PARTICIPANT_ID))
                .thenReturn(CanSignResult.cannotSign(CanSignCause.DID_NOT_PUBLISHED));

        var status = issuerServiceStatusService.getStatus();

        assertThat(status.canSign()).isFalse();
        assertThat(status.canSignCause()).isEqualTo(CanSignCause.DID_NOT_PUBLISHED);
        assertThat(status.overall()).isEqualTo(IssuerServiceStatus.Overall.FAILED);
    }

    @Test
    void safelyReportsCannotSignWhenTheRpcClientThrows() {
        when(issuerHostService.getHost()).thenReturn(HOST);
        when(connectorCertificateCheck.isReadyFor(HOST)).thenReturn(true);
        when(issuerSetUpLeaseService.currentHolder()).thenReturn(Optional.empty());
        when(dataspaceIssuerProperties.getDidPort()).thenReturn(DID_PORT);
        when(dataspaceIssuerDidService.findAll()).thenReturn(List.of(DID));
        when(issuerProvisioningRpcClient.canSign(DataspaceIssuerProvisioningServiceImpl.ISSUER_PARTICIPANT_ID))
                .thenThrow(new IllegalStateException("channel is not ready"));

        var status = issuerServiceStatusService.getStatus();

        assertThat(status.canSign()).isFalse();
        assertThat(status.canSignCause()).isEqualTo(CanSignCause.ISSUER_SERVICE_UNREACHABLE);
        assertThat(status.overall()).isEqualTo(IssuerServiceStatus.Overall.FAILED);
    }

    @Test
    void certificateNotReadyWhenTheCertificateCheckFails() {
        when(issuerHostService.getHost()).thenReturn(HOST);
        when(connectorCertificateCheck.isReadyFor(HOST))
                .thenThrow(new InternalServerErrorException("OpenBao is unreachable"));
        when(issuerSetUpLeaseService.currentHolder()).thenReturn(Optional.empty());
        when(dataspaceIssuerProperties.getDidPort()).thenReturn(DID_PORT);
        when(issuerProvisioningRpcClient.canSign(DataspaceIssuerProvisioningServiceImpl.ISSUER_PARTICIPANT_ID))
                .thenReturn(CanSignResult.absent());

        var status = issuerServiceStatusService.getStatus();

        assertThat(status.certificateReady()).isFalse();
        assertThat(status.overall()).isEqualTo(IssuerServiceStatus.Overall.WAITING_FOR_CERTIFICATE);
    }
}
