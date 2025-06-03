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
package org.niis.xroad.securityserver.restapi.service;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.InternalSSLKey;
import ee.ria.xroad.common.crypto.Digests;
import ee.ria.xroad.common.util.CertUtils;
import ee.ria.xroad.common.util.process.ProcessFailedException;
import ee.ria.xroad.common.util.process.ProcessNotExecutableException;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.bouncycastle.operator.OperatorCreationException;
import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.common.exception.ConflictException;
import org.niis.xroad.common.exception.InternalServerErrorException;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.model.ConfigurationAnchor;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.RestApiAuditProperty;
import org.niis.xroad.restapi.service.ConfigurationVerifier;
import org.niis.xroad.restapi.util.FormatUtils;
import org.niis.xroad.securityserver.restapi.cache.CurrentSecurityServerId;
import org.niis.xroad.securityserver.restapi.cache.MaintenanceModeStatus;
import org.niis.xroad.securityserver.restapi.cache.SecurityServerAddressChangeStatus;
import org.niis.xroad.securityserver.restapi.dto.AnchorFile;
import org.niis.xroad.securityserver.restapi.dto.MaintenanceMode;
import org.niis.xroad.securityserver.restapi.repository.AnchorRepository;
import org.niis.xroad.serverconf.impl.entity.TimestampingServiceEntity;
import org.niis.xroad.serverconf.impl.mapper.TimestampingServiceMapper;
import org.niis.xroad.serverconf.model.TimestampingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Optional;

import static ee.ria.xroad.common.ErrorCodes.X_MALFORMED_GLOBALCONF;
import static org.niis.xroad.common.exception.util.CommonDeviationMessage.MALFORMED_ANCHOR;
import static org.niis.xroad.securityserver.restapi.dto.MaintenanceMode.Status.DISABLED;
import static org.niis.xroad.securityserver.restapi.dto.MaintenanceMode.Status.DISABLING;
import static org.niis.xroad.securityserver.restapi.dto.MaintenanceMode.Status.ENABLED;
import static org.niis.xroad.securityserver.restapi.dto.MaintenanceMode.Status.ENABLING;
import static org.niis.xroad.securityserver.restapi.exceptions.ErrorMessage.ALREADY_DISABLED_MAINTENANCE_MODE;
import static org.niis.xroad.securityserver.restapi.exceptions.ErrorMessage.ALREADY_ENABLED_MAINTENANCE_MODE;
import static org.niis.xroad.securityserver.restapi.exceptions.ErrorMessage.ANCHOR_EXISTS;
import static org.niis.xroad.securityserver.restapi.exceptions.ErrorMessage.ANCHOR_UPLOAD_FAILED;
import static org.niis.xroad.securityserver.restapi.exceptions.ErrorMessage.DUPLICATE_ADDRESS_CHANGE_REQUEST;
import static org.niis.xroad.securityserver.restapi.exceptions.ErrorMessage.DUPLICATE_CONFIGURED_TIMESTAMPING_SERVICE;
import static org.niis.xroad.securityserver.restapi.exceptions.ErrorMessage.DUPLICATE_MAINTENANCE_MODE_CHANGE_REQUEST;
import static org.niis.xroad.securityserver.restapi.exceptions.ErrorMessage.FORBIDDEN_ENABLE_MAINTENANCE_MODE_FOR_MANAGEMENT_SERVICE;
import static org.niis.xroad.securityserver.restapi.exceptions.ErrorMessage.INTERNAL_ANCHOR_UPLOAD_INVALID_INSTANCE_ID;
import static org.niis.xroad.securityserver.restapi.exceptions.ErrorMessage.SAME_ADDRESS_CHANGE_REQUEST;

/**
 * Service that handles system services
 */
