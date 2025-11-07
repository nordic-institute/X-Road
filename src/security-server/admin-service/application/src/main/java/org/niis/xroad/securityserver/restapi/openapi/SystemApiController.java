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
package org.niis.xroad.securityserver.restapi.openapi;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.common.exception.InternalServerErrorException;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.AuditEventMethod;
import org.niis.xroad.restapi.config.audit.RestApiAuditEvent;
import org.niis.xroad.restapi.config.audit.RestApiAuditProperty;
import org.niis.xroad.restapi.openapi.ControllerUtil;
import org.niis.xroad.restapi.util.ResourceUtils;
import org.niis.xroad.securityserver.restapi.cache.CurrentSecurityServerId;
import org.niis.xroad.securityserver.restapi.cache.SecurityServerAddressChangeStatus;
import org.niis.xroad.securityserver.restapi.converter.AnchorConverter;
import org.niis.xroad.securityserver.restapi.converter.CertificateDetailsConverter;
import org.niis.xroad.securityserver.restapi.converter.MaintenanceModeConverter;
import org.niis.xroad.securityserver.restapi.converter.NodeTypeMapping;
import org.niis.xroad.securityserver.restapi.converter.TimestampingServiceConverter;
import org.niis.xroad.securityserver.restapi.converter.VersionConverter;
import org.niis.xroad.securityserver.restapi.dto.AnchorFile;
import org.niis.xroad.securityserver.restapi.dto.VersionInfo;
import org.niis.xroad.securityserver.restapi.openapi.model.AnchorDto;
import org.niis.xroad.securityserver.restapi.openapi.model.CertificateDetailsDto;
import org.niis.xroad.securityserver.restapi.openapi.model.DistinguishedNameDto;
import org.niis.xroad.securityserver.restapi.openapi.model.MaintenanceModeDto;
import org.niis.xroad.securityserver.restapi.openapi.model.MaintenanceModeMessageDto;
import org.niis.xroad.securityserver.restapi.openapi.model.NodeTypeDto;
import org.niis.xroad.securityserver.restapi.openapi.model.NodeTypeResponseDto;
import org.niis.xroad.securityserver.restapi.openapi.model.SecurityServerAddressDto;
import org.niis.xroad.securityserver.restapi.openapi.model.SecurityServerAddressStatusDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ServicePrioritizationStrategyDto;
import org.niis.xroad.securityserver.restapi.openapi.model.TimestampingServiceDto;
import org.niis.xroad.securityserver.restapi.openapi.model.VersionInfoDto;
import org.niis.xroad.securityserver.restapi.service.GlobalConfService;
import org.niis.xroad.securityserver.restapi.service.InternalTlsCertificateService;
import org.niis.xroad.securityserver.restapi.service.KeyNotFoundException;
import org.niis.xroad.securityserver.restapi.service.SystemService;
import org.niis.xroad.securityserver.restapi.service.VersionService;
import org.niis.xroad.serverconf.model.TimestampingService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Set;

import static org.niis.xroad.securityserver.restapi.exceptions.ErrorMessage.INTERNAL_KEY_CERT_INTERRUPTED;

/**
 * system api controller
 */
@Controller
@RequestMapping(ControllerUtil.API_V1_PREFIX)
@Slf4j
@PreAuthorize("denyAll")
@RequiredArgsConstructor
public class SystemApiController implements SystemApi {
    private final InternalTlsCertificateService internalTlsCertificateService;
    private final CertificateDetailsConverter certificateDetailsConverter;
    private final TimestampingServiceConverter timestampingServiceConverter;
    private final MaintenanceModeConverter maintenanceModeConverter;
    private final AnchorConverter anchorConverter;
    private final VersionConverter versionConverter;
    private final SystemService systemService;
    private final VersionService versionService;
    private final CurrentSecurityServerId currentSecurityServerId;
    private final GlobalConfService globalConfService;
    private final SecurityServerAddressChangeStatus addressChangeStatus;
    private final CsrFilenameCreator csrFilenameCreator;
    private final AuditDataHelper auditDataHelper;

    @Override
    @PreAuthorize("hasAuthority('EXPORT_INTERNAL_TLS_CERT')")
    public ResponseEntity<Resource> downloadSystemCertificate() {
        String filename = "certs.tar.gz";
        byte[] certificateTar = internalTlsCertificateService.exportInternalTlsCertificate();
        return ControllerUtil.createAttachmentResourceResponse(certificateTar, filename);
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_INTERNAL_TLS_CERT')")
    public ResponseEntity<CertificateDetailsDto> getSystemCertificate() {
        X509Certificate x509Certificate = internalTlsCertificateService.getInternalTlsCertificate();
        CertificateDetailsDto certificate = certificateDetailsConverter.convert(x509Certificate);
        return new ResponseEntity<>(certificate, HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_VERSION')")
    public ResponseEntity<VersionInfoDto> systemVersion() {
        VersionInfo versionInfo = versionService.getVersionInfo();
        VersionInfoDto result = versionConverter.convert(versionInfo);
        globalConfService.getGlobalConfigurationVersion().ifPresent(result::setGlobalConfigurationVersion);
        return ResponseEntity.ok(result);
    }

    @Override
    @PreAuthorize("hasAuthority('GENERATE_INTERNAL_TLS_KEY_CERT')")
    @AuditEventMethod(event = RestApiAuditEvent.GENERATE_INTERNAL_TLS_KEY_CERT)
    public ResponseEntity<Void> generateSystemTlsKeyAndCertificate() {
        try {
            internalTlsCertificateService.generateInternalTlsKeyAndCertificate();
        } catch (InterruptedException e) {
            throw new InternalServerErrorException(e, INTERNAL_KEY_CERT_INTERRUPTED.build());
        }
        return ControllerUtil.createCreatedResponse("/api/system/certificate", null);
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_TSPS')")
    public ResponseEntity<Set<TimestampingServiceDto>> getConfiguredTimestampingServices() {
        Set<TimestampingServiceDto> timestampingServiceDtos;
        List<TimestampingService> tsps = systemService.getConfiguredTimestampingServices();
        timestampingServiceDtos = timestampingServiceConverter.convert(tsps);

        return new ResponseEntity<>(timestampingServiceDtos, HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_TSPS')")
    public ResponseEntity<ServicePrioritizationStrategyDto> getTimestampingPrioritizationStrategy() {
        var strategy = systemService.getTimestampingPrioritizationStrategy();
        return new ResponseEntity<>(ServicePrioritizationStrategyDto.valueOf(strategy.name()), HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('ADD_TSP')")
    @AuditEventMethod(event = RestApiAuditEvent.ADD_TSP)
    public ResponseEntity<TimestampingServiceDto> addConfiguredTimestampingService(
            TimestampingServiceDto timestampingServiceToAdd) {

        systemService.addConfiguredTimestampingService(timestampingServiceConverter
                .convert(timestampingServiceToAdd));
        return new ResponseEntity<>(timestampingServiceToAdd, HttpStatus.CREATED);
    }

    @Override
    @PreAuthorize("hasAuthority('CHANGE_SS_ADDRESS')")
    @AuditEventMethod(event = RestApiAuditEvent.EDIT_SECURITY_SERVER_ADDRESS)
    public ResponseEntity<Void> addressChange(SecurityServerAddressDto securityServerAddressDto) {
        systemService.changeSecurityServerAddress(securityServerAddressDto.getAddress());
        return new ResponseEntity<>(HttpStatus.ACCEPTED);
    }

    @Override
    @PreAuthorize("hasAuthority('CHANGE_SS_ADDRESS')")
    public ResponseEntity<SecurityServerAddressStatusDto> getServerAddress() {
        String current = globalConfService.getSecurityServerAddress(currentSecurityServerId.getServerId());
        SecurityServerAddressStatusDto response = new SecurityServerAddressStatusDto();
        response.setCurrentAddress(new SecurityServerAddressDto(current));
        addressChangeStatus.getAddressChangeRequest()
                .map(SecurityServerAddressDto::new)
                .ifPresent(response::setRequestedChange);
        return ResponseEntity.ok(response);
    }

    @Override
    @PreAuthorize("hasAuthority('DELETE_TSP')")
    @AuditEventMethod(event = RestApiAuditEvent.DELETE_TSP)
    public ResponseEntity<Void> deleteConfiguredTimestampingService(TimestampingServiceDto timestampingServiceDto) {

        systemService.deleteConfiguredTimestampingService(timestampingServiceConverter
                .convert(timestampingServiceDto));

        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @PreAuthorize("hasAuthority('TOGGLE_MAINTENANCE_MODE')")
    @AuditEventMethod(event = RestApiAuditEvent.ENABLE_MAINTENANCE_MODE)
    @Override
    public ResponseEntity<Void> enableMaintenanceMode(MaintenanceModeMessageDto maintenanceModeMessageDto) {
        systemService.enableMaintenanceMode(maintenanceModeMessageDto.getMessage());
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAuthority('TOGGLE_MAINTENANCE_MODE')")
    @AuditEventMethod(event = RestApiAuditEvent.DISABLE_MAINTENANCE_MODE)
    @Override
    public ResponseEntity<Void> disableMaintenanceMode() {
        systemService.disableMaintenanceMode();
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAuthority('TOGGLE_MAINTENANCE_MODE')")
    @Override
    public ResponseEntity<MaintenanceModeDto> maintenanceMode() {
        return ResponseEntity.ok(
                maintenanceModeConverter.convert(systemService.getMaintenanceMode(), systemService.isManagementServiceProvider())
        );
    }

    @Override
    @PreAuthorize("hasAuthority('GENERATE_INTERNAL_TLS_CSR')")
    @AuditEventMethod(event = RestApiAuditEvent.GENERATE_INTERNAL_TLS_CSR)
    public ResponseEntity<Resource> generateSystemCertificateRequest(DistinguishedNameDto distinguishedName) {
        byte[] csrBytes = systemService.generateInternalCsr(distinguishedName.getName());
        return ControllerUtil.createAttachmentResourceResponse(
                csrBytes, csrFilenameCreator.createInternalCsrFilename());
    }

    @Override
    @PreAuthorize("hasAuthority('IMPORT_INTERNAL_TLS_CERT')")
    @AuditEventMethod(event = RestApiAuditEvent.IMPORT_INTERNAL_TLS_CERT)
    public ResponseEntity<CertificateDetailsDto> importSystemCertificate(Resource certificateResource) {
        // there's no filename since we only get a binary application/octet-stream.
        // Have audit log anyway (null behaves as no-op) in case different content type is added later
        String filename = certificateResource.getFilename();
        auditDataHelper.put(RestApiAuditProperty.CERT_FILE_NAME, filename);

        byte[] certificateBytes = ResourceUtils.springResourceToBytesOrThrowBadRequest(certificateResource);
        X509Certificate x509Certificate = null;
        try {
            x509Certificate = internalTlsCertificateService.importInternalTlsCertificate(certificateBytes);
        } catch (KeyNotFoundException e) {
            throw new BadRequestException(e);
        }
        CertificateDetailsDto certificateDetails = certificateDetailsConverter.convert(x509Certificate);
        return new ResponseEntity<>(certificateDetails, HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_ANCHOR')")
    public ResponseEntity<AnchorDto> getAnchor() {
        AnchorFile anchorFile = systemService.getAnchorFile();
        return new ResponseEntity<>(anchorConverter.convert(anchorFile), HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('DOWNLOAD_ANCHOR')")
    public ResponseEntity<Resource> downloadAnchor() {

        return ControllerUtil.createAttachmentResourceResponse(systemService.readAnchorFile(),
                systemService.getAnchorFilenameForDownload());
    }

    @Override
    @PreAuthorize("hasAuthority('UPLOAD_ANCHOR')")
    @AuditEventMethod(event = RestApiAuditEvent.UPLOAD_ANCHOR)
    public ResponseEntity<Void> replaceAnchor(Resource anchorResource) {
        byte[] anchorBytes = ResourceUtils.springResourceToBytesOrThrowBadRequest(anchorResource);

        systemService.replaceAnchor(anchorBytes);

        return ControllerUtil.createCreatedResponse("/api/system/anchor", null);
    }

    @Override
    @PreAuthorize("hasAuthority('UPLOAD_ANCHOR')")
    public ResponseEntity<AnchorDto> previewAnchor(Boolean verifyInstance, Resource anchorResource) {
        byte[] anchorBytes = ResourceUtils.springResourceToBytesOrThrowBadRequest(anchorResource);
        AnchorFile anchorFile = systemService.getAnchorFileFromBytes(anchorBytes, verifyInstance);

        return new ResponseEntity<>(anchorConverter.convert(anchorFile), HttpStatus.OK);
    }

    /**
     * For uploading an initial configuration anchor. The difference between this and {@link #replaceAnchor(Resource)}}
     * is that the anchor's instance does not get verified
     * @param anchorResource
     * @return
     */
    @Override
    @PreAuthorize("hasAuthority('INIT_CONFIG')")
    @AuditEventMethod(event = RestApiAuditEvent.INIT_ANCHOR)
    public ResponseEntity<Void> uploadInitialAnchor(Resource anchorResource) {
        byte[] anchorBytes = ResourceUtils.springResourceToBytesOrThrowBadRequest(anchorResource);

        systemService.uploadInitialAnchor(anchorBytes);

        return ControllerUtil.createCreatedResponse("/api/system/anchor", null);
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_NODE_TYPE')")
    public ResponseEntity<NodeTypeResponseDto> getNodeType() {
        // node type is never null so isPresent check can be omitted
        NodeTypeDto nodeTypeDto = NodeTypeMapping.map(systemService.getServerNodeType()).orElseThrow();
        NodeTypeResponseDto nodeTypeResponse = new NodeTypeResponseDto().nodeType(nodeTypeDto);
        return new ResponseEntity<>(nodeTypeResponse, HttpStatus.OK);
    }
}