@Slf4j
@Service
@Transactional
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class SystemService {

    private final GlobalConfService globalConfService;
    private final ServerConfService serverConfService;
    private final AnchorRepository anchorRepository;
    private final ConfigurationVerifier configurationVerifier;
    private final CurrentSecurityServerId currentSecurityServerId;
    private final ManagementRequestSenderService managementRequestSenderService;
    private final AuditDataHelper auditDataHelper;
    private final SecurityServerAddressChangeStatus addressChangeStatus;
    private final MaintenanceModeStatus maintenanceModeStatus;
    private final GlobalConfProvider globalConfProvider;

    @Setter
    private String internalKeyPath = SystemProperties.getConfPath() + InternalSSLKey.PK_FILE_NAME;
    @Setter
    private String tempFilesPath = SystemProperties.getTempFilesPath();
    @Setter
    @Value("${script.internal-configuration-verifier.path}")
    private String internalConfVerificationScriptPath;

    private static final String ANCHOR_DOWNLOAD_FILENAME_PREFIX = "configuration_anchor_UTC_";
    private static final String ANCHOR_DOWNLOAD_DATE_TIME_FORMAT = "yyyy-MM-dd_HH_mm_ss";
    private static final String ANCHOR_DOWNLOAD_FILE_EXTENSION = ".xml";

    /**
     * Return a list of configured timestamping services
     * @return
     */
    public List<TimestampingService> getConfiguredTimestampingServices() {
        return TimestampingServiceMapper.get().toTargets(serverConfService.getConfiguredTimestampingServiceEntities());
    }

    /**
     * Audit log tsp name and url
     * @param timestampingService
     */
    private void auditLog(TimestampingService timestampingService) {
        auditDataHelper.put(RestApiAuditProperty.TSP_NAME, timestampingService.getName());
        auditDataHelper.put(RestApiAuditProperty.TSP_URL, timestampingService.getUrl());
    }

    public void addConfiguredTimestampingService(TimestampingService timestampingServiceToAdd)
            throws TimestampingServiceNotFoundException, DuplicateConfiguredTimestampingServiceException {
        auditLog(timestampingServiceToAdd);

        // Check that the timestamping service is an approved timestamping service
        Optional<TimestampingService> match = globalConfService.getApprovedTspsForThisInstance().stream()
                .filter(tsp -> timestampingServiceToAdd.getName().equals(tsp.getName())
                        && timestampingServiceToAdd.getUrl().equals(tsp.getUrl()))
                .findFirst();

        if (match.isEmpty()) {
            throw new TimestampingServiceNotFoundException(getExceptionMessage(timestampingServiceToAdd.getName(),
                    timestampingServiceToAdd.getUrl(), "not found"));
        }

        // Check that the timestamping service is not already configured
        Optional<TimestampingService> existingTsp = getConfiguredTimestampingServices().stream()
                .filter(tsp -> timestampingServiceToAdd.getName().equals(tsp.getName())
                        && timestampingServiceToAdd.getUrl().equals(tsp.getUrl()))
                .findFirst();

        if (existingTsp.isPresent()) {
            throw new DuplicateConfiguredTimestampingServiceException(
                    getExceptionMessage(timestampingServiceToAdd.getName(), timestampingServiceToAdd.getUrl(),
                            "is already configured")
            );
        }
        serverConfService.getConfiguredTimestampingServiceEntities()
                .add(TimestampingServiceMapper.get().toEntity(timestampingServiceToAdd));
    }

    /**
     * Deletes a configured timestamping service from serverconf
     * @param timestampingServiceToDelete
     * @throws TimestampingServiceNotFoundException
     */
    public void deleteConfiguredTimestampingService(TimestampingService timestampingServiceToDelete)
            throws TimestampingServiceNotFoundException {
        auditLog(timestampingServiceToDelete);

        List<TimestampingServiceEntity> configuredTimestampingServices = serverConfService.getConfiguredTimestampingServiceEntities();

        Optional<TimestampingServiceEntity> delete = configuredTimestampingServices.stream()
                .filter(tsp -> timestampingServiceToDelete.getName().equals(tsp.getName())
                        && timestampingServiceToDelete.getUrl().equals(tsp.getUrl()))
                .findFirst();

        if (delete.isEmpty()) {
            throw new TimestampingServiceNotFoundException(getExceptionMessage(timestampingServiceToDelete.getName(),
                    timestampingServiceToDelete.getUrl(), "not found")
            );
        }

        configuredTimestampingServices.remove(delete.get());
    }

    private String getExceptionMessage(String name, String url, String message) {
        StringBuilder sb = new StringBuilder();
        sb.append("Timestamping service with name ").append(name).append(" and url ").append(url);
        sb.append(" ").append(message);
        return sb.toString();
    }

    /**
     * Generate internal auth cert CSR
     * @param distinguishedName
     * @return
     * @throws InvalidDistinguishedNameException if {@code distinguishedName} does not conform to
     *                                           <a href="http://www.ietf.org/rfc/rfc1779.txt">RFC 1779</a> or
     *                                           <a href="http://www.ietf.org/rfc/rfc2253.txt">RFC 2253</a>
     */
    public byte[] generateInternalCsr(String distinguishedName) throws InvalidDistinguishedNameException {
        auditDataHelper.put(RestApiAuditProperty.SUBJECT_NAME, distinguishedName);
        try {
            KeyPair keyPair = CertUtils.readKeyPairFromPemFile(internalKeyPath);
            return CertUtils.generateCertRequest(keyPair.getPrivate(), keyPair.getPublic(), distinguishedName);
        } catch (IllegalArgumentException e) {
            throw new InvalidDistinguishedNameException(e);
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException | OperatorCreationException e) {
            throw new InternalServerErrorException(e);
        }
    }

    /**
     * Get configuration anchor file
     * @return
     * @throws AnchorFileNotFoundException if anchor file is not found
     */
    public AnchorFile getAnchorFile() throws AnchorFileNotFoundException {
        AnchorFile anchorFile = new AnchorFile(calculateAnchorHexHash(readAnchorFile()));
        ConfigurationAnchor anchor = anchorRepository.loadAnchorFromFile();
        anchorFile.setCreatedAt(FormatUtils.fromDateToOffsetDateTime(anchor.getGeneratedAt()));
        return anchorFile;
    }

    /**
     * Calculate the hex hash of the given anchor file. Used to verify/preview an anchor file before
     * uploading it
     * @param anchorBytes
     * @param shouldVerifyAnchorInstance if the anchor instance should be verified
     * @return
     * @throws InvalidAnchorInstanceException anchor is not generated in the current instance
     * @throws MalformedAnchorException       if the Anchor content is wrong
     */
    public AnchorFile getAnchorFileFromBytes(byte[] anchorBytes, boolean shouldVerifyAnchorInstance)
            throws InvalidAnchorInstanceException, MalformedAnchorException {
        ConfigurationAnchor anchor = createAnchorFromBytes(anchorBytes);
        if (shouldVerifyAnchorInstance) {
            verifyAnchorInstance(anchor);
        }
        AnchorFile anchorFile = new AnchorFile(calculateAnchorHexHash(anchorBytes));
        anchorFile.setCreatedAt(FormatUtils.fromDateToOffsetDateTime(anchor.getGeneratedAt()));
        return anchorFile;
    }

    /**
     * Upload a new configuration anchor. This method should be used when initializing a new Security Server.
     * This method will throw {@link AnchorAlreadyExistsException} if an anchor already exists. When updating an
     * existing anchor one should use {@link #replaceAnchor(byte[])} instead.
     * @param anchorBytes
     */
    public void uploadInitialAnchor(byte[] anchorBytes) {
        if (isAnchorImported()) {
            throw new AnchorAlreadyExistsException("Anchor already exists - cannot upload a second one");
        }
        uploadAnchor(anchorBytes, false);
    }

    /**
     * Replace the current configuration anchor with a new one. When uploading the first anchor (in Security Server
     * init phase) one should use {@link #uploadInitialAnchor(byte[])};
     * @param anchorBytes
     */
    public void replaceAnchor(byte[] anchorBytes) {
        uploadAnchor(anchorBytes, true);
    }

    /**
     * Upload a new configuration anchor. A temporary anchor file is created on the filesystem in order to run
     * the verification process with configuration-client module (via external script).
     * @param anchorBytes
     * @param shouldVerifyAnchorInstance whether the anchor instance should be verified or not. Usually it should
     *                                   always be verified (and this parameter should be true)
     *                                   but e.g. when initializing a new Security Server it    cannot be verified
     *                                   (and this parameter should be set to false)
     * @throws InvalidAnchorInstanceException anchor is not generated in the current instance
     * @throws AnchorUploadException          in case of external process exceptions
     * @throws MalformedAnchorException       if the Anchor content is wrong
     * @throws ConfigurationDownloadException if the configuration download request succeeds
     *                                        but configuration-client returns an error
     */
    // SonarQube: "InterruptedException" should not be ignored -> it has already been handled at this point
    @SuppressWarnings("squid:S2142")
    private void uploadAnchor(byte[] anchorBytes, boolean shouldVerifyAnchorInstance)
            throws InvalidAnchorInstanceException, AnchorUploadException, MalformedAnchorException,
                   ConfigurationDownloadException {
        auditDataHelper.calculateAndPutAnchorHash(anchorBytes);
        ConfigurationAnchor anchor = createAnchorFromBytes(anchorBytes);
        auditDataHelper.putDate(RestApiAuditProperty.GENERATED_AT, anchor.getGeneratedAt());
        if (shouldVerifyAnchorInstance) {
            verifyAnchorInstance(anchor);
        }
        File tempAnchor = null;
        try {
            tempAnchor = createTemporaryAnchorFile(anchorBytes);
            configurationVerifier.verifyConfiguration(internalConfVerificationScriptPath, tempAnchor.getAbsolutePath());
            anchorRepository.saveAndReplace(tempAnchor);
            globalConfService.executeDownloadConfigurationFromAnchor();
        } catch (InterruptedException | ProcessNotExecutableException | ProcessFailedException e) {
            throw new AnchorUploadException(e);
        } catch (IOException e) {
            throw new RuntimeException("Cannot upload a new anchor", e);
        } finally {
            if (tempAnchor != null) {
                boolean deleted = tempAnchor.delete();
                if (!deleted) {
                    log.error("Temporary anchor could not be deleted: {}", tempAnchor.getAbsolutePath());
                }
            }
        }
    }

    /**
     * Is global conf initialized -> it is if whe can find a Configuration anchor
     * @return
     */
    public boolean isAnchorImported() {
        try {
            AnchorFile anchorFile = getAnchorFile();
            if (anchorFile != null) {
                return true;
            }
        } catch (AnchorFileNotFoundException e) {
            // global conf does not exist
        }
        return false;
    }

    /**
     * Sends a management request to change Security Server address
     * @param newAddress new address
     * @return request ID in the central server database
     * @throws GlobalConfOutdatedException
     * @throws ManagementRequestSendingFailedException
     */
    public Integer changeSecurityServerAddress(String newAddress) throws GlobalConfOutdatedException,
                                                                         ManagementRequestSendingFailedException {
        auditDataHelper.put(RestApiAuditProperty.ADDRESS, newAddress);
        if (addressChangeStatus.getAddressChangeRequest().isPresent()) {
            throw new ConflictException(DUPLICATE_ADDRESS_CHANGE_REQUEST.build());
        }
        if (globalConfService.getSecurityServerAddress(currentSecurityServerId.getServerId()).equals(newAddress)) {
            throw new ConflictException(SAME_ADDRESS_CHANGE_REQUEST.build());
        }
        Integer requestId = managementRequestSenderService.sendAddressChangeRequest(newAddress);
        addressChangeStatus.setAddress(newAddress);

        return requestId;
    }

    /**
     * Simple helper to create a ConfigurationAnchorV2 instance from bytes
     * @param anchorBytes
     * @return
     * @throws MalformedAnchorException if the anchor is malformed or somehow invalid
     */
    private ConfigurationAnchor createAnchorFromBytes(byte[] anchorBytes) throws MalformedAnchorException {
        ConfigurationAnchor anchor = null;
        try {
            anchor = new ConfigurationAnchor(anchorBytes);
        } catch (CodedException ce) {
            if (isCausedByMalformedAnchorContent(ce)) {
                throw new MalformedAnchorException("Anchor is invalid");
            } else {
                throw ce;
            }
        }
        return anchor;
    }

    /**
     * Create a temporary anchor file on the filesystem. This is needed for verifying the anchor with
     * configuration-client module (this might be changed in the future). This method does not delete the created
     * temporary file. Remember to delete the file after it is no longer needed.
     * @param anchorBytes
     * @return temporary anchor file
     * @throws IOException if temp file creation fails
     */
    private File createTemporaryAnchorFile(byte[] anchorBytes) throws IOException {
        try {
            String tempAnchorPrefix = "temp-internal-anchor-";
            String tempAnchorSuffix = ".xml";
            File tempDirectory = tempFilesPath != null ? new File(tempFilesPath) : null;
            File tempAnchor = File.createTempFile(tempAnchorPrefix, tempAnchorSuffix, tempDirectory);
            FileUtils.writeByteArrayToFile(tempAnchor, anchorBytes);
            return tempAnchor;
        } catch (Exception e) {
            log.error("Creating temporary anchor file failed", e);
            throw e;
        }
    }

    /**
     * Verify that the anchor has been generated in the current instance
     * @param anchor
     * @throws InvalidAnchorInstanceException anchor is not generated in the current instance
     */
    private void verifyAnchorInstance(ConfigurationAnchor anchor) throws InvalidAnchorInstanceException {
        String anchorInstanceId = anchor.getInstanceIdentifier();
        String ownerInstance = currentSecurityServerId.getServerId().getOwner().getXRoadInstance();
        if (!anchorInstanceId.equals(ownerInstance)) {
            String errorMessage = String.format("Cannot upload an anchor from instance %s into instance %s",
                    anchorInstanceId, ownerInstance);
            throw new InvalidAnchorInstanceException(errorMessage);
        }
    }

    /**
     * Read anchor file's content
     * @return
     * @throws AnchorFileNotFoundException if anchor file is not found
     */
    public byte[] readAnchorFile() throws AnchorFileNotFoundException {
        try {
            return anchorRepository.readAnchorFile();
        } catch (NoSuchFileException e) {
            throw new AnchorFileNotFoundException("Anchor file not found", e);
        }
    }

    /**
     * Generate anchor file download name with the anchor file created at date/time. The name format is:
     * "configuration_anchor_UTC_yyyy-MM-dd_HH_mm_ss.xml".
     * @return
     */
    public String getAnchorFilenameForDownload() {
        DateFormat df = new SimpleDateFormat(ANCHOR_DOWNLOAD_DATE_TIME_FORMAT);
        ConfigurationAnchor anchor = anchorRepository.loadAnchorFromFile();
        return ANCHOR_DOWNLOAD_FILENAME_PREFIX + df.format(anchor.getGeneratedAt()) + ANCHOR_DOWNLOAD_FILE_EXTENSION;
    }

    /**
     * Return anchor file's hash as a hex string
     * @return
     */
    private String calculateAnchorHexHash(byte[] anchor) {
        try {
            return Digests.hexDigest(Digests.DEFAULT_ANCHOR_HASH_ALGORITHM_ID, anchor).toUpperCase();
        } catch (Exception e) {
            log.error("can't create hex digest for anchor file");
            throw new RuntimeException(e);
        }
    }

    static boolean isCausedByMalformedAnchorContent(CodedException e) {
        return (X_MALFORMED_GLOBALCONF).equals(e.getFaultCode());
    }

    /**
     * Return the node type of the server
     * @return server node type
     */
    public SystemProperties.NodeType getServerNodeType() {
        return SystemProperties.getServerNodeType();
    }

    public boolean isManagementServiceProvider() {
        var managementRequestService = globalConfProvider.getManagementRequestService();
        return globalConfService.isSecurityServerClientForThisInstance(managementRequestService);
    }

    public void enableMaintenanceMode(String message) {
        auditDataHelper.put(RestApiAuditProperty.MESSAGE, message);

        var mode = getMaintenanceMode();

        if (isManagementServiceProvider()) {
            throw new ConflictException(FORBIDDEN_ENABLE_MAINTENANCE_MODE_FOR_MANAGEMENT_SERVICE.build());
        }

        if (mode.status() == DISABLING || mode.status() == ENABLING) {
            throw new ConflictException(DUPLICATE_MAINTENANCE_MODE_CHANGE_REQUEST.build());
        }

        if (mode.status() == ENABLED) {
            throw new ConflictException(ALREADY_ENABLED_MAINTENANCE_MODE.build());
        }

        Integer requestId = managementRequestSenderService.sendMaintenanceModeEnableRequest(message);
        maintenanceModeStatus.enableRequested(message);
        auditDataHelper.putManagementRequestId(requestId);
    }

    public void disableMaintenanceMode() {
        var mode = getMaintenanceMode();

        if (mode.status() == DISABLING || mode.status() == ENABLING) {
            throw new ConflictException(DUPLICATE_MAINTENANCE_MODE_CHANGE_REQUEST.build());
        }

        if (mode.status() == DISABLED) {
            throw new ConflictException(ALREADY_DISABLED_MAINTENANCE_MODE.build());
        }

        Integer requestId = managementRequestSenderService.sendMaintenanceModeDisableRequest();
        maintenanceModeStatus.disableRequested();
        auditDataHelper.putManagementRequestId(requestId);
    }

    public MaintenanceMode getMaintenanceMode() {
        return switch (maintenanceModeStatus.getStatus()) {
            case MaintenanceModeStatus.EnableRequested enableRequested -> new MaintenanceMode(ENABLING, enableRequested.message());
            case MaintenanceModeStatus.DisableRequested ignore -> new MaintenanceMode(DISABLING, null);
            case null -> {
                var securityServerId = serverConfService.getSecurityServerId();

                yield globalConfProvider.getMaintenanceMode(securityServerId)
                        .map(mode -> new MaintenanceMode(mode.enabled() ? ENABLED : DISABLED, mode.message()))
                        .orElseGet(() -> new MaintenanceMode(DISABLED, null));
            }
        };
    }

    /**
     * Thrown when attempt to add timestamping service that is already configured
     */
    public static class DuplicateConfiguredTimestampingServiceException extends ConflictException {
        public DuplicateConfiguredTimestampingServiceException(String s) {
            super(s, DUPLICATE_CONFIGURED_TIMESTAMPING_SERVICE.build());
        }
    }

    /**
     * Thrown when attempting to upload an anchor from a wrong instance
     */
    public static class InvalidAnchorInstanceException extends BadRequestException {
        public InvalidAnchorInstanceException(String s) {
            super(s, INTERNAL_ANCHOR_UPLOAD_INVALID_INSTANCE_ID.build());
        }
    }

    /**
     * Thrown when uploading a conf anchor fails
     */
    public static class AnchorUploadException extends InternalServerErrorException {
        public AnchorUploadException(Throwable t) {
            super(t, ANCHOR_UPLOAD_FAILED.build());
        }
    }

    /**
     * Thrown e.g. if Anchor upload or preview fails because of invalid content
     */
    public static class MalformedAnchorException extends BadRequestException {
        public MalformedAnchorException(String s) {
            super(s, MALFORMED_ANCHOR.build());
        }
    }

    /**
     * Thrown if user tries to upload a new anchor instead of updating the old
     */
    public static class AnchorAlreadyExistsException extends ConflictException {
        public AnchorAlreadyExistsException(String s) {
            super(s, ANCHOR_EXISTS.build());
        }
    }
}
